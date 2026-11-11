package com.organizacao.recursos.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.organizacao.recursos.persistencia.Armazenamento;
import com.organizacao.recursos.servico.ServicoAutenticacao;
import com.organizacao.recursos.servico.ServicoRelatorios;
import com.organizacao.recursos.servico.ServicoReservas;
import com.organizacao.recursos.servico.ServicoMovimentacoes;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Contrato de caixa-preta de {@code GET /relatorios/utilizacao} (RF-12, RN-09).
 *
 * <p>Todas as ações passam pela API HTTP: a massa é semeada com {@code POST /recursos} e
 * {@code POST /reservas}, e a conta esperada está num comentário em cada teste. As subclasses rodam
 * o mesmo contrato contra armazenamento em memória e contra PostgreSQL.
 *
 * <p>Regras de contagem usadas nas contas:
 * <ul>
 *   <li>Horas contam só reservas APROVADA, EM_USO e CONCLUIDA (mesmo critério de {@code ServicoRelatorios}).
 *       CANCELADA, REJEITADA, SOLICITADA e NAO_COMPARECEU não contam como uso.</li>
 *   <li>Cada reserva conta só a parte que cai dentro do período [inicio 00:00, fim+1 00:00).</li>
 *   <li>Conflito evitado = tentativa recusada (409) por sobreposição com reserva, cujo intervalo
 *       pedido cruza o período. Recusas por manutenção não entram.</li>
 * </ul>
 */
public abstract class RelatorioUtilizacaoContrato {

    /** Relógio fixo antes de todas as reservas da massa, para que cancelamentos sejam permitidos. */
    private static final Clock RELOGIO =
            Clock.fixed(LocalDateTime.of(2026, 11, 1, 8, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

    private final ObjectMapper json = new ObjectMapper();
    private ServicoAutenticacao auth;
    private MockMvc mvc;

    /** Armazenamento novo e vazio; cada subclasse escolhe a implementação. */
    protected abstract Armazenamento novoArmazenamento();

    @BeforeEach
    void montarApi() {
        Armazenamento armazenamento = novoArmazenamento();
        auth = new ServicoAutenticacao();
        ServicoReservas reservas = new ServicoReservas(RELOGIO, armazenamento);
        ServicoRelatorios relatorios = new ServicoRelatorios(reservas, new ServicoMovimentacoes(reservas));
        mvc = MockMvcBuilders
                .standaloneSetup(new RecursosController(auth, armazenamento),
                        new ReservasController(reservas, auth, armazenamento),
                        new RelatoriosController(relatorios, auth))
                .setControllerAdvice(new TratadorErros())
                .build();
    }

    // ------------------------------------------------------------------ testes

    @Test
    @DisplayName("RF-12 — Massa com reservas, cancelamento e conflitos: horas, carga por professor e conflitos evitados")
    void massaSemeada_retornaHorasCargaEConflitos() throws Exception {
        // Massa (todas as reservas são de alice e entram APROVADA, pois os recursos são livres):
        //   R1  SALA-A  05/11 10:00-12:00  -> 2 h dentro do período
        //   R2  PROF-1  05/11 09:00-11:00  -> 2 h dentro do período
        //   R3  SALA-A  04/11 22:00-05/11 02:00 -> cruza a borda: dentro do período 00:00-02:00 = 2 h
        //   R4  SALA-A  05/11 14:00-16:00  -> cancelada por alice antes do início: 0 h
        //   R5  SALA-A  07/11 10:00-11:00  -> fora do período: 0 h
        // Tentativas de bob recusadas por sobreposição (409):
        //   C1  SALA-A  05/11 10:30-11:30  -> sobrepõe R1, dentro do período: conta
        //   C2  PROF-1  05/11 09:30-10:30  -> sobrepõe R2, dentro do período: conta
        //   C3  SALA-A  07/11 10:30-11:30  -> sobrepõe R5, mas fora do período: não conta
        // Esperado (período 05/11 a 05/11):
        //   SALA-A: R1 + R3 = 2 + 2 = 4,0 h        (SALA)
        //   PROF-1: R2 = 2,0 h                     (PROFESSOR)
        //   carga horária por professor: PROF-1 = 2,0 h
        //   conflitos evitados: total = C1 + C2 = 2 (SALA-A 1, PROF-1 1)
        cadastrarRecurso("SALA-A", "SALA");
        cadastrarRecurso("PROF-1", "PROFESSOR");
        reservar("alice", "SALA-A", "2026-11-05T10:00", "2026-11-05T12:00");
        reservar("alice", "PROF-1", "2026-11-05T09:00", "2026-11-05T11:00");
        reservar("alice", "SALA-A", "2026-11-04T22:00", "2026-11-05T02:00");
        String r4 = reservar("alice", "SALA-A", "2026-11-05T14:00", "2026-11-05T16:00");
        cancelar("alice", r4, "Imprevisto");
        reservar("alice", "SALA-A", "2026-11-07T10:00", "2026-11-07T11:00");
        tentar("bob", "SALA-A", "2026-11-05T10:30", "2026-11-05T11:30", 409);
        tentar("bob", "PROF-1", "2026-11-05T09:30", "2026-11-05T10:30", 409);
        tentar("bob", "SALA-A", "2026-11-07T10:30", "2026-11-07T11:30", 409);

        JsonNode corpo = consultar("resp", "2026-11-05", "2026-11-05", 200);

        assertEquals(2, corpo.get("recursos").size());
        JsonNode prof = porCampo(corpo.get("recursos"), "recursoId", "PROF-1");
        JsonNode sala = porCampo(corpo.get("recursos"), "recursoId", "SALA-A");
        assertEquals("PROFESSOR", prof.get("tipo").asText());
        assertEquals(2.0, prof.get("horas").asDouble());
        assertEquals("SALA", sala.get("tipo").asText());
        assertEquals(4.0, sala.get("horas").asDouble());

        assertEquals(1, corpo.get("cargaHorariaPorProfessor").size());
        assertEquals("PROF-1", corpo.get("cargaHorariaPorProfessor").get(0).get("professorId").asText());
        assertEquals(2.0, corpo.get("cargaHorariaPorProfessor").get(0).get("horas").asDouble());

        assertEquals(2, corpo.get("conflitosEvitados").get("total").asInt());
        assertEquals(1, porCampo(corpo.get("conflitosEvitados").get("porRecurso"), "recursoId", "PROF-1")
                .get("quantidade").asInt());
        assertEquals(1, porCampo(corpo.get("conflitosEvitados").get("porRecurso"), "recursoId", "SALA-A")
                .get("quantidade").asInt());
    }

    @Test
    @DisplayName("RF-12 — Período sem reservas: zeros e listas vazias")
    void periodoVazio_retornaZeros() throws Exception {
        // Massa mínima: uma reserva de 2 h em 05/11. Consulta em 01/12, sem nenhuma reserva.
        // Esperado: 0 recursos, 0 professores, 0 conflitos.
        cadastrarRecurso("SALA-A", "SALA");
        reservar("alice", "SALA-A", "2026-11-05T10:00", "2026-11-05T12:00");

        JsonNode corpo = consultar("adm", "2026-12-01", "2026-12-01", 200);

        assertEquals(0, corpo.get("recursos").size());
        assertEquals(0, corpo.get("cargaHorariaPorProfessor").size());
        assertEquals(0, corpo.get("conflitosEvitados").get("total").asInt());
        assertEquals(0, corpo.get("conflitosEvitados").get("porRecurso").size());
    }

    @Test
    @DisplayName("RF-12 — Reserva que cruza a borda do período conta só a parte dentro dele")
    void reservaQueCruzaABorda_contaSoAParteDentroDoPeriodo() throws Exception {
        // Reserva: SALA-A 04/11 22:00 até 05/11 02:00 (4 h no total).
        // Período: 05/11 00:00 a 06/11 00:00. Parte dentro: 00:00 a 02:00 = 2,0 h.
        // Um critério que conte pelo dia de início daria 0 h (o início é em 04/11).
        cadastrarRecurso("SALA-A", "SALA");
        reservar("alice", "SALA-A", "2026-11-04T22:00", "2026-11-05T02:00");

        JsonNode corpo = consultar("resp", "2026-11-05", "2026-11-05", 200);

        assertEquals(1, corpo.get("recursos").size());
        assertEquals(2.0, corpo.get("recursos").get(0).get("horas").asDouble());
    }

    @Test
    @DisplayName("RF-12 — Reserva cancelada não conta como uso e libera o horário")
    void reservaCancelada_naoContaComoUso() throws Exception {
        // alice reserva SALA-A 05/11 10:00-12:00 e cancela antes do início: 0 h.
        // bob reserva o mesmo horário e passa (201, sem conflito); só essa reserva conta: 2,0 h.
        cadastrarRecurso("SALA-A", "SALA");
        String cancelada = reservar("alice", "SALA-A", "2026-11-05T10:00", "2026-11-05T12:00");
        cancelar("alice", cancelada, "Desistência");
        reservar("bob", "SALA-A", "2026-11-05T10:00", "2026-11-05T12:00");

        JsonNode corpo = consultar("resp", "2026-11-05", "2026-11-05", 200);

        assertEquals(1, corpo.get("recursos").size());
        assertEquals(2.0, corpo.get("recursos").get(0).get("horas").asDouble());
        assertEquals(0, corpo.get("conflitosEvitados").get("total").asInt());
    }

    @Test
    @DisplayName("RF-12 — Recusa por sobreposição fora do período não conta como conflito evitado")
    void conflitoForaDoPeriodo_naoEhContado() throws Exception {
        // alice reserva SALA-A 07/11 10:00-11:00. bob tenta 07/11 10:30-11:30: recusado (409).
        // Consulta em 05/11: nada no período; conflitos evitados = 0.
        cadastrarRecurso("SALA-A", "SALA");
        reservar("alice", "SALA-A", "2026-11-07T10:00", "2026-11-07T11:00");
        tentar("bob", "SALA-A", "2026-11-07T10:30", "2026-11-07T11:30", 409);

        JsonNode corpo = consultar("adm", "2026-11-05", "2026-11-05", 200);

        assertEquals(0, corpo.get("recursos").size());
        assertEquals(0, corpo.get("conflitosEvitados").get("total").asInt());
    }

    @Test
    @DisplayName("RF-12 — Sem token: 401")
    void semToken_retorna401() throws Exception {
        assertEquals(401, mvc.perform(get("/relatorios/utilizacao")
                        .param("inicio", "2026-11-05").param("fim", "2026-11-05"))
                .andReturn().getResponse().getStatus());
    }

    @Test
    @DisplayName("RF-12 — Solicitante, mesmo com reserva própria no período: 403")
    void solicitante_retorna403() throws Exception {
        cadastrarRecurso("SALA-A", "SALA");
        reservar("alice", "SALA-A", "2026-11-05T10:00", "2026-11-05T12:00");

        consultar("alice", "2026-11-05", "2026-11-05", 403);
    }

    @Test
    @DisplayName("RF-12 — Responsável e Administrador consultam: 200")
    void responsavelEAdministrador_consultam() throws Exception {
        consultar("resp", "2026-11-05", "2026-11-05", 200);
        consultar("adm", "2026-11-05", "2026-11-05", 200);
    }

    @Test
    @DisplayName("RF-12 — Período com fim antes do início ou data inválida: 400")
    void periodoInvalido_retorna400() throws Exception {
        consultar("adm", "2026-11-06", "2026-11-05", 400);
        consultar("adm", "2026-13-01", "2026-11-05", 400);
    }

    // ------------------------------------------------------------------ apoio à massa e às chamadas

    private String token(String sub) {
        String perfil = switch (sub) {
            case "resp" -> "RESPONSAVEL";
            case "adm" -> "ADMINISTRADOR";
            default -> "SOLICITANTE";
        };
        return "Bearer " + auth.gerarToken(sub, List.of(perfil), Instant.now().plusSeconds(3600));
    }

    private void cadastrarRecurso(String id, String tipo) throws Exception {
        String corpo = "{\"id\":\"" + id + "\",\"tipo\":\"" + tipo + "\",\"nome\":\"" + id + "\","
                + "\"localizacao\":\"BLOCO_A\",\"capacidade\":30,\"restrito\":false,\"status\":\"DISPONIVEL\","
                + "\"competencias\":[]}";
        int status = mvc.perform(post("/recursos").header(HttpHeaders.AUTHORIZATION, token("adm"))
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andReturn().getResponse().getStatus();
        assertEquals(201, status, "cadastro do recurso " + id);
    }

    /** Reserva pela API e devolve o id; espera 201. */
    private String reservar(String quem, String recurso, String inicio, String fim) throws Exception {
        MvcResult res = mvc.perform(post("/reservas").header(HttpHeaders.AUTHORIZATION, token(quem))
                        .contentType(MediaType.APPLICATION_JSON).content(corpoReserva(recurso, inicio, fim)))
                .andReturn();
        assertEquals(201, res.getResponse().getStatus(), "reserva de " + quem + " em " + recurso + " " + inicio);
        return json.readTree(res.getResponse().getContentAsString()).get("id").asText();
    }

    /** Tentativa que deve ser recusada com o status esperado. */
    private void tentar(String quem, String recurso, String inicio, String fim, int esperado) throws Exception {
        int status = mvc.perform(post("/reservas").header(HttpHeaders.AUTHORIZATION, token(quem))
                        .contentType(MediaType.APPLICATION_JSON).content(corpoReserva(recurso, inicio, fim)))
                .andReturn().getResponse().getStatus();
        assertEquals(esperado, status, "tentativa de " + quem + " em " + recurso + " " + inicio);
    }

    private void cancelar(String quem, String reservaId, String justificativa) throws Exception {
        int status = mvc.perform(post("/reservas/" + reservaId + "/cancelamento")
                        .header(HttpHeaders.AUTHORIZATION, token(quem))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"justificativa\":\"" + justificativa + "\"}"))
                .andReturn().getResponse().getStatus();
        assertEquals(200, status, "cancelamento da reserva " + reservaId);
    }

    private JsonNode consultar(String quem, String inicio, String fim, int esperado) throws Exception {
        MvcResult res = mvc.perform(get("/relatorios/utilizacao")
                        .header(HttpHeaders.AUTHORIZATION, token(quem))
                        .param("inicio", inicio).param("fim", fim))
                .andReturn();
        assertEquals(esperado, res.getResponse().getStatus(), "consulta por " + quem);
        if (esperado != 200) {
            return null;
        }
        return json.readTree(res.getResponse().getContentAsString());
    }

    private static String corpoReserva(String recurso, String inicio, String fim) {
        return "{\"recursoId\":\"" + recurso + "\",\"inicio\":\"" + inicio + "\",\"fim\":\"" + fim + "\"}";
    }

    private static JsonNode porCampo(JsonNode lista, String campo, String valor) {
        for (JsonNode n : lista) {
            if (valor.equals(n.get(campo).asText())) {
                return n;
            }
        }
        throw new AssertionError("nenhum item com " + campo + "=" + valor + " em " + lista);
    }
}
