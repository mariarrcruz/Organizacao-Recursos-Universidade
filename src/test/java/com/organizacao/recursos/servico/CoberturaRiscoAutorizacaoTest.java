package com.organizacao.recursos.servico;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.organizacao.recursos.dominio.ErroAplicacao;
import com.organizacao.recursos.dominio.Perfil;
import com.organizacao.recursos.dominio.Usuario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Lacuna de risco em Autorizacao: usuário sem conjunto de perfis não recebe permissão. */
class CoberturaRiscoAutorizacaoTest {

    @Test
    @DisplayName("Usuário com perfis nulos é negado por perfil (403)")
    void perfisNulosNegadosPorPerfil() {
        Usuario semPerfis = new Usuario("x", null);
        ErroAplicacao e = assertThrows(ErroAplicacao.class,
                () -> Autorizacao.exigirPerfil(semPerfis, Perfil.RESPONSAVEL));
        assertEquals(403, e.getStatus());
    }

    @Test
    @DisplayName("Usuário com perfis nulos é negado por alguma-perfil (403)")
    void perfisNulosNegadosPorAlgumPerfil() {
        Usuario semPerfis = new Usuario("x", null);
        ErroAplicacao e = assertThrows(ErroAplicacao.class,
                () -> Autorizacao.exigirAlgumPerfil(semPerfis, Perfil.RESPONSAVEL, Perfil.ADMINISTRADOR));
        assertEquals(403, e.getStatus());
    }
}
