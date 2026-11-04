package com.organizacao.recursos.servico;

import static com.organizacao.recursos.servico.Pendente.nao;

import com.organizacao.recursos.dominio.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Assinaturas mínimas para a fase red. Nenhuma regra de negócio implementada.
 * Os métodos de semeadura ({@code registrarExistente}, {@code registrarManutencaoExistente},
 * {@code reservasDoRecurso}) são armazenamento em memória sem regra, usados só para preparar cenários.
 */
public class ServicoReservas {

    private static final String C = "ServicoReservas";

    private final List<Reserva> armazenadas = new ArrayList<>();
    private final List<Manutencao> manutencoesArmazenadas = new ArrayList<>();

    public Reserva solicitar(Usuario ator, Recurso recurso, LocalDateTime inicio, LocalDateTime fim) { throw nao(C, "solicitar"); }
    public Reserva aprovar(Usuario ator, Reserva reserva) { throw nao(C, "aprovar"); }
    public Reserva rejeitar(Usuario ator, Reserva reserva, String justificativa) { throw nao(C, "rejeitar"); }
    public Reserva iniciarUso(Usuario ator, Reserva reserva) { throw nao(C, "iniciarUso"); }
    public Reserva concluir(Usuario ator, Reserva reserva) { throw nao(C, "concluir"); }
    public Reserva cancelar(Usuario ator, Reserva reserva, String justificativa) { throw nao(C, "cancelar"); }
    public Reserva registrarNaoComparecimento(Usuario ator, Reserva reserva) { throw nao(C, "registrarNaoComparecimento"); }
    public Reserva alterar(Usuario ator, Reserva reserva, LocalDateTime inicio, LocalDateTime fim) { throw nao(C, "alterar"); }
    public void excluir(Usuario ator, Reserva reserva) { throw nao(C, "excluir"); }
    public Reserva transicionar(Usuario ator, Reserva reserva, EstadoReserva destino, String justificativa) { throw nao(C, "transicionar"); }

    /** Insere uma reserva já existente no armazenamento (usado para preparar cenários de teste). */
    public void registrarExistente(Reserva reserva) { armazenadas.add(reserva); }
    public void registrarManutencaoExistente(Manutencao manutencao) { manutencoesArmazenadas.add(manutencao); }
    public void criarManutencao(Usuario ator, Manutencao manutencao) { throw nao(C, "criarManutencao"); }
    public void criarBloqueio(Usuario ator, Manutencao bloqueio) { throw nao(C, "criarBloqueio"); }
    public List<Reserva> reservasDoRecurso(String recursoId) {
        return armazenadas.stream().filter(r -> r.getRecurso().id().equals(recursoId)).toList();
    }
    public List<Reserva> pendentesDeAnalise(Usuario ator) { throw nao(C, "pendentesDeAnalise"); }
    public List<EventoAuditoria> eventosDe(Usuario ator, String reservaId) { throw nao(C, "eventosDe"); }
    public void alterarEvento(Usuario ator, EventoAuditoria evento, String novoValor) { throw nao(C, "alterarEvento"); }
    public void excluirEvento(Usuario ator, EventoAuditoria evento) { throw nao(C, "excluirEvento"); }
}
