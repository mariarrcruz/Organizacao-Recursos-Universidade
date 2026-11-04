package com.organizacao.recursos.testes;

import static com.organizacao.recursos.testes.Fixtures.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.organizacao.recursos.dominio.*;
import com.organizacao.recursos.servico.ServicoMovimentacoes;
import com.organizacao.recursos.servico.ServicoReservas;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Requisito RF-10 — Controle de retirada e devolução de materiais (RFC RF-10; plano § 1.6). */
class RF10MovimentacoesMaterialTest {

    private final ServicoReservas reservas = new ServicoReservas();
    private final ServicoMovimentacoes movimentacoes = new ServicoMovimentacoes(reservas);
    private final Usuario alice = solicitante("alice");
    private final Usuario resp = responsavel("resp");
    private final Recurso sala = sala("S1", false);

    private Reserva aprovada(String id) {
        Reserva r = reserva(id, alice, sala, "10:00", "11:00", EstadoReserva.APROVADA);
        reservas.registrarExistente(r);
        return r;
    }

    @Test
    @DisplayName("RF-10.1 — Retirada com reserva APROVADA: MaterialMovement de retirada criado")
    void rf10_1_retiradaComReservaAprovada() {
        // Arrange
        Reserva r = aprovada("R1");
        // Act
        Movimentacao mov = movimentacoes.registrarRetirada(resp, r, "M1", "OK");
        // Assert
        assertEquals(TipoMovimentacao.RETIRADA, mov.tipo());
    }

    @Test
    @DisplayName("RF-10.2 — Retirada com reserva SOLICITADA: rejeitada (estado inválido)")
    void rf10_2_retiradaComReservaSolicitadaEhRejeitada() {
        // Arrange
        Reserva solicitada = reserva("R1", alice, sala, "10:00", "11:00", EstadoReserva.SOLICITADA);
        reservas.registrarExistente(solicitada);
        // Act & Assert
        assertRejeitado(() -> movimentacoes.registrarRetirada(resp, solicitada, "M1", "OK"));
    }

    @Test
    @DisplayName("RF-10.3 — Retirada por Solicitante: negada (403)")
    void rf10_3_retiradaPorSolicitanteEhNegada() {
        // Arrange
        Reserva r = aprovada("R1");
        // Act & Assert
        assertErro(403, () -> movimentacoes.registrarRetirada(alice, r, "M1", "OK"));
    }

    @Test
    @DisplayName("RF-10.4 — Devolução de material em retirada ativa: MaterialMovement de devolução")
    void rf10_4_devolucaoDeMaterialAtivo() {
        // Arrange
        Reserva r = aprovada("R1");
        movimentacoes.registrarRetirada(resp, r, "M1", "OK");
        // Act
        Movimentacao mov = movimentacoes.registrarDevolucao(resp, r, "M1", "OK");
        // Assert
        assertEquals(TipoMovimentacao.DEVOLUCAO, mov.tipo());
    }

    @Test
    @DisplayName("RF-10.5 — Devolução de material já devolvido: rejeitada (estado inválido)")
    void rf10_5_devolucaoDeMaterialJaDevolvidoEhRejeitada() {
        // Arrange
        Reserva r = aprovada("R1");
        movimentacoes.registrarRetirada(resp, r, "M1", "OK");
        movimentacoes.registrarDevolucao(resp, r, "M1", "OK");
        // Act & Assert
        assertRejeitado(() -> movimentacoes.registrarDevolucao(resp, r, "M1", "OK"));
    }

    @Test
    @DisplayName("RF-10.6 — Retirada OK e devolução DANIFICADO: histórico completo registrado")
    void rf10_6_historicoCompletoComCondicoes() {
        // Arrange
        Reserva r = aprovada("R1");
        movimentacoes.registrarRetirada(resp, r, "M1", "OK");
        movimentacoes.registrarDevolucao(resp, r, "M1", "DANIFICADO");
        // Act
        var historico = movimentacoes.historico("M1");
        // Assert
        assertEquals(2, historico.size());
        assertEquals("OK", historico.get(0).condicao());
        assertEquals("DANIFICADO", historico.get(1).condicao());
    }

    @Test
    @DisplayName("RF-10.7 — Duração entre retirada e devolução: igual à diferença dos instantes")
    void rf10_7_duracaoDeUsoEhCalculadaCorretamente() {
        // Arrange
        Reserva r = aprovada("R1");
        Movimentacao retirada = movimentacoes.registrarRetirada(resp, r, "M1", "OK");
        Movimentacao devolucao = movimentacoes.registrarDevolucao(resp, r, "M1", "OK");
        // Act
        Duration duracao = movimentacoes.duracaoUso("M1");
        // Assert
        assertEquals(Duration.between(retirada.instante(), devolucao.instante()), duracao);
        assertTrue(!duracao.isNegative());
    }

    @Test
    @DisplayName("RF-10.8 — Múltiplos materiais [M1, M2, M3]: retiradas e devoluções independentes")
    void rf10_8_multiplosMateriaisSaoIndependentes() {
        // Arrange
        Reserva r = aprovada("R1");
        movimentacoes.registrarRetirada(resp, r, "M1", "OK");
        movimentacoes.registrarRetirada(resp, r, "M2", "OK");
        movimentacoes.registrarRetirada(resp, r, "M3", "OK");
        // Act
        movimentacoes.registrarDevolucao(resp, r, "M2", "OK");
        // Assert
        assertEquals(1, movimentacoes.historico("M1").size());
        assertEquals(2, movimentacoes.historico("M2").size());
        assertEquals(1, movimentacoes.historico("M3").size());
    }
}
