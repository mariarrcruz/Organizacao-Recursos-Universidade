package com.organizacao.recursos.servico;

import static com.organizacao.recursos.servico.Pendente.nao;

import com.organizacao.recursos.dominio.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Assinaturas mínimas para a fase red. Nenhuma regra de negócio implementada.
 * {@code registrarRecursoExistente} é armazenamento em memória sem regra, usado só para semear cenários.
 */
public class ServicoRecursos {

    private static final String C = "ServicoRecursos";

    private final List<Recurso> armazenados = new ArrayList<>();

    public ServicoRecursos(ServicoReservas reservas) { }

    public void registrarRecursoExistente(Recurso recurso) { armazenados.add(recurso); }
    public Recurso cadastrarRecurso(Usuario ator, Recurso recurso) { throw nao(C, "cadastrarRecurso"); }
    public Recurso consultarRecurso(String id) { throw nao(C, "consultarRecurso"); }
    public Recurso atualizarRecurso(Usuario ator, String id, String novoNome) { throw nao(C, "atualizarRecurso"); }
    public void excluirRecurso(Usuario ator, String id) { throw nao(C, "excluirRecurso"); }
    public List<Recurso> pesquisar(Usuario ator, Map<String, Object> filtros) { throw nao(C, "pesquisar"); }
}
