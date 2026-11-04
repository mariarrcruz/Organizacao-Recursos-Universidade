package com.organizacao.recursos.testes;

import static com.organizacao.recursos.testes.Fixtures.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.organizacao.recursos.dominio.*;
import com.organizacao.recursos.servico.ServicoAdministracao;
import com.organizacao.recursos.servico.ServicoRecursos;
import com.organizacao.recursos.servico.ServicoReservas;
import com.organizacao.recursos.servico.ServicoUsuarios;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Lacunas de risco de recursos (RF-02, RF-03), usuários (RF-15) e parâmetros do sistema. */
class CoberturaRiscoRecursosTest {

    private final Usuario alice = solicitante("alice");
    private final Usuario admin = administrador("admin");

    private ServicoRecursos recursos() {
        return new ServicoRecursos(new ServicoReservas());
    }

    @Test
    @DisplayName("Cadastrar recurso nulo é 400")
    void cadastrarNulo() {
        ServicoRecursos s = recursos();
        assertErro(400, () -> s.cadastrarRecurso(admin, null));
    }

    @ParameterizedTest(name = "nome = [{0}]")
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("Nome do recurso obrigatório no cadastro (nulo, vazio ou em branco)")
    void cadastrarNomeInvalido(String nome) {
        ServicoRecursos s = recursos();
        Recurso r = new Recurso("S1", TipoRecurso.SALA, nome, "BLOCO_A", 30, false, StatusRecurso.DISPONIVEL, Set.of());
        assertErro(400, () -> s.cadastrarRecurso(admin, r));
    }

    @Test
    @DisplayName("Cadastrar recurso com id já existente é 409")
    void cadastrarIdDuplicado() {
        ServicoRecursos s = recursos();
        s.cadastrarRecurso(admin, sala("S1", false));
        assertErro(409, () -> s.cadastrarRecurso(admin, sala("S1", false)));
    }

    @Test
    @DisplayName("Atualizar nome para branco é 400")
    void atualizarNomeEmBrancoRejeita400() {
        ServicoRecursos s = recursos();
        s.cadastrarRecurso(admin, sala("S1", false));
        assertErro(400, () -> s.atualizarRecurso(admin, "S1", "  "));
    }

    @Test
    @DisplayName("Capacidade mínima não numérica é 400")
    void capacidadeMinNaoNumericaRejeita400() {
        ServicoRecursos s = recursos();
        assertErro(400, () -> s.pesquisar(alice, Map.of("capacidade_min", "10")));
    }

    @ParameterizedTest(name = "filtros com {0} e sem o outro → 400")
    @CsvSource({"inicio", "fim"})
    @DisplayName("Disponibilidade exige início e fim juntos")
    void disponibilidadeExigeInicioEFim(String presente) {
        ServicoRecursos s = recursos();
        Map<String, Object> filtros = Map.of(presente, t("10:00"));
        assertErro(400, () -> s.pesquisar(alice, filtros));
    }

    @Test
    @DisplayName("Competência informada como texto único casa com o recurso")
    void competenciaTextoUnico() throws Exception {
        ServicoRecursos s = recursos();
        s.registrarRecursoExistente(recurso("S1", TipoRecurso.SALA, false, StatusRecurso.DISPONIVEL, Set.of("PROJETOR")));
        s.registrarRecursoExistente(recurso("S2", TipoRecurso.SALA, false, StatusRecurso.DISPONIVEL, Set.of("QUADRO")));
        var achados = s.pesquisar(alice, Map.of("competencia", "PROJETOR"));
        assertEquals(1, achados.size());
        assertEquals("S1", achados.get(0).id());
    }

    @ParameterizedTest(name = "reserva em {0} → bloqueia exclusão = {1}")
    @CsvSource({
            "SOLICITADA,true",
            "APROVADA,true",
            "EM_USO,true",
            "CONCLUIDA,false",
            "CANCELADA,false",
            "REJEITADA,false",
            "NAO_COMPARECEU,false"
    })
    @DisplayName("Exclusão de recurso é bloqueada só por reserva ativa (RF-02.5)")
    void exclusaoBloqueadaSomentePorReservaAtiva(EstadoReserva estado, boolean bloqueia) {
        ServicoReservas reservas = new ServicoReservas();
        ServicoRecursos s = new ServicoRecursos(reservas);
        s.cadastrarRecurso(admin, sala("S1", false));
        reservas.registrarExistente(reserva("R1", alice, sala("S1", false), "10:00", "11:00", estado));
        if (bloqueia) {
            assertErro(409, () -> s.excluirRecurso(admin, "S1"));
        } else {
            s.excluirRecurso(admin, "S1");
            assertErro(404, () -> s.consultarRecurso("S1"));
        }
    }

    @ParameterizedTest(name = "id = [{0}]")
    @NullSource
    @ValueSource(strings = {"", " "})
    @DisplayName("Cadastro de usuário exige id não vazio")
    void cadastrarUsuarioSemId(String id) {
        ServicoUsuarios u = new ServicoUsuarios();
        assertErro(400, () -> u.cadastrarUsuario(admin, new Usuario(id, Set.of(Perfil.SOLICITANTE))));
    }

    @Test
    @DisplayName("Cadastro de usuário nulo é 400 e edição sem alvo é 400")
    void usuarioNuloEEdicaoSemAlvo() {
        ServicoUsuarios u = new ServicoUsuarios();
        assertErro(400, () -> u.cadastrarUsuario(admin, null));
        assertErro(400, () -> u.editarUsuario(admin, null));
        assertErro(400, () -> u.editarUsuario(admin, new Usuario(null, Set.of(Perfil.SOLICITANTE))));
    }

    @ParameterizedTest(name = "chave = [{0}]")
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("Chave de parâmetro obrigatória (nula, vazia ou em branco)")
    void chaveDeParametroObrigatoria(String chave) {
        ServicoAdministracao adm = new ServicoAdministracao();
        assertErro(400, () -> adm.alterarParametro(admin, chave, "valor"));
    }
}
