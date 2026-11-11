package com.organizacao.recursos.testes;

import static com.organizacao.recursos.testes.Fixtures.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.organizacao.recursos.dominio.*;
import com.organizacao.recursos.servico.ServicoReservas;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Decisão Q1 (PRD RF-07, RN-07): aprovação é exigida só para recurso restrito; recurso livre entra já APROVADA. */
class RecursoLivreAprovacaoTest {

    private final Clock relogio = Clock.fixed(Instant.parse("2026-11-11T08:00:00Z"), ZoneId.of("UTC"));
    private final ServicoReservas servico = new ServicoReservas(relogio);
    private final Usuario alice = solicitante("alice");

    @Test
    @DisplayName("Solicitação de recurso livre (não restrito): reserva nasce APROVADA, sem aprovação de Responsável")
    void reservaDeRecursoLivreNasceAprovada() {
        // Arrange
        Recurso livre = sala("SALA-03", false);
        // Act
        Reserva reserva = servico.solicitar(alice, livre, t("10:00"), t("11:00"));
        // Assert
        assertEquals(EstadoReserva.APROVADA, reserva.getEstado());
    }
}
