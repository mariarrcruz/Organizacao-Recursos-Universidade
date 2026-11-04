package com.organizacao.recursos.testes;

import static com.organizacao.recursos.testes.Fixtures.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.organizacao.recursos.dominio.*;
import com.organizacao.recursos.servico.ServicoReservas;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/** Regra RN-07 — Alçada de aprovação: só Responsável aprova recursos restritos (PRD § 4.2; RFC RF-08, RF-17). */
class RN07AlcadaAprovacaoTest {

    private final ServicoReservas servico = new ServicoReservas();
    private final Usuario alice = solicitante("alice");
    private final Usuario resp = responsavel("resp");
    private final Usuario admin = administrador("adm");
    private final Recurso restrito = sala("S-RESTRITA", true);
    private final Recurso livre = sala("S-LIVRE", false);

    @Test
    @DisplayName("RN-07.1 — Restrito=true + Responsável: aprovação permitida")
    void rn07_1_responsavelAprovaRecursoRestrito() {
        // Arrange
        Reserva solicitada = reserva("R1", alice, restrito, "10:00", "11:00", EstadoReserva.SOLICITADA);
        servico.registrarExistente(solicitada);
        // Act
        Reserva aprovada = servico.aprovar(resp, solicitada);
        // Assert
        assertEquals(EstadoReserva.APROVADA, aprovada.getEstado());
    }

    @Test
    @DisplayName("RN-07.2 — Restrito=true + Solicitante: negado (403)")
    void rn07_2_solicitanteNaoAprovaRecursoRestrito() {
        // Arrange
        Reserva solicitada = reserva("R1", alice, restrito, "10:00", "11:00", EstadoReserva.SOLICITADA);
        servico.registrarExistente(solicitada);
        // Act & Assert
        assertErro(403, () -> servico.aprovar(alice, solicitada));
    }

    @Test
    @Disabled("Esclarecer: Administrador pode aprovar recurso restrito? Escopo (AMB-02). Plano RN-07.3 ⚠️.")
    @DisplayName("RN-07.3 — Restrito=true + Administrador: esclarecer escopo")
    void rn07_3_administradorAprovaRecursoRestrito() {
        // Arrange
        Reserva solicitada = reserva("R1", alice, restrito, "10:00", "11:00", EstadoReserva.SOLICITADA);
        servico.registrarExistente(solicitada);
        // Act & Assert
        assertErro(403, () -> servico.aprovar(admin, solicitada));
    }

    @Test
    @Disabled("Valor-limite marcado com '?' no plano RN-07.4; confirmar se Responsável aprova recurso não restrito.")
    @DisplayName("RN-07.4 — Restrito=false + Responsável: permitido? (confirmar)")
    void rn07_4_responsavelAprovaRecursoLivre() {
        // Arrange
        Reserva solicitada = reserva("R1", alice, livre, "10:00", "11:00", EstadoReserva.SOLICITADA);
        servico.registrarExistente(solicitada);
        // Act
        Reserva aprovada = servico.aprovar(resp, solicitada);
        // Assert
        assertEquals(EstadoReserva.APROVADA, aprovada.getEstado());
    }

    @Test
    @DisplayName("RN-07.5 — Restrito=false + Solicitante: negado")
    void rn07_5_solicitanteNaoAprovaRecursoLivre() {
        // Arrange
        Reserva solicitada = reserva("R1", alice, livre, "10:00", "11:00", EstadoReserva.SOLICITADA);
        servico.registrarExistente(solicitada);
        // Act & Assert
        assertErro(403, () -> servico.aprovar(alice, solicitada));
    }

    @Test
    @DisplayName("RN-07.6 — Restrito=true + Usuário anônimo: negado (401)")
    void rn07_6_anonimoNaoAprovaRecursoRestrito() {
        // Arrange
        Reserva solicitada = reserva("R1", alice, restrito, "10:00", "11:00", EstadoReserva.SOLICITADA);
        servico.registrarExistente(solicitada);
        // Act & Assert
        assertErro(401, () -> servico.aprovar(null, solicitada));
    }
}
