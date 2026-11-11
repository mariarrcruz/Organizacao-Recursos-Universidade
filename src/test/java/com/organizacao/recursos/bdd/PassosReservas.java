package com.organizacao.recursos.bdd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.organizacao.recursos.dominio.*;
import com.organizacao.recursos.servico.ServicoReservas;
import io.cucumber.java.pt.Dado;
import io.cucumber.java.pt.E;
import io.cucumber.java.pt.Então;
import io.cucumber.java.pt.Quando;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Passos das features de aprovação, bloqueio e transições de reserva.
 *
 * <p>Chama {@link ServicoReservas} diretamente, com um {@link Usuario} por perfil (sem HTTP, pois a
 * camada web ainda não existe — ver lacuna 5 em docs/testes/bdd-aprovacao-bloqueio.md). Cada cenário
 * recebe uma instância nova, portanto estado limpo.
 *
 * <p>Padrões do harness, que não são regra de negócio:
 * <ul>
 *   <li>Recurso citado sem configuração é criado restrito e DISPONIVEL (os cenários de SOLICITADA dependem disso).</li>
 *   <li>Reserva citada sem período usa {@code 2026-11-20T10:00} a {@code 11:00}.</li>
 *   <li>Relógio começa em {@code 2026-11-20T10:30}; o ator autorizado ajusta o relógio só para transições
 *       com horário (ver {@link #ajustarRelogioPara(EstadoReserva, Reserva)}).</li>
 *   <li>Transições que exigem justificativa recebem {@value #JUSTIFICATIVA}.</li>
 * </ul>
 */
public class PassosReservas {

    private static final String JUSTIFICATIVA = "justificativa de teste";
    private static final LocalDateTime INICIO_PADRAO = LocalDateTime.of(2026, 11, 20, 10, 0);
    private static final LocalDateTime FIM_PADRAO = LocalDateTime.of(2026, 11, 20, 11, 0);
    private static final Usuario AUDITOR = new Usuario("auditor", EnumSet.of(Perfil.ADMINISTRADOR));
    private static final String MENSAGEM_CONFLITO = "Conflito de horário";

    private final RelogioAjustavel relogio = new RelogioAjustavel(LocalDateTime.of(2026, 11, 20, 10, 30));
    private final ServicoReservas servico = new ServicoReservas(relogio);
    private final Map<String, Usuario> usuarios = new HashMap<>();
    private final Map<String, Recurso> recursos = new HashMap<>();

    private Reserva reservaAtual;
    private Reserva reservaAnterior;
    private ErroAplicacao erro;
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
    public void existeReserva(String estado, String dono, String recursoId) {
        reservaAtual = criarEm(EstadoReserva.valueOf(estado), usuario(dono), recursoId, INICIO_PADRAO, FIM_PADRAO);
    }

    @E("que {string} tem uma reserva {word} em {string}")
    public void usuarioTemReserva(String dono, String estado, String recursoId) {
        existeReserva(estado, dono, recursoId);
    }

    @E("que {string} tem uma reserva {word} em {string} de {instante} a {instante}")
    public void usuarioTemReservaComPeriodo(String dono, String estado, String recursoId,
                                            LocalDateTime inicio, LocalDateTime fim) {
        reservaAtual = criarEm(EstadoReserva.valueOf(estado), usuario(dono), recursoId, inicio, fim);
    }

    @Dado("que a reserva está em {word}")
    public void reservaEmEstado(String estado) {
        reservaAtual = criarEm(EstadoReserva.valueOf(estado), usuario("alice"), "SALA-02", INICIO_PADRAO, FIM_PADRAO);
    }

    @E("que {string} tem manutenção de {instante} a {instante}")
    public void recursoTemManutencao(String recursoId, LocalDateTime inicio, LocalDateTime fim) {
        servico.criarManutencao(usuario("adm"), new Manutencao(recursoId, inicio, fim));
    }

    // ------------------------------------------------------------------ ações (Quando)

    @Quando("{string} solicita {string} de {instante} a {instante}")
    public void solicita(String ator, String recursoId, LocalDateTime inicio, LocalDateTime fim) {
        executar(() -> servico.solicitar(usuario(ator), recurso(recursoId), inicio, fim));
    }

    @Quando("{string} tenta iniciar o uso( da reserva)")
    public void tentaIniciarUso(String ator) {
        executar(() -> servico.iniciarUso(usuario(ator), reservaAtual));
    }

    @Quando("{string} inicia o uso( da reserva)")
    public void iniciaUso(String ator) {
        tentaIniciarUso(ator);
    }

    @Quando("{string} aprova a reserva")
    public void aprova(String ator) {
        executar(() -> servico.aprovar(usuario(ator), reservaAtual));
    }

    @Quando("{string} tenta aprovar a reserva")
    public void tentaAprovar(String ator) {
        aprova(ator);
    }

    @Quando("{string} tenta aprovar a própria reserva")
    public void tentaAprovarPropria(String ator) {
        aprova(ator);
    }

    @Quando("{string} aprova a reserva de {string}")
    public void aprovaReservaDe(String ator, String dono) {
        reservaAtual = reservaPersistidaDe(dono);
        aprova(ator);
    }

    @Quando("um ator anônimo tenta aprovar a reserva")
    public void anonimoTentaAprovar() {
        executar(() -> servico.aprovar(null, reservaAtual));
    }

    @Quando("o Responsável tenta aprovar a reserva")
    public void responsavelTentaAprovar() {
        aprova("resp");
    }

    @Quando("{string} rejeita a reserva com justificativa {string}")
    public void rejeitaComJustificativa(String ator, String justificativa) {
        executar(() -> servico.rejeitar(usuario(ator), reservaAtual, justificativa));
    }

    @Quando("{string} rejeita a reserva sem justificativa")
    public void rejeitaSemJustificativa(String ator) {
        executar(() -> servico.rejeitar(usuario(ator), reservaAtual, null));
    }

    @Quando("{string} cancela a própria reserva com justificativa {string}")
    public void cancelaPropria(String ator, String justificativa) {
        executar(() -> servico.cancelar(usuario(ator), reservaAtual, justificativa));
    }

    @Quando("{string} tenta cancelar a reserva com justificativa {string}")
    public void tentaCancelar(String ator, String justificativa) {
        cancelaPropria(ator, justificativa);
    }

    @Quando("{string} conclui a reserva")
    public void concluiReserva(String ator) {
        executar(() -> servico.concluir(usuario(ator), reservaAtual));
    }

    @Quando("{string} registra não comparecimento")
    public void registraNaoComparecimento(String ator) {
        executar(() -> servico.registrarNaoComparecimento(usuario(ator), reservaAtual));
    }

    @Quando("{string} tenta excluir a reserva")
    public void tentaExcluir(String ator) {
        executar(() -> {
            servico.excluir(usuario(ator), reservaAtual);
            return null;
        });
    }

    @Quando("{string} altera a reserva para {instante} a {instante}")
    public void alteraPeriodo(String ator, LocalDateTime inicio, LocalDateTime fim) {
        executar(() -> servico.alterar(usuario(ator), reservaAtual, inicio, fim));
    }

    @Quando("um ator tenta a transição para {word}")
    public void atorTentaTransicao(String destino) {
        EstadoReserva alvo = EstadoReserva.valueOf(destino);
        executar(() -> servico.transicionar(usuario("alice"), reservaAtual, alvo, justificativaPara(alvo)));
    }

    @Quando("o ator autorizado executa a transição para {word}")
    public void atorAutorizadoExecutaTransicao(String destino) {
        EstadoReserva alvo = EstadoReserva.valueOf(destino);
        ajustarRelogioPara(alvo, reservaAtual);
        Usuario ator = atorAutorizadoPara(alvo);
        executar(() -> servico.transicionar(ator, reservaAtual, alvo, justificativaPara(alvo)));
    }

    // ------------------------------------------------------------------ verificações (Então / E)

    @Então("a reserva fica com estado {word}")
    public void reservaFicaComEstado(String estado) {
        assertEquals(EstadoReserva.valueOf(estado), estadoPersistido());
    }

    @E("a reserva não fica com estado {word} nem {word}")
    public void reservaNaoFicaComEstado(String estado1, String estado2) {
        EstadoReserva atual = estadoPersistido();
        assertNotEquals(EstadoReserva.valueOf(estado1), atual);
        assertNotEquals(EstadoReserva.valueOf(estado2), atual);
    }

    @E("o estado da reserva continua {word}")
    public void estadoContinua(String estado) {
        assertEquals(EstadoReserva.valueOf(estado), estadoPersistido());
    }

    @E("a reserva continua {word}")
    public void reservaContinua(String estado) {
        estadoContinua(estado);
    }

    @Então("a operação é recusada com {int}")
    public void operacaoRecusada(int status) {
        assertNotNull(erro, "a operação deveria ter sido recusada");
        assertEquals(status, erro.getStatus());
    }

    @E("a mensagem informa conflito com o período bloqueado")
    public void mensagemConflito() {
        assertNotNull(erro);
        assertTrue(erro.getMessage().contains(MENSAGEM_CONFLITO), erro.getMessage());
    }

    @E("a reserva mantém o período original")
    public void mantemPeriodoOriginal() {
        Reserva persistida = reservaPersistida();
        assertEquals(reservaAnterior.getInicio(), persistida.getInicio());
        assertEquals(reservaAnterior.getFim(), persistida.getFim());
    }

    @E("nenhuma reserva é criada")
    public void nenhumaReservaCriada() {
        assertTrue(recursos.keySet().stream().allMatch(id -> servico.reservasDoRecurso(id).isEmpty()));
    }

    /** Regex: parênteses do texto são literais aqui, não texto opcional do Cucumber Expression. */
    @Então("^o resultado é aceita \\(estado (\\w+)\\)$")
    public void resultadoAceitaComEstado(String estado) {
        assertNull(erro, "a operação não deveria ter sido recusada");
        assertEquals(EstadoReserva.valueOf(estado), estadoPersistido());
    }

    @Então("^o resultado é recusada com (\\d+)$")
    public void resultadoRecusado(String status) {
        operacaoRecusada(Integer.parseInt(status));
    }

    @Então("existe um evento de auditoria com ator {string}, estado anterior {word} e estado novo {word}")
    public void existeEvento(String ator, String anterior, String novo) {
        assertTrue(eventos().stream().anyMatch(e -> e.atorId().equals(ator)
                && e.estadoAnterior() == EstadoReserva.valueOf(anterior)
                && e.estadoNovo() == EstadoReserva.valueOf(novo)));
    }

    @Então("existe exatamente um novo evento de auditoria com estado anterior {word} e estado novo {word}")
    public void existeExatamenteUmNovoEvento(String anterior, String novo) {
        List<EventoAuditoria> eventos = eventos();
        assertEquals(1, eventos.size() - auditoriaAntes);
        EventoAuditoria ultimo = eventos.get(eventos.size() - 1);
        assertEquals(EstadoReserva.valueOf(anterior), ultimo.estadoAnterior());
        assertEquals(EstadoReserva.valueOf(novo), ultimo.estadoNovo());
    }

    @E("nenhum evento de auditoria novo é registrado")
    public void nenhumEventoNovo() {
        assertEquals(0, eventos().size() - auditoriaAntes);
    }

    @Então("existem quatro eventos de auditoria: SOLICITAR, e uma transição para cada estado seguinte")
    public void quatroEventosDeAuditoria() {
        List<EventoAuditoria> eventos = eventos();
        assertEquals(4, eventos.size());
        assertEquals("SOLICITAR", eventos.get(0).acao());
        assertEquals(List.of(EstadoReserva.SOLICITADA, EstadoReserva.APROVADA, EstadoReserva.EM_USO,
                        EstadoReserva.CONCLUIDA),
                eventos.stream().map(EventoAuditoria::estadoNovo).toList());
    }

    // ------------------------------------------------------------------ internos

    /** Executa a ação, guarda o erro (se houver) e o resultado. Registra o estado de partida para as verificações. */
    private void executar(Supplier<Object> acao) {
        reservaAnterior = reservaAtual;
        auditoriaAntes = reservaAtual == null ? 0 : eventos().size();
        erro = null;
        try {
            Object resultado = acao.get();
            if (resultado instanceof Reserva nova) {
                reservaAtual = nova;
            }
        } catch (ErroAplicacao e) {
            erro = e;
        }
    }

    private Reserva criarEm(EstadoReserva alvo, Usuario dono, String recursoId, LocalDateTime inicio, LocalDateTime fim) {
        Recurso recurso = recurso(recursoId);
        if (!recurso.restrito()) {
            throw new IllegalStateException("Reserva SOLICITADA exige recurso restrito: " + recursoId);
        }
        Reserva criada = servico.solicitar(dono, recurso, inicio, fim);
        Usuario resp = usuario("resp");
        return switch (alvo) {
            case SOLICITADA -> criada;
            case APROVADA -> servico.aprovar(resp, criada);
            case REJEITADA -> servico.rejeitar(resp, criada, JUSTIFICATIVA);
            case CANCELADA -> servico.cancelar(dono, criada, JUSTIFICATIVA);
            case EM_USO -> {
                relogio.ajustarPara(inicio);
                yield servico.iniciarUso(dono, servico.aprovar(resp, criada));
            }
            case CONCLUIDA -> {
                relogio.ajustarPara(inicio);
                yield servico.concluir(dono, servico.iniciarUso(dono, servico.aprovar(resp, criada)));
            }
            case NAO_COMPARECEU -> {
                relogio.ajustarPara(inicio);
                yield servico.registrarNaoComparecimento(resp, servico.aprovar(resp, criada));
            }
            default -> throw new IllegalArgumentException("Estado sem preparo: " + alvo);
        };
    }

    /** Relógio para a transição: cancelamento antes do início, uso e não comparecimento a partir do início. */
    private void ajustarRelogioPara(EstadoReserva destino, Reserva reserva) {
        if (destino == EstadoReserva.CANCELADA) {
            relogio.ajustarPara(reserva.getInicio().minusMinutes(30));
        } else if (destino == EstadoReserva.EM_USO || destino == EstadoReserva.NAO_COMPARECEU) {
            relogio.ajustarPara(reserva.getInicio());
        }
    }

    private Usuario atorAutorizadoPara(EstadoReserva destino) {
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

    private Reserva reservaPersistida() {
        return servico.reservasDoRecurso(reservaAtual.getRecurso().id()).stream()
                .filter(r -> r.getId().equals(reservaAtual.getId()))
                .findFirst()
                .orElseThrow();
    }

    private EstadoReserva estadoPersistido() {
        return reservaPersistida().getEstado();
    }

    private Reserva reservaPersistidaDe(String dono) {
        return recursos.keySet().stream()
                .flatMap(id -> servico.reservasDoRecurso(id).stream())
                .filter(r -> r.getSolicitante().id().equals(dono))
                .reduce((primeira, ultima) -> ultima)
                .orElseThrow();
    }

    private List<EventoAuditoria> eventos() {
        return servico.eventosDe(AUDITOR, reservaAtual.getId());
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
