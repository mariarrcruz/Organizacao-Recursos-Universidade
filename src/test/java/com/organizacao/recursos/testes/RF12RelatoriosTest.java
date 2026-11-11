package com.organizacao.recursos.testes;

import static com.organizacao.recursos.testes.Fixtures.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.organizacao.recursos.dominio.*;
import com.organizacao.recursos.servico.ServicoMovimentacoes;
import com.organizacao.recursos.servico.ServicoRelatorios;
import com.organizacao.recursos.servico.ServicoReservas;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/** Requisito RF-12 — Relatórios gerenciais (RFC RF-12; plano § 1.7). */
class RF12RelatoriosTest {

    private final ServicoReservas reservas = new ServicoReservas();
    private final ServicoMovimentacoes movimentacoes = new ServicoMovimentacoes(reservas);
    private final ServicoRelatorios relatorios = new ServicoRelatorios(reservas, movimentacoes);
    private final Usuario admin = administrador("adm");
    private final Usuario resp = responsavel("resp");
    private final Usuario alice = solicitante("alice");
    private final Recurso sala = sala("S1", false);

    @Test
    @DisplayName("RF-12.1 — Utilização por recurso [data_início, data_fim]: total de horas reservadas")
    void rf12_1_utilizacaoPorRecursoSomaHoras() {
        // Arrange
        reservas.registrarExistente(reserva("R1", alice, sala, "10:00", "11:00", EstadoReserva.APROVADA));
        reservas.registrarExistente(reserva("R2", alice, sala, "14:00", "15:00", EstadoReserva.CONCLUIDA));
        // Act
        Map<String, Duration> utilizacao = relatorios.utilizacaoPorRecurso(admin, DIA, DIA);
        // Assert
        assertEquals(Duration.ofHours(2), utilizacao.get("S1"));
    }

    @Test
    @DisplayName("RF-12.2 — Carga horária por sala [período, sala_id]: soma das horas alocadas")
    void rf12_2_cargaHorariaPorSala() {
        // Arrange
        reservas.registrarExistente(reserva("R1", alice, sala, "10:00", "11:00", EstadoReserva.APROVADA));
        reservas.registrarExistente(reserva("R2", alice, sala, "14:00", "15:00", EstadoReserva.CONCLUIDA));
        // Act
        Duration carga = relatorios.cargaHorariaSala(admin, "S1", DIA, DIA);
        // Assert
        assertEquals(Duration.ofHours(2), carga);
    }

    @Test
    @DisplayName("RF-12.3 — Reservas por estado [APROVADA, EM_USO, CONCLUÍDA]: contagens por estado")
    void rf12_3_contagemPorEstado() {
        // Arrange
        reservas.registrarExistente(reserva("R1", alice, sala, "10:00", "11:00", EstadoReserva.APROVADA));
        reservas.registrarExistente(reserva("R2", alice, sala, "14:00", "15:00", EstadoReserva.CONCLUIDA));
        // Act
        Map<EstadoReserva, Long> contagem = relatorios.contarPorEstado(admin,
                Set.of(EstadoReserva.APROVADA, EstadoReserva.EM_USO, EstadoReserva.CONCLUIDA));
        // Assert
        assertEquals(1L, contagem.get(EstadoReserva.APROVADA));
        assertEquals(0L, contagem.get(EstadoReserva.EM_USO));
        assertEquals(1L, contagem.get(EstadoReserva.CONCLUIDA));
    }

    @Test
    @DisplayName("RF-12.4 — Histórico de retiradas/devoluções [material_id, período]: lista de movimentações")
    void rf12_4_historicoDeMovimentacoes() {
        // Arrange
        Reserva r = reserva("R1", alice, sala, "10:00", "11:00", EstadoReserva.APROVADA);
        reservas.registrarExistente(r);
        movimentacoes.registrarRetirada(resp, r, "M1", "OK");
        // Act
        var lista = relatorios.historicoMovimentacoes(admin, "M1", DIA, DIA);
        // Assert
        assertEquals(1, lista.size());
        assertEquals(TipoMovimentacao.RETIRADA, lista.get(0).tipo());
    }

    @Test
    @DisplayName("RF-12.5 — Período sem dados: retorna estrutura vazia")
    void rf12_5_periodoSemDadosRetornaVazio() {
        // Arrange
        reservas.registrarExistente(reserva("R1", alice, sala, "10:00", "11:00", EstadoReserva.APROVADA));
        // Act
        Map<String, Duration> utilizacao = relatorios.utilizacaoPorRecurso(admin, DIA.plusDays(10), DIA.plusDays(10));
        // Assert
        assertTrue(utilizacao.isEmpty());
    }

    @Test
    @DisplayName("RF-12.6 — Relatório gerencial por Solicitante: negado (403)")
    void rf12_6_solicitanteNaoConsultaRelatorioGerencial() {
        // Arrange
        reservas.registrarExistente(reserva("R1", alice, sala, "10:00", "11:00", EstadoReserva.APROVADA));
        // Act & Assert
        assertErro(403, () -> relatorios.utilizacaoPorRecurso(alice, DIA, DIA));
    }

    @Test
    @DisplayName("RF-12.7 — Id inexistente (sala_id = PROF-1) em relatório de sala: 404 (decisão da pendência 2)")
    void rf12_7_filtroIncompativelComRelatorio() {
        // Arrange
        // Act & Assert
        assertErro(404, () -> relatorios.cargaHorariaSala(admin, "PROF-1", DIA, DIA));
    }

    @Test
    @DisplayName("RF-12.8 — Volume alto (1000 reservas): serviço em memória responde em menos de 5 s (RNF-05; desempenho na aplicação é medido em PEN-07)")
    void rf12_8_relatorioComVolumeAltoRespondeEmMenosDeCincoSegundos() {
        // Arrange
        for (int i = 0; i < 1000; i++) {
            LocalDateTime inicio = DIA.atStartOfDay().plusMinutes(i * 2L);
            reservas.registrarExistente(new Reserva("R" + i, alice, sala,
                    inicio, inicio.plusMinutes(1), EstadoReserva.APROVADA));
        }
        long inicioMedicao = System.nanoTime();
        // Act
        relatorios.utilizacaoPorRecurso(admin, DIA, DIA);
        long decorrido = System.nanoTime() - inicioMedicao;
        // Assert
        assertTrue(Duration.ofNanos(decorrido).compareTo(Duration.ofSeconds(5)) < 0,
                "Relatório levou " + Duration.ofNanos(decorrido).toMillis() + " ms");
    }
}
