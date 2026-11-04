package com.organizacao.recursos.servico;

/** Sinaliza, na fase red do TDD, que um método ainda não foi implementado. */
final class Pendente {

    private Pendente() {}

    static UnsupportedOperationException nao(String classe, String metodo) {
        return new UnsupportedOperationException("Não implementado: " + classe + "." + metodo);
    }
}
