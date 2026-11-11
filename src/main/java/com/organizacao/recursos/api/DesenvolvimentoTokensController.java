package com.organizacao.recursos.api;

import com.organizacao.recursos.servico.ServicoAutenticacao;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SOMENTE LABORATÓRIO: emite token JWT para um sub e perfis informados, para testes de carga locais
 * (JMeter). Ativo apenas com app.dev.tokens=true. Em produção não deve existir: o login real é pendência.
 */
@RestController
@RequestMapping("/dev/tokens")
@ConditionalOnProperty(name = "app.dev.tokens", havingValue = "true")
public class DesenvolvimentoTokensController {

    private final ServicoAutenticacao autenticacao;

    public DesenvolvimentoTokensController(ServicoAutenticacao autenticacao) {
        this.autenticacao = autenticacao;
    }

    @PostMapping
    public Map<String, String> emitir(@RequestBody PedidoToken pedido) {
        return Map.of("token", autenticacao.gerarToken(pedido.sub(), pedido.perfis(), Instant.now().plusSeconds(3600)));
    }

    public record PedidoToken(String sub, List<String> perfis) {}
}
