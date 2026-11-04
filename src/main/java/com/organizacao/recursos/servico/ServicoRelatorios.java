package com.organizacao.recursos.servico;

import static com.organizacao.recursos.servico.Autorizacao.exigirAlgumPerfil;

import com.organizacao.recursos.dominio.*;
import java.time.Duration;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Relatórios gerenciais (RF-12). Acesso restrito a Responsável e Administrador;
 * o escopo do Solicitante segue pendente (AMB-06).
 */
public class ServicoRelatorios {

    /** Estados que contam como utilização efetiva do recurso. */
    private static final Set<EstadoReserva> UTILIZADAS =
            EnumSet.of(EstadoReserva.APROVADA, EstadoReserva.EM_USO, EstadoReserva.CONCLUIDA);

    private final ServicoReservas reservas;
    private final ServicoMovimentacoes movimentacoes;

    public ServicoRelatorios(ServicoReservas reservas, ServicoMovimentacoes movimentacoes) {
        this.reservas = reservas;
        this.movimentacoes = movimentacoes;
    }

    public Map<String, Duration> utilizacaoPorRecurso(Usuario ator, LocalDate inicio, LocalDate fim) {
        exigirGerencial(ator);
        Map<String, Duration> mapa = new HashMap<>();
        for (Reserva r : reservas.todas()) {
            if (ocupaPeriodo(r, inicio, fim)) {
                mapa.merge(r.getRecurso().id(), duracao(r), Duration::plus);
            }
        }
        return mapa;
    }

    public Duration cargaHorariaSala(Usuario ator, String salaId, LocalDate inicio, LocalDate fim) {
        exigirGerencial(ator);
        // Limitação: sem o registro de recursos, "existe" é inferido das reservas.
        boolean conhecido = reservas.todas().stream().anyMatch(r -> r.getRecurso().id().equals(salaId));
        if (!conhecido) {
            throw new ErroAplicacao(404, "Sala não encontrada: " + salaId);
        }
        Duration total = Duration.ZERO;
        for (Reserva r : reservas.todas()) {
            if (r.getRecurso().id().equals(salaId) && ocupaPeriodo(r, inicio, fim)) {
                total = total.plus(duracao(r));
            }
        }
        return total;
    }

    public Map<EstadoReserva, Long> contarPorEstado(Usuario ator, Set<EstadoReserva> estados) {
        exigirGerencial(ator);
        Map<EstadoReserva, Long> contagem = new EnumMap<>(EstadoReserva.class);
        for (EstadoReserva e : estados) {
            contagem.put(e, 0L);
        }
        for (Reserva r : reservas.todas()) {
            if (estados.contains(r.getEstado())) {
                contagem.merge(r.getEstado(), 1L, Long::sum);
            }
        }
        return contagem;
    }

    public List<Movimentacao> historicoMovimentacoes(Usuario ator, String materialId, LocalDate inicio, LocalDate fim) {
        exigirGerencial(ator);
        return movimentacoes.historicoPeriodo(materialId, inicio, fim);
    }

    // ---------------------------------------------------------------- internos

    private static void exigirGerencial(Usuario ator) {
        exigirAlgumPerfil(ator, Perfil.RESPONSAVEL, Perfil.ADMINISTRADOR);
    }

    private static boolean ocupaPeriodo(Reserva r, LocalDate inicio, LocalDate fim) {
        if (!UTILIZADAS.contains(r.getEstado())) {
            return false;
        }
        LocalDate dia = r.getInicio().toLocalDate();
        return !dia.isBefore(inicio) && !dia.isAfter(fim);
    }

    private static Duration duracao(Reserva r) {
        return Duration.between(r.getInicio(), r.getFim());
    }
}
