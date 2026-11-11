package com.organizacao.recursos.testes;

import static com.organizacao.recursos.testes.Fixtures.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.organizacao.recursos.dominio.*;
import com.organizacao.recursos.servico.ServicoReservas;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Regra RN-09 — Rastreabilidade obrigatória: toda mudança de estado gera auditoria imutável (PRD § 4.2; RFC RF-11; RNF-12). */
class RN09RastreabilidadeTest {

    /** Relógio fixo às 09:00 do dia das fixtures: o cancelamento só é permitido antes do início (10:00). */
    private final ServicoReservas servico = new ServicoReservas(
            java.time.Clock.fixed(t("09:00").toInstant(java.time.ZoneOffset.UTC), java.time.ZoneOffset.UTC));
    private final Usuario alice = solicitante("alice");
    private final Usuario resp = responsavel("resp");
    private final Usuario admin = administrador("adm");
    private final Recurso sala = sala("S1", true);

    @Test
    @DisplayName("RN-09.1 — Criar reserva (SOLICITADA): registro de auditoria criado")
    void rn09_1_criacaoGeraAuditoria() {
        // Arrange
        Reserva criada = servico.solicitar(alice, sala, t("10:00"), t("11:00"));
        // Act
        List<EventoAuditoria> eventos = servico.eventosDe(resp, criada.getId());
        // Assert
        assertEquals(1, eventos.size());
        assertEquals(EstadoReserva.SOLICITADA, eventos.get(0).estadoNovo());
    }

    @Test
    @DisplayName("RN-09.2 — Transição para APROVADA: registro de auditoria criado")
    void rn09_2_aprovacaoGeraAuditoria() {
        // Arrange
        Reserva solicitada = reserva("R1", alice, sala, "10:00", "11:00", EstadoReserva.SOLICITADA);
        servico.registrarExistente(solicitada);
        servico.aprovar(resp, solicitada);
        // Act
        List<EventoAuditoria> eventos = servico.eventosDe(resp, "R1");
        // Assert
        assertEquals(EstadoReserva.APROVADA, eventos.get(eventos.size() - 1).estadoNovo());
    }

    @Test
    @DisplayName("RN-09.3 — Transição para EM_USO: registro de auditoria criado")
    void rn09_3_inicioUsoGeraAuditoria() {
        // Arrange — uso só começa a partir de 10:00 (Q7); relógio local às 10:30
        ServicoReservas servico = new ServicoReservas(
                java.time.Clock.fixed(t("10:30").toInstant(java.time.ZoneOffset.UTC), java.time.ZoneOffset.UTC));
        Reserva aprovada = reserva("R1", alice, sala, "10:00", "11:00", EstadoReserva.APROVADA);
        servico.registrarExistente(aprovada);
        servico.iniciarUso(alice, aprovada);
        // Act
        List<EventoAuditoria> eventos = servico.eventosDe(resp, "R1");
        // Assert
        assertEquals(EstadoReserva.EM_USO, eventos.get(eventos.size() - 1).estadoNovo());
    }

    @Test
    @DisplayName("RN-09.4 — Transição para CONCLUÍDA: registro de auditoria criado")
    void rn09_4_conclusaoGeraAuditoria() {
        // Arrange
        Reserva emUso = reserva("R1", alice, sala, "10:00", "11:00", EstadoReserva.EM_USO);
        servico.registrarExistente(emUso);
        servico.concluir(alice, emUso);
        // Act
        List<EventoAuditoria> eventos = servico.eventosDe(resp, "R1");
        // Assert
        assertEquals(EstadoReserva.CONCLUIDA, eventos.get(eventos.size() - 1).estadoNovo());
    }

    @Test
    @DisplayName("RN-09.5 — Transição para REJEITADA: auditoria com justificativa")
    void rn09_5_rejeicaoGeraAuditoriaComJustificativa() {
        // Arrange
        Reserva solicitada = reserva("R1", alice, sala, "10:00", "11:00", EstadoReserva.SOLICITADA);
        servico.registrarExistente(solicitada);
        servico.rejeitar(resp, solicitada, "Conflito com evento institucional");
        // Act
        List<EventoAuditoria> eventos = servico.eventosDe(resp, "R1");
        // Assert
        EventoAuditoria ultimo = eventos.get(eventos.size() - 1);
        assertEquals(EstadoReserva.REJEITADA, ultimo.estadoNovo());
        assertEquals("Conflito com evento institucional", ultimo.justificativa());
    }

    @Test
    @DisplayName("RN-09.6 — Transição para CANCELADA: auditoria com justificativa")
    void rn09_6_cancelamentoGeraAuditoriaComJustificativa() {
        // Arrange
        Reserva aprovada = reserva("R1", alice, sala, "10:00", "11:00", EstadoReserva.APROVADA);
        servico.registrarExistente(aprovada);
        servico.cancelar(alice, aprovada, "Imprevisto pessoal");
        // Act
        List<EventoAuditoria> eventos = servico.eventosDe(resp, "R1");
        // Assert
        EventoAuditoria ultimo = eventos.get(eventos.size() - 1);
        assertEquals(EstadoReserva.CANCELADA, ultimo.estadoNovo());
        assertEquals("Imprevisto pessoal", ultimo.justificativa());
    }

    @Test
    @DisplayName("RN-09.7 — Registro contém usuário, timestamp, ação, estado anterior e estado novo")
    void rn09_7_registroAuditoriaEhCompleto() {
        // Arrange
        Reserva solicitada = reserva("R1", alice, sala, "10:00", "11:00", EstadoReserva.SOLICITADA);
        servico.registrarExistente(solicitada);
        servico.aprovar(resp, solicitada);
        // Act
        EventoAuditoria evento = servico.eventosDe(resp, "R1").get(0);
        // Assert
        assertNotNull(evento.atorId());
        assertNotNull(evento.instante());
        assertNotNull(evento.acao());
        assertEquals(EstadoReserva.SOLICITADA, evento.estadoAnterior());
        assertEquals(EstadoReserva.APROVADA, evento.estadoNovo());
    }

    @Test
    @DisplayName("RN-09.10 — Transição inválida bloqueada: nenhum AuditEvent é criado")
    void rn09_10_transicaoInvalidaNaoGeraAuditoria() {
        // Arrange
        Reserva solicitada = reserva("R1", alice, sala, "10:00", "11:00", EstadoReserva.SOLICITADA);
        servico.registrarExistente(solicitada);
        int antes = servico.eventosDe(resp, "R1").size();
        // Act
        assertRejeitado(() -> servico.transicionar(alice, solicitada, EstadoReserva.CONCLUIDA, null));
        // Assert
        assertEquals(antes, servico.eventosDe(resp, "R1").size());
    }

    @Test
    @DisplayName("RN-09.11 — Solicitante dono da reserva não consulta o histórico: 403")
    void rn09_11_solicitanteNaoConsultaHistorico() {
        // Arrange
        Reserva propria = reserva("R1", alice, sala, "10:00", "11:00", EstadoReserva.SOLICITADA);
        servico.registrarExistente(propria);
        // Act & Assert
        assertErro(403, () -> servico.eventosDe(alice, "R1"));
    }

    @Test
    @DisplayName("RN-09.12 — Responsável e Administrador consultam o histórico da reserva")
    void rn09_12_responsavelEAdministradorConsultamHistorico() {
        // Arrange
        Reserva solicitada = servico.solicitar(alice, sala, t("10:00"), t("11:00"));
        // Act & Assert
        assertEquals(1, servico.eventosDe(resp, solicitada.getId()).size());
        assertEquals(1, servico.eventosDe(admin, solicitada.getId()).size());
    }

    @Test
    @DisplayName("RN-09.13 — Instante do registro vem do Clock injetado")
    void rn09_13_instanteVemDoRelogio() {
        // Arrange — relógio fixo às 09:00
        Reserva solicitada = servico.solicitar(alice, sala, t("10:00"), t("11:00"));
        // Act
        EventoAuditoria evento = servico.eventosDe(resp, solicitada.getId()).get(0);
        // Assert
        assertEquals(t("09:00"), evento.instante());
    }

    @Test
    @DisplayName("RN-09.14 — Reserva já iniciada não pode ser apagada e a tentativa não gera registro")
    void rn09_14_reservaIniciadaNaoPodeSerApagada() {
        // Arrange — relógio às 10:30; a reserva começou às 10:00 e está APROVADA
        ServicoReservas tarde = new ServicoReservas(
                java.time.Clock.fixed(t("10:30").toInstant(java.time.ZoneOffset.UTC), java.time.ZoneOffset.UTC));
        Reserva iniciada = reserva("R1", alice, sala, "10:00", "11:00", EstadoReserva.APROVADA);
        tarde.registrarExistente(iniciada);
        int antes = tarde.eventosDe(resp, "R1").size();
        // Act & Assert
        assertErro(409, () -> tarde.excluir(alice, iniciada));
        assertEquals(antes, tarde.eventosDe(resp, "R1").size());
    }
}
