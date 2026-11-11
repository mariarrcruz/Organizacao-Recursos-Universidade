package com.organizacao.recursos.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.organizacao.recursos.dominio.Recurso;
import com.organizacao.recursos.dominio.StatusRecurso;
import com.organizacao.recursos.dominio.TipoRecurso;
import com.organizacao.recursos.persistencia.ArmazenamentoMemoria;
import com.organizacao.recursos.servico.ServicoAutenticacao;
import com.organizacao.recursos.servico.ServicoReservas;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * D3 — o Responsável age só sobre recursos vinculados a ele (persona: "sob sua responsabilidade").
 * O Administrador cadastra o vínculo. Aprovar, rejeitar e consultar reserva exigem vínculo.
 */
class VinculoResponsavelTest {

    private ServicoAutenticacao auth;
    private MockMvc mvc;

    @BeforeEach
    void montar() {
        auth = new ServicoAutenticacao();
        ArmazenamentoMemoria arm = new ArmazenamentoMemoria();
        ServicoReservas servico = new ServicoReservas(
                Clock.fixed(LocalDateTime.of(2026, 11, 1, 8, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC), arm);
        arm.gravarRecurso(new Recurso("SALA-R", TipoRecurso.SALA, "R", "B", 10, true, StatusRecurso.DISPONIVEL, Set.of()));
        arm.gravarRecurso(new Recurso("SALA-S", TipoRecurso.SALA, "S", "B", 10, true, StatusRecurso.DISPONIVEL, Set.of()));
        mvc = MockMvcBuilders.standaloneSetup(
                        new ReservasController(servico, auth, arm),
                        new VinculosController(servico, auth))
                .setControllerAdvice(new TratadorErros()).build();
    }

    private String token(String sub, String perfil) {
        return "Bearer " + auth.gerarToken(sub, List.of(perfil), Instant.now().plusSeconds(600));
    }

    private int status(MvcResult r) {
        return r.getResponse().getStatus();
    }

    private String criarReservaRestrita() throws Exception {
        MvcResult r = mvc.perform(post("/reservas").header(HttpHeaders.AUTHORIZATION, token("alice", "SOLICITANTE"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"recursoId\":\"SALA-R\",\"inicio\":\"2026-11-05T10:00\",\"fim\":\"2026-11-05T11:00\"}")).andReturn();
        assertEquals(201, status(r), "solicitação restrita");
        return r.getResponse().getContentAsString().replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
    }

    private void vincular(String responsavel, String recurso) throws Exception {
        int s = status(mvc.perform(post("/vinculos").header(HttpHeaders.AUTHORIZATION, token("adm", "ADMINISTRADOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"responsavelId\":\"" + responsavel + "\",\"recursoId\":\"" + recurso + "\"}")).andReturn());
        assertEquals(201, s, "vínculo " + responsavel + " a " + recurso);
    }

    @Test
    @DisplayName("D3 — Administrador vincula Responsável a recurso: 201")
    void administradorVincula() throws Exception {
        vincular("resp1", "SALA-R");
    }

    @Test
    @DisplayName("D3 — Solicitante não vincula: 403")
    void solicitanteNaoVincula() throws Exception {
        int s = status(mvc.perform(post("/vinculos").header(HttpHeaders.AUTHORIZATION, token("alice", "SOLICITANTE"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"responsavelId\":\"alice\",\"recursoId\":\"SALA-R\"}")).andReturn());
        assertEquals(403, s);
    }

    @Test
    @DisplayName("D3 — Responsável vinculado aprova reserva restrita do recurso: 200")
    void vinculadoAprova() throws Exception {
        vincular("resp1", "SALA-R");
        String id = criarReservaRestrita();
        assertEquals(200, status(mvc.perform(post("/reservas/" + id + "/aprovacao")
                .header(HttpHeaders.AUTHORIZATION, token("resp1", "RESPONSAVEL"))).andReturn()));
    }

    @Test
    @DisplayName("D3 — Responsável sem vínculo com o recurso não aprova: 403")
    void naoVinculadoNaoAprova() throws Exception {
        vincular("resp1", "SALA-S");
        String id = criarReservaRestrita();
        assertEquals(403, status(mvc.perform(post("/reservas/" + id + "/aprovacao")
                .header(HttpHeaders.AUTHORIZATION, token("resp1", "RESPONSAVEL"))).andReturn()));
    }

    @Test
    @DisplayName("D3 — Responsável sem vínculo não rejeita: 403")
    void naoVinculadoNaoRejeita() throws Exception {
        vincular("resp1", "SALA-S");
        String id = criarReservaRestrita();
        assertEquals(403, status(mvc.perform(post("/reservas/" + id + "/rejeicao")
                .header(HttpHeaders.AUTHORIZATION, token("resp1", "RESPONSAVEL"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"justificativa\":\"motivo\"}")).andReturn()));
    }

    @Test
    @DisplayName("D3 — Responsável sem vínculo não consulta a reserva: 403; vinculado consulta: 200")
    void consultaSegueVinculo() throws Exception {
        vincular("resp1", "SALA-S");
        String id = criarReservaRestrita();
        assertEquals(403, status(mvc.perform(get("/reservas/" + id)
                .header(HttpHeaders.AUTHORIZATION, token("resp1", "RESPONSAVEL"))).andReturn()));
        vincular("resp1", "SALA-R");
        assertEquals(200, status(mvc.perform(get("/reservas/" + id)
                .header(HttpHeaders.AUTHORIZATION, token("resp1", "RESPONSAVEL"))).andReturn()));
    }
}
