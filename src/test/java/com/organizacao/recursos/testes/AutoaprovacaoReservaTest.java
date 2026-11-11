package com.organizacao.recursos.testes;

import static com.organizacao.recursos.testes.Fixtures.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.organizacao.recursos.dominio.*;
import com.organizacao.recursos.servico.ServicoReservas;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Funcionalidade 2 do BDD (docs/testes/bdd-aprovacao-bloqueio.md) — solicitante não aprova a própria reserva. */
class AutoaprovacaoReservaTest {

    private final Clock relogio = Clock.fixed(Instant.parse("2026-11-11T08:00:00Z"), ZoneId.of("UTC"));
    private final ServicoReservas servico = new ServicoReservas(relogio);

    @Test
    @DisplayName("Usuário com perfis SOLICITANTE e RESPONSAVEL tenta aprovar a própria reserva: recusado com 403 e estado continua SOLICITADA")
    void usuarioComDoisPerfisNaoAprovaPropriaReserva() {
        // Arrange
        Usuario carla = new Usuario("carla", Set.of(Perfil.SOLICITANTE, Perfil.RESPONSAVEL));
        Recurso sala02 = sala("SALA-02", true);
        Reserva solicitada = servico.solicitar(carla, sala02, t("10:00"), t("11:00"));
        // Act & Assert
        assertErro(403, () -> servico.aprovar(carla, solicitada));
        assertEquals(EstadoReserva.SOLICITADA, servico.reservasDoRecurso("SALA-02").get(0).getEstado());
    }
}
