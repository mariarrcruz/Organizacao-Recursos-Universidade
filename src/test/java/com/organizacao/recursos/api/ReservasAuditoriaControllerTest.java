package com.organizacao.recursos.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.organizacao.recursos.dominio.Perfil;
import com.organizacao.recursos.dominio.Recurso;
import com.organizacao.recursos.dominio.StatusRecurso;
import com.organizacao.recursos.dominio.TipoRecurso;
import com.organizacao.recursos.dominio.Usuario;
import com.organizacao.recursos.persistencia.ArmazenamentoMemoria;
import com.organizacao.recursos.servico.ServicoAutenticacao;
import com.organizacao.recursos.servico.ServicoReservas;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Consulta do histórico de uma reserva por HTTP: só Responsável e Administrador (RN-09, ADR-005). */
class ReservasAuditoriaControllerTest {

    private final ServicoAutenticacao auth = new ServicoAutenticacao();
    private final ArmazenamentoMemoria armazenamento = new ArmazenamentoMemoria();
    private final ServicoReservas servico = new ServicoReservas(
            Clock.fixed(LocalDateTime.of(2026, 11, 5, 9, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC), armazenamento);
    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new ReservasController(servico, auth, armazenamento))
            .setControllerAdvice(new TratadorErros())
            .build();

    private final Usuario alice = new Usuario("alice", Set.of(Perfil.SOLICITANTE));
    private final Recurso sala = new Recurso("S1", TipoRecurso.SALA, "Sala 1", "BLOCO_A", 30, true,
            StatusRecurso.DISPONIVEL, Set.of());

    private String token(String sub, String perfil) {
        return auth.gerarToken(sub, List.of(perfil), Instant.now().plusSeconds(3600));
    }

    private String novaReserva() {
        armazenamento.gravarRecurso(sala);
        return servico.solicitar(alice, sala, LocalDateTime.of(2026, 11, 5, 10, 0),
                LocalDateTime.of(2026, 11, 5, 11, 0)).getId();
    }

    @Test
    void solicitanteDonoRecebe403AoConsultarHistorico() throws Exception {
        String id = novaReserva();
        int status = mvc.perform(get("/reservas/" + id + "/auditoria")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("alice", "SOLICITANTE")))
                .andReturn().getResponse().getStatus();
        assertEquals(403, status);
    }

    @Test
    void responsavelConsultaHistorico() throws Exception {
        String id = novaReserva();
        var resposta = mvc.perform(get("/reservas/" + id + "/auditoria")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("resp", "RESPONSAVEL")))
                .andReturn().getResponse();
        assertEquals(200, resposta.getStatus());
        // RF-10 / RFC RF-11: cada registro traz o instante (Clock: 2026-11-05 09:00 UTC)
        assertTrue(resposta.getContentAsString().contains("\"instante\":\"2026-11-05T09:00:00\""),
                resposta.getContentAsString());
    }

    @Test
    void administradorConsultaHistorico() throws Exception {
        String id = novaReserva();
        int status = mvc.perform(get("/reservas/" + id + "/auditoria")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("adm", "ADMINISTRADOR")))
                .andReturn().getResponse().getStatus();
        assertEquals(200, status);
    }

    /** RN-09: o registro de auditoria não pode ser alterado nem apagado pela API (ADR-005). */
    @Test
    void auditoriaNaoPodeSerAlteradaNemApagadaPelaApi() throws Exception {
        String id = novaReserva();
        String admin = token("adm", "ADMINISTRADOR");
        int alterada = mvc.perform(put("/reservas/" + id + "/auditoria")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andReturn().getResponse().getStatus();
        int apagada = mvc.perform(delete("/reservas/" + id + "/auditoria")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andReturn().getResponse().getStatus();
        assertEquals(405, alterada);
        assertEquals(405, apagada);
    }
}
