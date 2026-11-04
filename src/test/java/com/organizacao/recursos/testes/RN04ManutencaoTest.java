package com.organizacao.recursos.testes;

import static com.organizacao.recursos.testes.Fixtures.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.organizacao.recursos.dominio.*;
import com.organizacao.recursos.servico.ServicoReservas;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Regra RN-04 — Inoperância por manutenção (PRD § 4.1; RFC RF-09). */
class RN04ManutencaoTest {

    private final ServicoReservas servico = new ServicoReservas();
    private final Usuario alice = solicitante("alice");
    private final Usuario admin = administrador("adm");
    private final Recurso r1 = sala("R1", false);

    private void manutencao(String inicio, String fim) {
        servico.criarManutencao(admin, new Manutencao("R1", t(inicio), t(fim)));
    }

    @Test
    @DisplayName("RN-04.1 — Sem manutenção: aceito")
    void rn04_1_semManutencaoEAceito() {
        // Arrange
        // Act
        Reserva reserva = servico.solicitar(alice, r1, t("10:00"), t("11:00"));
        // Assert
        assertNotNull(reserva);
        assertEquals(EstadoReserva.SOLICITADA, reserva.getEstado());
    }

    @Test
    @DisplayName("RN-04.2 — Manutenção [12,13), reserva [10,11): aceito (sem sobreposição)")
    void rn04_2_manutencaoDistanteEAceita() {
        // Arrange
        manutencao("12:00", "13:00");
        // Act
        Reserva reserva = servico.solicitar(alice, r1, t("10:00"), t("11:00"));
        // Assert
        assertNotNull(reserva);
        assertEquals(EstadoReserva.SOLICITADA, reserva.getEstado());
    }

    @Test
    @DisplayName("RN-04.3 — Manutenção [12,13), reserva [11,12): aceito (adjacente, semiabertura)")
    void rn04_3_reservaAdjacenteAManutencaoEAceita() {
        // Arrange
        manutencao("12:00", "13:00");
        // Act
        Reserva reserva = servico.solicitar(alice, r1, t("11:00"), t("12:00"));
        // Assert
        assertNotNull(reserva);
        assertEquals(EstadoReserva.SOLICITADA, reserva.getEstado());
    }

    @Test
    @DisplayName("RN-04.4 — Manutenção [12,13), reserva exata [12,13): rejeitado")
    void rn04_4_reservaExataNaManutencaoEhRejeitada() {
        // Arrange
        manutencao("12:00", "13:00");
        // Act & Assert
        assertRejeitado(() -> servico.solicitar(alice, r1, t("12:00"), t("13:00")));
    }

    @Test
    @DisplayName("RN-04.5 — Manutenção [12,13), reserva [11:30,12:30): rejeitado")
    void rn04_5_reservaParcialSobreManutencaoEhRejeitada() {
        // Arrange
        manutencao("12:00", "13:00");
        // Act & Assert
        assertRejeitado(() -> servico.solicitar(alice, r1, t("11:30"), t("12:30")));
    }

    @Test
    @DisplayName("RN-04.6 — Manutenção [12,13), reserva [11,14) envolve o período: rejeitado")
    void rn04_6_reservaQueEnvolveManutencaoEhRejeitada() {
        // Arrange
        manutencao("12:00", "13:00");
        // Act & Assert
        assertRejeitado(() -> servico.solicitar(alice, r1, t("11:00"), t("14:00")));
    }

    @Test
    @DisplayName("RN-04.7 — Manutenções fragmentadas [9,10) e [12,13); reserva [10,12) sem conflito: aceito")
    void rn04_7_intervalosEntreManutencoesFragmentadasSaoAceitos() {
        // Arrange
        manutencao("09:00", "10:00");
        manutencao("12:00", "13:00");
        // Act
        Reserva reserva = servico.solicitar(alice, r1, t("10:00"), t("12:00"));
        // Assert
        assertNotNull(reserva);
        assertEquals(EstadoReserva.SOLICITADA, reserva.getEstado());
    }
}
