package com.organizacao.recursos.servico;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.organizacao.recursos.dominio.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Lacunas de risco de ServicoMovimentacoes (RF-10) e de Autorizacao. Pacote servico para acessar historicoPeriodo. */
class CoberturaRiscoMovimentacoesTest {

    private static final LocalDate DIA = LocalDate.of(2026, 11, 5);
    private static final Recurso MATERIAL_SALA = new Recurso("S1", TipoRecurso.SALA, "Sala 1", "BLOCO_A", 30,
            false, StatusRecurso.DISPONIVEL, Set.of());
    private final Usuario alice = new Usuario("alice", Set.of(Perfil.SOLICITANTE));
    private final Usuario resp = new Usuario("resp", Set.of(Perfil.RESPONSAVEL));

    private static Reserva reservaEm(String id, Usuario dono, EstadoReserva estado, LocalDateTime inicio) {
        return new Reserva(id, dono, MATERIAL_SALA, inicio, inicio.plusHours(1), estado);
    }

    private static void assertErro(int status, org.junit.jupiter.api.function.Executable acao) {
        ErroAplicacao e = assertThrows(ErroAplicacao.class, acao);
        assertEquals(status, e.getStatus());
    }

    @ParameterizedTest(name = "reserva {0} → retirada aceita = {1}")
    @CsvSource({"SOLICITADA,false", "REJEITADA,false", "CANCELADA,false", "CONCLUIDA,false",
            "APROVADA,true", "EM_USO,true"})
    @DisplayName("Retirada exige reserva APROVADA ou EM_USO (RF-10)")
    void retiradaExigeEstadoDaReserva(EstadoReserva estado, boolean aceita) {
        ServicoReservas reservas = new ServicoReservas();
        ServicoMovimentacoes mov = new ServicoMovimentacoes(reservas);
        Reserva r = reservaEm("R1", alice, estado, DIA.atTime(10, 0));
        reservas.registrarExistente(r);
        if (aceita) {
            assertDoesNotThrow(() -> mov.registrarRetirada(resp, r, "MAT1", "bom"));
        } else {
            assertErro(409, () -> mov.registrarRetirada(resp, r, "MAT1", "bom"));
        }
    }

    @Test
    @DisplayName("Retirada de reserva inexistente é 404")
    void retiradaReservaInexistente() {
        ServicoMovimentacoes mov = new ServicoMovimentacoes(new ServicoReservas());
        Reserva fantasma = reservaEm("RX", alice, EstadoReserva.APROVADA, DIA.atTime(10, 0));
        assertErro(404, () -> mov.registrarRetirada(resp, fantasma, "MAT1", "bom"));
    }

    @Test
    @DisplayName("Material já retirado não pode ser retirado de novo (409)")
    void materialJaRetirado() {
        ServicoReservas reservas = new ServicoReservas();
        ServicoMovimentacoes mov = new ServicoMovimentacoes(reservas);
        Reserva r1 = reservaEm("R1", alice, EstadoReserva.APROVADA, DIA.atTime(10, 0));
        Reserva r2 = reservaEm("R2", alice, EstadoReserva.APROVADA, DIA.atTime(12, 0));
        reservas.registrarExistente(r1);
        reservas.registrarExistente(r2);
        mov.registrarRetirada(resp, r1, "MAT1", "bom");
        assertErro(409, () -> mov.registrarRetirada(resp, r2, "MAT1", "bom"));
    }

    @Test
    @DisplayName("Devolução sem retirada aberta é 409; devolução por outra reserva é 409")
    void devolucaoExigeRetiradaAbertaNaMesmaReserva() {
        ServicoReservas reservas = new ServicoReservas();
        ServicoMovimentacoes mov = new ServicoMovimentacoes(reservas);
        Reserva r1 = reservaEm("R1", alice, EstadoReserva.APROVADA, DIA.atTime(10, 0));
        Reserva r2 = reservaEm("R2", alice, EstadoReserva.APROVADA, DIA.atTime(12, 0));
        reservas.registrarExistente(r1);
        reservas.registrarExistente(r2);

        assertErro(409, () -> mov.registrarDevolucao(resp, r1, "MAT1", "bom"));

        mov.registrarRetirada(resp, r1, "MAT1", "bom");
        assertErro(409, () -> mov.registrarDevolucao(resp, r2, "MAT1", "bom"));

        assertDoesNotThrow(() -> mov.registrarDevolucao(resp, r1, "MAT1", "bom"));
        assertEquals(2, mov.historico("MAT1").size());
    }

    @ParameterizedTest(name = "reserva em {0} dia(s) do fim do período → incluída = {1}")
    @CsvSource({"-1,0", "0,1", "1,0"})
    @DisplayName("Histórico por período inclui a reserva apenas dentro de [início, fim] (trinca -1, 0, +1 dia)")
    void historicoPeriodoFronteiraDeDia(long deslocamentoDias, int esperado) {
        ServicoReservas reservas = new ServicoReservas();
        ServicoMovimentacoes mov = new ServicoMovimentacoes(reservas);
        Reserva r = reservaEm("R1", alice, EstadoReserva.APROVADA, DIA.plusDays(deslocamentoDias).atTime(10, 0));
        reservas.registrarExistente(r);
        mov.registrarRetirada(resp, r, "MAT1", "bom");
        List<Movimentacao> periodo = mov.historicoPeriodo("MAT1", DIA, DIA);
        assertEquals(esperado, periodo.size());
    }
}
