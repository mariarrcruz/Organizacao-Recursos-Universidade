package com.organizacao.recursos.testes;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Regra RNF-08 — Integrações externas simuladas com WireMock, sem depender de serviço real
 * (PRD RNF-03; RFC RNF-08). Valida a infraestrutura: stubs de sucesso, degradação e contrato de request.
 */
class RNF08WireMockIntegracaoTest extends TesteIntegracaoWireMock {

    /** Token fictício: nunca uma credencial real. */
    private static final String TOKEN_FICTICIO = "token-de-teste";
    private static final String CAMINHO = "/disponibilidade/sala-1";
    private static final Duration LIMITE = Duration.ofMillis(500);

    @Test
    @DisplayName("RNF-08.1 — Sucesso: resposta 200 é entregue íntegra e o request carrega o header de autenticação")
    void rnf08_1_sucessoRespeitaContrato() throws Exception {
        // Arrange
        stubFor(get(urlEqualTo(CAMINHO))
                .withHeader("X-Api-Key", equalTo(TOKEN_FICTICIO))
                .willReturn(okJson("{\"id\":\"sala-1\",\"status\":\"LIVRE\"}")));
        // Act
        HttpResponse<String> resposta = chamar(CAMINHO, LIMITE, "X-Api-Key", TOKEN_FICTICIO);
        // Assert
        assertEquals(200, resposta.statusCode());
        assertTrue(resposta.body().contains("\"status\":\"LIVRE\""));
        verify(1, getRequestedFor(urlEqualTo(CAMINHO)).withHeader("X-Api-Key", equalTo(TOKEN_FICTICIO)));
    }

    @Test
    @DisplayName("RNF-08.2 — Request sem o header esperado não casa com o stub (404 do servidor de teste)")
    void rnf08_2_requestSemContratoNaoCasa() throws Exception {
        // Arrange
        stubFor(get(urlEqualTo(CAMINHO))
                .withHeader("X-Api-Key", equalTo(TOKEN_FICTICIO))
                .willReturn(okJson("{}")));
        // Act
        HttpResponse<String> resposta = chamar(CAMINHO, LIMITE);
        // Assert
        assertEquals(404, resposta.statusCode());
    }

    @Test
    @DisplayName("RNF-08.3 — Lentidão: atraso maior que o timeout interrompe a chamada com HttpTimeoutException")
    void rnf08_3_timeoutInterrompeChamada() {
        // Arrange: atraso de 2s contra limite de 500ms
        stubFor(get(urlEqualTo(CAMINHO)).willReturn(aResponse().withFixedDelay(2000).withStatus(200)));
        // Act + Assert
        assertThrows(HttpTimeoutException.class, () -> chamar(CAMINHO, LIMITE));
    }

    @ParameterizedTest(name = "RNF-08.4 — Status {0} é devolvido ao cliente sem alteração")
    @ValueSource(ints = {400, 404, 500, 503})
    void rnf08_4_erroHttpPreservaStatus(int status) throws Exception {
        // Arrange
        stubFor(get(urlEqualTo(CAMINHO)).willReturn(aResponse().withStatus(status).withBody("{\"erro\":\"falha\"}")));
        // Act
        HttpResponse<String> resposta = chamar(CAMINHO, LIMITE);
        // Assert
        assertEquals(status, resposta.statusCode());
    }
}
