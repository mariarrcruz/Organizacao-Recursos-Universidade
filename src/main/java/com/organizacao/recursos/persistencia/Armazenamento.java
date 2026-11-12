package com.organizacao.recursos.persistencia;

import com.organizacao.recursos.dominio.ConflitoReserva;
import com.organizacao.recursos.dominio.EventoAuditoria;
import com.organizacao.recursos.dominio.Manutencao;
import com.organizacao.recursos.dominio.Recurso;
import com.organizacao.recursos.dominio.Reserva;
import java.util.List;
import java.util.Optional;

/**
 * Porta de armazenamento de reservas, manutenções, recursos e auditoria.
 *
 * <p>Duas implementações: {@link ArmazenamentoMemoria} (testes unitários rápidos) e
 * {@link ArmazenamentoPostgres} (aplicação e testes de integração, ADR-016 e ADR-017).
 * Cada escrita de estado recebe o evento de auditoria correspondente, para que as duas
 * gravações sejam atômicas (AD-05, RN-09).
 */
public interface Armazenamento {

    Optional<Reserva> buscarReserva(String id);

    List<Reserva> listarReservas();

    /**
     * Grava uma reserva nova e, se não for {@code null}, o evento de auditoria, na mesma unidade de trabalho.
     * Recusa com 409 se o recurso tiver manutenção sobreposta ou reserva ativa sobreposta (no PostgreSQL,
     * a garantia vem do banco).
     */
    void inserirReserva(Reserva reserva, EventoAuditoria evento);

    /**
     * Atualiza estado e período da reserva e grava o evento. Com {@code revalidarManutencao}, recusa com 409
     * se o período atual estiver sobreposto a manutenção (RTM Q5).
     */
    void atualizarReserva(Reserva reserva, EventoAuditoria evento, boolean revalidarManutencao);

    void removerReserva(String id, EventoAuditoria evento);

    void inserirManutencao(Manutencao manutencao);

    List<Manutencao> listarManutencoes();

    List<EventoAuditoria> listarEventos();

    void gravarRecurso(Recurso recurso);

    Optional<Recurso> buscarRecurso(String id);

    /** Todos os recursos cadastrados, ordenados por id. */
    List<Recurso> listarRecursos();

    /** Remove o recurso. Quem chama garante que não há reservas referenciando-o (RF-02). */
    void removerRecurso(String id);

    /** Registra uma tentativa de reserva recusada por sobreposição (conflito evitado). */
    void registrarConflito(ConflitoReserva conflito);

    List<ConflitoReserva> listarConflitos();

    /** Vincula um Responsável a um recurso (D3). Gravar de novo o mesmo vínculo não tem efeito. */
    void gravarVinculo(String responsavelId, String recursoId);

    boolean existeVinculo(String responsavelId, String recursoId);
}
