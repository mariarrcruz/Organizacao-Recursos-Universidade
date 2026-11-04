package com.organizacao.recursos.servico;

import static com.organizacao.recursos.servico.Autorizacao.exigirPerfil;

import com.organizacao.recursos.dominio.ErroAplicacao;
import com.organizacao.recursos.dominio.Perfil;
import com.organizacao.recursos.dominio.Usuario;
import java.util.HashMap;
import java.util.Map;

/** Gestão de usuários do sistema. Restrita ao Administrador (RF-15). */
public class ServicoUsuarios {

    private final Map<String, Usuario> usuarios = new HashMap<>();

    public synchronized void cadastrarUsuario(Usuario ator, Usuario novo) {
        exigirPerfil(ator, Perfil.ADMINISTRADOR);
        if (novo == null || novo.id() == null || novo.id().isBlank()) {
            throw new ErroAplicacao(400, "Usuário inválido");
        }
        usuarios.put(novo.id(), novo);
    }

    public synchronized void editarUsuario(Usuario ator, Usuario alvo) {
        exigirPerfil(ator, Perfil.ADMINISTRADOR);
        if (alvo == null || alvo.id() == null) {
            throw new ErroAplicacao(400, "Usuário inválido");
        }
        usuarios.put(alvo.id(), alvo);
    }

    /** Idempotente: excluir usuário inexistente não é erro. */
    public synchronized void excluirUsuario(Usuario ator, String id) {
        exigirPerfil(ator, Perfil.ADMINISTRADOR);
        usuarios.remove(id);
    }
}
