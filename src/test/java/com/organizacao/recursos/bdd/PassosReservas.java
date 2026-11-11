package com.organizacao.recursos.bdd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.organizacao.recursos.api.ManutencaoController;
import com.organizacao.recursos.api.ReservasController;
import com.organizacao.recursos.api.TratadorErros;
import com.organizacao.recursos.dominio.EstadoReserva;
import com.organizacao.recursos.dominio.Perfil;
import com.organizacao.recursos.dominio.Recurso;
import com.organizacao.recursos.dominio.StatusRecurso;
import com.organizacao.recursos.dominio.TipoRecurso;
import com.organizacao.recursos.dominio.Usuario;
import com.organizacao.recursos.servico.ServicoAutenticacao;
import com.organizacao.recursos.servico.ServicoReservas;
import io.cucumber.java.pt.Dado;
import io.cucumber.java.pt.E;
import io.cucumber.java.pt.Então;
import io.cucumber.java.pt.Quando;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Passos das features de aprovação, bloqueio e transições de reserva.
 *
 * <p>Toda ação e toda verificação passa pela API HTTP (MockMvc sobre os controladores reais), com um
 * token JWT emitido pelo {@link ServicoAutenticacao} para cada perfil. Só o relógio e o cadastro de
 * recursos são preparados diretamente, porque não há endpoint de relógio nem de cadastro nesta etapa.
 *
 * <p>Padrões do harness, que não são regra de negócio:
 * <ul>
 *   <li>Recurso citado sem configuração é criado restrito e DISPONIVEL (os cenários de SOLICITADA dependem disso).</li>
 *   <li>Reserva citada sem período usa {@code 2026-11-20T10:00} a {@code 11:00}.</li>
 *   <li>Relógio começa em {@code 2026-11-20T10:30}; o ator autorizado ajusta o relógio só para transições
 *       com horário (ver {@link #ajustarRelogioPara(EstadoReserva)}).</li>
 *   <li>Transições que exigem justificativa recebem {@value #JUSTIFICATIVA}.</li>
 * </ul>
 */
public class PassosReservas {

    private static final ServicoAutenticacao AUTENTICACAO = new ServicoAutenticacao();
    private static final String JUSTIFICATIVA = "justificativa de teste";
    private static final LocalDateTime INICIO_PADRAO = LocalDateTime.of(2026, 11, 20, 10, 0);
    private static final LocalDateTime FIM_PADRAO = LocalDateTime.of(2026, 11, 20, 11, 0);
    private static final Usuario AUDITOR = new Usuario("auditor", EnumSet.of(Perfil.ADMINISTRADOR));
    private static final String MENSAGEM_CONFLITO = "Conflito de horário";

    private final RelogioAjustavel relogio = new RelogioAjustavel(LocalDateTime.of(2026, 11, 20, 10, 30));
    private final ServicoReservas servico = new ServicoReservas(relogio);
    private final Map<String, Usuario> usuarios = new HashMap<>();
    private final Map<String, Recurso> recursos = new HashMap<>();
    private final ObjectMapper json = new ObjectMapper();
    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new ReservasController(servico, AUTENTICACAO, recursos::get),
                    new ManutencaoController(servico, AUTENTICACAO))
            .setControllerAdvice(new TratadorErros())
            .build();

    private String reservaId;
    private LocalDateTime inicioOriginal;
    private LocalDateTime fimOriginal;
    private MvcResult ultimaAcao;
    private int auditoriaAntes;

    // ------------------------------------------------------------------ contexto (Dado / E que)

    @Dado("que o recurso {string} está com status {word}")
    public void recursoComStatus(String recursoId, String status) {
        Recurso atual = recurso(recursoId);
        recursos.put(recursoId, new Recurso(recursoId, atual.tipo(), atual.nome(), atual.localizacao(),
                atual.capacidade(), atual.restrito(), StatusRecurso.valueOf(status), atual.competencias()));
    }

    @E("que {string} está marcado como restrito")
    public void recursoRestrito(String recursoId) {
        Recurso atual = recurso(recursoId);
        recursos.put(recursoId, new Recurso(recursoId, atual.tipo(), atual.nome(), atual.localizacao(),
                atual.capacidade(), true, atual.status(), atual.competencias()));
    }

    @E("que {string} tem o perfil {word}")
    public void usuarioComPerfil(String id, String perfil) {
        usuarios.put(id, new Usuario(id, EnumSet.of(Perfil.valueOf(perfil))));
    }

    @E("que {string} tem apenas o perfil {word}")
    public void usuarioComApenasPerfil(String id, String perfil) {
        usuarioComPerfil(id, perfil);
    }

    @E("que {string} tem os perfis {word} e {word}")
    public void usuarioComDoisPerfis(String id, String perfil1, String perfil2) {
        usuarios.put(id, new Usuario(id, EnumSet.of(Perfil.valueOf(perfil1), Perfil.valueOf(perfil2))));
    }

    @Dado("que existe uma reserva {word} de {string} em {string}")
    public void existeReserva(String estado, String dono, String recursoId) throws Exception {
        criarEm(EstadoReserva.valueOf(estado), usuario(dono), recursoId, INICIO_PADRAO, FIM_PADRAO);
    }

    @E("que {string} tem uma reserva {word} em {string}")
    public void usuarioTemReserva(String dono, String estado, String recursoId) throws Exception {
        existeReserva(estado, dono, recursoId);
    }

    @E("que {string} tem uma reserva {word} em {string} de {instante} a {instante}")
    public void usuarioTemReservaComPeriodo(String dono, String estado, String recursoId,
                                            LocalDateTime inicio, LocalDateTime fim) throws Exception {
        criarEm(EstadoReserva.valueOf(estado), usuario(dono), recursoId, inicio, fim);
    }

    @Dado("que a reserva está em {word}")
    public void reservaEmEstado(String estado) throws Exception {
        criarEm(EstadoReserva.valueOf(estado), usuario("alice"), "SALA-02", INICIO_PADRAO, FIM_PADRAO);
    }

    @E("que {string} tem manutenção de {instante} a {instante}")
    public void recursoTemManutencao(String recursoId, LocalDateTime inicio, LocalDateTime fim) throws Exception {
        acao("POST", "/manutencoes", usuario("adm"), corpo("recursoId", recursoId,
                "inicio", inicio.toString(), "fim", fim.toString()));
        assertEquals(201, ultimaAcao.getResponse().getStatus(), "preparo da manutenção");
    }

    // ------------------------------------------------------------------ ações (Quando)

    @Quando("{string} solicita {string} de {instante} a {instante}")
    public void solicita(String ator, String recursoId, LocalDateTime inicio, LocalDateTime fim) throws Exception {
        recurso(recursoId);
        acao("POST", "/reservas", usuario(ator), corpo("recursoId", recursoId,
                "inicio", inicio.toString(), "fim", fim.toString()));
        if (ultimaAcao.getResponse().getStatus() == 201) {
            reservaId = corpoResposta().get("id").asText();
            inicioOriginal = inicio;
            fimOriginal = fim;
        }
    }

    @Quando("{string} tenta iniciar o uso( da reserva)")
    public void tentaIniciarUso(String ator) throws Exception {
        acaoEm("/inicio-uso", usuario(ator), null);
    }

    @Quando("{string} inicia o uso( da reserva)")
    public void iniciaUso(String ator) throws Exception {
        tentaIniciarUso(ator);
    }

    @Quando("{string} aprova a reserva")
    public void aprova(String ator) throws Exception {
        acaoEm("/aprovacao", usuario(ator), null);
    }

    @Quando("{string} tenta aprovar a reserva")
    public void tentaAprovar(String ator) throws Exception {
        aprova(ator);
    }

    @Quando("{string} tenta aprovar a própria reserva")
    public void tentaAprovarPropria(String ator) throws Exception {
        aprova(ator);
    }

    @Quando("{string} aprova a reserva de {string}")
    public void aprovaReservaDe(String ator, String dono) throws Exception {
        reservaId = reservaPersistidaDe(dono);
        aprova(ator);
    }

    @Quando("um ator anônimo tenta aprovar a reserva")
    public void anonimoTentaAprovar() throws Exception {
        acaoEm("/aprovacao", null, null);
    }

    @Quando("o Responsável tenta aprovar a reserva")
    public void responsavelTentaAprovar() throws Exception {
        aprova("resp");
    }

    @Quando("{string} rejeita a reserva com justificativa {string}")
    public void rejeitaComJustificativa(String ator, String justificativa) throws Exception {
        acaoEm("/rejeicao", usuario(ator), corpo("justificativa", justificativa));
    }

    @Quando("{string} rejeita a reserva sem justificativa")
    public void rejeitaSemJustificativa(String ator) throws Exception {
        acaoEm("/rejeicao", usuario(ator), null);
    }

    @Quando("{string} cancela a própria reserva com justificativa {string}")
    public void cancelaPropria(String ator, String justificativa) throws Exception {
        acaoEm("/cancelamento", usuario(ator), corpo("justificativa", justificativa));
    }

    @Quando("{string} tenta cancelar a reserva com justificativa {string}")
    public void tentaCancelar(String ator, String justificativa) throws Exception {
        cancelaPropria(ator, justificativa);
    }

    @Quando("{string} conclui a reserva")
    public void concluiReserva(String ator) throws Exception {
        acaoEm("/conclusao", usuario(ator), null);
    }

    @Quando("{string} registra não comparecimento")
    public void registraNaoComparecimento(String ator) throws Exception {
        acaoEm("/nao-comparecimento", usuario(ator), null);
    }

    @Quando("{string} tenta excluir a reserva")
    public void tentaExcluir(String ator) throws Exception {
        acao("DELETE", "/reservas/" + reservaId, usuario(ator), null);
    }

    @Quando("{string} altera a reserva para {instante} a {instante}")
    public void alteraPeriodo(String ator, LocalDateTime inicio, LocalDateTime fim) throws Exception {
        acao("PUT", "/reservas/" + reservaId, usuario(ator),
                corpo("inicio", inicio.toString(), "fim", fim.toString()));
    }

    @Quando("um ator tenta a transição para {word}")
    public void atorTentaTransicao(String destino) throws Exception {
        EstadoReserva alvo = EstadoReserva.valueOf(destino);
        acaoEm("/transicoes", usuario("alice"), corpo("destino", alvo.name(),
                "justificativa", justificativaPara(alvo)));
    }

    @Quando("o ator autorizado executa a transição para {word}")
    public void atorAutorizadoExecutaTransicao(String destino) throws Exception {
        EstadoReserva alvo = EstadoReserva.valueOf(destino);
        ajustarRelogioPara(alvo);
        acaoEm("/transicoes", usuarioAutorizadoPara(alvo), corpo("destino", alvo.name(),
                "justificativa", justificativaPara(alvo)));
    }

    // ------------------------------------------------------------------ verificações (Então / E)

    @Então("a reserva fica com estado {word}")
    public void reservaFicaComEstado(String estado) throws Exception {
        assertEquals(EstadoReserva.valueOf(estado), estadoPersistido());
    }

    @E("a reserva não fica com estado {word} nem {word}")
    public void reservaNaoFicaComEstado(String estado1, String estado2) throws Exception {
        EstadoReserva atual = estadoPersistido();
        assertNotEquals(EstadoReserva.valueOf(estado1), atual);
        assertNotEquals(EstadoReserva.valueOf(estado2), atual);
    }

    @E("o estado da reserva continua {word}")
    public void estadoContinua(String estado) throws Exception {
        assertEquals(EstadoReserva.valueOf(estado), estadoPersistido());
    }

    @E("a reserva continua {word}")
    public void reservaContinua(String estado) throws Exception {
        estadoContinua(estado);
    }

    @Então("a operação é recusada com {int}")
    public void operacaoRecusada(int status) throws IOException {
        assertEquals(status, ultimaAcao.getResponse().getStatus(), ultimaAcao.getResponse().getContentAsString());
    }

    @E("a mensagem informa conflito com o período bloqueado")
    public void mensagemConflito() throws IOException {
        String mensagem = corpoResposta().get("erro").asText();
        assertTrue(mensagem.contains(MENSAGEM_CONFLITO), mensagem);
    }

    @E("a reserva mantém o período original")
    public void mantemPeriodoOriginal() throws Exception {
        JsonNode reserva = reservaPersistida();
        assertEquals(inicioOriginal.toString(), reserva.get("inicio").asText());
        assertEquals(fimOriginal.toString(), reserva.get("fim").asText());
    }

    @E("nenhuma reserva é criada")
    public void nenhumaReservaCriada() throws Exception {
        for (String id : recursos.keySet()) {
            consulta("/reservas?recursoId=" + id, AUDITOR);
            assertEquals(0, json.readTree(ultimaConsulta.getResponse().getContentAsString()).size(), id);
        }
    }

    /** Regex: parênteses do texto são literais aqui, não texto opcional do Cucumber Expression. */
    @Então("^o resultado é aceita \\(estado (\\w+)\\)$")
    public void resultadoAceitaComEstado(String estado) throws Exception {
        assertTrue(ultimaAcao.getResponse().getStatus() / 100 == 2, ultimaAcao.getResponse().getContentAsString());
        assertEquals(EstadoReserva.valueOf(estado), estadoPersistido());
    }

    @Então("^o resultado é recusada com (\\d+)$")
    public void resultadoRecusado(String status) throws IOException {
        operacaoRecusada(Integer.parseInt(status));
    }

    @Então("existe um evento de auditoria com ator {string}, estado anterior {word} e estado novo {word}")
    public void existeEvento(String ator, String anterior, String novo) throws Exception {
        assertTrue(eventos().stream().anyMatch(e -> ator.equals(texto(e, "atorId"))
                && anterior.equals(texto(e, "estadoAnterior"))
                && novo.equals(texto(e, "estadoNovo"))));
    }

    @Então("existe exatamente um novo evento de auditoria com estado anterior {word} e estado novo {word}")
    public void existeExatamenteUmNovoEvento(String anterior, String novo) throws Exception {
        List<JsonNode> eventos = eventos();
        assertEquals(1, eventos.size() - auditoriaAntes);
        JsonNode ultimo = eventos.get(eventos.size() - 1);
        assertEquals(anterior, texto(ultimo, "estadoAnterior"));
        assertEquals(novo, texto(ultimo, "estadoNovo"));
    }

    @E("nenhum evento de auditoria novo é registrado")
    public void nenhumEventoNovo() throws Exception {
        assertEquals(0, eventos().size() - auditoriaAntes);
    }

    @Então("existem quatro eventos de auditoria: SOLICITAR, e uma transição para cada estado seguinte")
    public void quatroEventosDeAuditoria() throws Exception {
        List<JsonNode> eventos = eventos();
        assertEquals(4, eventos.size());
        assertEquals("SOLICITAR", texto(eventos.get(0), "acao"));
        List<String> estadosNovos = new ArrayList<>();
        for (JsonNode e : eventos) {
            estadosNovos.add(texto(e, "estadoNovo"));
        }
        assertEquals(List.of("SOLICITADA", "APROVADA", "EM_USO", "CONCLUIDA"), estadosNovos);
    }

    // ------------------------------------------------------------------ internos: HTTP

    /** Envia uma ação (escrita) e guarda a resposta como resposta da última ação. */
    private void acao(String metodo, String caminho, Usuario ator, Map<String, Object> corpo) throws Exception {
        auditoriaAntes = reservaId == null ? 0 : eventos().size();
        ultimaAcao = enviar(metodo, caminho, ator, corpo);
    }

    private void acaoEm(String sufixo, Usuario ator, Map<String, Object> corpo) throws Exception {
        acao("POST", "/reservas/" + reservaId + sufixo, ator, corpo);
    }

    private MvcResult ultimaConsulta;

    /** Consulta (leitura) que não altera a resposta da última ação. */
    private void consulta(String caminho, Usuario ator) throws Exception {
        ultimaConsulta = enviar("GET", caminho, ator, null);
        assertEquals(200, ultimaConsulta.getResponse().getStatus(), ultimaConsulta.getResponse().getContentAsString());
    }

    private MvcResult enviar(String metodo, String caminho, Usuario ator, Map<String, Object> corpo) throws Exception {
        MockHttpServletRequestBuilder req = switch (metodo) {
            case "GET" -> get(caminho);
            case "POST" -> post(caminho);
            case "PUT" -> put(caminho);
            case "DELETE" -> delete(caminho);
            default -> throw new IllegalArgumentException(metodo);
        };
        if (ator != null) {
            req.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDe(ator));
        }
        if (corpo != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(corpo));
        }
        return mvc.perform(req).andReturn();
    }

    private String tokenDe(Usuario ator) {
        List<String> perfis = ator.perfis().stream().map(Enum::name).toList();
        return AUTENTICACAO.gerarToken(ator.id(), perfis, Instant.now().plusSeconds(3600));
    }

    private JsonNode corpoResposta() throws IOException {
        return json.readTree(ultimaAcao.getResponse().getContentAsString());
    }

    private JsonNode reservaPersistida() throws Exception {
        consulta("/reservas/" + reservaId, AUDITOR);
        return json.readTree(ultimaConsulta.getResponse().getContentAsString());
    }

    private EstadoReserva estadoPersistido() throws Exception {
        return EstadoReserva.valueOf(reservaPersistida().get("estado").asText());
    }

    private List<JsonNode> eventos() throws Exception {
        consulta("/reservas/" + reservaId + "/auditoria", AUDITOR);
        List<JsonNode> lista = new ArrayList<>();
        json.readTree(ultimaConsulta.getResponse().getContentAsString()).forEach(lista::add);
        return lista;
    }

    private String reservaPersistidaDe(String dono) throws Exception {
        for (String recursoId : recursos.keySet()) {
            consulta("/reservas?recursoId=" + recursoId, AUDITOR);
            for (JsonNode r : json.readTree(ultimaConsulta.getResponse().getContentAsString())) {
                if (dono.equals(r.get("solicitanteId").asText())) {
                    return r.get("id").asText();
                }
            }
        }
        throw new IllegalStateException("Sem reserva de " + dono);
    }

    /** Cria a reserva pela API e leva-a ao estado pedido, usando as transições da API. */
    private void criarEm(EstadoReserva alvo, Usuario dono, String recursoId, LocalDateTime inicio, LocalDateTime fim)
            throws Exception {
        recurso(recursoId);
        acao("POST", "/reservas", dono, corpo("recursoId", recursoId,
                "inicio", inicio.toString(), "fim", fim.toString()));
        assertEquals(201, ultimaAcao.getResponse().getStatus(), "preparo da reserva");
        reservaId = corpoResposta().get("id").asText();
        inicioOriginal = inicio;
        fimOriginal = fim;
        Usuario resp = usuario("resp");
        switch (alvo) {
            case SOLICITADA -> { }
            case APROVADA -> preparar("/aprovacao", resp, null);
            case REJEITADA -> preparar("/rejeicao", resp, corpo("justificativa", JUSTIFICATIVA));
            case CANCELADA -> preparar("/cancelamento", dono, corpo("justificativa", JUSTIFICATIVA));
            case EM_USO -> {
                preparar("/aprovacao", resp, null);
                relogio.ajustarPara(inicio);
                preparar("/inicio-uso", dono, null);
            }
            case CONCLUIDA -> {
                preparar("/aprovacao", resp, null);
                relogio.ajustarPara(inicio);
                preparar("/inicio-uso", dono, null);
                preparar("/conclusao", dono, null);
            }
            case NAO_COMPARECEU -> {
                preparar("/aprovacao", resp, null);
                relogio.ajustarPara(inicio);
                preparar("/nao-comparecimento", resp, null);
            }
            default -> throw new IllegalArgumentException("Estado sem preparo: " + alvo);
        }
    }

    private void preparar(String sufixo, Usuario ator, Map<String, Object> corpo) throws Exception {
        acaoEm(sufixo, ator, corpo);
        assertEquals(200, ultimaAcao.getResponse().getStatus(),
                "preparo " + sufixo + ": " + ultimaAcao.getResponse().getContentAsString());
    }

    // ------------------------------------------------------------------ internos: domínio do cenário

    /** Relógio para a transição: cancelamento antes do início, uso e não comparecimento a partir do início. */
    private void ajustarRelogioPara(EstadoReserva destino) throws Exception {
        LocalDateTime inicio = inicioOriginal;
        if (destino == EstadoReserva.CANCELADA) {
            relogio.ajustarPara(inicio.minusMinutes(30));
        } else if (destino == EstadoReserva.EM_USO || destino == EstadoReserva.NAO_COMPARECEU) {
            relogio.ajustarPara(inicio);
        }
    }

    private Usuario usuarioAutorizadoPara(EstadoReserva destino) {
        return switch (destino) {
            case APROVADA, REJEITADA, NAO_COMPARECEU -> usuario("resp");
            default -> usuario("alice");
        };
    }

    private static String justificativaPara(EstadoReserva destino) {
        return destino == EstadoReserva.REJEITADA || destino == EstadoReserva.CANCELADA ? JUSTIFICATIVA : null;
    }

    /** Usuário citado sem "tem o perfil": o id identifica o papel usado nas features (resp, adm, demais solicitantes). */
    private Usuario usuario(String id) {
        return usuarios.computeIfAbsent(id, PassosReservas::perfilPadrao);
    }

    private static Usuario perfilPadrao(String id) {
        return switch (id) {
            case "resp" -> new Usuario(id, EnumSet.of(Perfil.RESPONSAVEL));
            case "adm" -> new Usuario(id, EnumSet.of(Perfil.ADMINISTRADOR));
            default -> new Usuario(id, EnumSet.of(Perfil.SOLICITANTE));
        };
    }

    private Recurso recurso(String id) {
        return recursos.computeIfAbsent(id, r -> new Recurso(r, TipoRecurso.SALA, "Recurso " + r, "BLOCO_A", 30,
                true, StatusRecurso.DISPONIVEL, Set.of()));
    }

    /** Monta um corpo JSON a partir de pares chave/valor; valores nulos são enviados como null. */
    private static Map<String, Object> corpo(Object... pares) {
        Map<String, Object> mapa = new LinkedHashMap<>();
        for (int i = 0; i < pares.length; i += 2) {
            mapa.put((String) pares[i], pares[i + 1]);
        }
        return mapa;
    }

    private static String texto(JsonNode no, String campo) {
        JsonNode valor = no.get(campo);
        return valor == null || valor.isNull() ? null : valor.asText();
    }

    /** Relógio controlado pelo cenário. Usa UTC para que a conversão de instante seja determinística. */
    static final class RelogioAjustavel extends Clock {
        private Instant instante;

        RelogioAjustavel(LocalDateTime ponto) {
            this.instante = ponto.toInstant(ZoneOffset.UTC);
        }

        void ajustarPara(LocalDateTime ponto) {
            this.instante = ponto.toInstant(ZoneOffset.UTC);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instante;
        }
    }
}
