package com.organizacao.recursos.testes;

import org.junit.jupiter.api.BeforeEach;
import static com.organizacao.recursos.testes.Fixtures.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.organizacao.recursos.dominio.*;
import com.organizacao.recursos.servico.ServicoReservas;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/** Requisito RF-16 — Reserva do próprio usuário (RFC RF-16; plano § 1.3). */
class RF16PropriedadeReservaTest {

    private final ServicoReservas servico = new ServicoReservas();
    private final Usuario alice = solicitante("alice");
    private final Usuario bob = solicitante("bob");
    private final Usuario resp = responsavel("resp");
    private final Recurso sala = sala("S1", false);

    @BeforeEach
    void vincularResponsavelDeTeste() {
        servico.vincularResponsavel(administrador("adm"), "resp", "S1");
    }

    @Test
    @DisplayName("RF-16.1 — Alice altera reserva criada por Alice (PATCH): permitido (propriedade)")
    void rf16_1_alterarPropriaReserva() {
        // Arrange
        Reserva propria = reserva("R-ALICE", alice, sala, "10:00", "11:00", EstadoReserva.SOLICITADA);
        servico.registrarExistente(propria);
        // Act
        Reserva alterada = servico.alterar(alice, propria, t("14:00"), t("15:00"));
        // Assert
        assertEquals(t("14:00"), alterada.getInicio());
    }

    @Test
    @DisplayName("RF-16.2 — Alice altera reserva criada por Bob (PATCH): negado (403)")
    void rf16_2_alterarReservaAlheiaEhNegado() {
        // Arrange
        Reserva deBob = reserva("R-BOB", bob, sala, "10:00", "11:00", EstadoReserva.SOLICITADA);
        servico.registrarExistente(deBob);
        // Act & Assert
        assertErro(403, () -> servico.alterar(alice, deBob, t("14:00"), t("15:00")));
    }

    @Test
    @DisplayName("RF-16.3 — Alice exclui reserva criada por Alice (SOLICITADA): depende de estado")
    void rf16_3_excluirPropriaReservaDependeDeEstado() {
        // Arrange
        Reserva propria = reserva("R-ALICE", alice, sala, "10:00", "11:00", EstadoReserva.SOLICITADA);
        servico.registrarExistente(propria);
        // Act & Assert
        assertDoesNotThrow(() -> servico.excluir(alice, propria));
    }

    @Test
    @DisplayName("RF-16.4 — Alice exclui própria reserva EM_USO: rejeitado (RN-08)")
    void rf16_4_excluirPropriaReservaEmUsoEhRejeitado() {
        // Arrange
        Reserva emUso = reserva("R-ALICE", alice, sala, "10:00", "11:00", EstadoReserva.EM_USO);
        servico.registrarExistente(emUso);
        // Act & Assert
        assertRejeitado(() -> servico.excluir(alice, emUso));
    }

    @Test
    @DisplayName("RF-16.5 — Alice muda própria reserva para CANCELADA: permitido (transição por proprietário)")
    void rf16_5_proprietarioCancelaPropriaReserva() {
        // Arrange
        Reserva propria = reserva("R-ALICE", alice, sala, "10:00", "11:00", EstadoReserva.SOLICITADA);
        servico.registrarExistente(propria);
        // Act
        Reserva cancelada = servico.cancelar(alice, propria, "Desistência");
        // Assert
        assertEquals(EstadoReserva.CANCELADA, cancelada.getEstado());
    }

    @Test
    @DisplayName("RF-16.6 — Responsável altera aprovação da reserva de Alice: permitido (permissão por perfil)")
    void rf16_6_responsavelAlteraReservaDeOutroUsuario() {
        // Arrange
        Reserva deAlice = reserva("R-ALICE", alice, sala, "10:00", "11:00", EstadoReserva.SOLICITADA);
        servico.registrarExistente(deAlice);
        // Act
        Reserva aprovada = servico.aprovar(resp, deAlice);
        // Assert
        assertEquals(EstadoReserva.APROVADA, aprovada.getEstado());
    }

    @Test
    @DisplayName("RF-16.7 — Alice (Solicitante) consulta auditoria da própria reserva: 403 (histórico só para Responsável e Administrador)")
    void rf16_7_proprietarioNaoConsultaAuditoriaDaPropriaReserva() {
        // Arrange
        Reserva propria = reserva("R-ALICE", alice, sala, "10:00", "11:00", EstadoReserva.SOLICITADA);
        servico.registrarExistente(propria);
        // Act & Assert
        assertErro(403, () -> servico.eventosDe(alice, "R-ALICE"));
    }
}
