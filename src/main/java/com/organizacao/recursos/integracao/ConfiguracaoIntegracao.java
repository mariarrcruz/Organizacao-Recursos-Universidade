package com.organizacao.recursos.integracao;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * Configuração externa da integração. Segredos vêm do ambiente, nunca do código.
 * O {@code toString} mascara o token para não vazar em logs ou diagnósticos.
 */
public record ConfiguracaoIntegracao(String baseUrl, String token, Duration connectTimeout, Duration readTimeout) {

    public static final String VAR_BASE_URL = "CALENDARIO_BASE_URL";
    public static final String VAR_TOKEN = "CALENDARIO_TOKEN";

    public ConfiguracaoIntegracao {
        Objects.requireNonNull(baseUrl, "baseUrl obrigatória");
        Objects.requireNonNull(token, "token obrigatório");
        Objects.requireNonNull(connectTimeout, "connectTimeout obrigatório");
        Objects.requireNonNull(readTimeout, "readTimeout obrigatório");
        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            throw new IllegalArgumentException("baseUrl deve começar com http:// ou https://");
        }
        if (token.isBlank()) {
            throw new IllegalArgumentException("token não pode ser vazio");
        }
        if (connectTimeout.isNegative() || connectTimeout.isZero() || readTimeout.isNegative() || readTimeout.isZero()) {
            throw new IllegalArgumentException("timeouts devem ser positivos");
        }
        baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /** Lê a configuração de variáveis de ambiente. Timeouts padrão: conexão 500ms, leitura 1500ms. */
    public static ConfiguracaoIntegracao deAmbiente(Map<String, String> ambiente) {
        String base = ambiente.get(VAR_BASE_URL);
        String token = ambiente.get(VAR_TOKEN);
        if (base == null || base.isBlank()) {
            throw new IllegalArgumentException(VAR_BASE_URL + " não definida");
        }
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException(VAR_TOKEN + " não definida");
        }
        return new ConfiguracaoIntegracao(base, token, Duration.ofMillis(500), Duration.ofMillis(1500));
    }

    @Override
    public String toString() {
        return "ConfiguracaoIntegracao[baseUrl=" + baseUrl + ", token=***, connectTimeout=" + connectTimeout
                + ", readTimeout=" + readTimeout + "]";
    }
}
