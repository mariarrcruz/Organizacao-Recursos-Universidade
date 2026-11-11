package com.organizacao.recursos.persistencia;

import com.organizacao.recursos.dominio.ErroAplicacao;
import com.organizacao.recursos.dominio.EventoAuditoria;
import com.organizacao.recursos.dominio.Manutencao;
import com.organizacao.recursos.dominio.Recurso;
import com.organizacao.recursos.dominio.Reserva;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Armazenamento em memória. Não verifica sobreposição: a regra fica no {@code ServicoReservas},
 * que roda sob seu monitor. Usado nos testes unitários.
 */
public final class ArmazenamentoMemoria implements Armazenamento {

    private final List<Reserva> reservas = new ArrayList<>();
    private final List<Manutencao> manutencoes = new ArrayList<>();
    private final List<EventoAuditoria> eventos = new ArrayList<>();
    private final Map<String, Recurso> recursos = new HashMap<>();

    @Override
    public synchronized Optional<Reserva> buscarReserva(String id) {
        return reservas.stream().filter(r -> r.getId().equals(id)).findFirst();
    }

    @Override
    public synchronized List<Reserva> listarReservas() {
        return List.copyOf(reservas);
    }

    @Override
    public synchronized void inserirReserva(Reserva reserva, EventoAuditoria evento) {
        reservas.add(reserva);
        if (evento != null) {
            eventos.add(evento);
        }
    }

    @Override
    public synchronized void atualizarReserva(Reserva reserva, EventoAuditoria evento, boolean revalidarManutencao) {
        for (int i = 0; i < reservas.size(); i++) {
            if (reservas.get(i).getId().equals(reserva.getId())) {
                reservas.set(i, reserva);
                eventos.add(evento);
                return;
            }
        }
        throw new ErroAplicacao(404, "Reserva não encontrada: " + reserva.getId());
    }

    @Override
    public synchronized void removerReserva(String id, EventoAuditoria evento) {
        reservas.removeIf(r -> r.getId().equals(id));
        eventos.add(evento);
    }

    @Override
    public synchronized void inserirManutencao(Manutencao manutencao) {
        manutencoes.add(manutencao);
    }

    @Override
    public synchronized List<Manutencao> listarManutencoes() {
        return List.copyOf(manutencoes);
    }

    @Override
    public synchronized List<EventoAuditoria> listarEventos() {
        return List.copyOf(eventos);
    }

    @Override
    public synchronized void gravarRecurso(Recurso recurso) {
        recursos.put(recurso.id(), recurso);
    }

    @Override
    public synchronized Optional<Recurso> buscarRecurso(String id) {
        return Optional.ofNullable(recursos.get(id));
    }
}
