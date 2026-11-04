package com.organizacao.recursos.testes;

import static com.organizacao.recursos.testes.Fixtures.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.organizacao.recursos.dominio.*;
import com.organizacao.recursos.servico.ServicoRecursos;
import com.organizacao.recursos.servico.ServicoReservas;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Requisito RF-03 — Pesquisa parametrizada de recursos (RFC RF-03; plano § 1.5). */
class RF03PesquisaRecursosTest {

    private final ServicoReservas reservas = new ServicoReservas();
    private final ServicoRecursos recursos = new ServicoRecursos(reservas);
    private final Usuario admin = administrador("adm");
    private final Usuario alice = solicitante("alice");

    private Recurso sala(String id, String local, int capacidade) {
        return new Recurso(id, TipoRecurso.SALA, "Sala " + id, local, capacidade, false,
                StatusRecurso.DISPONIVEL, Set.of());
    }

    private boolean contem(List<Recurso> lista, String id) {
        return lista.stream().anyMatch(r -> r.id().equals(id));
    }

    @Test
    @DisplayName("RF-03.1 — tipo=SALA: retorna todas as salas (não retorna material)")
    void rf03_1_filtroPorTipoSala() {
        // Arrange
        recursos.cadastrarRecurso(admin, sala("S1", "BLOCO_A", 60));
        recursos.cadastrarRecurso(admin, sala("S2", "BLOCO_B", 20));
        recursos.cadastrarRecurso(admin, new Recurso("M1", TipoRecurso.MATERIAL, "Projetor", "DEPOSITO", 0, false,
                StatusRecurso.DISPONIVEL, Set.of()));
        // Act
        List<Recurso> resultado = recursos.pesquisar(alice, Map.of("tipo", "SALA"));
        // Assert
        assertTrue(contem(resultado, "S1"));
        assertTrue(contem(resultado, "S2"));
        assertFalse(contem(resultado, "M1"));
    }

    @Test
    @DisplayName("RF-03.2 — tipo=SALA, localização=BLOCO_A: retorna apenas salas do Bloco A")
    void rf03_2_filtroPorTipoELocalizacao() {
        // Arrange
        recursos.cadastrarRecurso(admin, sala("S1", "BLOCO_A", 60));
        recursos.cadastrarRecurso(admin, sala("S2", "BLOCO_B", 20));
        // Act
        List<Recurso> resultado = recursos.pesquisar(alice, Map.of("tipo", "SALA", "localizacao", "BLOCO_A"));
        // Assert
        assertTrue(contem(resultado, "S1"));
        assertFalse(contem(resultado, "S2"));
    }

    @Test
    @DisplayName("RF-03.3 — tipo=SALA, capacidade_min=50: retorna salas com capacidade >= 50")
    void rf03_3_filtroPorCapacidadeMinima() {
        // Arrange
        recursos.cadastrarRecurso(admin, sala("S1", "BLOCO_A", 60));
        recursos.cadastrarRecurso(admin, sala("S2", "BLOCO_A", 20));
        // Act
        List<Recurso> resultado = recursos.pesquisar(alice, Map.of("tipo", "SALA", "capacidade_min", 50));
        // Assert
        assertTrue(contem(resultado, "S1"));
        assertFalse(contem(resultado, "S2"));
    }

    @Test
    @DisplayName("RF-03.4 — tipo=PROFESSOR, competência [PYTHON, JAVA]: retorna quem tem uma delas")
    void rf03_4_filtroPorCompetenciaDeProfessor() {
        // Arrange
        recursos.cadastrarRecurso(admin, new Recurso("P1", TipoRecurso.PROFESSOR, "Prof. Python", "BLOCO_B", 0,
                false, StatusRecurso.DISPONIVEL, Set.of("PYTHON")));
        recursos.cadastrarRecurso(admin, new Recurso("P2", TipoRecurso.PROFESSOR, "Prof. Cobol", "BLOCO_B", 0,
                false, StatusRecurso.DISPONIVEL, Set.of("COBOL")));
        // Act
        List<Recurso> resultado = recursos.pesquisar(alice,
                Map.of("tipo", "PROFESSOR", "competencia", List.of("PYTHON", "JAVA")));
        // Assert
        assertTrue(contem(resultado, "P1"));
        assertFalse(contem(resultado, "P2"));
    }

    @Test
    @DisplayName("RF-03.5 — disponibilidade [2026-11-05, 10:00-12:00]: retorna só recursos livres no intervalo")
    void rf03_5_filtroPorDisponibilidade() {
        // Arrange
        Recurso s1 = sala("S1", "BLOCO_A", 30);
        Recurso s2 = sala("S2", "BLOCO_A", 30);
        recursos.cadastrarRecurso(admin, s1);
        recursos.cadastrarRecurso(admin, s2);
        reservas.registrarExistente(reserva("R1", alice, s1, "10:00", "11:00", EstadoReserva.APROVADA));
        // Act
        List<Recurso> resultado = recursos.pesquisar(alice,
                Map.of("inicio", t("10:00"), "fim", t("12:00")));
        // Assert
        assertTrue(contem(resultado, "S2"));
        assertFalse(contem(resultado, "S1"));
    }

    @Test
    @DisplayName("RF-03.6 — Sem filtros: retorna todos os recursos cadastrados")
    void rf03_6_semFiltrosRetornaTodos() {
        // Arrange
        recursos.cadastrarRecurso(admin, sala("S1", "BLOCO_A", 60));
        recursos.cadastrarRecurso(admin, new Recurso("M1", TipoRecurso.MATERIAL, "Projetor", "DEPOSITO", 0, false,
                StatusRecurso.DISPONIVEL, Set.of()));
        // Act
        List<Recurso> resultado = recursos.pesquisar(alice, Map.of());
        // Assert
        assertTrue(contem(resultado, "S1"));
        assertTrue(contem(resultado, "M1"));
    }

    @Test
    @DisplayName("RF-03.7 — Filtro inválido (tipo=XYZ): rejeitado (400)")
    void rf03_7_filtroTipoInvalidoEhRejeitado() {
        // Arrange
        // Act & Assert
        assertErro(400, () -> recursos.pesquisar(alice, Map.of("tipo", "XYZ")));
    }

    @Test
    @DisplayName("RF-03.8 — Disponibilidade com fim < início [11:00, 10:00]: rejeitado (400)")
    void rf03_8_disponibilidadeInvertidaEhRejeitada() {
        // Arrange
        // Act & Assert
        assertErro(400, () -> recursos.pesquisar(alice, Map.of("inicio", t("11:00"), "fim", t("10:00"))));
    }
}
