package com.organizacao.recursos.testes;

import static com.organizacao.recursos.testes.Fixtures.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.organizacao.recursos.dominio.*;
import com.organizacao.recursos.servico.*;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Requisito RF-15 — Controle de acesso por perfil (RFC RF-15, RF-01; plano § 1.3). */
class RF15ControleAcessoPerfilTest {

    private final ServicoReservas reservas = new ServicoReservas();
    private final ServicoRecursos recursos = new ServicoRecursos(reservas);
    private final ServicoUsuarios usuarios = new ServicoUsuarios();
    private final ServicoAdministracao administracao = new ServicoAdministracao();
    private final ServicoMovimentacoes movimentacoes = new ServicoMovimentacoes(reservas);
    private final ServicoRelatorios relatorios = new ServicoRelatorios(reservas, movimentacoes);

    private final Usuario alice = solicitante("alice");
    private final Usuario bob = solicitante("bob");
    private final Usuario resp = responsavel("resp");
    private final Usuario admin = administrador("adm");
    private final Recurso sala = sala("S1", false);
    private final Recurso salaRestrita = sala("S-RESTRITA", true);

    @Nested
    @DisplayName("Solicitante")
    class Solicitante {

        @Test
        @DisplayName("RF-15.S.1 — Criar reserva própria: permitido")
        void rf15_s1_criarReservaPropria() {
            // Arrange
            // Act
            Reserva r = reservas.solicitar(alice, sala, t("10:00"), t("11:00"));
            // Assert
            assertEquals(EstadoReserva.SOLICITADA, r.getEstado());
        }

        @Test
        @DisplayName("RF-15.S.2 — Alterar reserva própria (SOLICITADA): permitido")
        void rf15_s2_alterarReservaPropria() {
            // Arrange
            Reserva propria = reserva("R-ALICE", alice, sala, "10:00", "11:00", EstadoReserva.SOLICITADA);
            reservas.registrarExistente(propria);
            // Act
            Reserva alterada = reservas.alterar(alice, propria, t("14:00"), t("15:00"));
            // Assert
            assertEquals(t("14:00"), alterada.getInicio());
        }

        @Test
        @DisplayName("RF-15.S.3 — Cancelar reserva própria: permitido")
        void rf15_s3_cancelarReservaPropria() {
            // Arrange
            Reserva propria = reserva("R-ALICE", alice, sala, "10:00", "11:00", EstadoReserva.SOLICITADA);
            reservas.registrarExistente(propria);
            // Act
            Reserva cancelada = reservas.cancelar(alice, propria, "Desistência");
            // Assert
            assertEquals(EstadoReserva.CANCELADA, cancelada.getEstado());
        }

        @Test
        @DisplayName("RF-15.S.4 — Consultar disponibilidade de recursos públicos: permitido")
        void rf15_s4_consultarDisponibilidade() {
            // Arrange
            recursos.cadastrarRecurso(admin, sala);
            // Act
            List<Recurso> resultado = recursos.pesquisar(alice, Map.of("tipo", "SALA"));
            // Assert
            assertTrue(resultado.stream().anyMatch(r -> r.id().equals("S1")));
        }

        @Test
        @DisplayName("RF-15.S.5 — Alterar reserva de Responsável: negado (403)")
        void rf15_s5_alterarReservaDeTerceiroEhNegado() {
            // Arrange
            Reserva doResponsavel = reserva("R-RESP", resp, sala, "10:00", "11:00", EstadoReserva.SOLICITADA);
            reservas.registrarExistente(doResponsavel);
            // Act & Assert
            assertErro(403, () -> reservas.alterar(alice, doResponsavel, t("14:00"), t("15:00")));
        }

        @Test
        @DisplayName("RF-15.S.6 — Aprovar qualquer solicitação: negado (403, fora de escopo)")
        void rf15_s6_aprovarEhNegadoParaSolicitante() {
            // Arrange
            Reserva solicitada = reserva("R-BOB", bob, sala, "10:00", "11:00", EstadoReserva.SOLICITADA);
            reservas.registrarExistente(solicitada);
            // Act & Assert
            assertErro(403, () -> reservas.aprovar(alice, solicitada));
        }

        @Test
        @DisplayName("RF-15.S.7 — Gerenciar usuários (sistema global): negado (403)")
        void rf15_s7_gerenciarUsuariosEhNegado() {
            // Arrange
            Usuario novo = solicitante("novo");
            // Act & Assert
            assertErro(403, () -> usuarios.cadastrarUsuario(alice, novo));
        }

        @Test
        @DisplayName("RF-15.S.8 — Ver relatórios gerenciais (dashboard): esclarecer")
        void rf15_s8_verRelatoriosGerenciais() {
            // Arrange
            // Act & Assert
            assertErro(403, () -> relatorios.utilizacaoPorRecurso(alice, DIA, DIA));
        }
    }

    @Nested
    @DisplayName("Responsável")
    class Responsavel {

        @Test
        @DisplayName("RF-15.R.1 — Consultar disponibilidade de recursos públicos: permitido")
        void rf15_r1_consultarDisponibilidade() {
            // Arrange
            recursos.cadastrarRecurso(admin, sala);
            // Act
            List<Recurso> resultado = recursos.pesquisar(resp, Map.of("tipo", "SALA"));
            // Assert
            assertTrue(resultado.stream().anyMatch(r -> r.id().equals("S1")));
        }

        @Test
        @DisplayName("RF-15.R.2 — Analisar solicitações sob responsabilidade: permitido")
        void rf15_r2_analisarSolicitacoes() {
            // Arrange
            Reserva pendente = reserva("R-PEND", alice, salaRestrita, "10:00", "11:00", EstadoReserva.SOLICITADA);
            reservas.registrarExistente(pendente);
            // Act
            List<Reserva> pendentes = reservas.pendentesDeAnalise(resp);
            // Assert
            assertNotNull(pendentes);
            assertTrue(pendentes.contains(pendente));
        }

        @Test
        @DisplayName("RF-15.R.3 — Aprovar solicitação especial (recurso restrito): permitido")
        void rf15_r3_aprovarRecursoRestrito() {
            // Arrange
            Reserva solicitada = reserva("R-R3", alice, salaRestrita, "10:00", "11:00", EstadoReserva.SOLICITADA);
            reservas.registrarExistente(solicitada);
            // Act
            Reserva aprovada = reservas.aprovar(resp, solicitada);
            // Assert
            assertEquals(EstadoReserva.APROVADA, aprovada.getEstado());
        }

        @Test
        @DisplayName("RF-15.R.4 — Rejeitar solicitação (recurso restrito): permitido")
        void rf15_r4_rejeitarRecursoRestrito() {
            // Arrange
            Reserva solicitada = reserva("R-R4", alice, salaRestrita, "10:00", "11:00", EstadoReserva.SOLICITADA);
            reservas.registrarExistente(solicitada);
            // Act
            Reserva rejeitada = reservas.rejeitar(resp, solicitada, "Sem justificativa pedagógica");
            // Assert
            assertEquals(EstadoReserva.REJEITADA, rejeitada.getEstado());
        }

        @Test
        @DisplayName("RF-15.R.5 — Registrar retirada de material aprovado: permitido")
        void rf15_r5_registrarRetirada() {
            // Arrange
            Reserva aprovada = reserva("R-R5", alice, sala, "10:00", "11:00", EstadoReserva.APROVADA);
            reservas.registrarExistente(aprovada);
            // Act
            Movimentacao mov = movimentacoes.registrarRetirada(resp, aprovada, "M1", "OK");
            // Assert
            assertEquals(TipoMovimentacao.RETIRADA, mov.tipo());
        }

        @Test
        @DisplayName("RF-15.R.6 — Registrar devolução de material em posse: permitido")
        void rf15_r6_registrarDevolucao() {
            // Arrange
            Reserva aprovada = reserva("R-R6", alice, sala, "10:00", "11:00", EstadoReserva.APROVADA);
            reservas.registrarExistente(aprovada);
            movimentacoes.registrarRetirada(resp, aprovada, "M1", "OK");
            // Act
            Movimentacao mov = movimentacoes.registrarDevolucao(resp, aprovada, "M1", "OK");
            // Assert
            assertEquals(TipoMovimentacao.DEVOLUCAO, mov.tipo());
        }

        @Test
        @DisplayName("RF-15.R.7 — Criar reserva própria: esclarecer escopo")
        void rf15_r7_criarReservaPropria() {
            // Arrange
            // Act
            Reserva r = reservas.solicitar(resp, sala, t("10:00"), t("11:00"));
            // Assert
            assertEquals(EstadoReserva.SOLICITADA, r.getEstado());
        }

        @Test
        @DisplayName("RF-15.R.8 — Gerenciar usuários (sistema global): negado (403)")
        void rf15_r8_gerenciarUsuariosEhNegado() {
            // Arrange
            Usuario novo = solicitante("novo");
            // Act & Assert
            assertErro(403, () -> usuarios.cadastrarUsuario(resp, novo));
        }

        @Test
        @DisplayName("RF-15.R.9 — Alterar bloqueios globais sem delegação: negado (403)")
        void rf15_r9_alterarBloqueioGlobalEhNegado() {
            // Arrange
            Manutencao bloqueio = new Manutencao("S1", t("12:00"), t("13:00"));
            // Act & Assert
            assertErro(403, () -> reservas.criarBloqueio(resp, bloqueio));
        }
    }

    @Nested
    @DisplayName("Administrador")
    class Administrador {

        @Test
        @DisplayName("RF-15.A.1 — Cadastrar recurso: permitido")
        void rf15_a1_cadastrarRecurso() {
            // Arrange
            // Act & Assert
            assertDoesNotThrow(() -> recursos.cadastrarRecurso(admin, sala));
        }

        @Test
        @DisplayName("RF-15.A.2 — Editar recurso: permitido")
        void rf15_a2_editarRecurso() {
            // Arrange
            recursos.cadastrarRecurso(admin, sala);
            // Act & Assert
            assertDoesNotThrow(() -> recursos.atualizarRecurso(admin, "S1", "Novo nome"));
        }

        @Test
        @DisplayName("RF-15.A.3 — Deletar recurso: permitido")
        void rf15_a3_deletarRecurso() {
            // Arrange
            recursos.cadastrarRecurso(admin, sala);
            // Act & Assert
            assertDoesNotThrow(() -> recursos.excluirRecurso(admin, "S1"));
        }

        @Test
        @DisplayName("RF-15.A.4 — Cadastrar usuário: permitido")
        void rf15_a4_cadastrarUsuario() {
            // Arrange
            Usuario novo = solicitante("novo");
            // Act & Assert
            assertDoesNotThrow(() -> usuarios.cadastrarUsuario(admin, novo));
        }

        @Test
        @DisplayName("RF-15.A.5 — Editar usuário: permitido")
        void rf15_a5_editarUsuario() {
            // Arrange
            // Act & Assert
            assertDoesNotThrow(() -> usuarios.editarUsuario(admin, alice));
        }

        @Test
        @DisplayName("RF-15.A.6 — Deletar usuário: permitido")
        void rf15_a6_deletarUsuario() {
            // Arrange
            // Act & Assert
            assertDoesNotThrow(() -> usuarios.excluirUsuario(admin, "alice"));
        }

        @Test
        @DisplayName("RF-15.A.7 — Criar bloqueios: permitido")
        void rf15_a7_criarBloqueio() {
            // Arrange
            Manutencao bloqueio = new Manutencao("S1", t("12:00"), t("13:00"));
            // Act & Assert
            assertDoesNotThrow(() -> reservas.criarBloqueio(admin, bloqueio));
        }

        @Test
        @DisplayName("RF-15.A.8 — Criar manutenção: permitido")
        void rf15_a8_criarManutencao() {
            // Arrange
            Manutencao manutencao = new Manutencao("S1", t("12:00"), t("13:00"));
            // Act & Assert
            assertDoesNotThrow(() -> reservas.criarManutencao(admin, manutencao));
        }

        @Test
        @DisplayName("RF-15.A.9 — Consultar auditoria: permitido")
        void rf15_a9_consultarAuditoria() {
            // Arrange
            Reserva r = reservas.solicitar(alice, sala, t("10:00"), t("11:00"));
            // Act & Assert
            assertDoesNotThrow(() -> reservas.eventosDe(admin, r.getId()));
        }

        @Test
        @DisplayName("RF-15.A.10 — Gerar relatórios: permitido")
        void rf15_a10_gerarRelatorios() {
            // Arrange
            // Act & Assert
            assertDoesNotThrow(() -> relatorios.utilizacaoPorRecurso(admin, DIA, DIA));
        }

        @Test
        @DisplayName("RF-15.A.11 — Alterar parâmetros do sistema: permitido")
        void rf15_a11_alterarParametros() {
            // Arrange
            // Act & Assert
            assertDoesNotThrow(() -> administracao.alterarParametro(admin, "chave", "valor"));
        }

        @Test
        @DisplayName("RF-15.A.12 — Bypass de aprovação em recurso restrito: negado ou escopo?")
        void rf15_a12_bypassDeAprovacao() {
            // Arrange
            Reserva solicitada = reserva("R-A12", alice, salaRestrita, "10:00", "11:00", EstadoReserva.SOLICITADA);
            reservas.registrarExistente(solicitada);
            // Act & Assert
            assertErro(403, () -> reservas.aprovar(admin, solicitada));
        }
    }
}
