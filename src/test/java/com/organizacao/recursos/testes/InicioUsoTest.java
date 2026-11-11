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

/** Questão Q7 do BDD — início de uso (EM_USO) só é permitido a partir do horário de início da reserva. */
class InicioUsoTest {

    private final Clock antesDoInicio = Clock.fixed(Instant.parse("2026-11-05T09:00:00Z"), ZoneId.of("UTC"));
    private final Usuario alice = solicitante("alice");

    @Test
    @DisplayName("Uso iniciado antes do horário de início da reserva aprovada: recusado com 409 e estado continua APROVADA")
    void inicioDeUsoAntesDoHorarioEhRecusado() {
        // Arrange
        ServicoReservas servico = new ServicoReservas(antesDoInicio);
        Reserva aprovada = servico.solicitar(alice, sala("SALA-05", false), t("10:00"), t("11:00"));
        // Act & Assert
        assertErro(409, () -> servico.iniciarUso(alice, aprovada));
        assertEquals(EstadoReserva.APROVADA, servico.reservasDoRecurso("SALA-05").get(0).getEstado());
    }
}
