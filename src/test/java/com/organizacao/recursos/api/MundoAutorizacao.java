package com.organizacao.recursos.api;

import com.organizacao.recursos.dominio.EstadoReserva;
import com.organizacao.recursos.dominio.Perfil;
import com.organizacao.recursos.dominio.Recurso;
import com.organizacao.recursos.dominio.Reserva;
import com.organizacao.recursos.dominio.StatusRecurso;
import com.organizacao.recursos.dominio.TipoRecurso;
import com.organizacao.recursos.dominio.Usuario;
import com.organizacao.recursos.persistencia.Armazenamento;
import com.organizacao.recursos.persistencia.ArmazenamentoMemoria;
import com.organizacao.recursos.servico.ServicoAutenticacao;
import com.organizacao.recursos.servico.ServicoMovimentacoes;
import com.organizacao.recursos.servico.ServicoRelatorios;
import com.organizacao.recursos.servico.ServicoReservas;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Massa fixa para os testes de autorização e de validação. Relógio em 05/11/2026 10:30 UTC.
 *
 * <p>Reservas (todas de "alice", Solicitante dono):
 * RA restrita SOLICITADA 10-12 (SALA-R) · RE APROVADA 10-12 (SALA-P) · RF APROVADA 14-16 (SALA-M)
 * · RG SOLICITADA 15-16 (SALA-N) · RH SOLICITADA 17-18 (SALA-O) · RD EM_USO 10-12 (SALA-Q).
 *
 * <p>Vínculos: "resp" é vinculado a todos os recursos; "resp2" não é vinculado a nenhum.
 */
final class MundoAutorizacao {

    static final Clock RELOGIO =
            Clock.fixed(LocalDateTime.of(2026, 11, 5, 10, 30).toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

    private static final List<String> SALAS_LIVRES = List.of("SALA-L", "SALA-M", "SALA-N", "SALA-O", "SALA-P",
            "SALA-Q", "SALA-V");

    final ArmazenamentoMemoria armazenamento = new ArmazenamentoMemoria();
    final ServicoAutenticacao auth = new ServicoAutenticacao();
    final ServicoReservas servico = new ServicoReservas(RELOGIO, armazenamento);
    final ServicoRelatorios relatorios = new ServicoRelatorios(servico, new ServicoMovimentacoes(servico));
    final Usuario administrador = new Usuario("adm", Set.of(Perfil.ADMINISTRADOR));

    MundoAutorizacao() {
        for (String id : SALAS_LIVRES) {
            armazenamento.gravarRecurso(recurso(id, false));
        }
        armazenamento.gravarRecurso(recurso("SALA-R", true));
        Usuario alice = new Usuario("alice", Set.of(Perfil.SOLICITANTE));
        servico.registrarExistente(reserva("RA", alice, "SALA-R", "10:00", "12:00", EstadoReserva.SOLICITADA));
        servico.registrarExistente(reserva("RE", alice, "SALA-P", "10:00", "12:00", EstadoReserva.APROVADA));
        servico.registrarExistente(reserva("RF", alice, "SALA-M", "14:00", "16:00", EstadoReserva.APROVADA));
        servico.registrarExistente(reserva("RG", alice, "SALA-N", "15:00", "16:00", EstadoReserva.SOLICITADA));
        servico.registrarExistente(reserva("RH", alice, "SALA-O", "17:00", "18:00", EstadoReserva.SOLICITADA));
        servico.registrarExistente(reserva("RD", alice, "SALA-Q", "10:00", "12:00", EstadoReserva.EM_USO));
        for (String id : SALAS_LIVRES) {
            servico.vincularResponsavel(administrador, "resp", id);
        }
        servico.vincularResponsavel(administrador, "resp", "SALA-R");
    }

    MockMvc mvc() {
        return MockMvcBuilders.standaloneSetup(
                        new RecursosController(auth, armazenamento),
                        new ReservasController(servico, auth, armazenamento),
                        new ManutencaoController(servico, auth),
                        new RelatoriosController(relatorios, auth),
                        new DisponibilidadeController(servico, auth, armazenamento),
                        new VinculosController(servico, auth))
                .setControllerAdvice(new TratadorErros())
                .build();
    }

    /** Cabeçalho Authorization para um perfil de teste, ou null para anônimo. */
    String token(String perfil) {
        String sub;
        String papel;
        switch (perfil) {
            case "anon" -> { return null; }
            case "solicitante-dono", "solicitante-terceiro" -> {
                sub = perfil.equals("solicitante-dono") ? "alice" : "bob";
                papel = "SOLICITANTE";
            }
            case "responsavel-vinculado" -> { sub = "resp"; papel = "RESPONSAVEL"; }
            case "responsavel-nao-vinculado" -> { sub = "resp2"; papel = "RESPONSAVEL"; }
            case "administrador" -> { sub = "adm"; papel = "ADMINISTRADOR"; }
            default -> throw new IllegalArgumentException("perfil desconhecido: " + perfil);
        }
        return "Bearer " + auth.gerarToken(sub, List.of(papel), Instant.now().plusSeconds(3600));
    }

    static Recurso recurso(String id, boolean restrito) {
        return new Recurso(id, TipoRecurso.SALA, "Recurso " + id, "BLOCO_A", 30, restrito,
                StatusRecurso.DISPONIVEL, Set.of());
    }

    private static Reserva reserva(String id, Usuario dono, String recurso, String ini, String fim,
                                   EstadoReserva estado) {
        return new Reserva(id, dono, recurso(recurso, recurso.equals("SALA-R")), LocalDateTime.parse("2026-11-05T" + ini),
                LocalDateTime.parse("2026-11-05T" + fim), estado);
    }
}
