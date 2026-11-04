package com.organizacao.recursos.servico;

import static com.organizacao.recursos.servico.Autorizacao.exigirAutenticado;
import static com.organizacao.recursos.servico.Autorizacao.exigirPerfil;

import com.organizacao.recursos.dominio.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Cadastro e pesquisa de recursos institucionais (RF-02, RF-03). Gestão restrita ao Administrador. */
public class ServicoRecursos {

    private final ServicoReservas reservas;
    private final List<Recurso> armazenados = new ArrayList<>();

    public ServicoRecursos(ServicoReservas reservas) {
        this.reservas = reservas;
    }

    /** Insere um recurso já existente (usado para preparar cenários de teste). */
    public synchronized void registrarRecursoExistente(Recurso recurso) { armazenados.add(recurso); }

    public synchronized Recurso cadastrarRecurso(Usuario ator, Recurso recurso) {
        exigirPerfil(ator, Perfil.ADMINISTRADOR);
        if (recurso == null) {
            throw new ErroAplicacao(400, "Recurso obrigatório");
        }
        validarNome(recurso.nome());
        if (armazenados.stream().anyMatch(r -> r.id().equals(recurso.id()))) {
            throw new ErroAplicacao(409, "Recurso já cadastrado: " + recurso.id());
        }
        armazenados.add(recurso);
        return recurso;
    }

    public synchronized Recurso consultarRecurso(String id) {
        return buscar(id);
    }

    public synchronized Recurso atualizarRecurso(Usuario ator, String id, String novoNome) {
        exigirPerfil(ator, Perfil.ADMINISTRADOR);
        Recurso atual = buscar(id);
        validarNome(novoNome);
        Recurso nova = new Recurso(atual.id(), atual.tipo(), novoNome, atual.localizacao(),
                atual.capacidade(), atual.restrito(), atual.status(), atual.competencias());
        armazenados.set(armazenados.indexOf(atual), nova);
        return nova;
    }

    public synchronized void excluirRecurso(Usuario ator, String id) {
        exigirPerfil(ator, Perfil.ADMINISTRADOR);
        Recurso atual = buscar(id);
        if (reservas.temReservaAtiva(id)) {
            throw new ErroAplicacao(409, "Recurso possui reservas ativas e não pode ser excluído");
        }
        armazenados.remove(atual);
    }

    /**
     * Filtros: tipo, localizacao, capacidade_min, competencia (uma ou mais), inicio e fim.
     * Só retorna recursos DISPONIVEIS; com período, exclui os que têm conflito de reserva ou manutenção.
     */
    public synchronized List<Recurso> pesquisar(Usuario ator, Map<String, Object> filtros) {
        exigirAutenticado(ator);
        Map<String, Object> f = filtros == null ? Map.of() : filtros;

        TipoRecurso tipo = f.get("tipo") == null ? null : parseTipo(f.get("tipo"));
        String localizacao = f.get("localizacao") == null ? null : String.valueOf(f.get("localizacao"));
        Integer capacidadeMin = null;
        if (f.get("capacidade_min") != null) {
            if (!(f.get("capacidade_min") instanceof Number n)) {
                throw new ErroAplicacao(400, "capacidade_min deve ser numérico");
            }
            capacidadeMin = n.intValue();
        }
        Set<String> competencias = competencias(f.get("competencia"));
        LocalDateTime inicio = (LocalDateTime) f.get("inicio");
        LocalDateTime fim = (LocalDateTime) f.get("fim");
        if ((inicio == null) != (fim == null)) {
            throw new ErroAplicacao(400, "Disponibilidade exige início e fim");
        }
        if (inicio != null && !fim.isAfter(inicio)) {
            throw new ErroAplicacao(400, "Fim da disponibilidade deve ser posterior ao início");
        }

        final TipoRecurso tipoFiltro = tipo;
        final Integer capMin = capacidadeMin;
        final LocalDateTime ini = inicio;
        final LocalDateTime fimDisp = fim;
        return armazenados.stream()
                .filter(r -> r.status() == StatusRecurso.DISPONIVEL)
                .filter(r -> tipoFiltro == null || r.tipo() == tipoFiltro)
                .filter(r -> localizacao == null || localizacao.equals(r.localizacao()))
                .filter(r -> capMin == null || r.capacidade() >= capMin)
                .filter(r -> competencias.isEmpty() || r.competencias().stream().anyMatch(competencias::contains))
                .filter(r -> ini == null || !reservas.temConflito(r.id(), ini, fimDisp))
                .toList();
    }

    // ---------------------------------------------------------------- internos

    private Recurso buscar(String id) {
        return armazenados.stream()
                .filter(r -> r.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new ErroAplicacao(404, "Recurso não encontrado: " + id));
    }

    private static void validarNome(String nome) {
        if (nome == null || nome.isBlank()) {
            throw new ErroAplicacao(400, "Nome do recurso obrigatório");
        }
    }

    private static TipoRecurso parseTipo(Object valor) {
        try {
            return TipoRecurso.valueOf(String.valueOf(valor));
        } catch (IllegalArgumentException e) {
            throw new ErroAplicacao(400, "Tipo de recurso inválido: " + valor);
        }
    }

    private static Set<String> competencias(Object valor) {
        Set<String> resultado = new HashSet<>();
        if (valor instanceof Collection<?> c) {
            c.forEach(x -> resultado.add(String.valueOf(x)));
        } else if (valor != null) {
            resultado.add(String.valueOf(valor));
        }
        return resultado;
    }
}
