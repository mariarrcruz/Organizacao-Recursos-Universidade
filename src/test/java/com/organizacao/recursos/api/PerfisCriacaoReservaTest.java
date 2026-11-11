package com.organizacao.recursos.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Quem cria reserva (D5): Solicitante cria; Responsável cria a própria (decisão AMB-03);
 * Administrador não cria reserva (persona do Administrador não lista essa ação).
 */
class PerfisCriacaoReservaTest {

    private final ServicoAutenticacao auth = new ServicoAutenticacao();
    private final ArmazenamentoMemoria armazenamento = new ArmazenamentoMemoria();
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ReservasController(
                    new ServicoReservas(Clock.fixed(LocalDateTime.of(2026, 11, 1, 8, 0).toInstant(ZoneOffset.UTC),
                            ZoneOffset.UTC), armazenamento), auth, armazenamento))
            .setControllerAdvice(new TratadorErros()).build();

    private int criar(String sub, String perfil) throws Exception {
        armazenamento.gravarRecurso(new Recurso("SALA-P", TipoRecurso.SALA, "P", "B", 10, false,
                StatusRecurso.DISPONIVEL, Set.of()));
        String token = "Bearer " + auth.gerarToken(sub, List.of(perfil), Instant.now().plusSeconds(600));
        String corpo = "{\"recursoId\":\"SALA-P\",\"inicio\":\"2026-11-05T10:00\",\"fim\":\"2026-11-05T11:00\"}";
        return mvc.perform(post("/reservas").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andReturn().getResponse().getStatus();
    }

    @Test
    @DisplayName("D5 — Solicitante cria a própria reserva: 201")
    void solicitanteCria() throws Exception {
        assertEquals(201, criar("alice", "SOLICITANTE"));
    }

    @Test
    @DisplayName("D5 — Responsável cria a própria reserva (AMB-03): 201")
    void responsavelCria() throws Exception {
        assertEquals(201, criar("resp", "RESPONSAVEL"));
    }

    @Test
    @DisplayName("D5 — Administrador não cria reserva: 403")
    void administradorNaoCria() throws Exception {
        assertEquals(403, criar("adm", "ADMINISTRADOR"));
    }
}
