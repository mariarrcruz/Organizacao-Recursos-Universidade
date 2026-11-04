package com.organizacao.recursos.servico;

import static com.organizacao.recursos.servico.Pendente.nao;

import com.organizacao.recursos.dominio.*;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Assinaturas mínimas para a fase red. Nenhuma regra implementada. */
public class ServicoRelatorios {

    private static final String C = "ServicoRelatorios";

    public ServicoRelatorios(ServicoReservas reservas, ServicoMovimentacoes movimentacoes) { }

    public Map<String, Duration> utilizacaoPorRecurso(Usuario ator, LocalDate inicio, LocalDate fim) { throw nao(C, "utilizacaoPorRecurso"); }
    public Duration cargaHorariaSala(Usuario ator, String salaId, LocalDate inicio, LocalDate fim) { throw nao(C, "cargaHorariaSala"); }
    public Map<EstadoReserva, Long> contarPorEstado(Usuario ator, Set<EstadoReserva> estados) { throw nao(C, "contarPorEstado"); }
    public List<Movimentacao> historicoMovimentacoes(Usuario ator, String materialId, LocalDate inicio, LocalDate fim) { throw nao(C, "historicoMovimentacoes"); }
}
