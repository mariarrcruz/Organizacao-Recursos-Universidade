package com.organizacao.recursos.servico;

import static com.organizacao.recursos.servico.Pendente.nao;

import com.organizacao.recursos.dominio.Usuario;

/** Assinaturas mínimas para a fase red. Nenhuma regra implementada. */
public class ServicoUsuarios {

    private static final String C = "ServicoUsuarios";

    public void cadastrarUsuario(Usuario ator, Usuario novo) { throw nao(C, "cadastrarUsuario"); }
    public void editarUsuario(Usuario ator, Usuario alvo) { throw nao(C, "editarUsuario"); }
    public void excluirUsuario(Usuario ator, String id) { throw nao(C, "excluirUsuario"); }
}
