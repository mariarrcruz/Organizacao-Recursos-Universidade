package com.organizacao.recursos.dominio;

/** Erro de regra ou de acesso, com código HTTP equivalente. */
public class ErroAplicacao extends RuntimeException {

    private final int status;

    public ErroAplicacao(int status, String mensagem) {
        super(mensagem);
        this.status = status;
    }

    public int getStatus() { return status; }
}
