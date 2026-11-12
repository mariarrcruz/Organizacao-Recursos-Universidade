package com.organizacao.recursos.servico;

import static com.organizacao.recursos.servico.Autorizacao.exigirPerfil;

import com.organizacao.recursos.dominio.*;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Retirada e devolução de materiais vinculadas a reservas aprovadas (RF-10). */
public class ServicoMovimentacoes {

    private final ServicoReservas reservas;
    private final List<Movimentacao> movimentos = new ArrayList<>();

    public ServicoMovimentacoes(ServicoReservas reservas) {
        this.reservas = reservas;
    }

    public synchronized Movimentacao registrarRetirada(Usuario ator, Reserva reserva, String materialId, String condicao) {
        exigirPerfil(ator, Perfil.RESPONSAVEL);
        Reserva atual = reservaOuErro(reserva);
        if (atual.getEstado() != EstadoReserva.APROVADA && atual.getEstado() != EstadoReserva.EM_USO) {
            throw new ErroAplicacao(409, "Retirada exige reserva APROVADA ou EM_USO, atual: " + atual.getEstado());
        }
        if (retiradaEmAberto(materialId).isPresent()) {
            throw new ErroAplicacao(409, "Material " + materialId + " já está retirado");
        }
        Movimentacao m = new Movimentacao(materialId, atual.getId(), TipoMovimentacao.RETIRADA,
                condicao, LocalDateTime.now(), ator.id());
        movimentos.add(m);
        return m;
    }

    public synchronized Movimentacao registrarDevolucao(Usuario ator, Reserva reserva, String materialId, String condicao) {
        exigirPerfil(ator, Perfil.RESPONSAVEL);
        Reserva atual = reservaOuErro(reserva);
        Optional<Movimentacao> aberta = retiradaEmAberto(materialId);
        if (aberta.isEmpty() || !aberta.get().reservaId().equals(atual.getId())) {
            throw new ErroAplicacao(409, "Material " + materialId + " não está retirado nesta reserva");
        }
        Movimentacao m = new Movimentacao(materialId, atual.getId(), TipoMovimentacao.DEVOLUCAO,
                condicao, LocalDateTime.now(), ator.id());
        movimentos.add(m);
        return m;
    }

    public synchronized List<Movimentacao> historico(String materialId) {
        return movimentos.stream().filter(m -> m.materialId().equals(materialId)).toList();
    }

    /** Duração entre a última retirada e a devolução que a encerrou; zero se não houver ciclo completo. */
    public synchronized Duration duracaoUso(String materialId) {
        Duration ultima = Duration.ZERO;
        Movimentacao retirada = null;
        for (Movimentacao m : historico(materialId)) {
            if (m.tipo() == TipoMovimentacao.RETIRADA) {
                retirada = m;
            } else if (retirada != null) {
                ultima = Duration.between(retirada.instante().toInstant(ZoneOffset.UTC), m.instante().toInstant(ZoneOffset.UTC));
                retirada = null;
            }
        }
        return ultima;
    }

    /**
     * Movimentações do material cuja reserva tem data de início no período [inicio, fim].
     * Decisão provisória: filtra pela data da reserva, pois o instante da movimentação é o momento
     * do registro (ver divergência registrada no plano TDD).
     */
    synchronized List<Movimentacao> historicoPeriodo(String materialId, LocalDate inicio, LocalDate fim) {
        return historico(materialId).stream()
                .filter(m -> reservas.buscar(m.reservaId())
                        .map(r -> {
                            LocalDate dia = r.getInicio().toLocalDate();
                            return !dia.isBefore(inicio) && !dia.isAfter(fim);
                        })
                        .orElse(false))
                .toList();
    }

    // ---------------------------------------------------------------- internos

    private Optional<Movimentacao> retiradaEmAberto(String materialId) {
        Movimentacao ultima = null;
        for (Movimentacao m : historico(materialId)) {
            ultima = m;
        }
        return ultima != null && ultima.tipo() == TipoMovimentacao.RETIRADA ? Optional.of(ultima) : Optional.empty();
    }

    private Reserva reservaOuErro(Reserva reserva) {
        return reservas.buscar(reserva.getId())
                .orElseThrow(() -> new ErroAplicacao(404, "Reserva não encontrada: " + reserva.getId()));
    }
}
