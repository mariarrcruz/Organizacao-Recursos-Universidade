package com.organizacao.recursos.testes;

import static com.organizacao.recursos.testes.Fixtures.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.organizacao.recursos.dominio.*;
import com.organizacao.recursos.servico.ServicoReservas;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Regra RN-02 — Exclusividade de alocação por recurso; intervalo [início, fim) (PRD § 4.1; RFC RF-06). */
class RN02ExclusividadeAlocacaoTest {

    private final ServicoReservas servico = new ServicoReservas();
    private final Usuario alice = solicitante("alice");
    private final Recurso r1 = sala("R1", false);

    /** Reserva existente de [10:00, 11:00) para R1. */
    private void existenteR1() {
        servico.registrarExistente(reserva("EXIST-R1", alice, r1, "10:00", "11:00", EstadoReserva.APROVADA));
    }

    @Test
    @DisplayName("RN-02.1 — Sem conflito (distante): aceito")
    void rn02_1_reservaDistanteEAceita() {
        // Arrange
        existenteR1();
        // Act
        Reserva nova = servico.solicitar(alice, r1, t("14:00"), t("15:00"));
        // Assert
        assertNotNull(nova);
        assertEquals(EstadoReserva.APROVADA, nova.getEstado());
    }

    @Test
    @DisplayName("RN-02.2 — Adjacente [11:00, 12:00) após [10:00, 11:00): aceito (semiabertura)")
    void rn02_2_reservaAdjacenteEAceita() {
        // Arrange
        existenteR1();
        // Act
        Reserva nova = servico.solicitar(alice, r1, t("11:00"), t("12:00"));
        // Assert
        assertNotNull(nova);
        assertEquals(EstadoReserva.APROVADA, nova.getEstado());
    }

    @Test
    @DisplayName("RN-02.3 — Sobreposição total [10:30, 11:30): rejeitado")
    void rn02_3_sobreposicaoDeslocadaEhRejeitada() {
        // Arrange
        existenteR1();
        // Act & Assert
        assertRejeitado(() -> servico.solicitar(alice, r1, t("10:30"), t("11:30")));
    }

    @Test
    @DisplayName("RN-02.4 — Sobreposição parcial no início [09:30, 10:30): rejeitado")
    void rn02_4_sobreposicaoParcialNoInicioEhRejeitada() {
        // Arrange
        existenteR1();
        // Act & Assert
        assertRejeitado(() -> servico.solicitar(alice, r1, t("09:30"), t("10:30")));
    }

    @Test
    @DisplayName("RN-02.5 — Sobreposição parcial no fim [10:30, 11:00): rejeitado")
    void rn02_5_sobreposicaoParcialNoFimEhRejeitada() {
        // Arrange
        existenteR1();
        // Act & Assert
        assertRejeitado(() -> servico.solicitar(alice, r1, t("10:30"), t("11:00")));
    }

    @Test
    @DisplayName("RN-02.6 — Mínima sobreposição [09:59, 10:01): rejeitado")
    void rn02_6_minimaSobreposicaoEhRejeitada() {
        // Arrange
        existenteR1();
        // Act & Assert
        assertRejeitado(() -> servico.solicitar(alice, r1, t("09:59"), t("10:01")));
    }

    @Test
    @DisplayName("RN-02.7 — Nova reserva contém a existente [09:30, 12:00): rejeitado")
    void rn02_7_novaReservaQueContemExistenteEhRejeitada() {
        // Arrange
        existenteR1();
        // Act & Assert
        assertRejeitado(() -> servico.solicitar(alice, r1, t("09:30"), t("12:00")));
    }

    @Test
    @DisplayName("RN-02.8 — Idêntica [10:00, 11:00): rejeitado")
    void rn02_8_reservaIdenticaEhRejeitada() {
        // Arrange
        existenteR1();
        // Act & Assert
        assertRejeitado(() -> servico.solicitar(alice, r1, t("10:00"), t("11:00")));
    }

    @Test
    @DisplayName("RN-02.9 — Mesmo horário em recursos diferentes: aceito (regra por recurso)")
    void rn02_9_mesmoHorarioEmRecursosDiferentesEAceito() {
        // Arrange
        Recurso r2 = sala("R2", false);
        Recurso r3 = sala("R3", false);
        servico.registrarExistente(reserva("EXIST-R1", alice, r1, "10:00", "11:00", EstadoReserva.APROVADA));
        servico.registrarExistente(reserva("EXIST-R2", alice, r2, "10:00", "11:00", EstadoReserva.APROVADA));
        // Act
        Reserva nova = servico.solicitar(alice, r3, t("10:00"), t("11:00"));
        // Assert
        assertNotNull(nova);
        assertEquals(EstadoReserva.APROVADA, nova.getEstado());
    }
}
