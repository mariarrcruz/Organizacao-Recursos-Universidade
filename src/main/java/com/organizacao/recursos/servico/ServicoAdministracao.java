package com.organizacao.recursos.servico;

import static com.organizacao.recursos.servico.Pendente.nao;

import com.organizacao.recursos.dominio.Usuario;

/** Assinaturas mínimas para a fase red. Nenhuma regra implementada. */
public class ServicoAdministracao {

    private static final String C = "ServicoAdministracao";

    public void alterarParametro(Usuario ator, String chave, String valor) { throw nao(C, "alterarParametro"); }
}
