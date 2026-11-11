package com.organizacao.recursos.testes;

import static com.organizacao.recursos.testes.Fixtures.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.organizacao.recursos.dominio.*;
import com.organizacao.recursos.servico.ServicoReservas;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/** Regra RN-01 — Ordem temporal da reserva (PRD § 4.1; RFC RF-04). */
class RN01OrdemTemporalTest {

    private final ServicoReservas servico = new ServicoReservas();
    private final Usuario alice = solicitante("alice");
    private final Recurso sala = sala("S1", false);

    @Test
    @DisplayName("RN-01.1 — Fim > Início: aceito")
    void rn01_1_fimPosteriorAoInicioEAceito() {
        // Arrange
        // Act
        Reserva reserva = servico.solicitar(alice, sala, t("10:00"), t("11:00"));
        // Assert
        assertNotNull(reserva);
        assertEquals(EstadoReserva.APROVADA, reserva.getEstado());
    }

    @Test
    @DisplayName("RN-01.2 — Fim = Início: rejeitado (duração zero)")
    void rn01_2_fimIgualInicioEhRejeitado() {
        // Arrange
        // Act & Assert
        assertRejeitado(() -> servico.solicitar(alice, sala, t("10:00"), t("10:00")));
    }

    @Test
    @DisplayName("RN-01.3 — Fim < Início: rejeitado")
    void rn01_3_fimAnteriorAoInicioEhRejeitado() {
        // Arrange
        // Act & Assert
        assertRejeitado(() -> servico.solicitar(alice, sala, t("10:00"), t("09:59")));
    }

    @Test
    @DisplayName("RN-01.4 — Duração < 1 minuto: validar se permitido")
    void rn01_4_duracaoMenorQueUmMinuto() {
        // Arrange
        // Act & Assert
        assertRejeitado(() -> servico.solicitar(alice, sala, t("10:00"), t("10:00").plusNanos(1_000_000)));
    }

    @Test
    @DisplayName("RN-01.5 — Início ausente: rejeitado (obrigatório)")
    void rn01_5_inicioAusenteEhRejeitado() {
        // Arrange
        // Act & Assert
        assertRejeitado(() -> servico.solicitar(alice, sala, null, t("11:00")));
    }

    @Test
    @DisplayName("RN-01.6 — Fim ausente: rejeitado (obrigatório)")
    void rn01_6_fimAusenteEhRejeitado() {
        // Arrange
        // Act & Assert
        assertRejeitado(() -> servico.solicitar(alice, sala, t("10:00"), null));
    }
}
