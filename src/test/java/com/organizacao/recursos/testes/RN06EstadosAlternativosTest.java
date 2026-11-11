package com.organizacao.recursos.testes;

import org.junit.jupiter.api.BeforeEach;
import static com.organizacao.recursos.testes.Fixtures.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.organizacao.recursos.dominio.*;
import com.organizacao.recursos.servico.ServicoReservas;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Regra RN-06 — Estados alternativos REJEITADA, CANCELADA e NÃO COMPARECEU (PRD § 4.2; RFC RF-05). */
class RN06EstadosAlternativosTest {

    /** Relógio fixo antes de DIA (2026-11-05), para que "antes do início" seja determinístico. */
    private final ServicoReservas servico = new ServicoReservas(
            java.time.Clock.fixed(java.time.LocalDateTime.of(2026, 11, 3, 12, 0)
                    .atZone(java.time.ZoneId.systemDefault()).toInstant(), java.time.ZoneId.systemDefault()));
    private final Usuario alice = solicitante("alice");
    private final Usuario resp = responsavel("resp");
    private final Recurso sala = sala("S1", false);

    @BeforeEach
    void vincularResponsavelDeTeste() {
        servico.vincularResponsavel(administrador("adm"), "resp", "S1");
    }

    @Test
    @DisplayName("RN-06.1 — SOLICITADA → REJEITADA por Responsável: permitido")
    void rn06_1_solicitadaParaRejeitadaPorResponsavel() {
        // Arrange
        Reserva solicitada = reserva("R1", alice, sala, "10:00", "11:00", EstadoReserva.SOLICITADA);
        servico.registrarExistente(solicitada);
        // Act
        Reserva rejeitada = servico.rejeitar(resp, solicitada, "Sala indisponível para o evento");
        // Assert
        assertEquals(EstadoReserva.REJEITADA, rejeitada.getEstado());
    }

    @Test
    @DisplayName("RN-06.2 — SOLICITADA → CANCELADA por Solicitante: permitido")
    void rn06_2_solicitadaParaCanceladaPeloSolicitante() {
        // Arrange
        Reserva solicitada = reserva("R1", alice, sala, "10:00", "11:00", EstadoReserva.SOLICITADA);
        servico.registrarExistente(solicitada);
        // Act
        Reserva cancelada = servico.cancelar(alice, solicitada, "Mudança de planos");
        // Assert
        assertEquals(EstadoReserva.CANCELADA, cancelada.getEstado());
    }

    @Test
    @DisplayName("RN-06.3 — APROVADA → CANCELADA antes do início: permitido")
    void rn06_3_aprovadaParaCanceladaAntesDoInicio() {
        // Arrange
        Reserva aprovada = reserva("R1", alice, sala, "10:00", "11:00", EstadoReserva.APROVADA);
        servico.registrarExistente(aprovada);
        // Act
        Reserva cancelada = servico.cancelar(alice, aprovada, "Imprevisto");
        // Assert
        assertEquals(EstadoReserva.CANCELADA, cancelada.getEstado());
    }

    @Test
    @DisplayName("RN-06.4 — APROVADA → NÃO COMPARECEU (registro de ausência): permitido")
    void rn06_4_aprovadaParaNaoComparecimento() {
        // Arrange — não comparecimento só após o início (Q8); relógio local às 10:30 do dia da reserva
        ServicoReservas servico = new ServicoReservas(java.time.Clock.fixed(
                java.time.LocalDateTime.of(2026, 11, 5, 10, 30).atZone(java.time.ZoneId.systemDefault()).toInstant(),
                java.time.ZoneId.systemDefault()));
        Reserva aprovada = reserva("R1", alice, sala, "10:00", "11:00", EstadoReserva.APROVADA);
        servico.registrarExistente(aprovada);
        // Act
        Reserva ausente = servico.registrarNaoComparecimento(resp, aprovada);
        // Assert
        assertEquals(EstadoReserva.NAO_COMPARECEU, ausente.getEstado());
    }

    @Test
    @DisplayName("RN-06.5 — EM_USO → CANCELADA durante uso: transição proibida (RN-08)")
    void rn06_5_emUsoParaCanceladaEhProibida() {
        // Arrange
        Reserva emUso = reserva("R1", alice, sala, "10:00", "11:00", EstadoReserva.EM_USO);
        servico.registrarExistente(emUso);
        // Act & Assert
        assertRejeitado(() -> servico.cancelar(alice, emUso, "Desistência"));
    }

    @Test
    @DisplayName("RN-06.6 — EM_USO → REJEITADA após início: transição proibida")
    void rn06_6_emUsoParaRejeitadaEhProibida() {
        // Arrange
        Reserva emUso = reserva("R1", alice, sala, "10:00", "11:00", EstadoReserva.EM_USO);
        servico.registrarExistente(emUso);
        // Act & Assert
        assertRejeitado(() -> servico.rejeitar(resp, emUso, "Motivo tardio"));
    }

    @Test
    @DisplayName("RN-06.7 — REJEITADA → CANCELADA (entre estados finais): rejeitado (terminal)")
    void rn06_7_rejeitadaParaCanceladaEhRejeitada() {
        // Arrange
        Reserva rejeitada = reserva("R1", alice, sala, "10:00", "11:00", EstadoReserva.REJEITADA);
        servico.registrarExistente(rejeitada);
        // Act & Assert
        assertRejeitado(() -> servico.transicionar(alice, rejeitada, EstadoReserva.CANCELADA, "Tentativa"));
    }

    @Test
    @DisplayName("RN-06.8 — CANCELADA → APROVADA (retomada): rejeitado (terminal)")
    void rn06_8_canceladaParaAprovadaEhRejeitada() {
        // Arrange
        Reserva cancelada = reserva("R1", alice, sala, "10:00", "11:00", EstadoReserva.CANCELADA);
        servico.registrarExistente(cancelada);
        // Act & Assert
        assertRejeitado(() -> servico.transicionar(resp, cancelada, EstadoReserva.APROVADA, null));
    }
}
