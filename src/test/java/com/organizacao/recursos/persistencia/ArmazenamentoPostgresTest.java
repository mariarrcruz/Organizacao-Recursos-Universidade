package com.organizacao.recursos.persistencia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.organizacao.recursos.dominio.ErroAplicacao;
import com.organizacao.recursos.dominio.EstadoReserva;
import com.organizacao.recursos.dominio.EventoAuditoria;
import com.organizacao.recursos.dominio.Manutencao;
import com.organizacao.recursos.dominio.Perfil;
import com.organizacao.recursos.dominio.Recurso;
import com.organizacao.recursos.dominio.Reserva;
import com.organizacao.recursos.dominio.StatusRecurso;
import com.organizacao.recursos.dominio.TipoRecurso;
import com.organizacao.recursos.dominio.Usuario;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Garantias do banco: ida e volta de reserva, manutenção recusada no banco e auditoria somente inserção (RN-09). */
@Testcontainers
class ArmazenamentoPostgresTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private ArmazenamentoPostgres armazenamento;
    private final Recurso sala = new Recurso("SALA-DB", TipoRecurso.SALA, "Sala DB", "BLOCO_B", 20, true,
            StatusRecurso.DISPONIVEL, Set.of("projetor", "quadro"));
    private final Usuario alice = new Usuario("alice", EnumSet.of(Perfil.SOLICITANTE));
    private final LocalDateTime inicio = LocalDateTime.of(2026, 11, 20, 10, 0);
    private final LocalDateTime fim = LocalDateTime.of(2026, 11, 20, 11, 0);

    @BeforeEach
    void esquemaLimpo() {
        armazenamento = new ArmazenamentoPostgres(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        armazenamento.criarEsquema();
        armazenamento.limpar();
        armazenamento.gravarRecurso(sala);
    }

    @Test
    @DisplayName("Reserva gravada volta do banco com recurso, solicitante, perfis e período")
    void reservaFazIdaEVolta() {
        Reserva r = new Reserva("R-1", alice, sala, inicio, fim, EstadoReserva.SOLICITADA);
        armazenamento.inserirReserva(r, null);

        Reserva lida = armazenamento.buscarReserva("R-1").orElseThrow();
        assertEquals(inicio, lida.getInicio());
        assertEquals(fim, lida.getFim());
        assertEquals(EstadoReserva.SOLICITADA, lida.getEstado());
        assertEquals("alice", lida.getSolicitante().id());
        assertEquals(EnumSet.of(Perfil.SOLICITANTE), lida.getSolicitante().perfis());
        assertEquals(Set.of("projetor", "quadro"), lida.getRecurso().competencias());
    }

    @Test
    @DisplayName("Manutenção sobreposta é recusada pelo banco, sem depender do serviço")
    void manutencaoSobrepostaEhRecusadaNoBanco() {
        armazenamento.inserirManutencao(new Manutencao("SALA-DB", LocalDateTime.of(2026, 11, 20, 10, 30),
                LocalDateTime.of(2026, 11, 20, 12, 0)));
        Reserva r = new Reserva("R-2", alice, sala, inicio, fim, EstadoReserva.SOLICITADA);

        ErroAplicacao erro = assertThrows(ErroAplicacao.class, () -> armazenamento.inserirReserva(r, null));
        assertEquals(409, erro.getStatus());
        assertEquals(0, armazenamento.listarReservas().size());
    }

    @Test
    @DisplayName("RN-09: evento de auditoria não pode ser alterado nem excluído no banco")
    void auditoriaEhSomenteInsercao() throws SQLException {
        Reserva r = new Reserva("R-3", alice, sala, inicio, fim, EstadoReserva.SOLICITADA);
        armazenamento.inserirReserva(r, new EventoAuditoria("R-3", "alice", inicio, "SOLICITAR", null,
                EstadoReserva.SOLICITADA, null));
        assertEquals(1, armazenamento.listarEventos().size());

        try (Connection c = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement st = c.createStatement()) {
            SQLException alteracao = assertThrows(SQLException.class,
                    () -> st.executeUpdate("UPDATE evento_auditoria SET acao = 'FORJADO'"));
            assertTrue(alteracao.getMessage().contains("append-only"), alteracao.getMessage());
            assertThrows(SQLException.class, () -> st.executeUpdate("DELETE FROM evento_auditoria"));
        }
        assertEquals("SOLICITAR", armazenamento.listarEventos().get(0).acao());
    }

    @Test
    @DisplayName("RN-09: mudança de estado e evento de auditoria são gravados na mesma transação")
    void mudancaDeEstadoEEventoSaoGravadosJuntos() {
        Reserva solicitada = new Reserva("R-4", alice, sala, inicio, fim, EstadoReserva.SOLICITADA);
        armazenamento.inserirReserva(solicitada, evento("R-4", "SOLICITAR", null, EstadoReserva.SOLICITADA, null));
        Reserva aprovada = new Reserva("R-4", alice, sala, inicio, fim, EstadoReserva.APROVADA);

        armazenamento.atualizarReserva(aprovada,
                evento("R-4", "TRANSICIONAR", EstadoReserva.SOLICITADA, EstadoReserva.APROVADA, null), false);

        assertEquals(EstadoReserva.APROVADA, armazenamento.buscarReserva("R-4").orElseThrow().getEstado());
        assertEquals(2, armazenamento.listarEventos().size());
        assertEquals(EstadoReserva.APROVADA, armazenamento.listarEventos().get(1).estadoNovo());
    }

    @Test
    @DisplayName("RN-09: se o evento falha, a mudança de estado é desfeita e não sobra auditoria órfã")
    void falhaNoEventoDesfazMudancaDeEstadoSemAuditoriaOrfa() {
        Reserva solicitada = new Reserva("R-5", alice, sala, inicio, fim, EstadoReserva.SOLICITADA);
        armazenamento.inserirReserva(solicitada, evento("R-5", "SOLICITAR", null, EstadoReserva.SOLICITADA, null));
        Reserva aprovada = new Reserva("R-5", alice, sala, inicio, fim, EstadoReserva.APROVADA);
        // Justificativa acima de varchar(500): o INSERT do evento falha depois do UPDATE da reserva.
        String justificativaInvalida = "x".repeat(501);

        assertThrows(IllegalStateException.class, () -> armazenamento.atualizarReserva(aprovada,
                evento("R-5", "TRANSICIONAR", EstadoReserva.SOLICITADA, EstadoReserva.APROVADA, justificativaInvalida),
                false));

        assertEquals(EstadoReserva.SOLICITADA, armazenamento.buscarReserva("R-5").orElseThrow().getEstado());
        List<EventoAuditoria> eventos = armazenamento.listarEventos();
        assertEquals(1, eventos.size());
        assertEquals("SOLICITAR", eventos.get(0).acao());
    }

    private static EventoAuditoria evento(String reservaId, String acao, EstadoReserva anterior,
                                          EstadoReserva novo, String justificativa) {
        return new EventoAuditoria(reservaId, "alice", LocalDateTime.of(2026, 11, 20, 9, 0), acao, anterior, novo,
                justificativa);
    }
}
