package com.organizacao.recursos.servico;

import static com.organizacao.recursos.servico.Autorizacao.exigirAlgumPerfil;
import static com.organizacao.recursos.servico.Autorizacao.exigirAutenticado;
import static com.organizacao.recursos.servico.Autorizacao.exigirPerfil;
import static com.organizacao.recursos.servico.Autorizacao.tem;

import com.organizacao.recursos.dominio.*;
import com.organizacao.recursos.persistencia.Armazenamento;
import com.organizacao.recursos.persistencia.ArmazenamentoMemoria;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Regras de reserva. Não usa monitor Java em volta de I/O: a atomicidade entre verificar disponibilidade e
 * gravar é garantida pelo armazenamento (PostgreSQL: transação, lock consultivo por recurso e restrição EXCLUDE,
 * DEF-001, DEF-008). O armazenamento em memória só deve ser usado por um único thread (testes e demonstração).
 */

public class ServicoReservas {

    /** Estados que ocupam o recurso e bloqueiam novas sobreposições. */
    private static final Set<EstadoReserva> ATIVAS =
            EnumSet.of(EstadoReserva.SOLICITADA, EstadoReserva.APROVADA, EstadoReserva.EM_USO);

    private final Armazenamento armazenamento;
    private final Clock relogio;

    /** Duração mínima de uma reserva (AMB-04, decisão provisória: 1 minuto). */
    static final Duration DURACAO_MINIMA = Duration.ofMinutes(1);

    public ServicoReservas() {
        this(Clock.systemDefaultZone());
    }

    public ServicoReservas(Clock relogio) {
        this(relogio, new ArmazenamentoMemoria());
    }

    public ServicoReservas(Clock relogio, Armazenamento armazenamento) {
        this.relogio = relogio;
        this.armazenamento = armazenamento;
    }

    /** Destino das notificações de mudança de estado (RF-11). Por padrão, descarta. */
    private volatile Notificador notificador = Notificador.NULO;

    public void definirNotificador(Notificador notificador) {
        this.notificador = notificador == null ? Notificador.NULO : notificador;
    }

    // ---------------------------------------------------------------- criação e alteração

    public Reserva solicitar(Usuario ator, Recurso recurso, LocalDateTime inicio, LocalDateTime fim) {
        exigirAutenticado(ator);
        // Quem cria reserva é Solicitante ou Responsável (AMB-03). Administrador não cria reservas (D5).
        if (!tem(ator, Perfil.SOLICITANTE) && !tem(ator, Perfil.RESPONSAVEL)) {
            throw new ErroAplicacao(403, "Somente Solicitante ou Responsável criam reservas");
        }
        validarPeriodo(inicio, fim);
        if (recurso == null) {
            throw new ErroAplicacao(400, "Recurso obrigatório");
        }
        if (recurso.status() != StatusRecurso.DISPONIVEL) {
            throw new ErroAplicacao(409, "Recurso indisponível para reserva");
        }
        verificarDisponibilidade(recurso.id(), inicio, fim, null, ator.id());
        EstadoReserva inicial = recurso.restrito() ? EstadoReserva.SOLICITADA : EstadoReserva.APROVADA;
        Reserva nova = new Reserva(UUID.randomUUID().toString(), ator, recurso, inicio, fim, inicial);
        armazenamento.inserirReserva(nova, evento(nova.getId(), ator, "SOLICITAR", null, inicial, null));
        notificador.notificar(ator.id(), "Reserva " + nova.getId() + " (" + recurso.id() + ") registrada: " + inicial);
        return nova;
    }

    public Reserva alterar(Usuario ator, Reserva reserva, LocalDateTime inicio, LocalDateTime fim) {
        exigirAutenticado(ator);
        Reserva atual = buscarObrigatoria(reserva.getId());
        if (!ehDono(ator, atual)) {
            throw new ErroAplicacao(403, "Somente o solicitante pode alterar a reserva");
        }
        if (atual.getEstado() != EstadoReserva.SOLICITADA) {
            throw new ErroAplicacao(409, "Reserva em " + atual.getEstado() + " não pode ser alterada");
        }
        if (atual.getRecurso().status() != StatusRecurso.DISPONIVEL) {
            throw new ErroAplicacao(409, "Recurso indisponível para reserva");
        }
        validarPeriodo(inicio, fim);
        verificarDisponibilidade(atual.getRecurso().id(), inicio, fim, atual.getId(), ator.id());
        Reserva nova = new Reserva(atual.getId(), atual.getSolicitante(), atual.getRecurso(),
                inicio, fim, EstadoReserva.SOLICITADA);
        armazenamento.atualizarReserva(nova,
                evento(nova.getId(), ator, "ALTERAR", EstadoReserva.SOLICITADA, EstadoReserva.SOLICITADA, null), true);
        return nova;
    }

    public void excluir(Usuario ator, Reserva reserva) {
        exigirAutenticado(ator);
        Reserva atual = buscarObrigatoria(reserva.getId());
        if (!ehDono(ator, atual)) {
            throw new ErroAplicacao(403, "Somente o solicitante pode excluir a reserva");
        }
        if (atual.getEstado() == EstadoReserva.EM_USO) {
            throw new ErroAplicacao(409, "Reserva em uso não pode ser excluída (RN-08)");
        }
        if (atual.getEstado() != EstadoReserva.SOLICITADA) {
            // Decisão pendente de AMB-01: exclusão apenas em SOLICITADA; demais estados ficam bloqueados.
            throw new ErroAplicacao(409, "Exclusão permitida apenas em SOLICITADA");
        }
        armazenamento.removerReserva(atual.getId(), evento(atual.getId(), ator, "EXCLUIR", atual.getEstado(), null, null));
    }

    // ---------------------------------------------------------------- transições (RN-05, RN-06)

    public Reserva aprovar(Usuario ator, Reserva reserva) {
        return transicionar(ator, reserva, EstadoReserva.APROVADA, null);
    }

    public Reserva rejeitar(Usuario ator, Reserva reserva, String justificativa) {
        return transicionar(ator, reserva, EstadoReserva.REJEITADA, justificativa);
    }

    public Reserva iniciarUso(Usuario ator, Reserva reserva) {
        return transicionar(ator, reserva, EstadoReserva.EM_USO, null);
    }

    public Reserva concluir(Usuario ator, Reserva reserva) {
        return transicionar(ator, reserva, EstadoReserva.CONCLUIDA, null);
    }

    public Reserva cancelar(Usuario ator, Reserva reserva, String justificativa) {
        return transicionar(ator, reserva, EstadoReserva.CANCELADA, justificativa);
    }

    public Reserva registrarNaoComparecimento(Usuario ator, Reserva reserva) {
        return transicionar(ator, reserva, EstadoReserva.NAO_COMPARECEU, null);
    }

    public Reserva transicionar(Usuario ator, Reserva reserva, EstadoReserva destino, String justificativa) {
        exigirAutenticado(ator);
        Reserva atual = buscarObrigatoria(reserva.getId());
        if (!transicaoPermitida(atual.getEstado(), destino)) {
            throw new ErroAplicacao(409, "Transição proibida: " + atual.getEstado() + " → " + destino);
        }
        exigirAutorizacaoTransicao(ator, atual, destino);
        if (destino == EstadoReserva.APROVADA) {
            verificarDisponibilidade(atual.getRecurso().id(), atual.getInicio(), atual.getFim(), atual.getId(),
                    atual.getSolicitante().id());
        }
        if (destino == EstadoReserva.EM_USO && antesDoInicio(atual)) {
            throw new ErroAplicacao(409, "Uso só pode começar a partir do horário de início");
        }
        if (destino == EstadoReserva.NAO_COMPARECEU && antesDoInicio(atual)) {
            throw new ErroAplicacao(409, "Não comparecimento só pode ser registrado após o início");
        }
        if (destino == EstadoReserva.CANCELADA && atual.getEstado() == EstadoReserva.APROVADA
                && !antesDoInicio(atual)) {
            throw new ErroAplicacao(409, "Cancelamento só é permitido antes do início");
        }
        if (destino == EstadoReserva.REJEITADA || destino == EstadoReserva.CANCELADA) {
            if (justificativa == null || justificativa.isBlank()) {
                throw new ErroAplicacao(400, "Justificativa obrigatória para " + destino);
            }
        }
        Reserva nova = new Reserva(atual.getId(), atual.getSolicitante(), atual.getRecurso(),
                atual.getInicio(), atual.getFim(), destino);
        armazenamento.atualizarReserva(nova,
                evento(nova.getId(), ator, "TRANSICIONAR", atual.getEstado(), destino, justificativa),
                destino == EstadoReserva.APROVADA);
        notificador.notificar(atual.getSolicitante().id(),
                "Reserva " + nova.getId() + " (" + nova.getRecurso().id() + "): " + atual.getEstado() + " → " + destino);
        return nova;
    }

    private static boolean transicaoPermitida(EstadoReserva de, EstadoReserva para) {
        return switch (de) {
            case SOLICITADA -> para == EstadoReserva.APROVADA
                    || para == EstadoReserva.REJEITADA
                    || para == EstadoReserva.CANCELADA;
            case APROVADA -> para == EstadoReserva.EM_USO
                    || para == EstadoReserva.CANCELADA
                    || para == EstadoReserva.NAO_COMPARECEU;
            case EM_USO -> para == EstadoReserva.CONCLUIDA;
            default -> false; // estados terminais não têm saída
        };
    }

    private void exigirAutorizacaoTransicao(Usuario ator, Reserva r, EstadoReserva destino) {
        boolean permitido = switch (destino) {
            case APROVADA -> tem(ator, Perfil.RESPONSAVEL) && !ehDono(ator, r) && vinculado(ator, r);
            case REJEITADA -> tem(ator, Perfil.RESPONSAVEL) && !ehDono(ator, r) && vinculado(ator, r);
            case NAO_COMPARECEU -> tem(ator, Perfil.RESPONSAVEL);
            case CANCELADA -> ehDono(ator, r);
            case EM_USO, CONCLUIDA -> ehDono(ator, r) || tem(ator, Perfil.RESPONSAVEL);
            default -> false;
        };
        if (!permitido) {
            throw new ErroAplicacao(403, "Ator sem permissão para a transição " + destino);
        }
    }

    // ---------------------------------------------------------------- manutenção e bloqueios (RN-04)

    public void criarManutencao(Usuario ator, Manutencao manutencao) {
        exigirPerfil(ator, Perfil.ADMINISTRADOR);
        if (manutencao.inicio() == null || manutencao.fim() == null || !manutencao.fim().isAfter(manutencao.inicio())) {
            throw new ErroAplicacao(400, "Período de manutenção inválido");
        }
        armazenamento.inserirManutencao(manutencao);
    }

    public void criarBloqueio(Usuario ator, Manutencao bloqueio) {
        criarManutencao(ator, bloqueio);
    }

    // ---------------------------------------------------------------- consultas e auditoria (RN-09)

    /** Solicitações aguardando análise nos recursos vinculados ao Responsável (D3). */
    public List<Reserva> pendentesDeAnalise(Usuario ator) {
        exigirPerfil(ator, Perfil.RESPONSAVEL);
        return armazenamento.listarReservas().stream()
                .filter(r -> r.getEstado() == EstadoReserva.SOLICITADA)
                .filter(r -> vinculado(ator, r))
                .toList();
    }

    /** Reservas em que o usuário é o solicitante. */
    public List<Reserva> minhasReservas(Usuario ator) {
        exigirAutenticado(ator);
        return armazenamento.listarReservas().stream().filter(r -> ehDono(ator, r)).toList();
    }

    /** Vincula um Responsável a um recurso (D3). Só Administrador. */
    public void vincularResponsavel(Usuario ator, String responsavelId, String recursoId) {
        exigirPerfil(ator, Perfil.ADMINISTRADOR);
        if (responsavelId == null || responsavelId.isBlank() || recursoId == null || recursoId.isBlank()) {
            throw new ErroAplicacao(400, "Responsável e recurso são obrigatórios");
        }
        armazenamento.gravarVinculo(responsavelId, recursoId);
    }

    /** Consulta uma reserva: dono, Administrador ou Responsável vinculado ao recurso (RF-16, D3). */
    public Reserva consultar(Usuario ator, String reservaId) {
        exigirAutenticado(ator);
        Reserva r = buscarObrigatoria(reservaId);
        boolean responsavelVinculado = tem(ator, Perfil.RESPONSAVEL) && vinculado(ator, r);
        if (!(ehDono(ator, r) || responsavelVinculado || tem(ator, Perfil.ADMINISTRADOR))) {
            throw new ErroAplicacao(403, "Sem permissão para consultar esta reserva");
        }
        return r;
    }

    /** Lista as reservas de um recurso; restrito a Administrador. */
    public List<Reserva> listarDoRecurso(Usuario ator, String recursoId) {
        exigirPerfil(ator, Perfil.ADMINISTRADOR);
        return reservasDoRecurso(recursoId);
    }

    /** Histórico de uma reserva: somente Responsável e Administrador, mesmo para o dono (RN-09). */
    public List<EventoAuditoria> eventosDe(Usuario ator, String reservaId) {
        exigirAlgumPerfil(ator, Perfil.RESPONSAVEL, Perfil.ADMINISTRADOR);
        buscarObrigatoria(reservaId);
        return armazenamento.listarEventos().stream().filter(e -> e.reservaId().equals(reservaId)).toList();
    }

    // ---------------------------------------------------------------- acesso interno aos demais serviços

    /** Insere uma reserva já existente no armazenamento (usado para preparar cenários de teste). */
    public void registrarExistente(Reserva reserva) {
        armazenamento.inserirReserva(reserva, null);
    }

    public void registrarManutencaoExistente(Manutencao manutencao) {
        armazenamento.inserirManutencao(manutencao);
    }

    public List<Reserva> reservasDoRecurso(String recursoId) {
        return armazenamento.listarReservas().stream().filter(r -> r.getRecurso().id().equals(recursoId)).toList();
    }

    Optional<Reserva> buscar(String id) {
        return armazenamento.buscarReserva(id);
    }

    List<Reserva> todas() {
        return armazenamento.listarReservas();
    }

    boolean temReservaAtiva(String recursoId) {
        return armazenamento.listarReservas().stream()
                .anyMatch(r -> r.getRecurso().id().equals(recursoId) && ATIVAS.contains(r.getEstado()));
    }

    boolean temConflito(String recursoId, LocalDateTime inicio, LocalDateTime fim) {
        return sobrepoeManutencao(recursoId, inicio, fim) || sobrepoeReserva(recursoId, inicio, fim, null);
    }

    /**
     * Consulta de disponibilidade: livre de reservas ativas e de manutenção no intervalo. Qualquer usuário
     * autenticado consulta; o retorno não identifica reservas de terceiros.
     */
    public boolean disponivel(Usuario ator, String recursoId, LocalDateTime inicio, LocalDateTime fim) {
        exigirAutenticado(ator);
        validarPeriodo(inicio, fim);
        return !temConflito(recursoId, inicio, fim);
    }

    /** Tentativas recusadas por sobreposição com reserva (conflitos evitados, RF-12). */
    List<ConflitoReserva> conflitosRegistrados() {
        return armazenamento.listarConflitos();
    }

    // ---------------------------------------------------------------- internos

    /**
     * Recusa com 409 se houver sobreposição com reserva ativa ou manutenção. A sobreposição com reserva
     * fica registrada como conflito evitado (RF-12); a de manutenção não entra nessa contagem.
     */
    private void verificarDisponibilidade(String recursoId, LocalDateTime inicio, LocalDateTime fim,
                                          String idIgnorado, String solicitanteId) {
        if (sobrepoeReserva(recursoId, inicio, fim, idIgnorado)) {
            armazenamento.registrarConflito(new ConflitoReserva(recursoId, solicitanteId, inicio, fim));
            throw new ErroAplicacao(409, "Conflito de horário para o recurso " + recursoId);
        }
        if (sobrepoeManutencao(recursoId, inicio, fim)) {
            throw new ErroAplicacao(409, "Conflito de horário para o recurso " + recursoId);
        }
    }

    private boolean sobrepoeManutencao(String recursoId, LocalDateTime inicio, LocalDateTime fim) {
        return armazenamento.listarManutencoes().stream()
                .filter(m -> m.recursoId().equals(recursoId))
                .anyMatch(m -> sobrepoe(m.inicio(), m.fim(), inicio, fim));
    }

    private boolean sobrepoeReserva(String recursoId, LocalDateTime inicio, LocalDateTime fim, String idIgnorado) {
        return armazenamento.listarReservas().stream()
                .filter(r -> !r.getId().equals(idIgnorado))
                .filter(r -> r.getRecurso().id().equals(recursoId))
                .filter(r -> ATIVAS.contains(r.getEstado()))
                .anyMatch(r -> sobrepoe(r.getInicio(), r.getFim(), inicio, fim));
    }

    /** Interseção de intervalos semiabertos: inicioExistente < fimNovo AND fimExistente > inicioNovo. */
    static boolean sobrepoe(LocalDateTime iniA, LocalDateTime fimA, LocalDateTime iniB, LocalDateTime fimB) {
        return iniA.isBefore(fimB) && fimA.isAfter(iniB);
    }

    private static void validarPeriodo(LocalDateTime inicio, LocalDateTime fim) {
        if (inicio == null) {
            throw new ErroAplicacao(400, "Início obrigatório");
        }
        if (fim == null) {
            throw new ErroAplicacao(400, "Fim obrigatório");
        }
        if (!fim.isAfter(inicio)) {
            throw new ErroAplicacao(400, "Fim deve ser posterior ao início");
        }
        if (Duration.between(inicio.toInstant(ZoneOffset.UTC), fim.toInstant(ZoneOffset.UTC)).compareTo(DURACAO_MINIMA) < 0) {
            throw new ErroAplicacao(400, "Duração mínima da reserva é de " + DURACAO_MINIMA.toMinutes() + " minuto(s)");
        }
    }

    /** Verdadeiro enquanto o horário de início da reserva ainda não chegou (relógio injetado). */
    private boolean antesDoInicio(Reserva r) {
        return LocalDateTime.now(relogio).isBefore(r.getInicio());
    }

    /** Responsável vinculado ao recurso da reserva (D3). */
    private boolean vinculado(Usuario ator, Reserva r) {
        return ator != null && armazenamento.existeVinculo(ator.id(), r.getRecurso().id());
    }

    private static boolean ehDono(Usuario ator, Reserva r) {
        return ator != null && ator.id().equals(r.getSolicitante().id());
    }

    private Reserva buscarObrigatoria(String id) {
        return buscar(id).orElseThrow(() -> new ErroAplicacao(404, "Reserva não encontrada: " + id));
    }

    private EventoAuditoria evento(String reservaId, Usuario ator, String acao,
                                   EstadoReserva anterior, EstadoReserva novo, String justificativa) {
        return new EventoAuditoria(reservaId, ator.id(), LocalDateTime.now(relogio), acao, anterior, novo, justificativa);
    }
}
