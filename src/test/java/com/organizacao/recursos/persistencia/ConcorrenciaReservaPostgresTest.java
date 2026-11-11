package com.organizacao.recursos.persistencia;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.organizacao.recursos.dominio.ErroAplicacao;
import com.organizacao.recursos.dominio.TipoRecurso;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * RN-02 / RN-03: vinte solicitações simultâneas para o mesmo recurso e horário geram exatamente uma reserva.
 * PostgreSQL real em contêiner (Testcontainers), sem H2. O resultado é verificado por consulta SQL.
 */
@Testcontainers
class ConcorrenciaReservaPostgresTest {

    private static final int SOLICITACOES = 20;
    private static final int HTTP_CRIADA = 201;
    private static final int HTTP_CONFLITO = 409;
    private static final int FALHA_INESPERADA = -1;

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static RepositorioReservasPostgres repositorio;

    @BeforeAll
    static void criarEsquema() {
        repositorio = new RepositorioReservasPostgres(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        repositorio.criarEsquema();
    }

    @ParameterizedTest(name = "{0}: 20 solicitações simultâneas, uma reserva")
    @EnumSource(value = TipoRecurso.class, names = {"SALA", "PROFESSOR"})
    @DisplayName("Concorrência: 20 solicitações no mesmo horário resultam em 1 reserva e 19 recusas")
    void vinteSolicitacoesSimultaneasGeramUmaReserva(TipoRecurso tipo) throws Exception {
        // Arrange
        String recursoId = tipo.name() + "-" + UUID.randomUUID();
        repositorio.registrarRecurso(recursoId, tipo);
        LocalDateTime inicio = LocalDateTime.of(2026, 11, 20, 10, 0);
        LocalDateTime fim = LocalDateTime.of(2026, 11, 20, 11, 0);

        // Act: todas as threads esperam o mesmo latch e partem juntas
        ExecutorService pool = Executors.newFixedThreadPool(SOLICITACOES);
        CountDownLatch partida = new CountDownLatch(1);
        List<Future<Integer>> futuros = new ArrayList<>();
        List<RuntimeException> falhas = new CopyOnWriteArrayList<>();
        try {
            for (int i = 0; i < SOLICITACOES; i++) {
                String solicitante = "solicitante-" + i;
                futuros.add(pool.submit(() -> {
                    partida.await();
                    try {
                        repositorio.solicitar(recursoId, solicitante, inicio, fim);
                        return HTTP_CRIADA;
                    } catch (ErroAplicacao e) {
                        return e.getStatus();
                    } catch (RuntimeException e) {
                        falhas.add(e);
                        return FALHA_INESPERADA;
                    }
                }));
            }
            partida.countDown();
            List<Integer> resultados = new ArrayList<>();
            for (Future<Integer> f : futuros) {
                resultados.add(f.get(60, TimeUnit.SECONDS));
            }

            // Assert: resposta das solicitações
            assertEquals(0, resultados.stream().filter(r -> r == FALHA_INESPERADA).count(),
                    "falhas inesperadas: " + (falhas.isEmpty() ? "" : falhas.get(0).getCause() + " / " + falhas.get(0)));
            assertEquals(1, resultados.stream().filter(r -> r == HTTP_CRIADA).count(), "aceitas");
            assertEquals(SOLICITACOES - 1, resultados.stream().filter(r -> r == HTTP_CONFLITO).count(), "recusadas com 409");

            // Assert: consulta direta no banco, independente da resposta
            assertEquals(1, repositorio.contarReservasAtivas(recursoId, inicio, fim), "linhas no banco para o recurso e intervalo");
        } finally {
            pool.shutdownNow();
        }
    }
}
