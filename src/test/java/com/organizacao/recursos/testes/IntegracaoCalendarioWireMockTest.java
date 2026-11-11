package com.organizacao.recursos.testes;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.organizacao.recursos.integracao.ClienteCalendarioHttp;
import com.organizacao.recursos.integracao.ConfiguracaoIntegracao;
import com.organizacao.recursos.integracao.ErroIntegracao;
import com.organizacao.recursos.integracao.EventoInstitucional;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Logger;
import java.util.logging.LogRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Integração com o calendário institucional, isolada por WireMock (RNF-08; RF-11; RN-09).
 * Cobre a matriz INT-01 a INT-05 do material de WireMock e os requisitos de segurança da chamada.
 */
class IntegracaoCalendarioWireMockTest extends TesteIntegracaoWireMock {

    /** Token fictício: nunca uma credencial real. */
    private static final String TOKEN = "token-de-teste";
    private static final LocalDate INICIO = LocalDate.of(2026, 11, 10);
    private static final LocalDate FIM = LocalDate.of(2026, 11, 20);
    private static final String CAMINHO = "/eventos";
    private static final Duration LEITURA = Duration.ofMillis(500);

    private final List<LogRecord> registros = new ArrayList<>();
    private final Handler capturaLog = new Handler() {
        @Override public void publish(LogRecord registro) { registros.add(registro); }
        @Override public void flush() { }
        @Override public void close() { }
    };
    private final Logger logDoAdapter = Logger.getLogger(ClienteCalendarioHttp.class.getName());

    @BeforeEach
    void capturarLogs() {
        logDoAdapter.addHandler(capturaLog);
    }

    @AfterEach
    void pararCapturaLogs() {
        logDoAdapter.removeHandler(capturaLog);
    }

    private ClienteCalendarioHttp cliente(Duration leitura) {
        return new ClienteCalendarioHttp(new ConfiguracaoIntegracao(
                baseUrl(), TOKEN, Duration.ofMillis(500), leitura));
    }

    private ClienteCalendarioHttp cliente() {
        return cliente(LEITURA);
    }

    private static String corpoValido() {
        return """
                {"eventos":[
                  {"id":"ev-1","titulo":"Semana acadêmica","inicio":"2026-11-11T08:00:00","fim":"2026-11-11T18:00:00"},
                  {"id":"ev-2","titulo":"Feriado local","inicio":"2026-11-15T00:00:00","fim":"2026-11-15T23:59:00"}
                ]}
                """;
    }

    private void stubEventos(int status, String corpo) {
        stubFor(get(urlPathEqualTo(CAMINHO))
                .withQueryParam("inicio", equalTo(INICIO.toString()))
                .withQueryParam("fim", equalTo(FIM.toString()))
                .withHeader("Authorization", equalTo("Bearer " + TOKEN))
                .willReturn(aResponse().withStatus(status).withHeader("Content-Type", "application/json").withBody(corpo)));
    }

    private ErroIntegracao esperarErro(Duration leitura) {
        return assertThrows(ErroIntegracao.class, () -> cliente(leitura).eventosNoPeriodo(INICIO, FIM));
    }

    @Test
    @DisplayName("INT-01 — 200 válido: resposta é mapeada para o modelo interno e o request respeita o contrato")
    void int01_respostaValidaMapeiaModeloInterno() {
        // Arrange
        stubEventos(200, corpoValido());
        // Act
        List<EventoInstitucional> eventos = cliente().eventosNoPeriodo(INICIO, FIM);
        // Assert
        assertEquals(2, eventos.size());
        assertEquals(new EventoInstitucional("ev-1", "Semana acadêmica",
                LocalDateTime.of(2026, 11, 11, 8, 0), LocalDateTime.of(2026, 11, 11, 18, 0)), eventos.get(0));
        assertEquals("ev-2", eventos.get(1).id());
        verify(1, getRequestedFor(urlPathEqualTo(CAMINHO))
                .withQueryParam("inicio", equalTo("2026-11-10"))
                .withQueryParam("fim", equalTo("2026-11-20"))
                .withHeader("Authorization", equalTo("Bearer " + TOKEN)));
    }

    @Test
    @DisplayName("INT-02 — 404: recurso inexistente vira NAO_ENCONTRADO do domínio, sem expor o corpo externo")
    void int02_naoEncontradoTraduzParaDominio() {
        // Arrange
        stubEventos(404, "{\"erro\":\"nao encontrado interno\"}");
        // Act
        ErroIntegracao erro = esperarErro(LEITURA);
        // Assert
        assertEquals(ErroIntegracao.Tipo.NAO_ENCONTRADO, erro.getTipo());
        assertFalse(erro.getMessage().contains("interno"));
    }

    @ParameterizedTest(name = "INT-03 — Status {0} vira INDISPONIVEL controlado")
    @ValueSource(ints = {500, 502, 503})
    void int03_erroDeServidorViraIndisponivel(int status) {
        // Arrange
        stubEventos(status, "{\"erro\":\"falha\"}");
        // Act
        ErroIntegracao erro = esperarErro(LEITURA);
        // Assert
        assertEquals(ErroIntegracao.Tipo.INDISPONIVEL, erro.getTipo());
    }

    @Test
    @DisplayName("INT-04 — Resposta lenta: atraso maior que o timeout interrompe a chamada como INDISPONIVEL")
    void int04_timeoutInterrompeChamada() {
        // Arrange: atraso de 2s contra leitura de 500ms
        stubFor(get(urlPathEqualTo(CAMINHO)).willReturn(aResponse()
                .withFixedDelay(2000).withStatus(200).withHeader("Content-Type", "application/json")
                .withBody(corpoValido())));
        // Act
        ErroIntegracao erro = esperarErro(LEITURA);
        // Assert
        assertEquals(ErroIntegracao.Tipo.INDISPONIVEL, erro.getTipo());
    }

    @Test
    @DisplayName("INT-04.1 — Provedor fora do ar (conexão recusada) vira INDISPONIVEL, sem exceção crua")
    void int04_1_conexaoRecusadaViraIndisponivel() {
        // Arrange: porta 1 não tem servidor escutando
        ClienteCalendarioHttp cliente = new ClienteCalendarioHttp(new ConfiguracaoIntegracao(
                "http://localhost:1", TOKEN, Duration.ofMillis(500), LEITURA));
        // Act
        ErroIntegracao erro = assertThrows(ErroIntegracao.class, () -> cliente.eventosNoPeriodo(INICIO, FIM));
        // Assert
        assertEquals(ErroIntegracao.Tipo.INDISPONIVEL, erro.getTipo());
    }

    @ParameterizedTest(name = "INT-05 — Corpo fora do contrato é rejeitado: {0}")
    @ValueSource(strings = {
            "nao e json",
            "{}",
            "{\"eventos\":\"nao-lista\"}",
            "{\"eventos\":[\"texto solto\"]}",
            "{\"eventos\":[{\"titulo\":\"Sem id\",\"inicio\":\"2026-11-11T08:00:00\",\"fim\":\"2026-11-11T09:00:00\"}]}",
            "{\"eventos\":[{\"id\":\"  \",\"titulo\":\"x\",\"inicio\":\"2026-11-11T08:00:00\",\"fim\":\"2026-11-11T09:00:00\"}]}",
            "{\"eventos\":[{\"id\":\"e\",\"titulo\":\"x\",\"inicio\":\"10/11/2026 08:00\",\"fim\":\"2026-11-11T09:00:00\"}]}",
            "{\"eventos\":[{\"id\":\"e\",\"titulo\":\"x\",\"inicio\":\"2026-11-11T09:00:00\",\"fim\":\"2026-11-11T08:00:00\"}]}"
    })
    void int05_corpoForaDoContratoEhRejeitado(String corpo) {
        // Arrange
        stubEventos(200, corpo);
        // Act
        ErroIntegracao erro = esperarErro(LEITURA);
        // Assert
        assertEquals(ErroIntegracao.Tipo.CONTRATO_INVALIDO, erro.getTipo());
    }

    @Test
    @DisplayName("INT-05.1 — Corpo acima do limite de tamanho é rejeitado antes de ser interpretado")
    void int05_1_corpoGrandeDemaisEhRejeitado() {
        // Arrange: corpo válido na forma, mas acima de 1.000.000 caracteres
        String preenchimento = "a".repeat(ClienteCalendarioHttp.LIMITE_CORPO);
        stubEventos(200, "{\"eventos\":[],\"extra\":\"" + preenchimento + "\"}");
        // Act
        ErroIntegracao erro = esperarErro(LEITURA);
        // Assert
        assertEquals(ErroIntegracao.Tipo.CONTRATO_INVALIDO, erro.getTipo());
    }

    @ParameterizedTest(name = "SEG-01 — Status {0} rejeita a requisição sem retry (REJEITADO)")
    @ValueSource(ints = {400, 401, 403})
    void seg01_erroDeCredencialOuRequisicaoNaoEhRepetido(int status) {
        // Arrange
        stubEventos(status, "{\"erro\":\"negado\"}");
        // Act
        ErroIntegracao erro = esperarErro(LEITURA);
        // Assert
        assertEquals(ErroIntegracao.Tipo.REJEITADO, erro.getTipo());
        verify(1, getRequestedFor(urlPathEqualTo(CAMINHO)));
    }

    @Test
    @DisplayName("SEG-02 — Teste negativo: o provedor ecoa o token no erro e ele não aparece na exceção nem nos logs")
    void seg02_tokenNaoVazaParaExcecaoNemLogs() {
        // Arrange: provedor devolve o próprio token dentro do corpo de erro
        stubEventos(500, "{\"erro\":\"falha ao validar " + TOKEN + "\"}");
        // Act
        ErroIntegracao erro = esperarErro(LEITURA);
        // Assert
        assertFalse(erro.getMessage().contains(TOKEN), "token vazou para a exceção");
        for (LogRecord registro : registros) {
            assertFalse(registro.getMessage().contains(TOKEN), "token vazou para o log");
        }
        assertTrue(registros.stream().anyMatch(r -> r.getMessage().contains("tipo=INDISPONIVEL")),
                "diagnóstico de falha deveria ser registrado");
    }

    @Test
    @DisplayName("SEG-03 — Configuração: toString mascara o token")
    void seg03_configuracaoMascaraToken() {
        ConfiguracaoIntegracao config = new ConfiguracaoIntegracao(
                "https://exemplo.invalido/", TOKEN, Duration.ofMillis(500), Duration.ofMillis(1500));
        assertFalse(config.toString().contains(TOKEN));
        assertTrue(config.toString().contains("token=***"));
        assertEquals("https://exemplo.invalido", config.baseUrl(), "barra final deve ser removida");
    }

    @Test
    @DisplayName("CFG-01 — Configuração do ambiente: variáveis ausentes são recusadas com mensagem clara")
    void cfg01_variaveisAusentesSaoRecusadas() {
        IllegalArgumentException semBase = assertThrows(IllegalArgumentException.class,
                () -> ConfiguracaoIntegracao.deAmbiente(Map.of(ConfiguracaoIntegracao.VAR_TOKEN, TOKEN)));
        assertTrue(semBase.getMessage().contains(ConfiguracaoIntegracao.VAR_BASE_URL));

        IllegalArgumentException semToken = assertThrows(IllegalArgumentException.class,
                () -> ConfiguracaoIntegracao.deAmbiente(Map.of(ConfiguracaoIntegracao.VAR_BASE_URL, baseUrl())));
        assertTrue(semToken.getMessage().contains(ConfiguracaoIntegracao.VAR_TOKEN));
    }

    @Test
    @DisplayName("CFG-02 — Configuração do ambiente: valores válidos carregam timeouts padrão 500ms e 1500ms")
    void cfg02_ambienteValidoUsaTimeoutsPadrao() {
        ConfiguracaoIntegracao config = ConfiguracaoIntegracao.deAmbiente(Map.of(
                ConfiguracaoIntegracao.VAR_BASE_URL, baseUrl(),
                ConfiguracaoIntegracao.VAR_TOKEN, TOKEN));
        assertEquals(Duration.ofMillis(500), config.connectTimeout());
        assertEquals(Duration.ofMillis(1500), config.readTimeout());
    }
}
