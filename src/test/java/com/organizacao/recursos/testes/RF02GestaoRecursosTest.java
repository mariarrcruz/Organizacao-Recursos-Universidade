package com.organizacao.recursos.testes;

import static com.organizacao.recursos.testes.Fixtures.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.organizacao.recursos.dominio.*;
import com.organizacao.recursos.servico.ServicoRecursos;
import com.organizacao.recursos.servico.ServicoReservas;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/** Requisito RF-02 — Gestão de recursos institucionais (RFC RF-02; plano § 1.5). */
class RF02GestaoRecursosTest {

    private final ServicoReservas reservas = new ServicoReservas();
    private final ServicoRecursos recursos = new ServicoRecursos(reservas);
    private final Usuario admin = administrador("adm");
    private final Usuario alice = solicitante("alice");

    private Recurso salaS1(int capacidade) {
        return new Recurso("S1", TipoRecurso.SALA, "Laboratório 1", "BLOCO_A", capacidade, false,
                StatusRecurso.DISPONIVEL, Set.of());
    }

    @Test
    @DisplayName("RF-02.1 — CREATE sala {nome, local, capacidade}: criado")
    void rf02_1_criarSala() {
        // Arrange
        Recurso sala = salaS1(30);
        // Act
        Recurso criada = recursos.cadastrarRecurso(admin, sala);
        // Assert
        assertEquals("Laboratório 1", criada.nome());
    }

    @Test
    @DisplayName("RF-02.2 — READ por id: retorno completo")
    void rf02_2_consultarSalaPorId() {
        // Arrange
        recursos.cadastrarRecurso(admin, salaS1(30));
        // Act
        Recurso lida = recursos.consultarRecurso("S1");
        // Assert
        assertEquals("Laboratório 1", lida.nome());
        assertEquals("BLOCO_A", lida.localizacao());
        assertEquals(30, lida.capacidade());
    }

    @Test
    @DisplayName("RF-02.3 — UPDATE do nome: atualizado")
    void rf02_3_atualizarNomeDaSala() {
        // Arrange
        recursos.cadastrarRecurso(admin, salaS1(30));
        // Act
        recursos.atualizarRecurso(admin, "S1", "Laboratório 1 — Atualizado");
        // Assert
        assertEquals("Laboratório 1 — Atualizado", recursos.consultarRecurso("S1").nome());
    }

    @Test
    @DisplayName("RF-02.4 — DELETE de sala sem reservas: deletado")
    void rf02_4_excluirSalaSemReservas() {
        // Arrange
        recursos.cadastrarRecurso(admin, salaS1(30));
        // Act
        recursos.excluirRecurso(admin, "S1");
        // Assert
        assertErro(404, () -> recursos.consultarRecurso("S1"));
    }

    @Test
    @Disabled("Esclarecer: exclusão de sala com reserva EM_USO (referência ativa, plano RF-02.5 ⚠️).")
    @DisplayName("RF-02.5 — DELETE de sala com reserva EM_USO: esclarecer")
    void rf02_5_excluirSalaComReservaEmUso() {
        // Arrange
        recursos.cadastrarRecurso(admin, salaS1(30));
        reservas.registrarExistente(reserva("R1", alice, salaS1(30), "10:00", "11:00", EstadoReserva.EM_USO));
        // Act & Assert
        assertRejeitado(() -> recursos.excluirRecurso(admin, "S1"));
    }

    @Test
    @DisplayName("RF-02.6 — CREATE com nome vazio: rejeitado (validação)")
    void rf02_6_nomeVazioEhRejeitado() {
        // Arrange
        Recurso semNome = new Recurso("S2", TipoRecurso.SALA, "", "BLOCO_A", 30, false,
                StatusRecurso.DISPONIVEL, Set.of());
        // Act & Assert
        assertRejeitado(() -> recursos.cadastrarRecurso(admin, semNome));
    }

    @Test
    @DisplayName("RF-02.7 — UPDATE de id inexistente: 404 Not Found")
    void rf02_7_atualizarIdInexistenteRetorna404() {
        // Arrange
        // Act & Assert
        assertErro(404, () -> recursos.atualizarRecurso(admin, "INEXISTENTE", "Qualquer"));
    }

    @Test
    @DisplayName("RF-02.8 — Material INDISPONÍVEL: não listado em buscas")
    void rf02_8_materialIndisponivelNaoEhListado() {
        // Arrange
        Recurso indisponivel = new Recurso("M1", TipoRecurso.MATERIAL, "Projetor", "DEPOSITO", 0, false,
                StatusRecurso.INDISPONIVEL, Set.of());
        recursos.cadastrarRecurso(admin, indisponivel);
        // Act
        List<Recurso> resultado = recursos.pesquisar(alice, Map.of("tipo", "MATERIAL"));
        // Assert
        assertFalse(resultado.stream().anyMatch(r -> r.id().equals("M1")));
    }

    @Test
    @DisplayName("RF-02.9 — Equipamento status MANUTENCAO: bloqueado, sem reservas aceitas")
    void rf02_9_equipamentoEmManutencaoNaoAceitaReservas() {
        // Arrange
        Recurso equipamento = new Recurso("E1", TipoRecurso.EQUIPAMENTO, "Microscópio", "LAB_2", 1, false,
                StatusRecurso.MANUTENCAO, Set.of());
        // Act & Assert
        assertRejeitado(() -> reservas.solicitar(alice, equipamento, t("10:00"), t("11:00")));
    }

    @Test
    @DisplayName("RF-02.10 — Professor com competências [C1, C2]: filtrável por competência (RF-03)")
    void rf02_10_professorComCompetenciasEhFiltravel() {
        // Arrange
        Recurso professor = new Recurso("P1", TipoRecurso.PROFESSOR, "Prof. Teste", "BLOCO_B", 0, false,
                StatusRecurso.DISPONIVEL, Set.of("C1", "C2"));
        recursos.cadastrarRecurso(admin, professor);
        // Act
        List<Recurso> resultado = recursos.pesquisar(alice, Map.of("tipo", "PROFESSOR", "competencia", List.of("C1")));
        // Assert
        assertNotNull(resultado);
        assertTrue(resultado.stream().anyMatch(r -> r.id().equals("P1")));
    }
}
