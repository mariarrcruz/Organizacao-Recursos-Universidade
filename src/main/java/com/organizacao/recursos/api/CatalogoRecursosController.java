package com.organizacao.recursos.api;

import com.organizacao.recursos.dominio.ErroAplicacao;
import com.organizacao.recursos.dominio.Perfil;
import com.organizacao.recursos.dominio.Recurso;
import com.organizacao.recursos.dominio.StatusRecurso;
import com.organizacao.recursos.dominio.TipoRecurso;
import com.organizacao.recursos.dominio.Usuario;
import com.organizacao.recursos.persistencia.Armazenamento;
import com.organizacao.recursos.servico.ServicoAutenticacao;
import com.organizacao.recursos.servico.ServicoReservas;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Pesquisa, consulta, edição e exclusão de recursos (RF-02, RF-03).
 *
 * <p>Pesquisa e consulta: qualquer usuário autenticado. Edição e exclusão: somente Administrador.
 * O cadastro (POST) continua em {@link RecursosController}.
 */
@RestController
@RequestMapping("/recursos")
public class CatalogoRecursosController {

    private final ServicoAutenticacao autenticacao;
    private final Armazenamento armazenamento;
    private final ServicoReservas reservas;

    public CatalogoRecursosController(ServicoAutenticacao autenticacao, Armazenamento armazenamento,
                                      ServicoReservas reservas) {
        this.autenticacao = autenticacao;
        this.armazenamento = armazenamento;
        this.reservas = reservas;
    }

    /**
     * Pesquisa por tipo, localização, capacidade mínima, competência e disponibilidade em um período.
     * Só retorna recursos DISPONIVEIS; com {@code inicio} e {@code fim}, exclui os que têm reserva ativa ou
     * manutenção sobreposta.
     */
    @GetMapping
    public List<RecursoJson> pesquisar(@RequestHeader(value = "Authorization", required = false) String autorizacao,
                                       @RequestParam(value = "tipo", required = false) String tipo,
                                       @RequestParam(value = "localizacao", required = false) String localizacao,
                                       @RequestParam(value = "capacidade_min", required = false) Integer capacidadeMin,
                                       @RequestParam(value = "competencia", required = false) List<String> competencias,
                                       @RequestParam(value = "inicio", required = false) String inicio,
                                       @RequestParam(value = "fim", required = false) String fim) {
        Usuario ator = autenticacao.autenticar(autorizacao);
        TipoRecurso tipoFiltro = tipo == null || tipo.isBlank() ? null : enumDe(TipoRecurso.class, tipo, "tipo");
        Set<String> competenciasFiltro = competencias == null ? Set.of()
                : competencias.stream().map(String::trim).filter(c -> !c.isEmpty()).collect(Collectors.toSet());
        boolean comPeriodo = inicio != null || fim != null;
        if (comPeriodo && (inicio == null || fim == null)) {
            throw new ErroAplicacao(400, "Disponibilidade exige os campos inicio e fim");
        }
        LocalDateTime ini = comPeriodo ? Datas.instante(inicio, "inicio") : null;
        LocalDateTime termino = comPeriodo ? Datas.instante(fim, "fim") : null;
        if (comPeriodo && !termino.isAfter(ini)) {
            throw new ErroAplicacao(400, "Fim da disponibilidade deve ser posterior ao início");
        }

        return armazenamento.listarRecursos().stream()
                .filter(r -> r.status() == StatusRecurso.DISPONIVEL)
                .filter(r -> tipoFiltro == null || r.tipo() == tipoFiltro)
                .filter(r -> localizacao == null || localizacao.equalsIgnoreCase(r.localizacao()))
                .filter(r -> capacidadeMin == null || r.capacidade() >= capacidadeMin)
                .filter(r -> competenciasFiltro.isEmpty() || r.competencias().stream().anyMatch(competenciasFiltro::contains))
                .filter(r -> !comPeriodo || reservas.disponivel(ator, r.id(), ini, termino))
                .map(CatalogoRecursosController::paraJson)
                .toList();
    }

    @GetMapping("/{id}")
    public RecursoJson consultar(@RequestHeader(value = "Authorization", required = false) String autorizacao,
                                 @PathVariable("id") String id) {
        autenticacao.autenticar(autorizacao);
        return paraJson(buscar(id));
    }

    /** Edição parcial: campos ausentes mantêm o valor atual. O tipo do recurso não muda. */
    @PutMapping("/{id}")
    public RecursoJson editar(@RequestHeader(value = "Authorization", required = false) String autorizacao,
                              @PathVariable("id") String id,
                              @RequestBody RecursoEdicaoJson corpo) {
        exigirAdministrador(autorizacao);
        Recurso atual = buscar(id);
        String nome = corpo.nome() == null ? atual.nome() : corpo.nome();
        if (nome == null || nome.isBlank()) {
            throw new ErroAplicacao(400, "Nome do recurso não pode ser vazio (campo nome)");
        }
        int capacidade = corpo.capacidade() == null ? atual.capacidade() : corpo.capacidade();
        if (capacidade < 0) {
            throw new ErroAplicacao(400, "Capacidade não pode ser negativa (campo capacidade)");
        }
        StatusRecurso status = corpo.status() == null ? atual.status()
                : enumDe(StatusRecurso.class, corpo.status(), "status");
        Set<String> competencias = corpo.competencias() == null ? atual.competencias()
                : Set.copyOf(corpo.competencias());
        Recurso nova = new Recurso(atual.id(), atual.tipo(), nome,
                corpo.localizacao() == null ? atual.localizacao() : corpo.localizacao(),
                capacidade,
                corpo.restrito() == null ? atual.restrito() : corpo.restrito(),
                status, competencias);
        armazenamento.gravarRecurso(nova);
        return paraJson(nova);
    }

    /**
     * Exclui o recurso. Recusa com 409 se houver qualquer reserva (inclusive concluída) ligada a ele, para
     * preservar o histórico e a auditoria; nesse caso, o caminho é colocá-lo como INDISPONIVEL.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@RequestHeader(value = "Authorization", required = false) String autorizacao,
                                        @PathVariable("id") String id) {
        exigirAdministrador(autorizacao);
        buscar(id);
        if (!reservas.reservasDoRecurso(id).isEmpty()) {
            throw new ErroAplicacao(409, "Recurso possui histórico de reservas e não pode ser excluído; "
                    + "marque-o como INDISPONIVEL");
        }
        armazenamento.removerRecurso(id);
        return ResponseEntity.noContent().build();
    }

    // ---------------------------------------------------------------- internos

    private void exigirAdministrador(String autorizacao) {
        Usuario ator = autenticacao.autenticar(autorizacao);
        if (!ator.perfis().contains(Perfil.ADMINISTRADOR)) {
            throw new ErroAplicacao(403, "Perfil ADMINISTRADOR exigido");
        }
    }

    private Recurso buscar(String id) {
        return armazenamento.buscarRecurso(id)
                .orElseThrow(() -> new ErroAplicacao(404, "Recurso não encontrado: " + id));
    }

    private static RecursoJson paraJson(Recurso r) {
        return new RecursoJson(r.id(), r.tipo().name(), r.nome(), r.localizacao(), r.capacidade(),
                r.restrito(), r.status().name(), r.competencias().stream().sorted().toList());
    }

    private static <E extends Enum<E>> E enumDe(Class<E> tipo, String valor, String campo) {
        try {
            return Enum.valueOf(tipo, valor.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ErroAplicacao(400, "Valor inválido para " + campo + ": " + valor);
        }
    }

    public record RecursoJson(String id, String tipo, String nome, String localizacao, int capacidade,
                              boolean restrito, String status, List<String> competencias) {}

    public record RecursoEdicaoJson(String nome, String localizacao, Integer capacidade, Boolean restrito,
                                    String status, List<String> competencias) {}
}
