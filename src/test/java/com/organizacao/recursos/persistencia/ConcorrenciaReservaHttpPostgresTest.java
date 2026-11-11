package com.organizacao.recursos.persistencia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.organizacao.recursos.api.ReservasController;
import com.organizacao.recursos.api.TratadorErros;
import com.organizacao.recursos.dominio.Recurso;
import com.organizacao.recursos.dominio.StatusRecurso;
import com.organizacao.recursos.dominio.TipoRecurso;
import com.organizacao.recursos.servico.ServicoAutenticacao;
import com.organizacao.recursos.servico.ServicoReservas;
import java.time.Clock;
import java.time.Instant;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * RN-02 / RN-03 pela API: 20 solicitações HTTP simultâneas, cada uma com a SUA instância de
 * {@link ServicoReservas} (como se fossem 20 nós). Nenhum monitor Java é compartilhado, então só o
 * PostgreSQL pode impedir a dupla reserva. A verificação é por consulta ao banco.
 */
@Testcontainers
class ConcorrenciaReservaHttpPostgresTest {

    private static final int SOLICITACOES = 20;
    private static final int HTTP_CRIADA = 201;
    private static final int HTTP_CONFLITO = 409;
    private static final int FALHA_INESPERADA = -1;
    private static final ServicoAutenticacao AUTENTICACAO = new ServicoAutenticacao();

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static ArmazenamentoPostgres base;

    @BeforeAll
    static void criarEsquema() {
        base = new ArmazenamentoPostgres(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        base.criarEsquema();
    }

    @ParameterizedTest(name = "{0}: 20 requisições HTTP simultâneas, uma reserva")
    @EnumSource(value = TipoRecurso.class, names = {"SALA", "PROFESSOR"})
    @DisplayName("HTTP: 20 solicitações simultâneas em instâncias separadas geram 1 reserva e 19 recusas")
    void vinteRequisicoesSimultaneasGeramUmaReserva(TipoRecurso tipo) throws Exception {
        // Arrange
        Recurso recurso = new Recurso(tipo + "-" + UUID.randomUUID(), tipo, "Recurso", "BLOCO_A", 30, true,
                StatusRecurso.DISPONIVEL, Set.of());
        base.gravarRecurso(recurso);
        String corpo = String.format("{\"recursoId\":\"%s\",\"inicio\":\"2026-11-20T10:00\",\"fim\":\"2026-11-20T11:00\"}",
                recurso.id());

        // Act: cada thread monta sua própria instância do serviço e do controlador
        ExecutorService pool = Executors.newFixedThreadPool(SOLICITACOES);
        CountDownLatch partida = new CountDownLatch(1);
        List<Future<Integer>> futuros = new ArrayList<>();
        try {
            for (int i = 0; i < SOLICITACOES; i++) {
                String token = AUTENTICACAO.gerarToken("solicitante-" + i, List.of("SOLICITANTE"),
                        Instant.now().plusSeconds(3600));
                futuros.add(pool.submit(() -> {
                    MockMvc mvc = novoMvc();
                    partida.await();
                    try {
                        return mvc.perform(post("/reservas")
                                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(corpo))
                                .andReturn().getResponse().getStatus();
                    } catch (RuntimeException e) {
                        return FALHA_INESPERADA;
                    }
                }));
            }
            partida.countDown();
            List<Integer> resultados = new ArrayList<>();
            for (Future<Integer> f : futuros) {
                resultados.add(f.get(60, TimeUnit.SECONDS));
            }

            // Assert: respostas HTTP
            assertEquals(0, resultados.stream().filter(r -> r == FALHA_INESPERADA).count(), "falhas inesperadas");
            assertEquals(1, resultados.stream().filter(r -> r == HTTP_CRIADA).count(), "criadas (201)");
            assertEquals(SOLICITACOES - 1, resultados.stream().filter(r -> r == HTTP_CONFLITO).count(), "recusadas (409)");

            // Assert: consulta direta ao banco
            long ativasNoBanco = base.listarReservas().stream()
                    .filter(r -> r.getRecurso().id().equals(recurso.id()))
                    .filter(r -> r.getEstado().name().equals("SOLICITADA"))
                    .count();
            assertEquals(1, ativasNoBanco, "linhas ativas no banco para o recurso");
        } finally {
            pool.shutdownNow();
        }
    }

    private static MockMvc novoMvc() {
        ServicoReservas servico = new ServicoReservas(Clock.systemUTC(),
                new ArmazenamentoPostgres(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        return MockMvcBuilders.standaloneSetup(new ReservasController(servico, AUTENTICACAO,
                        id -> base.buscarRecurso(id).orElse(null)))
                .setControllerAdvice(new TratadorErros())
                .build();
    }
}
