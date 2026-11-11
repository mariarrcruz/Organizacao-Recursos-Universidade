package com.organizacao.recursos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.junit.jupiter.api.Test;
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

    @Test
    void aplicacaoAtendeSolicitacaoDeReservaComBancoReal() throws Exception {
        String token = mvc.perform(post("/dev/tokens").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sub\":\"adm\",\"perfis\":[\"ADMINISTRADOR\"]}"))
                .andReturn().getResponse().getContentAsString()
                .replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");

        int cadastro = mvc.perform(post("/recursos").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"SALA-CTX\",\"tipo\":\"SALA\",\"nome\":\"Sala\",\"localizacao\":\"A\","
                                + "\"capacidade\":10,\"restrito\":false,\"status\":\"DISPONIVEL\",\"competencias\":[]}"))
                .andReturn().getResponse().getStatus();
        assertEquals(201, cadastro);

        int semToken = mvc.perform(get("/reservas/inexistente")).andReturn().getResponse().getStatus();
        assertEquals(401, semToken);
    }
}
