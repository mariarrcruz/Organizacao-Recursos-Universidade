package com.organizacao.recursos.testes;

import static com.organizacao.recursos.testes.Fixtures.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.organizacao.recursos.dominio.*;
import com.organizacao.recursos.servico.ServicoMovimentacoes;
import com.organizacao.recursos.servico.ServicoRelatorios;
import com.organizacao.recursos.servico.ServicoReservas;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Lacunas de risco de ServicoRelatorios (RF-12): quais estados contam como utilização e fronteira de período. */
class CoberturaRiscoRelatoriosTest {

    private final ServicoReservas reservas = new ServicoReservas();
    private final ServicoRelatorios relatorios =
            new ServicoRelatorios(reservas, new ServicoMovimentacoes(reservas));
    private final Usuario resp = responsavel("resp");
    private final Usuario alice = solicitante("alice");
    private final Recurso sala = sala("S1", false);

    @ParameterizedTest(name = "reserva em {0} → conta na utilização = {1}")
    @CsvSource({
            "SOLICITADA,false",
            "APROVADA,true",
            "EM_USO,true",
            "CONCLUIDA,true",
            "CANCELADA,false",
            "REJEITADA,false",
            "NAO_COMPARECEU,false"
    })
    @DisplayName("Só APROVADA, EM_USO e CONCLUIDA contam como utilização do recurso (RF-12)")
    void apenasEstadosUtilizadosContamNaUtilizacao(EstadoReserva estado, boolean conta) {
        reservas.registrarExistente(reserva("R1", alice, sala, "10:00", "11:00", estado));
        Map<String, Duration> mapa = relatorios.utilizacaoPorRecurso(resp, DIA, DIA);
        assertEquals(conta ? Duration.ofHours(1) : null, mapa.get("S1"));
    }

    @ParameterizedTest(name = "reserva {0} dia(s) em relação ao período → contada = {1}")
    @CsvSource({"-1,false", "0,true", "1,false"})
    @DisplayName("Utilização só considera reservas com início dentro de [início, fim] (trinca -1, 0, +1 dia)")
    void utilizacaoFronteiraDeDia(long deslocamento, boolean conta) {
        Reserva deslocada = new Reserva("R2", alice, sala,
                DIA.plusDays(deslocamento).atTime(10, 0), DIA.plusDays(deslocamento).atTime(11, 0),
                EstadoReserva.APROVADA);
        ServicoReservas outras = new ServicoReservas();
        outras.registrarExistente(deslocada);
        ServicoRelatorios rel = new ServicoRelatorios(outras, new ServicoMovimentacoes(outras));
        assertEquals(conta ? Duration.ofHours(1) : null, rel.utilizacaoPorRecurso(resp, DIA, DIA).get("S1"));
    }
}
