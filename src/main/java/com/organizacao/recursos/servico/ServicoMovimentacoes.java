package com.organizacao.recursos.servico;

import static com.organizacao.recursos.servico.Pendente.nao;

import com.organizacao.recursos.dominio.*;
import java.time.Duration;
import java.util.List;

/** Assinaturas mínimas para a fase red. Nenhuma regra implementada. */
public class ServicoMovimentacoes {

    private static final String C = "ServicoMovimentacoes";

    public ServicoMovimentacoes(ServicoReservas reservas) { }

    public Movimentacao registrarRetirada(Usuario ator, Reserva reserva, String materialId, String condicao) { throw nao(C, "registrarRetirada"); }
    public Movimentacao registrarDevolucao(Usuario ator, Reserva reserva, String materialId, String condicao) { throw nao(C, "registrarDevolucao"); }
    public List<Movimentacao> historico(String materialId) { throw nao(C, "historico"); }
    public Duration duracaoUso(String materialId) { throw nao(C, "duracaoUso"); }
}
