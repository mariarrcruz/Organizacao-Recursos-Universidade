package com.organizacao.recursos.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.organizacao.recursos.persistencia.ArmazenamentoMemoria;
import com.organizacao.recursos.servico.ServicoAutenticacao;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Cadastro de recursos: só ADMINISTRADOR; o recurso fica gravado no armazenamento. */
class RecursosControllerTest {

    private final ServicoAutenticacao auth = new ServicoAutenticacao();
    private final ArmazenamentoMemoria armazenamento = new ArmazenamentoMemoria();
    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new RecursosController(auth, armazenamento))
            .setControllerAdvice(new TratadorErros())
            .build();

    private static final String CORPO = "{\"id\":\"SALA-9\",\"tipo\":\"SALA\",\"nome\":\"Sala 9\","
            + "\"localizacao\":\"BLOCO_A\",\"capacidade\":20,\"restrito\":true,\"status\":\"DISPONIVEL\","
            + "\"competencias\":[\"projetor\"]}";

    private String token(String sub, String perfil) {
        return auth.gerarToken(sub, List.of(perfil), Instant.now().plusSeconds(3600));
    }

    @Test
    void administradorCadastraRecurso() throws Exception {
        int status = mvc.perform(post("/recursos")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("adm", "ADMINISTRADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(CORPO))
                .andReturn().getResponse().getStatus();
        assertEquals(201, status);
        assertTrue(armazenamento.buscarRecurso("SALA-9").isPresent());
        assertEquals(List.of("projetor"), List.copyOf(armazenamento.buscarRecurso("SALA-9").get().competencias()));
    }

    @Test
    void solicitanteNaoCadastraRecurso() throws Exception {
        int status = mvc.perform(post("/recursos")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("alice", "SOLICITANTE"))
                        .contentType(MediaType.APPLICATION_JSON).content(CORPO))
                .andReturn().getResponse().getStatus();
        assertEquals(403, status);
        assertTrue(armazenamento.buscarRecurso("SALA-9").isEmpty());
    }

    @Test
    void anonimoNaoCadastraRecurso() throws Exception {
        int status = mvc.perform(post("/recursos").contentType(MediaType.APPLICATION_JSON).content(CORPO))
                .andReturn().getResponse().getStatus();
        assertEquals(401, status);
    }
}
