package com.organizacao.recursos.testes;

import static com.organizacao.recursos.testes.Fixtures.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.organizacao.recursos.dominio.*;
import com.organizacao.recursos.servico.ServicoReservas;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Regra RN-03 — Atomicidade sob concorrência (PRD § 4.1; RFC RF-07; RISK-01). */
class RN03AtomicidadeConcorrenciaTest {

    private final ServicoReservas servico = new ServicoReservas();
    private final Usuario alice = solicitante("alice");
    private final Recurso r1 = sala("R1", false);

    @Test
    @DisplayName("RN-03.1 — Sequencial: R1 aceito em [10,11); R2 aceito em [11,12)")
    void rn03_1_requisicoesSequenciaisSaoAceitas() {
        // Arrange
        // Act
        Reserva primeira = servico.solicitar(alice, r1, t("10:00"), t("11:00"));
        Reserva segunda = servico.solicitar(alice, r1, t("11:00"), t("12:00"));
        // Assert
        assertNotNull(primeira);
        assertNotNull(segunda);
        assertEquals(2, servico.reservasDoRecurso("R1").size());
    }

    @Test
    @DisplayName("RN-03.2 — Simultâneas idênticas [10,11): uma aceita, outra 409; estado final com 1 reserva")
    void rn03_2_simultaneasIdenticasUmaAceitaOutraRejeitada() throws Exception {
        // Arrange
        // Act
        List<Object> resultados = simultaneas(2, i -> servico.solicitar(alice, r1, t("10:00"), t("11:00")));
        // Assert
        assertEquals(1, sucessos(resultados));
        assertEquals(1, erros(resultados, 409));
        assertEquals(1, servico.reservasDoRecurso("R1").size());
    }

    @Test
    @DisplayName("RN-03.3 — Simultâneas em conflito parcial [10,11) x [10:30,11:30): uma aceita, outra 409")
    void rn03_3_simultaneasEmConflitoParcialUmaAceita() throws Exception {
        // Arrange
        // Act
        List<Object> resultados = simultaneas(2, i -> i == 0
                ? servico.solicitar(alice, r1, t("10:00"), t("11:00"))
                : servico.solicitar(alice, r1, t("10:30"), t("11:30")));
        // Assert
        assertEquals(1, sucessos(resultados));
        assertEquals(1, erros(resultados, 409));
        assertEquals(1, servico.reservasDoRecurso("R1").size());
    }

    @Test
    @DisplayName("RN-03.4 — Stress: 10 requisições simultâneas em [10,11): 1 aceita, 9 rejeitadas (409)")
    void rn03_4_stressDezRequisicoesUmaAceita() throws Exception {
        // Arrange
        // Act
        List<Object> resultados = simultaneas(10, i -> servico.solicitar(alice, r1, t("10:00"), t("11:00")));
        // Assert
        assertEquals(1, sucessos(resultados));
        assertEquals(9, erros(resultados, 409));
        assertEquals(1, servico.reservasDoRecurso("R1").size());
    }

    @Test
    @DisplayName("RN-03.5 — Isolamento transacional: Req2 concorrente é rejeitada com 409 após Req1 persistir")
    void rn03_5_isolamentoTransacionalRejeitaPerdedor() throws Exception {
        // Arrange
        // Act
        List<Object> resultados = simultaneas(2, i -> servico.solicitar(alice, r1, t("10:00"), t("11:00")));
        // Assert
        assertEquals(1, sucessos(resultados));
        assertEquals(1, erros(resultados, 409));
        assertEquals(1, servico.reservasDoRecurso("R1").size());
    }
}
