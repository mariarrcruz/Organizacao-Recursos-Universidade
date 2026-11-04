package com.organizacao.recursos.testes;

import static com.organizacao.recursos.testes.Fixtures.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.organizacao.recursos.dominio.*;
import com.organizacao.recursos.servico.ServicoReservas;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/** Regra RN-08 — Imutabilidade de reservas em execução (PRD § 4.2; RFC RF-05). */
class RN08ImutabilidadeExecucaoTest {

    private final ServicoReservas servico = new ServicoReservas();
    private final Usuario alice = solicitante("alice");
    private final Recurso sala = sala("S1", false);

    @Test
    @DisplayName("RN-08.1 — SOLICITADA + DELETE pelo dono: permitido (decisão AMB-01, alinhada a RF-16.3)")
    void rn08_1_deleteEmSolicitada() {
        // Arrange
        Reserva solicitada = reserva("R1", alice, sala, "10:00", "11:00", EstadoReserva.SOLICITADA);
        servico.registrarExistente(solicitada);
        // Act & Assert
        assertDoesNotThrow(() -> servico.excluir(alice, solicitada));
    }

    @Test
    @DisplayName("RN-08.2 — APROVADA + DELETE: rejeitado (deletar após aprovação proibido?)")
    void rn08_2_deleteEmAprovada() {
        // Arrange
        Reserva aprovada = reserva("R1", alice, sala, "10:00", "11:00", EstadoReserva.APROVADA);
        servico.registrarExistente(aprovada);
        // Act & Assert
        assertRejeitado(() -> servico.excluir(alice, aprovada));
    }

    @Test
    @DisplayName("RN-08.3 — EM_USO + DELETE: rejeitado (crítico)")
    void rn08_3_deleteEmUsoEhRejeitado() {
        // Arrange
        Reserva emUso = reserva("R1", alice, sala, "10:00", "11:00", EstadoReserva.EM_USO);
        servico.registrarExistente(emUso);
        // Act & Assert
        assertRejeitado(() -> servico.excluir(alice, emUso));
    }

    @Test
    @DisplayName("RN-08.4 — CONCLUÍDA + DELETE: esclarecer")
    void rn08_4_deleteEmConcluida() {
        // Arrange
        Reserva concluida = reserva("R1", alice, sala, "10:00", "11:00", EstadoReserva.CONCLUIDA);
        servico.registrarExistente(concluida);
        // Act & Assert
        assertRejeitado(() -> servico.excluir(alice, concluida));
    }

    @Test
    @DisplayName("RN-08.5 — REJEITADA + DELETE: esclarecer")
    void rn08_5_deleteEmRejeitada() {
        // Arrange
        Reserva rejeitada = reserva("R1", alice, sala, "10:00", "11:00", EstadoReserva.REJEITADA);
        servico.registrarExistente(rejeitada);
        // Act & Assert
        assertRejeitado(() -> servico.excluir(alice, rejeitada));
    }

    @Test
    @DisplayName("RN-08.6 — EM_USO + PATCH/alterar: rejeitado (modificar durante uso)")
    void rn08_6_alterarEmUsoEhRejeitado() {
        // Arrange
        Reserva emUso = reserva("R1", alice, sala, "10:00", "11:00", EstadoReserva.EM_USO);
        servico.registrarExistente(emUso);
        // Act & Assert
        assertRejeitado(() -> servico.alterar(alice, emUso, t("12:00"), t("13:00")));
    }
}
