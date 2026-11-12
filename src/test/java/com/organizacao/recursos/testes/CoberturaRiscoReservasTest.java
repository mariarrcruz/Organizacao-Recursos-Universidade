package com.organizacao.recursos.testes;

import static com.organizacao.recursos.testes.Fixtures.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.organizacao.recursos.dominio.*;
import com.organizacao.recursos.servico.ServicoReservas;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Lacunas de risco de ServicoReservas (RN-02, RN-04, RN-05, RN-06, RN-09) apontadas pela cobertura JaCoCo. */
class CoberturaRiscoReservasTest {

    private static final ZoneId ZONA = ZoneId.systemDefault();

    private static ServicoReservas servicoEm(LocalDateTime agora) {
        return new ServicoReservas(Clock.fixed(agora.atZone(ZONA).toInstant(), ZONA));
    }

    private final Usuario alice = solicitante("alice");
    private final Usuario bob = solicitante("bob");
    private final Usuario resp = responsavel("resp");
    private final Usuario admin = administrador("admin");
    private final Recurso sala = sala("S1", false);

    @Test
    @DisplayName("Solicitar sem recurso é 400 (recurso obrigatório)")
    void solicitarSemRecursoRejeita400() {
        ServicoReservas s = servicoEm(t("09:00"));
        assertErro(400, () -> s.solicitar(alice, null, t("10:00"), t("11:00")));
    }

    @Test
    @DisplayName("Excluir reserva de outro solicitante é 403")
    void excluirPorNaoDonoRejeita403() {
        ServicoReservas s = servicoEm(t("09:00"));
        Reserva solicitada = reserva("R1", alice, sala, "10:00", "11:00", EstadoReserva.SOLICITADA);
        s.registrarExistente(solicitada);
        assertErro(403, () -> s.excluir(bob, solicitada));
    }

    @ParameterizedTest(name = "início {0} min em relação ao início da reserva → cancelamento aceito = {1}")
    @CsvSource({"-1,true", "0,false", "1,false"})
    @DisplayName("Cancelar APROVADA só é permitido antes do início (trinca -1, 0, +1 min)")
    void cancelarAprovadaSomenteAntesDoInicio(long minutos, boolean aceito) {
        ServicoReservas s = servicoEm(t("10:00").plusMinutes(minutos));
        Reserva aprovada = reserva("R1", alice, sala, "10:00", "11:00", EstadoReserva.APROVADA);
        s.registrarExistente(aprovada);
        if (aceito) {
            assertEquals(EstadoReserva.CANCELADA, s.cancelar(alice, aprovada, "Imprevisto").getEstado());
        } else {
            assertErro(409, () -> s.cancelar(alice, aprovada, "Imprevisto"));
        }
    }

    @ParameterizedTest(name = "justificativa = {0}")
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("Rejeitar exige justificativa não vazia")
    void rejeitarExigeJustificativa(String justificativa) {
        ServicoReservas s = servicoEm(t("09:00"));
        s.vincularResponsavel(admin, "resp", "S1");
        Reserva solicitada = reserva("R1", alice, sala, "10:00", "11:00", EstadoReserva.SOLICITADA);
        s.registrarExistente(solicitada);
        assertErro(400, () -> s.rejeitar(resp, solicitada, justificativa));
    }

    @ParameterizedTest(name = "justificativa = {0}")
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("Cancelar exige justificativa não vazia")
    void cancelarExigeJustificativa(String justificativa) {
        ServicoReservas s = servicoEm(t("09:00"));
        Reserva solicitada = reserva("R1", alice, sala, "10:00", "11:00", EstadoReserva.SOLICITADA);
        s.registrarExistente(solicitada);
        assertErro(400, () -> s.cancelar(alice, solicitada, justificativa));
    }

    @Test
    @DisplayName("APROVADA → EM_USO é permitido ao dono e ao Responsável")
    void aprovadaParaEmUsoPermitido() {
        ServicoReservas s = servicoEm(t("10:30"));
        Reserva aprovada = reserva("R1", alice, sala, "10:00", "11:00", EstadoReserva.APROVADA);
        s.registrarExistente(aprovada);
        assertEquals(EstadoReserva.EM_USO, s.iniciarUso(alice, aprovada).getEstado());
    }

    @Test
    @DisplayName("APROVADA → EM_USO por Responsável é permitido")
    void aprovadaParaEmUsoPeloResponsavel() {
        ServicoReservas s = servicoEm(t("10:30"));
        Reserva aprovada = reserva("R1", alice, sala, "10:00", "11:00", EstadoReserva.APROVADA);
        s.registrarExistente(aprovada);
        assertEquals(EstadoReserva.EM_USO, s.iniciarUso(resp, aprovada).getEstado());
    }

    @ParameterizedTest(name = "{0} → {1} é transição proibida")
    @CsvSource({
            "SOLICITADA,CONCLUIDA",
            "APROVADA,CONCLUIDA",
            "EM_USO,REJEITADA",
            "CANCELADA,APROVADA",
            "CONCLUIDA,CANCELADA",
            "NAO_COMPARECEU,APROVADA"
    })
    @DisplayName("Transições fora da máquina de estados são 409 (RN-05, RN-06)")
    void transicoesForaDaMaquinaSaoProibidas(EstadoReserva de, EstadoReserva para) {
        ServicoReservas s = servicoEm(t("09:00"));
        Reserva r = reserva("R1", alice, sala, "10:00", "11:00", de);
        s.registrarExistente(r);
        assertErro(409, () -> s.transicionar(resp, r, para, "motivo"));
    }

    @Test
    @DisplayName("Terceiro sem vínculo não pode iniciar uso de reserva alheia (403)")
    void terceiroNaoIniciaUso() {
        ServicoReservas s = servicoEm(t("09:00"));
        Reserva aprovada = reserva("R1", alice, sala, "10:00", "11:00", EstadoReserva.APROVADA);
        s.registrarExistente(aprovada);
        assertErro(403, () -> s.iniciarUso(bob, aprovada));
    }

    @Test
    @DisplayName("Responsável não cancela reserva que não é sua (403, só o solicitante cancela)")
    void responsavelNaoCancelaReservaAlheia() {
        ServicoReservas s = servicoEm(t("09:00"));
        Reserva aprovada = reserva("R1", alice, sala, "10:00", "11:00", EstadoReserva.APROVADA);
        s.registrarExistente(aprovada);
        assertErro(403, () -> s.cancelar(resp, aprovada, "Motivo"));
    }

    @Test
    @DisplayName("Transição de reserva inexistente é 404")
    void transicaoReservaInexistente() {
        ServicoReservas s = servicoEm(t("09:00"));
        Reserva fantasma = reserva("RX", alice, sala, "10:00", "11:00", EstadoReserva.APROVADA);
        assertErro(404, () -> s.iniciarUso(resp, fantasma));
    }

    @ParameterizedTest(name = "fim = início {0} min → rejeitado = {1}")
    @CsvSource({"-1,true", "0,true", "1,false"})
    @DisplayName("Manutenção exige fim posterior ao início (trinca -1, 0, +1 min)")
    void manutencaoExigeFimPosteriorAoInicio(long minutos, boolean rejeitada) {
        ServicoReservas s = servicoEm(t("09:00"));
        Manutencao m = new Manutencao("S1", t("10:00"), t("10:00").plusMinutes(minutos));
        if (rejeitada) {
            assertErro(400, () -> s.criarManutencao(admin, m));
        } else {
            s.criarManutencao(admin, m);
        }
    }

    @Test
    @DisplayName("Manutenção sem início ou sem fim é 400")
    void manutencaoSemPeriodoRejeita400() {
        ServicoReservas s = servicoEm(t("09:00"));
        assertErro(400, () -> s.criarManutencao(admin, new Manutencao("S1", null, t("11:00"))));
        assertErro(400, () -> s.criarManutencao(admin, new Manutencao("S1", t("10:00"), null)));
    }

    @Test
    @DisplayName("Pendentes de análise listam apenas SOLICITADA e só para o Responsável")
    void pendentesDeAnaliseFiltraSolicitadas() {
        ServicoReservas s = servicoEm(t("09:00"));
        s.registrarExistente(reserva("R1", alice, sala, "10:00", "11:00", EstadoReserva.SOLICITADA));
        s.registrarExistente(reserva("R2", alice, sala, "12:00", "13:00", EstadoReserva.APROVADA));
        s.vincularResponsavel(admin, "resp", "S1");
        assertEquals(1, s.pendentesDeAnalise(resp).size());
        assertEquals("R1", s.pendentesDeAnalise(resp).get(0).getId());
        assertErro(403, () -> s.pendentesDeAnalise(alice));
    }

    @Test
    @DisplayName("Pendentes de análise: Responsável sem vínculo com o recurso não vê a solicitação")
    void pendentesSoDeRecursoVinculado() {
        ServicoReservas s = servicoEm(t("09:00"));
        s.registrarExistente(reserva("R1", alice, sala, "10:00", "11:00", EstadoReserva.SOLICITADA));
        assertTrue(s.pendentesDeAnalise(resp).isEmpty());
    }

    @Test
    @DisplayName("Auditoria de reserva alheia é 403 para o solicitante que não é dono")
    void auditoriaDeReservaAlheiaRejeita403() {
        ServicoReservas s = servicoEm(t("09:00"));
        s.registrarExistente(reserva("R1", alice, sala, "10:00", "11:00", EstadoReserva.SOLICITADA));
        assertErro(403, () -> s.eventosDe(bob, "R1"));
    }

    @Test
    @DisplayName("Transição altera apenas a reserva alvo quando há várias armazenadas")
    void transicaoAlteraSomenteAlvo() {
        ServicoReservas s = servicoEm(t("09:00"));
        s.vincularResponsavel(admin, "resp", "S1");
        Reserva r1 = reserva("R1", alice, sala, "10:00", "11:00", EstadoReserva.SOLICITADA);
        Reserva r2 = reserva("R2", alice, sala, "12:00", "13:00", EstadoReserva.SOLICITADA);
        s.registrarExistente(r1);
        s.registrarExistente(r2);
        s.rejeitar(resp, r2, "Conflito com evento");
        assertEquals(1, s.pendentesDeAnalise(resp).size());
        assertEquals("R1", s.pendentesDeAnalise(resp).get(0).getId());
    }
}
