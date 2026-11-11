package com.organizacao.recursos.integracao;

/**
 * Erro de integração já traduzido para o domínio. A mensagem é neutra: nunca contém corpo, cabeçalho
 * ou credencial do provedor.
 */
public class ErroIntegracao extends RuntimeException {

    public enum Tipo {
        /** Timeout, falha de conexão ou status 5xx. Pode ser transitório. */
        INDISPONIVEL,
        /** Status 404. */
        NAO_ENCONTRADO,
        /** Status 4xx diferente de 404 (credencial ou requisição). Não deve ser repetido. */
        REJEITADO,
        /** Status 200 com corpo fora do contrato. Nada deve ser persistido. */
        CONTRATO_INVALIDO
    }

    private final Tipo tipo;

    public ErroIntegracao(Tipo tipo, String mensagem) {
        super(mensagem);
        this.tipo = tipo;
    }

    public Tipo getTipo() {
        return tipo;
    }
}
