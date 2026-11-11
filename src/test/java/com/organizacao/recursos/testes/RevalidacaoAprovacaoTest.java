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

/** Questão Q5 do BDD (docs/testes/bdd-aprovacao-bloqueio.md) — revalidação de manutenção na aprovação (RTM, fluxo de aprovação). */
class RevalidacaoAprovacaoTest {

    private final Clock relogio = Clock.fixed(Instant.parse("2026-11-11T08:00:00Z"), ZoneId.of("UTC"));
    private final ServicoReservas servico = new ServicoReservas(relogio);
    private final Usuario alice = solicitante("alice");
    private final Usuario resp = responsavel("resp");
    private final Usuario admin = administrador("adm");
    private final Recurso sala01 = sala("SALA-01", false);

    @Test
    @DisplayName("Manutenção criada após a solicitação sobrepõe a reserva: aprovação recusada com 409 e estado continua SOLICITADA")
    void aprovacaoRecusadaQuandoManutencaoPosteriorSobrepoeReserva() {
        // Arrange
        Reserva solicitada = servico.solicitar(alice, sala01, t("10:00"), t("11:00"));
        servico.criarManutencao(admin, new Manutencao("SALA-01", t("10:30"), t("11:30")));
        // Act & Assert
        assertErro(409, () -> servico.aprovar(resp, solicitada));
        assertEquals(EstadoReserva.SOLICITADA, servico.reservasDoRecurso("SALA-01").get(0).getEstado());
    }
}
