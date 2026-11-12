package com.organizacao.recursos.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.organizacao.recursos.dominio.EstadoReserva;
import com.organizacao.recursos.dominio.Perfil;
import com.organizacao.recursos.dominio.Recurso;
import com.organizacao.recursos.dominio.Reserva;
import com.organizacao.recursos.dominio.StatusRecurso;
import com.organizacao.recursos.dominio.TipoRecurso;
import com.organizacao.recursos.dominio.Usuario;
import com.organizacao.recursos.persistencia.ArmazenamentoMemoria;
import com.organizacao.recursos.servico.RegistroNotificacoes;
import com.organizacao.recursos.servico.ServicoAutenticacao;
import com.organizacao.recursos.servico.ServicoReservas;
import com.organizacao.recursos.servico.ServicoUsuarios;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Login por senha (RF-01), catálogo de recursos (RF-02, RF-03) e notificações simuladas (RF-11). */
class CatalogoLoginNotificacoesTest {

    private static final Clock RELOGIO =
            Clock.fixed(LocalDateTime.of(2026, 11, 5, 9, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

    private final ServicoAutenticacao auth = new ServicoAutenticacao();
    private final ServicoUsuarios usuarios = new ServicoUsuarios();
    private final ArmazenamentoMemoria armazenamento = new ArmazenamentoMemoria();
    private final RegistroNotificacoes notificacoes = new RegistroNotificacoes(RELOGIO);
    private final ServicoReservas servico = new ServicoReservas(RELOGIO, armazenamento);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(
                    new AutenticacaoController(auth, usuarios),
                    new CatalogoRecursosController(auth, armazenamento, servico),
                    new NotificacoesController(auth, notificacoes),
                    new ReservasController(servico, auth, armazenamento))
            .setControllerAdvice(new TratadorErros())
            .build();

    private final Usuario administrador = new Usuario("adm", Set.of(Perfil.ADMINISTRADOR));

    @BeforeEach
    void preparar() {
        usuarios.semearAdministrador("adm", "senha-forte-1");
        armazenamento.gravarRecurso(new Recurso("SALA-A", TipoRecurso.SALA, "Sala A", "BLOCO_A", 30, false,
                StatusRecurso.DISPONIVEL, Set.of("projetor")));
        armazenamento.gravarRecurso(new Recurso("SALA-B", TipoRecurso.SALA, "Sala B", "BLOCO_B", 10, false,
                StatusRecurso.DISPONIVEL, Set.of()));
        armazenamento.gravarRecurso(new Recurso("MAT-1", TipoRecurso.MATERIAL, "Projetor portátil", "BLOCO_A", 1, false,
                StatusRecurso.DISPONIVEL, Set.of("projetor")));
    }

    private String bearer(String sub, Perfil perfil) {
        return "Bearer " + auth.gerarToken(sub, List.of(perfil.name()), Instant.now().plusSeconds(3600));
    }

    // ---------------------------------------------------------------- login

    @Test
    @DisplayName("RF-01 — login com senha correta devolve token e perfis")
    void loginComSenhaCorreta() throws Exception {
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"adm\",\"senha\":\"senha-forte-1\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.token").isNotEmpty())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.perfis[0]").value("ADMINISTRADOR"));
    }

    @Test
    @DisplayName("RF-01 — senha errada e usuário inexistente recebem a mesma resposta 401")
    void loginFalhaSemRevelarQualEstaErrado() throws Exception {
        int senhaErrada = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"adm\",\"senha\":\"outra-senha-x\"}")).andReturn().getResponse().getStatus();
        String corpoSenha = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"adm\",\"senha\":\"outra-senha-x\"}")).andReturn().getResponse().getContentAsString();
        String corpoUsuario = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"ninguem\",\"senha\":\"outra-senha-x\"}")).andReturn().getResponse().getContentAsString();
        assertEquals(401, senhaErrada);
        assertEquals(corpoSenha.replace("adm", "X"), corpoUsuario.replace("ninguem", "X"));
    }

    @Test
    @DisplayName("RF-15 — só administrador cadastra usuário com senha; solicitante recebe 403")
    void cadastroDeUsuarioExigeAdministrador() throws Exception {
        String corpo = "{\"id\":\"prof\",\"senha\":\"senha-prof-1\",\"perfis\":[\"SOLICITANTE\"]}";
        int solicitante = mvc.perform(post("/usuarios").header(HttpHeaders.AUTHORIZATION, bearer("x", Perfil.SOLICITANTE))
                .contentType(MediaType.APPLICATION_JSON).content(corpo)).andReturn().getResponse().getStatus();
        int admin = mvc.perform(post("/usuarios").header(HttpHeaders.AUTHORIZATION, bearer("adm", Perfil.ADMINISTRADOR))
                .contentType(MediaType.APPLICATION_JSON).content(corpo)).andReturn().getResponse().getStatus();
        assertEquals(403, solicitante);
        assertEquals(201, admin);
        assertEquals(Set.of(Perfil.SOLICITANTE), usuarios.autenticar("prof", "senha-prof-1"));
    }

    @Test
    @DisplayName("RF-01 — senha curta é recusada no cadastro (400)")
    void senhaCurtaRecusada() throws Exception {
        int status = mvc.perform(post("/usuarios").header(HttpHeaders.AUTHORIZATION, bearer("adm", Perfil.ADMINISTRADOR))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"novo\",\"senha\":\"curta\",\"perfis\":[\"SOLICITANTE\"]}")).andReturn().getResponse().getStatus();
        assertEquals(400, status);
    }

    // ---------------------------------------------------------------- catálogo

    @Test
    @DisplayName("RF-03 — pesquisa filtra por tipo, capacidade mínima e competência")
    void pesquisaComFiltros() throws Exception {
        String sol = bearer("alice", Perfil.SOLICITANTE);
        String corpo = mvc.perform(get("/recursos").header(HttpHeaders.AUTHORIZATION, sol)
                        .param("tipo", "SALA").param("capacidade_min", "20"))
                .andReturn().getResponse().getContentAsString();
        assertTrue(corpo.contains("SALA-A"));
        assertFalse(corpo.contains("SALA-B"));
        assertFalse(corpo.contains("MAT-1"));

        String porCompetencia = mvc.perform(get("/recursos").header(HttpHeaders.AUTHORIZATION, sol)
                        .param("competencia", "projetor"))
                .andReturn().getResponse().getContentAsString();
        assertTrue(porCompetencia.contains("MAT-1") && porCompetencia.contains("SALA-A"));
        assertFalse(porCompetencia.contains("SALA-B"));
    }

    @Test
    @DisplayName("RF-03 — pesquisa com período exclui recurso com reserva ativa sobreposta")
    void pesquisaPorDisponibilidade() throws Exception {
        servico.registrarExistente(new Reserva("R1", new Usuario("bob", Set.of(Perfil.SOLICITANTE)),
                armazenamento.buscarRecurso("SALA-A").orElseThrow(),
                LocalDateTime.parse("2026-11-20T10:00"), LocalDateTime.parse("2026-11-20T12:00"),
                EstadoReserva.APROVADA));
        String corpo = mvc.perform(get("/recursos").header(HttpHeaders.AUTHORIZATION, bearer("alice", Perfil.SOLICITANTE))
                        .param("tipo", "SALA")
                        .param("inicio", "2026-11-20T11:00").param("fim", "2026-11-20T13:00"))
                .andReturn().getResponse().getContentAsString();
        assertFalse(corpo.contains("SALA-A"));
        assertTrue(corpo.contains("SALA-B"));
    }

    @Test
    @DisplayName("RF-02 — recurso inexistente: 404; consulta de recurso existente: 200")
    void consultaRecurso() throws Exception {
        String sol = bearer("alice", Perfil.SOLICITANTE);
        assertEquals(404, mvc.perform(get("/recursos/NAO-EXISTE").header(HttpHeaders.AUTHORIZATION, sol))
                .andReturn().getResponse().getStatus());
        assertEquals(200, mvc.perform(get("/recursos/SALA-A").header(HttpHeaders.AUTHORIZATION, sol))
                .andReturn().getResponse().getStatus());
    }

    @Test
    @DisplayName("RF-02 — edição: solicitante 403; capacidade negativa 400; edição válida 200")
    void edicaoDeRecurso() throws Exception {
        int solicitante = mvc.perform(put("/recursos/SALA-A").header(HttpHeaders.AUTHORIZATION, bearer("alice", Perfil.SOLICITANTE))
                .contentType(MediaType.APPLICATION_JSON).content("{\"capacidade\":40}")).andReturn().getResponse().getStatus();
        int negativa = mvc.perform(put("/recursos/SALA-A").header(HttpHeaders.AUTHORIZATION, bearer("adm", Perfil.ADMINISTRADOR))
                .contentType(MediaType.APPLICATION_JSON).content("{\"capacidade\":-1}")).andReturn().getResponse().getStatus();
        int ok = mvc.perform(put("/recursos/SALA-A").header(HttpHeaders.AUTHORIZATION, bearer("adm", Perfil.ADMINISTRADOR))
                .contentType(MediaType.APPLICATION_JSON).content("{\"capacidade\":40,\"status\":\"INDISPONIVEL\"}"))
                .andReturn().getResponse().getStatus();
        assertEquals(403, solicitante);
        assertEquals(400, negativa);
        assertEquals(200, ok);
        assertEquals(40, armazenamento.buscarRecurso("SALA-A").orElseThrow().capacidade());
        assertEquals(StatusRecurso.INDISPONIVEL, armazenamento.buscarRecurso("SALA-A").orElseThrow().status());
    }

    @Test
    @DisplayName("RF-02 — exclusão: recurso com histórico de reservas recebe 409; sem histórico, 204")
    void exclusaoDeRecurso() throws Exception {
        servico.registrarExistente(new Reserva("R2", new Usuario("bob", Set.of(Perfil.SOLICITANTE)),
                armazenamento.buscarRecurso("SALA-B").orElseThrow(),
                LocalDateTime.parse("2026-11-21T10:00"), LocalDateTime.parse("2026-11-21T11:00"),
                EstadoReserva.CONCLUIDA));
        String adm = bearer("adm", Perfil.ADMINISTRADOR);
        assertEquals(409, mvc.perform(delete("/recursos/SALA-B").header(HttpHeaders.AUTHORIZATION, adm))
                .andReturn().getResponse().getStatus());
        assertEquals(204, mvc.perform(delete("/recursos/MAT-1").header(HttpHeaders.AUTHORIZATION, adm))
                .andReturn().getResponse().getStatus());
        assertEquals(404, mvc.perform(get("/recursos/MAT-1").header(HttpHeaders.AUTHORIZATION, adm))
                .andReturn().getResponse().getStatus());
    }

    // ---------------------------------------------------------------- notificações

    @Test
    @DisplayName("RF-11 — mudança de estado gera notificação para o solicitante, e só para ele")
    void transicaoGeraNotificacao() {
        Usuario alice = new Usuario("alice", Set.of(Perfil.SOLICITANTE));
        servico.definirNotificador(notificacoes);
        servico.vincularResponsavel(administrador, "resp", "SALA-A");
        Reserva r = new Reserva("R3", alice, armazenamento.buscarRecurso("SALA-A").orElseThrow(),
                LocalDateTime.parse("2026-11-22T10:00"), LocalDateTime.parse("2026-11-22T11:00"),
                EstadoReserva.SOLICITADA);
        servico.registrarExistente(r);
        servico.transicionar(new Usuario("resp", EnumSet.of(Perfil.RESPONSAVEL)), r, EstadoReserva.APROVADA, null);

        List<RegistroNotificacoes.Notificacao> daAlice = notificacoes.de("alice");
        assertEquals(1, daAlice.size());
        assertTrue(daAlice.get(0).mensagem().contains("SOLICITADA → APROVADA"));
        assertTrue(notificacoes.de("bob").isEmpty());
    }

    @Test
    @DisplayName("RF-11 — GET /notificacoes devolve só as notificações do próprio usuário")
    void endpointDeNotificacoesEhPessoal() throws Exception {
        notificacoes.notificar("alice", "para a alice");
        notificacoes.notificar("bob", "para o bob");
        String corpo = mvc.perform(get("/notificacoes").header(HttpHeaders.AUTHORIZATION, bearer("alice", Perfil.SOLICITANTE)))
                .andReturn().getResponse().getContentAsString();
        assertTrue(corpo.contains("para a alice"));
        assertFalse(corpo.contains("para o bob"));
    }

    // ---------------------------------------------------------------- minhas reservas e pendentes

    @Test
    @DisplayName("Solicitante vê só as próprias reservas em /reservas/minhas")
    void minhasReservasSoDoProprioUsuario() throws Exception {
        servico.registrarExistente(new Reserva("RM1", new Usuario("alice", Set.of(Perfil.SOLICITANTE)),
                armazenamento.buscarRecurso("SALA-A").orElseThrow(),
                LocalDateTime.parse("2026-11-23T10:00"), LocalDateTime.parse("2026-11-23T11:00"),
                EstadoReserva.APROVADA));
        servico.registrarExistente(new Reserva("RM2", new Usuario("bob", Set.of(Perfil.SOLICITANTE)),
                armazenamento.buscarRecurso("SALA-B").orElseThrow(),
                LocalDateTime.parse("2026-11-23T10:00"), LocalDateTime.parse("2026-11-23T11:00"),
                EstadoReserva.APROVADA));
        String corpo = mvc.perform(get("/reservas/minhas").header(HttpHeaders.AUTHORIZATION, bearer("alice", Perfil.SOLICITANTE)))
                .andReturn().getResponse().getContentAsString();
        assertTrue(corpo.contains("RM1"));
        assertFalse(corpo.contains("RM2"));
    }

    @Test
    @DisplayName("Responsável vê em /reservas/pendentes só solicitações de recursos vinculados")
    void pendentesSoDeRecursosVinculados() throws Exception {
        servico.vincularResponsavel(administrador, "resp", "SALA-A");
        servico.registrarExistente(new Reserva("RP1", new Usuario("alice", Set.of(Perfil.SOLICITANTE)),
                armazenamento.buscarRecurso("SALA-A").orElseThrow(),
                LocalDateTime.parse("2026-11-24T10:00"), LocalDateTime.parse("2026-11-24T11:00"),
                EstadoReserva.SOLICITADA));
        servico.registrarExistente(new Reserva("RP2", new Usuario("alice", Set.of(Perfil.SOLICITANTE)),
                armazenamento.buscarRecurso("SALA-B").orElseThrow(),
                LocalDateTime.parse("2026-11-24T10:00"), LocalDateTime.parse("2026-11-24T11:00"),
                EstadoReserva.SOLICITADA));
        String corpo = mvc.perform(get("/reservas/pendentes").header(HttpHeaders.AUTHORIZATION, bearer("resp", Perfil.RESPONSAVEL)))
                .andReturn().getResponse().getContentAsString();
        assertTrue(corpo.contains("RP1"));
        assertFalse(corpo.contains("RP2"));
    }
}
