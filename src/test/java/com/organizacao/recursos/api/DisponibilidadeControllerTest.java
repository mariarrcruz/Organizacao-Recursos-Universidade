package com.organizacao.recursos.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.organizacao.recursos.dominio.EstadoReserva;
import com.organizacao.recursos.dominio.Manutencao;
import com.organizacao.recursos.dominio.Perfil;
import com.organizacao.recursos.dominio.Recurso;
import com.organizacao.recursos.dominio.Reserva;
import com.organizacao.recursos.dominio.StatusRecurso;
import com.organizacao.recursos.dominio.TipoRecurso;
import com.organizacao.recursos.dominio.Usuario;
import com.organizacao.recursos.persistencia.ArmazenamentoMemoria;
import com.organizacao.recursos.servico.ServicoAutenticacao;
import com.organizacao.recursos.servico.ServicoReservas;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Consulta de disponibilidade (especificação: "O Solicitante consulta a disponibilidade").
 * Responde só se o recurso está livre, sem revelar quem reservou.
 */
class DisponibilidadeControllerTest {

    private static final Clock RELOGIO =
            Clock.fixed(LocalDateTime.of(2026, 11, 1, 8, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

    private ServicoAutenticacao auth;
    private ArmazenamentoMemoria armazenamento;
    private ServicoReservas servico;
    private MockMvc mvc;

    private final Recurso sala = new Recurso("SALA-D", TipoRecurso.SALA, "Sala D", "BLOCO_A", 30, false,
            StatusRecurso.DISPONIVEL, Set.of());

    @BeforeEach
    void montar() {
        auth = new ServicoAutenticacao();
        armazenamento = new ArmazenamentoMemoria();
        servico = new ServicoReservas(RELOGIO, armazenamento);
        armazenamento.gravarRecurso(sala);
        mvc = MockMvcBuilders.standaloneSetup(new DisponibilidadeController(servico, auth, armazenamento))
                .setControllerAdvice(new TratadorErros()).build();
    }

    private String token(String sub, String perfil) {
        return "Bearer " + auth.gerarToken(sub, List.of(perfil), Instant.now().plusSeconds(3600));
    }

    private MvcResult consultar(String token, String recurso, String inicio, String fim) throws Exception {
        var req = get("/disponibilidade").param("recursoId", recurso).param("inicio", inicio).param("fim", fim);
        if (token != null) {
            req.header(HttpHeaders.AUTHORIZATION, token);
        }
        return mvc.perform(req).andReturn();
    }

    @Test
    @DisplayName("Disponibilidade — sem token: 401")
    void semToken_retorna401() throws Exception {
        assertEquals(401, consultar(null, "SALA-D", "2026-11-05T10:00", "2026-11-05T12:00").getResponse().getStatus());
    }

    @Test
    @DisplayName("Disponibilidade — Solicitante consulta horário livre: disponível")
    void solicitante_horarioLivre_disponivel() throws Exception {
        MvcResult r = consultar(token("bob", "SOLICITANTE"), "SALA-D", "2026-11-05T10:00", "2026-11-05T12:00");
        assertEquals(200, r.getResponse().getStatus());
        assertTrue(r.getResponse().getContentAsString().contains("\"disponivel\":true"));
    }

    @Test
    @DisplayName("Disponibilidade — horário sobreposto a reserva ativa: indisponível, sem revelar quem reservou")
    void reservaSobreposta_indisponivel_semVazarSolicitante() throws Exception {
        servico.registrarExistente(new Reserva("R1", new Usuario("alice", Set.of(Perfil.SOLICITANTE)), sala,
                LocalDateTime.parse("2026-11-05T10:00"), LocalDateTime.parse("2026-11-05T12:00"),
                EstadoReserva.APROVADA));
        MvcResult r = consultar(token("bob", "SOLICITANTE"), "SALA-D", "2026-11-05T11:00", "2026-11-05T13:00");
        assertEquals(200, r.getResponse().getStatus());
        String corpo = r.getResponse().getContentAsString();
        assertTrue(corpo.contains("\"disponivel\":false"));
        assertFalse(corpo.contains("alice"), "a resposta não deve revelar o solicitante");
    }

    @Test
    @DisplayName("Disponibilidade — manutenção sobreposta: indisponível")
    void manutencao_indisponivel() throws Exception {
        servico.registrarManutencaoExistente(new Manutencao("SALA-D", LocalDateTime.parse("2026-11-05T10:00"),
                LocalDateTime.parse("2026-11-05T12:00")));
        MvcResult r = consultar(token("bob", "SOLICITANTE"), "SALA-D", "2026-11-05T10:30", "2026-11-05T11:00");
        assertTrue(r.getResponse().getContentAsString().contains("\"disponivel\":false"));
    }

    @Test
    @DisplayName("Disponibilidade — recurso inexistente: 404")
    void recursoInexistente_retorna404() throws Exception {
        assertEquals(404, consultar(token("bob", "SOLICITANTE"), "NAO-EXISTE", "2026-11-05T10:00",
                "2026-11-05T12:00").getResponse().getStatus());
    }

    @Test
    @DisplayName("Disponibilidade — fim antes do início: 400")
    void periodoInvalido_retorna400() throws Exception {
        assertEquals(400, consultar(token("bob", "SOLICITANTE"), "SALA-D", "2026-11-05T12:00",
                "2026-11-05T10:00").getResponse().getStatus());
    }

    @Test
    @DisplayName("Disponibilidade — Responsável e Administrador também consultam: 200")
    void demaisPerfis_consultam() throws Exception {
        assertEquals(200, consultar(token("resp", "RESPONSAVEL"), "SALA-D", "2026-11-05T10:00",
                "2026-11-05T12:00").getResponse().getStatus());
        assertEquals(200, consultar(token("adm", "ADMINISTRADOR"), "SALA-D", "2026-11-05T10:00",
                "2026-11-05T12:00").getResponse().getStatus());
    }
}
