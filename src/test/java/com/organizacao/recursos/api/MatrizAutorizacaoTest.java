package com.organizacao.recursos.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Cada célula de docs/seguranca/matriz-autorizacao.md (gerada em
 * src/test/resources/seguranca/matriz-autorizacao.csv por scripts/gerar_matriz_autorizacao.py)
 * vira um caso: operação, perfil com token real, status esperado.
 */
class MatrizAutorizacaoTest {

    /** Vazamentos que nenhuma resposta de erro pode conter: stack, SQL, classe interna, pacote. */
    static final Pattern VAZAMENTO = Pattern.compile(
            "(?i)(exception|stack|caused by|\\bsql\\b|select |insert into|update \\w+ set|delete from|"
                    + "com\\.organizacao|\\borg\\.|\\bjava\\.|\\.java:|\\bat [\\w.$]+\\()");

    private final ObjectMapper json = new ObjectMapper();

    record Celula(String operacao, String perfil, int status) {}

    static Stream<Arguments> celulas() throws IOException {
        List<Arguments> lista = new java.util.ArrayList<>();
        try (InputStream in = MatrizAutorizacaoTest.class.getResourceAsStream("/seguranca/matriz-autorizacao.csv")) {
            assertTrue(in != null, "CSV da matriz não encontrado");
            String texto = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            for (String linha : texto.split("\n")) {
                if (linha.isBlank() || linha.startsWith("operacao;")) {
                    continue;
                }
                String[] c = linha.split(";");
                lista.add(Arguments.of(c[0], c[1], Integer.parseInt(c[2])));
            }
        }
        return lista.stream();
    }

    /** Requisição da operação, com a massa de MundoAutorizacao. */
    static MockHttpServletRequestBuilder requisicao(String operacao) {
        return switch (operacao) {
            case "2" -> post("/recursos").contentType(MediaType.APPLICATION_JSON).content(
                    "{\"id\":\"NOVO-1\",\"tipo\":\"SALA\",\"nome\":\"Nova\",\"localizacao\":\"B\",\"capacidade\":10,"
                            + "\"restrito\":false,\"status\":\"DISPONIVEL\",\"competencias\":[]}");
            case "3" -> post("/manutencoes").contentType(MediaType.APPLICATION_JSON).content(
                    "{\"recursoId\":\"SALA-V\",\"inicio\":\"2026-11-06T09:00\",\"fim\":\"2026-11-06T10:00\"}");
            case "3b" -> post("/vinculos").contentType(MediaType.APPLICATION_JSON).content(
                    "{\"responsavelId\":\"resp2\",\"recursoId\":\"SALA-V\"}");
            case "4" -> post("/reservas").contentType(MediaType.APPLICATION_JSON).content(
                    "{\"recursoId\":\"SALA-V\",\"inicio\":\"2026-11-05T18:00\",\"fim\":\"2026-11-05T19:00\"}");
            case "5" -> get("/reservas").param("recursoId", "SALA-L");
            case "5b" -> get("/disponibilidade").param("recursoId", "SALA-L")
                    .param("inicio", "2026-11-05T18:00").param("fim", "2026-11-05T19:00");
            case "6" -> get("/reservas/RA");
            case "7" -> put("/reservas/RG").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"inicio\":\"2026-11-05T13:00\",\"fim\":\"2026-11-05T14:00\"}");
            case "8" -> delete("/reservas/RH");
            case "9" -> post("/reservas/RA/aprovacao");
            case "10" -> post("/reservas/RA/rejeicao").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"justificativa\":\"motivo\"}");
            case "11" -> post("/reservas/RE/inicio-uso");
            case "12" -> post("/reservas/RD/conclusao");
            case "13" -> post("/reservas/RF/cancelamento").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"justificativa\":\"motivo\"}");
            case "14" -> post("/reservas/RE/nao-comparecimento");
            case "16" -> get("/reservas/RA/auditoria");
            case "17" -> get("/relatorios/utilizacao").param("inicio", "2026-11-05").param("fim", "2026-11-05");
            case "18" -> put("/reservas/RA/auditoria").contentType(MediaType.APPLICATION_JSON).content("{}");
            case "19" -> delete("/reservas/RA/auditoria");
            default -> throw new IllegalArgumentException("operação sem definição de requisição: " + operacao);
        };
    }

    private MvcResult executar(String operacao, String perfil) throws Exception {
        MundoAutorizacao mundo = new MundoAutorizacao();
        MockMvc mvc = mundo.mvc();
        MockHttpServletRequestBuilder req = requisicao(operacao);
        String token = mundo.token(perfil);
        if (token != null) {
            req.header(HttpHeaders.AUTHORIZATION, token);
        }
        return mvc.perform(req).andReturn();
    }

    @ParameterizedTest(name = "operação {0} · {1} → {2}")
    @MethodSource("celulas")
    @DisplayName("Matriz de autorização — cada célula responde o status esperado")
    void respostaConformeAMatriz(String operacao, String perfil, int esperado) throws Exception {
        assertEquals(esperado, executar(operacao, perfil).getResponse().getStatus(),
                "operação " + operacao + " · perfil " + perfil);
    }

    @ParameterizedTest(name = "erro sem vazamento: operação {0} · {1} → {2}")
    @MethodSource("celulas")
    @DisplayName("Respostas de erro da matriz: problem+json e sem stack, SQL ou classe interna")
    void respostaDeErroNaoVazaDetalhesInternos(String operacao, String perfil, int esperado) throws Exception {
        if (esperado < 400) {
            return;
        }
        MvcResult r = executar(operacao, perfil);
        assertProblemaSemVazamento(r, esperado);
    }

    /** Erro no formato ProblemDetail (RFC 9457): status, título e detalhe, sem detalhes internos. */
    void assertProblemaSemVazamento(MvcResult r, int status) throws IOException {
        String corpo = r.getResponse().getContentAsString();
        String tipo = r.getResponse().getContentType();
        assertEquals(status, r.getResponse().getStatus());
        assertTrue(tipo != null && tipo.startsWith("application/problem+json"),
                "tipo de conteúdo do erro deve ser application/problem+json, veio: " + tipo + " · " + corpo);
        JsonNode no = json.readTree(corpo);
        assertEquals(status, no.path("status").asInt(), corpo);
        assertTrue(!no.path("title").asText("").isBlank(), "título ausente: " + corpo);
        assertTrue(!no.path("detail").asText("").isBlank(), "detalhe ausente: " + corpo);
        assertTrue(!VAZAMENTO.matcher(corpo).find(), "resposta de erro vaza detalhe interno: " + corpo);
    }

    // ------------------------------------------------------------------ posse pelo id

    @Test
    @DisplayName("Posse — Solicitante não altera a reserva de outro Solicitante pelo id (403), e a reserva fica intacta")
    void solicitanteNaoAlteraReservaDeOutroPeloId() throws Exception {
        MundoAutorizacao mundo = new MundoAutorizacao();
        MockMvc mvc = mundo.mvc();
        int status = mvc.perform(put("/reservas/RG").header(HttpHeaders.AUTHORIZATION, mundo.token("solicitante-terceiro"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"inicio\":\"2026-11-05T13:00\",\"fim\":\"2026-11-05T14:00\"}")).andReturn().getResponse().getStatus();
        assertEquals(403, status);
        JsonNode reserva = json.readTree(mvc.perform(get("/reservas/RG")
                .header(HttpHeaders.AUTHORIZATION, mundo.token("solicitante-dono"))).andReturn().getResponse().getContentAsString());
        assertEquals("2026-11-05T15:00", reserva.get("inicio").asText());
    }

    @Test
    @DisplayName("Posse — Solicitante não cancela a reserva de outro Solicitante pelo id (403), e a reserva fica intacta")
    void solicitanteNaoCancelaReservaDeOutroPeloId() throws Exception {
        MundoAutorizacao mundo = new MundoAutorizacao();
        MockMvc mvc = mundo.mvc();
        int status = mvc.perform(post("/reservas/RF/cancelamento").header(HttpHeaders.AUTHORIZATION, mundo.token("solicitante-terceiro"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"justificativa\":\"motivo\"}")).andReturn().getResponse().getStatus();
        assertEquals(403, status);
        JsonNode reserva = json.readTree(mvc.perform(get("/reservas/RF")
                .header(HttpHeaders.AUTHORIZATION, mundo.token("solicitante-dono"))).andReturn().getResponse().getContentAsString());
        assertEquals("APROVADA", reserva.get("estado").asText());
    }
}
