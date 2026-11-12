package com.organizacao.recursos.persistencia;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.organizacao.recursos.dominio.ErroAplicacao;
import com.organizacao.recursos.dominio.Manutencao;
import com.organizacao.recursos.dominio.Perfil;
import com.organizacao.recursos.dominio.Recurso;
import com.organizacao.recursos.dominio.Reserva;
import com.organizacao.recursos.dominio.StatusRecurso;
import com.organizacao.recursos.dominio.TipoRecurso;
import com.organizacao.recursos.dominio.Usuario;
import com.organizacao.recursos.servico.ServicoReservas;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * RN-04 sob concorrência: recurso com manutenção já registrada não aceita reserva, mesmo quando
 * 20 solicitações chegam ao mesmo tempo. Cada solicitação usa a sua própria instância do serviço,
 * e a única coordenação entre elas é o PostgreSQL (lock por recurso e verificação de manutenção).
 */
@Testcontainers
class ConcorrenciaManutencaoReservaPostgresTest {

    private static final int SOLICITACOES = 20;
    private static final Clock RELOGIO =
            Clock.fixed(LocalDateTime.of(2026, 11, 5, 9, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    private static final Usuario ADMIN = new Usuario("adm", Set.of(Perfil.ADMINISTRADOR));

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static ArmazenamentoPostgres base;

    @BeforeAll
    static void criarEsquema() {
        base = new ArmazenamentoPostgres(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        base.criarEsquema();
    }

    @Test
    @DisplayName("RN-04 sob concorrência: 20 reservas simultâneas em período de manutenção são todas recusadas")
    void reservasSimultaneasEmManutencaoSaoRecusadas() throws Exception {
        String id = "SALA-M-" + UUID.randomUUID();
        Recurso sala = new Recurso(id, TipoRecurso.SALA, "Sala manutenção", "BLOCO_A", 30, false,
                StatusRecurso.DISPONIVEL, Set.of());
        base.gravarRecurso(sala);
        new ServicoReservas(RELOGIO, base).criarManutencao(ADMIN,
                new Manutencao(id, LocalDateTime.parse("2026-11-20T10:00"), LocalDateTime.parse("2026-11-20T12:00")));

        Usuario solicitante = new Usuario("prof", Set.of(Perfil.SOLICITANTE));
        ExecutorService pool = Executors.newFixedThreadPool(SOLICITACOES);
        CountDownLatch partida = new CountDownLatch(1);
        List<Future<String>> futuros = new ArrayList<>();
        try {
            for (int i = 0; i < SOLICITACOES; i++) {
                futuros.add(pool.submit(() -> {
                    ServicoReservas instancia = new ServicoReservas(RELOGIO, new ArmazenamentoPostgres(
                            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
                    partida.await();
                    try {
                        instancia.solicitar(solicitante, sala, LocalDateTime.parse("2026-11-20T10:30"),
                                LocalDateTime.parse("2026-11-20T11:30"));
                        return "ACEITA";
                    } catch (ErroAplicacao e) {
                        return e.getStatus() == 409 ? "RECUSADA" : "INESPERADA " + e.getStatus();
                    }
                }));
            }
            partida.countDown();
            int aceitas = 0;
            int recusadas = 0;
            for (Future<String> f : futuros) {
                String r = f.get(60, TimeUnit.SECONDS);
                if (r.equals("ACEITA")) aceitas++;
                if (r.equals("RECUSADA")) recusadas++;
                assertEquals(false, r.startsWith("INESPERADA"), r);
            }
            assertEquals(0, aceitas, "nenhuma reserva pode ser aceita em período de manutenção");
            assertEquals(SOLICITACOES, recusadas);
        } finally {
            pool.shutdownNow();
        }
        // Estado final do banco: nenhuma reserva gravada para o recurso
        List<Reserva> gravadas = base.listarReservas().stream()
                .filter(r -> r.getRecurso().id().equals(id)).toList();
        assertEquals(0, gravadas.size());
    }
}
