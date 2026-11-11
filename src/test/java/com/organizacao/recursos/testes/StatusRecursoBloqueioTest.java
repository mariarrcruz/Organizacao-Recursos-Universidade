package com.organizacao.recursos.testes;

import static com.organizacao.recursos.testes.Fixtures.*;

import com.organizacao.recursos.dominio.*;
import com.organizacao.recursos.servico.ServicoReservas;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Questão Q6 do BDD — status do recurso (MANUTENCAO/INDISPONIVEL) também bloqueia a alteração de reserva. */
class StatusRecursoBloqueioTest {

    private final Clock relogio = Clock.fixed(Instant.parse("2026-11-11T08:00:00Z"), ZoneId.of("UTC"));
    private final ServicoReservas servico = new ServicoReservas(relogio);
    private final Usuario alice = solicitante("alice");

    @Test
    @DisplayName("Reserva solicitada em recurso com status MANUTENCAO não pode ser alterada: recusada com 409")
    void alteracaoRecusadaQuandoRecursoEstaEmManutencao() {
        // Arrange
        Recurso lab03 = recurso("LAB-03", TipoRecurso.SALA, false, StatusRecurso.MANUTENCAO, java.util.Set.of());
        Reserva existente = reserva("R-LAB", alice, lab03, "10:00", "11:00", EstadoReserva.SOLICITADA);
        servico.registrarExistente(existente);
        // Act & Assert
        assertErro(409, () -> servico.alterar(alice, existente, t("12:00"), t("13:00")));
    }
}
