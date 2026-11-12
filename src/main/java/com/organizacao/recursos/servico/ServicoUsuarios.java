package com.organizacao.recursos.servico;

import static com.organizacao.recursos.servico.Autorizacao.exigirPerfil;

import com.organizacao.recursos.dominio.ErroAplicacao;
import com.organizacao.recursos.dominio.Perfil;
import com.organizacao.recursos.dominio.Usuario;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Gestão de usuários do sistema e credenciais de login (RF-01, RF-15).
 *
 * <p>Senhas são guardadas como PBKDF2-HMAC-SHA256 com sal aleatório. Cadastro e exclusão: somente
 * Administrador. Os perfis de cada usuário ficam na credencial e são devolvidos no login.
 */
public class ServicoUsuarios {

    private static final int ITERACOES = 210_000;
    private static final int TAMANHO_CHAVE = 256;
    private static final String PREFIXO = "pbkdf2_sha256";

    private final Map<String, Usuario> usuarios = new HashMap<>();
    private final Map<String, String> credenciais = new HashMap<>();
    private final SecureRandom random = new SecureRandom();

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
        credenciais.remove(id);
    }

    /** Cadastra (ou troca a senha de) um usuário com login. Somente Administrador. */
    public synchronized void cadastrarComSenha(Usuario ator, String id, String senha, Set<Perfil> perfis) {
        exigirPerfil(ator, Perfil.ADMINISTRADOR);
        gravarCredencial(id, senha, perfis);
    }

    /** Cadastro inicial sem ator autenticado. Usado só para semear o primeiro administrador na inicialização. */
    public synchronized void semearAdministrador(String id, String senha) {
        if (credenciais.values().stream().anyMatch(c -> c.contains(";ADMINISTRADOR"))) {
            return;
        }
        gravarCredencial(id, senha, EnumSet.of(Perfil.ADMINISTRADOR));
    }

    /**
     * Verifica id e senha e devolve os perfis do usuário. Falha genérica (401) para id inexistente e senha
     * errada, para não revelar qual dos dois está incorreto.
     */
    public synchronized Set<Perfil> autenticar(String id, String senha) {
        String armazenado = id == null ? null : credenciais.get(id);
        if (armazenado == null || senha == null) {
            throw new ErroAplicacao(401, "Usuário ou senha inválidos");
        }
        // formato: pbkdf2_sha256$iteracoes$sal$hash;PERFIS
        String[] partes = armazenado.substring(0, armazenado.indexOf(';')).split("\\$");
        String perfisTexto = armazenado.substring(armazenado.indexOf(';') + 1);
        boolean ok;
        try {
            int iteracoes = Integer.parseInt(partes[1]);
            byte[] sal = Base64.getDecoder().decode(partes[2]);
            byte[] esperado = Base64.getDecoder().decode(partes[3]);
            byte[] calculado = derivar(senha, sal, iteracoes);
            ok = MessageDigest.isEqual(esperado, calculado);
        } catch (GeneralSecurityException | IllegalArgumentException | ArrayIndexOutOfBoundsException e) {
            ok = false;
        }
        if (!ok) {
            throw new ErroAplicacao(401, "Usuário ou senha inválidos");
        }
        Set<Perfil> perfis = EnumSet.noneOf(Perfil.class);
        for (String p : perfisTexto.split(",")) {
            if (!p.isBlank()) {
                perfis.add(Perfil.valueOf(p));
            }
        }
        return perfis;
    }

    // ---------------------------------------------------------------- internos

    private void gravarCredencial(String id, String senha, Set<Perfil> perfis) {
        if (id == null || id.isBlank()) {
            throw new ErroAplicacao(400, "Informe o id do usuário (campo id)");
        }
        if (senha == null || senha.length() < 8) {
            throw new ErroAplicacao(400, "A senha deve ter pelo menos 8 caracteres (campo senha)");
        }
        if (perfis == null || perfis.isEmpty()) {
            throw new ErroAplicacao(400, "Informe ao menos um perfil (campo perfis)");
        }
        byte[] sal = new byte[16];
        random.nextBytes(sal);
        try {
            byte[] hash = derivar(senha, sal, ITERACOES);
            String perfisTexto = String.join(",", perfis.stream().map(Enum::name).sorted().toList());
            credenciais.put(id, PREFIXO + "$" + ITERACOES + "$" + Base64.getEncoder().encodeToString(sal)
                    + "$" + Base64.getEncoder().encodeToString(hash) + ";" + perfisTexto);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao derivar senha", e);
        }
    }

    private static byte[] derivar(String senha, byte[] sal, int iteracoes) throws GeneralSecurityException {
        SecretKeyFactory fabrica = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        return fabrica.generateSecret(new PBEKeySpec(senha.toCharArray(), sal, iteracoes, TAMANHO_CHAVE)).getEncoded();
    }
}
