package com.organizacao.recursos.integracao;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Adapter HTTP da porta {@link CalendarioInstitucional}.
 *
 * <p>Contrato esperado: {@code GET {base}/eventos?inicio=AAAA-MM-DD&fim=AAAA-MM-DD} com
 * {@code Authorization: Bearer <token>} e resposta {@code {"eventos":[{"id","titulo","inicio","fim"}]}}.
 * Sem retry automático: a decisão de repetir fica com quem chama, porque 5xx só é seguro repetir com limite e backoff.
 */
public final class ClienteCalendarioHttp implements CalendarioInstitucional {

    private static final Logger LOG = Logger.getLogger(ClienteCalendarioHttp.class.getName());
    /** Limite de tamanho do corpo aceito (caracteres). Corpo maior é tratado como contrato inválido. */
    public static final int LIMITE_CORPO = 1_000_000;

    private final ConfiguracaoIntegracao config;
    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();

    public ClienteCalendarioHttp(ConfiguracaoIntegracao config) {
        this.config = config;
        this.http = HttpClient.newBuilder().connectTimeout(config.connectTimeout()).build();
    }

    @Override
    public List<EventoInstitucional> eventosNoPeriodo(LocalDate inicio, LocalDate fim) {
        if (fim.isBefore(inicio)) {
            throw new IllegalArgumentException("Período inválido: fim anterior ao início");
        }
        HttpRequest requisicao = HttpRequest.newBuilder(URI.create(
                        config.baseUrl() + "/eventos?inicio=" + inicio + "&fim=" + fim))
                .timeout(config.readTimeout())
                .header("Authorization", "Bearer " + config.token())
                .header("Accept", "application/json")
                .GET()
                .build();

        long inicioChamada = System.nanoTime();
        HttpResponse<String> resposta;
        try {
            resposta = http.send(requisicao, HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException e) {
            throw falha(ErroIntegracao.Tipo.INDISPONIVEL, "tempo esgotado", null, inicioChamada);
        } catch (IOException e) {
            throw falha(ErroIntegracao.Tipo.INDISPONIVEL, "falha de conexão", null, inicioChamada);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw falha(ErroIntegracao.Tipo.INDISPONIVEL, "chamada interrompida", null, inicioChamada);
        }

        int status = resposta.statusCode();
        if (status == 404) {
            throw falha(ErroIntegracao.Tipo.NAO_ENCONTRADO, "recurso não encontrado", status, inicioChamada);
        }
        if (status >= 400 && status < 500) {
            throw falha(ErroIntegracao.Tipo.REJEITADO, "requisição rejeitada pelo provedor", status, inicioChamada);
        }
        if (status >= 500) {
            throw falha(ErroIntegracao.Tipo.INDISPONIVEL, "provedor indisponível", status, inicioChamada);
        }
        if (status != 200) {
            throw falha(ErroIntegracao.Tipo.CONTRATO_INVALIDO, "status inesperado", status, inicioChamada);
        }

        List<EventoInstitucional> eventos = interpretar(resposta.body(), inicioChamada);
        LOG.info(() -> String.format("integracao=calendario status=200 duracao_ms=%d eventos=%d",
                duracaoMs(inicioChamada), eventos.size()));
        return eventos;
    }

    private List<EventoInstitucional> interpretar(String corpo, long inicioChamada) {
        if (corpo == null || corpo.length() > LIMITE_CORPO) {
            throw falha(ErroIntegracao.Tipo.CONTRATO_INVALIDO, "corpo ausente ou acima do limite", 200, inicioChamada);
        }
        JsonNode raiz;
        try {
            raiz = json.readTree(corpo);
        } catch (JsonProcessingException e) {
            throw falha(ErroIntegracao.Tipo.CONTRATO_INVALIDO, "JSON malformado", 200, inicioChamada);
        }
        if (raiz == null || !raiz.isObject() || !raiz.path("eventos").isArray()) {
            throw falha(ErroIntegracao.Tipo.CONTRATO_INVALIDO, "campo 'eventos' ausente ou inválido", 200, inicioChamada);
        }
        List<EventoInstitucional> eventos = new ArrayList<>();
        for (JsonNode item : raiz.path("eventos")) {
            eventos.add(mapear(item, inicioChamada));
        }
        return eventos;
    }

    private EventoInstitucional mapear(JsonNode item, long inicioChamada) {
        String id = textoObrigatorio(item, "id", inicioChamada);
        String titulo = textoObrigatorio(item, "titulo", inicioChamada);
        LocalDateTime inicio = dataHora(item, "inicio", inicioChamada);
        LocalDateTime fim = dataHora(item, "fim", inicioChamada);
        if (!fim.isAfter(inicio)) {
            throw falha(ErroIntegracao.Tipo.CONTRATO_INVALIDO, "fim deve ser posterior ao início", 200, inicioChamada);
        }
        return new EventoInstitucional(id, titulo, inicio, fim);
    }

    private String textoObrigatorio(JsonNode item, String campo, long inicioChamada) {
        JsonNode valor = item.path(campo);
        if (!valor.isTextual() || valor.asText().isBlank()) {
            throw falha(ErroIntegracao.Tipo.CONTRATO_INVALIDO, "campo obrigatório ausente: " + campo, 200, inicioChamada);
        }
        return valor.asText();
    }

    private LocalDateTime dataHora(JsonNode item, String campo, long inicioChamada) {
        String texto = textoObrigatorio(item, campo, inicioChamada);
        try {
            return LocalDateTime.parse(texto);
        } catch (RuntimeException e) {
            throw falha(ErroIntegracao.Tipo.CONTRATO_INVALIDO, "data inválida no campo " + campo, 200, inicioChamada);
        }
    }

    /** Registra o diagnóstico (status e duração, sem corpo nem credencial) e devolve a exceção de domínio. */
    private ErroIntegracao falha(ErroIntegracao.Tipo tipo, String mensagem, Integer status, long inicioChamada) {
        String detalhe = status == null ? "sem_status" : String.valueOf(status);
        LOG.log(Level.WARNING, () -> String.format("integracao=calendario tipo=%s status=%s duracao_ms=%d motivo=%s",
                tipo, detalhe, duracaoMs(inicioChamada), mensagem));
        return new ErroIntegracao(tipo, "Integração com calendário institucional: " + mensagem);
    }

    private static long duracaoMs(long inicioChamada) {
        return (System.nanoTime() - inicioChamada) / 1_000_000;
    }
}
