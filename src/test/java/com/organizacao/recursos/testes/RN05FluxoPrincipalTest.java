package com.organizacao.recursos.testes;

import org.junit.jupiter.api.BeforeEach;
import static com.organizacao.recursos.testes.Fixtures.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.organizacao.recursos.dominio.*;
import com.organizacao.recursos.servico.ServicoReservas;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/** Regra RN-05 — Fluxo principal de estados SOLICITADA → APROVADA → EM_USO → CONCLUÍDA (PRD § 4.2; RFC RF-05). */
class RN05FluxoPrincipalTest {

    private final ServicoReservas servico = new ServicoReservas();
    private final Usuario alice = solicitante("alice");
    private final Usuario resp = responsavel("resp");
    private final Recurso sala = sala("S1", true);

    @BeforeEach
    void vincularResponsavelDeTeste() {
        servico.vincularResponsavel(administrador("adm"), "resp", "S1");
    }

    @Test
    @DisplayName("RN-05.1 — SOLICITADA → APROVADA por Responsável: permitido")
    void rn05_1_solicitadaParaAprovadaPorResponsavel() {
        // Arrange
        Reserva solicitada = reserva("R1", alice, sala, "10:00", "11:00", EstadoReserva.SOLICITADA);
        servico.registrarExistente(solicitada);
        // Act
        Reserva aprovada = servico.aprovar(resp, solicitada);
        // Assert
        assertEquals(EstadoReserva.APROVADA, aprovada.getEstado());
    }

    @Test
    @DisplayName("RN-05.2 — APROVADA → EM_USO no início da utilização: permitido")
    void rn05_2_aprovadaParaEmUso() {
        // Arrange
        Reserva aprovada = reserva("R1", alice, sala, "10:00", "11:00", EstadoReserva.APROVADA);
        servico.registrarExistente(aprovada);
        // Act
        Reserva emUso = servico.iniciarUso(alice, aprovada);
        // Assert
        assertEquals(EstadoReserva.EM_USO, emUso.getEstado());
    }

    @Test
    @DisplayName("RN-05.3 — EM_USO → CONCLUÍDA na conclusão da utilização: permitido")
    void rn05_3_emUsoParaConcluida() {
        // Arrange
        Reserva emUso = reserva("R1", alice, sala, "10:00", "11:00", EstadoReserva.EM_USO);
        servico.registrarExistente(emUso);
        // Act
        Reserva concluida = servico.concluir(alice, emUso);
        // Assert
        assertEquals(EstadoReserva.CONCLUIDA, concluida.getEstado());
    }

    @Test
    @DisplayName("RN-05.4 — SOLICITADA → EM_USO sem aprovação intermediária: transição proibida")
    void rn05_4_solicitadaParaEmUsoEhProibida() {
        // Arrange
        Reserva solicitada = reserva("R1", alice, sala, "10:00", "11:00", EstadoReserva.SOLICITADA);
        servico.registrarExistente(solicitada);
        // Act & Assert
        assertRejeitado(() -> servico.transicionar(alice, solicitada, EstadoReserva.EM_USO, null));
    }

    @Test
    @DisplayName("RN-05.5 — EM_USO → SOLICITADA (retrocesso): transição proibida")
    void rn05_5_emUsoParaSolicitadaEhProibida() {
        // Arrange
        Reserva emUso = reserva("R1", alice, sala, "10:00", "11:00", EstadoReserva.EM_USO);
        servico.registrarExistente(emUso);
        // Act & Assert
        assertRejeitado(() -> servico.transicionar(alice, emUso, EstadoReserva.SOLICITADA, null));
    }

    @Test
    @DisplayName("RN-05.6 — CONCLUÍDA → SOLICITADA (revogação): transição proibida")
    void rn05_6_concluidaParaSolicitadaEhProibida() {
        // Arrange
        Reserva concluida = reserva("R1", alice, sala, "10:00", "11:00", EstadoReserva.CONCLUIDA);
        servico.registrarExistente(concluida);
        // Act & Assert
        assertRejeitado(() -> servico.transicionar(alice, concluida, EstadoReserva.SOLICITADA, null));
    }

    @Test
    @DisplayName("RN-05.7 — CONCLUÍDA → CONCLUÍDA (idempotência): ignorar ou erro?")
    void rn05_7_idempotenciaConcluida() {
        // Arrange
        Reserva concluida = reserva("R1", alice, sala, "10:00", "11:00", EstadoReserva.CONCLUIDA);
        servico.registrarExistente(concluida);
        // Act & Assert
        assertRejeitado(() -> servico.transicionar(alice, concluida, EstadoReserva.CONCLUIDA, null));
    }
}
