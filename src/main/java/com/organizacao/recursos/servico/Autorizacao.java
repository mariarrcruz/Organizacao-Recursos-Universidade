package com.organizacao.recursos.servico;

import com.organizacao.recursos.dominio.ErroAplicacao;
import com.organizacao.recursos.dominio.Perfil;
import com.organizacao.recursos.dominio.Usuario;

/** Verificações de autenticação (401) e de perfil (403) compartilhadas pelos serviços. */
final class Autorizacao {

    private Autorizacao() {}

    static void exigirAutenticado(Usuario ator) {
        if (ator == null) {
            throw new ErroAplicacao(401, "Autenticação obrigatória");
        }
    }

    static void exigirPerfil(Usuario ator, Perfil perfil) {
        exigirAutenticado(ator);
        if (!tem(ator, perfil)) {
            throw new ErroAplicacao(403, "Perfil " + perfil + " exigido");
        }
    }

    static void exigirAlgumPerfil(Usuario ator, Perfil... perfis) {
        exigirAutenticado(ator);
        for (Perfil p : perfis) {
            if (tem(ator, p)) {
                return;
            }
        }
        throw new ErroAplicacao(403, "Perfil insuficiente para a operação");
    }

    static boolean tem(Usuario ator, Perfil perfil) {
        return ator != null && ator.perfis() != null && ator.perfis().contains(perfil);
    }
}
