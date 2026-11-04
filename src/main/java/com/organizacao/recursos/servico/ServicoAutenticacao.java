package com.organizacao.recursos.servico;

import static com.organizacao.recursos.servico.Pendente.nao;

import com.organizacao.recursos.dominio.Usuario;
import java.time.Instant;
import java.util.Collection;

/** Assinaturas mínimas para a fase red. Nenhuma regra implementada. */
public class ServicoAutenticacao {

    private static final String C = "ServicoAutenticacao";

    /** Gera JWT. Parâmetros nulos omitem o claim correspondente. */
    public String gerarToken(String sub, Collection<String> roles, Instant expiracao) { throw nao(C, "gerarToken"); }
    /** Valida cabeçalho "Authorization: Bearer ..." e devolve o usuário. */
    public Usuario autenticar(String authorizationHeader) { throw nao(C, "autenticar"); }
    public String gerarRefreshToken(String sub, Instant expiracao) { throw nao(C, "gerarRefreshToken"); }
    public String renovar(String refreshToken) { throw nao(C, "renovar"); }
}
