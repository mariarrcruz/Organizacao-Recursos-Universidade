package com.organizacao.recursos.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.organizacao.recursos.persistencia.Armazenamento;
import com.organizacao.recursos.servico.ServicoAutenticacao;
import com.organizacao.recursos.servico.ServicoReservas;
import java.io.IOException;
import java.time.Clock;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Validação de entrada (400 com mensagem compreensível em ProblemDetail) e falha interna sem vazamento (500).
 */
class ValidacaoEntradaTest {

    private final MundoAutorizacao mundo = new MundoAutorizacao();
    private final MockMvc mvc = mundo.mvc();
    private final ObjectMapper json = new ObjectMapper();

    private MvcResult postar(String caminho, String token, String corpo) throws Exception {
        var req = post(caminho).contentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            req.header(HttpHeaders.AUTHORIZATION, token);
        }
        if (corpo != null) {
            req.content(corpo);
        }
        return mvc.perform(req).andReturn();
    }

    /** Erro em ProblemDetail (RFC 9457) com status, título, detalhe útil e sem vazamento de detalhe interno. */
    private void assertProblema(MvcResult r, int status, String trechoDoDetalhe) throws IOException {
        String corpo = r.getResponse().getContentAsString();
        assertEquals(status, r.getResponse().getStatus(), corpo);
        String tipo = r.getResponse().getContentType();
        assertTrue(tipo != null && tipo.startsWith("application/problem+json"), "veio " + tipo + " · " + corpo);
        JsonNode no = json.readTree(corpo);
        assertEquals(status, no.path("status").asInt(), corpo);
        assertTrue(!no.path("title").asText("").isBlank(), "título ausente: " + corpo);
        String detalhe = no.path("detail").asText("");
        assertTrue(detalhe.toLowerCase(Locale.ROOT).contains(trechoDoDetalhe.toLowerCase(Locale.ROOT)),
                "detalhe deve mencionar '" + trechoDoDetalhe + "', veio: " + detalhe);
        assertTrue(!MatrizAutorizacaoTest.VAZAMENTO.matcher(corpo).find(), "vaza detalhe interno: " + corpo);
    }

    private final String solicitante = mundo.token("solicitante-dono");
    private final String administrador = mundo.token("administrador");

    @Test
    @DisplayName("Validação — término antes do início: 400 com mensagem de período")
    void terminoAntesDoInicio() throws Exception {
        assertProblema(postar("/reservas", solicitante,
                "{\"recursoId\":\"SALA-V\",\"inicio\":\"2026-11-05T12:00\",\"fim\":\"2026-11-05T10:00\"}"), 400, "posterior");
    }

    @Test
    @DisplayName("Validação — capacidade negativa no cadastro de recurso: 400")
    void capacidadeNegativa() throws Exception {
        assertProblema(postar("/recursos", administrador,
                "{\"id\":\"X-1\",\"tipo\":\"SALA\",\"nome\":\"X\",\"localizacao\":\"B\",\"capacidade\":-5,"
                        + "\"restrito\":false,\"status\":\"DISPONIVEL\",\"competencias\":[]}"), 400, "capacidade");
    }

    @Test
    @DisplayName("Validação — recursoId ausente na solicitação: 400 indicando o campo")
    void recursoAusente() throws Exception {
        assertProblema(postar("/reservas", solicitante,
                "{\"inicio\":\"2026-11-05T18:00\",\"fim\":\"2026-11-05T19:00\"}"), 400, "recursoId");
    }

    @Test
    @DisplayName("Validação — início ausente na solicitação: 400 indicando o campo")
    void inicioAusente() throws Exception {
        assertProblema(postar("/reservas", solicitante,
                "{\"recursoId\":\"SALA-V\",\"fim\":\"2026-11-05T19:00\"}"), 400, "inicio");
    }

    @Test
    @DisplayName("Validação — campos obrigatórios do recurso ausentes: 400 indicando o campo")
    void recursoSemIdEhTipo() throws Exception {
        assertProblema(postar("/recursos", administrador,
                "{\"nome\":\"X\",\"localizacao\":\"B\",\"capacidade\":5,\"restrito\":false,\"status\":\"DISPONIVEL\"}"),
                400, "id");
    }

    @Test
    @DisplayName("Validação — tipo de recurso inexistente: 400, sem NullPointer nem nome de enum interno")
    void tipoInvalido() throws Exception {
        assertProblema(postar("/recursos", administrador,
                "{\"id\":\"X-2\",\"tipo\":\"NAVE\",\"nome\":\"X\",\"localizacao\":\"B\",\"capacidade\":5,"
                        + "\"restrito\":false,\"status\":\"DISPONIVEL\",\"competencias\":[]}"), 400, "tipo");
    }

    @Test
    @DisplayName("Validação — corpo ausente: 400 pedindo o corpo da requisição")
    void corpoAusente() throws Exception {
        assertProblema(postar("/reservas", solicitante, null), 400, "corpo");
    }

    @Test
    @DisplayName("Validação — parâmetro obrigatório ausente na consulta de utilização: 400 indicando o parâmetro")
    void parametroAusenteNoRelatorio() throws Exception {
        MvcResult r = mvc.perform(get("/relatorios/utilizacao").param("inicio", "2026-11-05")
                .header(HttpHeaders.AUTHORIZATION, administrador)).andReturn();
        assertProblema(r, 400, "fim");
    }

    @Test
    @DisplayName("Validação — fim de manutenção antes do início: 400")
    void manutencaoInvertida() throws Exception {
        assertProblema(postar("/manutencoes", administrador,
                "{\"recursoId\":\"SALA-V\",\"inicio\":\"2026-11-06T10:00\",\"fim\":\"2026-11-06T09:00\"}"), 400, "período");
    }

    @Test
    @DisplayName("Erro interno — falha do armazenamento vira 500 genérico, sem SQL, classe nem stack trace")
    void falhaInternaNaoVazaDetalhes() throws Exception {
        Armazenamento falho = mock(Armazenamento.class);
        when(falho.buscarRecurso(any())).thenReturn(java.util.Optional.of(MundoAutorizacao.recurso("SALA-V", false)));
        when(falho.listarReservas()).thenThrow(new IllegalStateException(
                "Falha de banco: SELECT * FROM reserva WHERE recurso_id = 'SALA-V' (org.postgresql.util.PSQLException)"));
        ServicoAutenticacao auth = mundo.auth;
        ServicoReservas servico = new ServicoReservas(MundoAutorizacao.RELOGIO, falho);
        MockMvc falhaMvc = MockMvcBuilders.standaloneSetup(new ReservasController(servico, auth, falho))
                .setControllerAdvice(new TratadorErros()).build();
        MvcResult r = falhaMvc.perform(post("/reservas").header(HttpHeaders.AUTHORIZATION, solicitante)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"recursoId\":\"SALA-V\",\"inicio\":\"2026-11-05T18:00\",\"fim\":\"2026-11-05T19:00\"}")).andReturn();
        assertProblema(r, 500, "erro interno");
    }
}
