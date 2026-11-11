package com.organizacao.recursos.servico;

import static com.organizacao.recursos.servico.Autorizacao.exigirAutenticado;
import static com.organizacao.recursos.servico.Autorizacao.exigirPerfil;
import static com.organizacao.recursos.servico.Autorizacao.tem;

import com.organizacao.recursos.dominio.*;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Reservas, transições de estado, manutenção e auditoria.
 *
 * <p>Concorrência: todos os métodos que leem ou escrevem o armazenamento são {@code synchronized}
 * no mesmo monitor, o que serializa conflitos (RN-03). Decisão de implementação em memória;
 * ADR-004 prevê lock por recurso em banco, a ser substituído na camada de persistência.
 *
 * <p>Intervalos são semiabertos [início, fim) (RN-02, RN-04).
 */
public class ServicoReservas {

    /** Estados que ocupam o recurso e bloqueiam novas sobreposições. */
    private static final Set<EstadoReserva> ATIVAS =
            EnumSet.of(EstadoReserva.SOLICITADA, EstadoReserva.APROVADA, EstadoReserva.EM_USO);

    private final List<Reserva> armazenadas = new ArrayList<>();
    private final List<Manutencao> manutencoesArmazenadas = new ArrayList<>();
    private final List<EventoAuditoria> auditoria = new ArrayList<>();
    private final Clock relogio;

    /** Duração mínima de uma reserva (AMB-04, decisão provisória: 1 minuto). */
    static final Duration DURACAO_MINIMA = Duration.ofMinutes(1);

    public ServicoReservas() {
        this(Clock.systemDefaultZone());
    }

    public ServicoReservas(Clock relogio) {
        this.relogio = relogio;
    }

    // ---------------------------------------------------------------- criação e alteração

    public synchronized Reserva solicitar(Usuario ator, Recurso recurso, LocalDateTime inicio, LocalDateTime fim) {
        exigirAutenticado(ator);
        validarPeriodo(inicio, fim);
        if (recurso == null) {
            throw new ErroAplicacao(400, "Recurso obrigatório");
        }
        if (recurso.status() != StatusRecurso.DISPONIVEL) {
            throw new ErroAplicacao(409, "Recurso indisponível para reserva");
        }
        verificarDisponibilidade(recurso.id(), inicio, fim, null);
        Reserva nova = new Reserva(UUID.randomUUID().toString(), ator, recurso, inicio, fim, EstadoReserva.SOLICITADA);
        armazenadas.add(nova);
        registrarEvento(nova.getId(), ator, "SOLICITAR", null, EstadoReserva.SOLICITADA, null);
        return nova;
    }

    public synchronized Reserva alterar(Usuario ator, Reserva reserva, LocalDateTime inicio, LocalDateTime fim) {
        exigirAutenticado(ator);
        Reserva atual = buscarObrigatoria(reserva.getId());
        if (!ehDono(ator, atual)) {
            throw new ErroAplicacao(403, "Somente o solicitante pode alterar a reserva");
        }
        if (atual.getEstado() != EstadoReserva.SOLICITADA) {
            throw new ErroAplicacao(409, "Reserva em " + atual.getEstado() + " não pode ser alterada");
        }
        validarPeriodo(inicio, fim);
        verificarDisponibilidade(atual.getRecurso().id(), inicio, fim, atual.getId());
        Reserva nova = new Reserva(atual.getId(), atual.getSolicitante(), atual.getRecurso(),
                inicio, fim, EstadoReserva.SOLICITADA);
        substituir(nova);
        registrarEvento(nova.getId(), ator, "ALTERAR", EstadoReserva.SOLICITADA, EstadoReserva.SOLICITADA, null);
        return nova;
    }

    public synchronized void excluir(Usuario ator, Reserva reserva) {
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
        armazenadas.remove(atual);
        registrarEvento(atual.getId(), ator, "EXCLUIR", atual.getEstado(), null, null);
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

    public synchronized Reserva transicionar(Usuario ator, Reserva reserva, EstadoReserva destino, String justificativa) {
        exigirAutenticado(ator);
        Reserva atual = buscarObrigatoria(reserva.getId());
        if (!transicaoPermitida(atual.getEstado(), destino)) {
            throw new ErroAplicacao(409, "Transição proibida: " + atual.getEstado() + " → " + destino);
        }
        exigirAutorizacaoTransicao(ator, atual, destino);
        if (destino == EstadoReserva.CANCELADA && atual.getEstado() == EstadoReserva.APROVADA
                && !LocalDateTime.now(relogio).isBefore(atual.getInicio())) {
            throw new ErroAplicacao(409, "Cancelamento só é permitido antes do início");
        }
        if (destino == EstadoReserva.REJEITADA || destino == EstadoReserva.CANCELADA) {
            if (justificativa == null || justificativa.isBlank()) {
                throw new ErroAplicacao(400, "Justificativa obrigatória para " + destino);
            }
        }
        Reserva nova = new Reserva(atual.getId(), atual.getSolicitante(), atual.getRecurso(),
                atual.getInicio(), atual.getFim(), destino);
        substituir(nova);
        registrarEvento(nova.getId(), ator, "TRANSICIONAR", atual.getEstado(), destino, justificativa);
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

    private static void exigirAutorizacaoTransicao(Usuario ator, Reserva r, EstadoReserva destino) {
        boolean permitido = switch (destino) {
            case APROVADA -> tem(ator, Perfil.RESPONSAVEL) && !ehDono(ator, r);
            case REJEITADA, NAO_COMPARECEU -> tem(ator, Perfil.RESPONSAVEL);
            case CANCELADA -> ehDono(ator, r);
            case EM_USO, CONCLUIDA -> ehDono(ator, r) || tem(ator, Perfil.RESPONSAVEL);
            default -> false;
        };
        if (!permitido) {
            throw new ErroAplicacao(403, "Ator sem permissão para a transição " + destino);
        }
    }

    // ---------------------------------------------------------------- manutenção e bloqueios (RN-04)

    public synchronized void criarManutencao(Usuario ator, Manutencao manutencao) {
        exigirPerfil(ator, Perfil.ADMINISTRADOR);
        if (manutencao.inicio() == null || manutencao.fim() == null || !manutencao.fim().isAfter(manutencao.inicio())) {
            throw new ErroAplicacao(400, "Período de manutenção inválido");
        }
        manutencoesArmazenadas.add(manutencao);
    }

    public void criarBloqueio(Usuario ator, Manutencao bloqueio) {
        criarManutencao(ator, bloqueio);
    }

    // ---------------------------------------------------------------- consultas e auditoria (RN-09)

    public synchronized List<Reserva> pendentesDeAnalise(Usuario ator) {
        exigirPerfil(ator, Perfil.RESPONSAVEL);
        return armazenadas.stream().filter(r -> r.getEstado() == EstadoReserva.SOLICITADA).toList();
    }

    public synchronized List<EventoAuditoria> eventosDe(Usuario ator, String reservaId) {
        exigirAutenticado(ator);
        Reserva r = buscarObrigatoria(reservaId);
        if (!(ehDono(ator, r) || tem(ator, Perfil.RESPONSAVEL) || tem(ator, Perfil.ADMINISTRADOR))) {
            throw new ErroAplicacao(403, "Sem permissão para consultar a auditoria desta reserva");
        }
        return auditoria.stream().filter(e -> e.reservaId().equals(reservaId)).toList();
    }

    /** Auditoria é append-only: alteração é sempre recusada (RN-09.8). */
    public void alterarEvento(Usuario ator, EventoAuditoria evento, String novoValor) {
        throw new ErroAplicacao(405, "Auditoria é append-only: alteração proibida");
    }

    /** Auditoria é append-only: exclusão é sempre recusada (RN-09.9). */
    public void excluirEvento(Usuario ator, EventoAuditoria evento) {
        throw new ErroAplicacao(405, "Auditoria é append-only: exclusão proibida");
    }

    // ---------------------------------------------------------------- acesso interno aos demais serviços

    /** Insere uma reserva já existente no armazenamento (usado para preparar cenários de teste). */
    public synchronized void registrarExistente(Reserva reserva) { armazenadas.add(reserva); }

    public synchronized void registrarManutencaoExistente(Manutencao manutencao) { manutencoesArmazenadas.add(manutencao); }

    public synchronized List<Reserva> reservasDoRecurso(String recursoId) {
        return armazenadas.stream().filter(r -> r.getRecurso().id().equals(recursoId)).toList();
    }

    synchronized Optional<Reserva> buscar(String id) {
        return armazenadas.stream().filter(r -> r.getId().equals(id)).findFirst();
    }

    synchronized List<Reserva> todas() {
        return List.copyOf(armazenadas);
    }

    synchronized boolean temReservaAtiva(String recursoId) {
        return armazenadas.stream()
                .anyMatch(r -> r.getRecurso().id().equals(recursoId) && ATIVAS.contains(r.getEstado()));
    }

    synchronized boolean temConflito(String recursoId, LocalDateTime inicio, LocalDateTime fim) {
        return conflita(recursoId, inicio, fim, null);
    }

    // ---------------------------------------------------------------- internos

    private void verificarDisponibilidade(String recursoId, LocalDateTime inicio, LocalDateTime fim, String idIgnorado) {
        if (conflita(recursoId, inicio, fim, idIgnorado)) {
            throw new ErroAplicacao(409, "Conflito de horário para o recurso " + recursoId);
        }
    }

    private boolean conflita(String recursoId, LocalDateTime inicio, LocalDateTime fim, String idIgnorado) {
        boolean emManutencao = manutencoesArmazenadas.stream()
                .filter(m -> m.recursoId().equals(recursoId))
                .anyMatch(m -> sobrepoe(m.inicio(), m.fim(), inicio, fim));
        if (emManutencao) {
            return true;
        }
        return armazenadas.stream()
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
        if (Duration.between(inicio, fim).compareTo(DURACAO_MINIMA) < 0) {
            throw new ErroAplicacao(400, "Duração mínima da reserva é de " + DURACAO_MINIMA.toMinutes() + " minuto(s)");
        }
    }

    private static boolean ehDono(Usuario ator, Reserva r) {
        return ator != null && ator.id().equals(r.getSolicitante().id());
    }

    private Reserva buscarObrigatoria(String id) {
        return buscar(id).orElseThrow(() -> new ErroAplicacao(404, "Reserva não encontrada: " + id));
    }

    private void substituir(Reserva nova) {
        for (int i = 0; i < armazenadas.size(); i++) {
            if (armazenadas.get(i).getId().equals(nova.getId())) {
                armazenadas.set(i, nova);
                return;
            }
        }
        throw new ErroAplicacao(404, "Reserva não encontrada: " + nova.getId());
    }

    private void registrarEvento(String reservaId, Usuario ator, String acao,
                                 EstadoReserva anterior, EstadoReserva novo, String justificativa) {
        auditoria.add(new EventoAuditoria(reservaId, ator.id(), LocalDateTime.now(relogio), acao, anterior, novo, justificativa));
    }
}
