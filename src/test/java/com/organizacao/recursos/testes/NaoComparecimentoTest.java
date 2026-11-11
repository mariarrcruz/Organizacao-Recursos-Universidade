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

/** Questão Q8 do BDD — não comparecimento só pode ser registrado depois do horário de início da reserva. */
class NaoComparecimentoTest {

    private final Clock antesDoInicio = Clock.fixed(Instant.parse("2026-11-05T09:00:00Z"), ZoneId.of("UTC"));

    @Test
    @DisplayName("Não comparecimento registrado antes do horário de início da reserva aprovada: recusado com 409 e estado continua APROVADA")
    void naoComparecimentoAntesDoInicioEhRecusado() {
        // Arrange
        ServicoReservas servico = new ServicoReservas(antesDoInicio);
        Usuario resp = responsavel("resp");
        Reserva aprovada = servico.solicitar(solicitante("alice"), sala("SALA-06", false), t("10:00"), t("11:00"));
        // Act & Assert
        assertErro(409, () -> servico.registrarNaoComparecimento(resp, aprovada));
        assertEquals(EstadoReserva.APROVADA, servico.reservasDoRecurso("SALA-06").get(0).getEstado());
    }
}
