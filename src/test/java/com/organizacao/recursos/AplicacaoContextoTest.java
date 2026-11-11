package com.organizacao.recursos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.junit.jupiter.api.Test;
import com.organizacao.recursos.dominio.Recurso;
import com.organizacao.recursos.dominio.StatusRecurso;
import com.organizacao.recursos.dominio.TipoRecurso;
import com.organizacao.recursos.persistencia.ArmazenamentoPostgres;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Contexto completo da aplicação (Spring Boot) com PostgreSQL real: sobe, cria o esquema e atende a API.
 * Cobre a configuração de produção (ConfiguracaoAplicacao) e o controlador com Armazenamento injetado.
 */
@Testcontainers
@SpringBootTest(properties = "app.dev.tokens=true")
@AutoConfigureMockMvc
class AplicacaoContextoTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void banco(DynamicPropertyRegistry registro) {
        registro.add("app.banco.url", POSTGRES::getJdbcUrl);
        registro.add("app.banco.usuario", POSTGRES::getUsername);
        registro.add("app.banco.senha", POSTGRES::getPassword);
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ArmazenamentoPostgres armazenamento;

    @Test
    void aplicacaoAtendeSolicitacaoDeReservaComBancoReal() throws Exception {
        // D4: o token de laboratório não emite ADMINISTRADOR, então o recurso é gravado direto no banco real.
        int tokenAdmin = mvc.perform(post("/dev/tokens").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sub\":\"adm\",\"perfis\":[\"ADMINISTRADOR\"]}"))
                .andReturn().getResponse().getStatus();
        assertEquals(400, tokenAdmin);

        armazenamento.gravarRecurso(new Recurso("SALA-CTX", TipoRecurso.SALA, "Sala", "A", 10, false,
                StatusRecurso.DISPONIVEL, Set.of()));
        assertTrue(armazenamento.buscarRecurso("SALA-CTX").isPresent());

        int semToken = mvc.perform(get("/reservas/inexistente")).andReturn().getResponse().getStatus();
        assertEquals(401, semToken);
    }
}
