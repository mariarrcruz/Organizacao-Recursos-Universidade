package com.organizacao.recursos.api;

import com.organizacao.recursos.dominio.ErroAplicacao;
import com.organizacao.recursos.dominio.Perfil;
import com.organizacao.recursos.dominio.Usuario;
import com.organizacao.recursos.servico.ServicoAutenticacao;
import com.organizacao.recursos.servico.ServicoUsuarios;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Login real por usuário e senha (RF-01) e cadastro de usuários com credencial (RF-15).
 * O token emitido tem 1 hora; a expiração e a assinatura são validadas em cada requisição.
 */
@RestController
public class AutenticacaoController {

    static final long VALIDADE_SEGUNDOS = 3600L;

    private final ServicoAutenticacao autenticacao;
    private final ServicoUsuarios usuarios;

    public AutenticacaoController(ServicoAutenticacao autenticacao, ServicoUsuarios usuarios) {
        this.autenticacao = autenticacao;
        this.usuarios = usuarios;
    }

    @PostMapping("/auth/login")
    public Map<String, Object> login(@RequestBody LoginJson corpo) {
        Set<Perfil> perfis = usuarios.autenticar(corpo == null ? null : corpo.id(), corpo == null ? null : corpo.senha());
        List<String> nomes = perfis.stream().map(Enum::name).toList();
        String token = autenticacao.gerarToken(corpo.id(), nomes, Instant.now().plusSeconds(VALIDADE_SEGUNDOS));
        return Map.of("token", token, "expiresIn", VALIDADE_SEGUNDOS, "perfis", nomes);
    }

    @PostMapping("/usuarios")
    public ResponseEntity<Void> cadastrar(@RequestHeader(value = "Authorization", required = false) String autorizacao,
                                          @RequestBody NovoUsuarioJson corpo) {
        Usuario ator = autenticacao.autenticar(autorizacao);
        if (corpo == null || corpo.perfis() == null || corpo.perfis().isEmpty()) {
            throw new ErroAplicacao(400, "Informe ao menos um perfil (campo perfis)");
        }
        Set<Perfil> perfis = corpo.perfis().stream().map(p -> {
            try {
                return Perfil.valueOf(p.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new ErroAplicacao(400, "Perfil inválido: " + p + " (valores: SOLICITANTE, RESPONSAVEL, ADMINISTRADOR)");
            }
        }).collect(Collectors.toSet());
        usuarios.cadastrarComSenha(ator, corpo.id(), corpo.senha(), perfis);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    public record LoginJson(String id, String senha) {}

    public record NovoUsuarioJson(String id, String senha, List<String> perfis) {}
}
