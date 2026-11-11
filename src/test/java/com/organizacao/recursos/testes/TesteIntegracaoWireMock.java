package com.organizacao.recursos.testes;

import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Base para testes de integração HTTP com WireMock (RNF-08).
 * O servidor sobe em porta aleatória; os testes nunca apontam para um provedor real nem usam credenciais reais.
 */
abstract class TesteIntegracaoWireMock {

    @RegisterExtension
    static final WireMockExtension WM = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    /** Aponta o cliente estático de stubs (WireMock.stubFor) para a porta dinâmica deste teste. */
    @BeforeEach
    void apontarParaServidorDeTeste() {
        configureFor("localhost", WM.getPort());
    }

    /** URL base do servidor de teste, por exemplo http://localhost:PORTA. */
    protected static String baseUrl() {
        return WM.baseUrl();
    }

    /**
     * Faz um GET real pela rede contra o WireMock.
     * {@code cabecalhos} recebe pares nome, valor. O timeout cobre conexão e resposta.
     */
    protected static HttpResponse<String> chamar(String caminho, Duration timeout, String... cabecalhos)
            throws IOException, InterruptedException {
        HttpRequest.Builder requisicao = HttpRequest.newBuilder(URI.create(baseUrl() + caminho))
                .timeout(timeout)
                .GET();
        for (int i = 0; i < cabecalhos.length; i += 2) {
            requisicao.header(cabecalhos[i], cabecalhos[i + 1]);
        }
        HttpClient cliente = HttpClient.newBuilder().connectTimeout(timeout).build();
        return cliente.send(requisicao.build(), HttpResponse.BodyHandlers.ofString());
    }
}
