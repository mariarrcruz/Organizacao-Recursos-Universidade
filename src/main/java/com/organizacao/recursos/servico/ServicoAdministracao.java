package com.organizacao.recursos.servico;

import static com.organizacao.recursos.servico.Autorizacao.exigirPerfil;

import com.organizacao.recursos.dominio.ErroAplicacao;
import com.organizacao.recursos.dominio.Perfil;
import com.organizacao.recursos.dominio.Usuario;
import java.util.HashMap;
import java.util.Map;

/** Parâmetros do sistema. Restrito ao Administrador (RF-15.A.11). */
public class ServicoAdministracao {

    private final Map<String, String> parametros = new HashMap<>();

    public synchronized void alterarParametro(Usuario ator, String chave, String valor) {
        exigirPerfil(ator, Perfil.ADMINISTRADOR);
        if (chave == null || chave.isBlank()) {
            throw new ErroAplicacao(400, "Chave do parâmetro obrigatória");
        }
        parametros.put(chave, valor);
    }
}
