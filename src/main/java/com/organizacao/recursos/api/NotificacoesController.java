package com.organizacao.recursos.api;

import com.organizacao.recursos.dominio.Usuario;
import com.organizacao.recursos.servico.RegistroNotificacoes;
import com.organizacao.recursos.servico.ServicoAutenticacao;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Notificações simuladas do próprio usuário (RF-11). Cada um vê somente as suas. */
@RestController
@RequestMapping("/notificacoes")
public class NotificacoesController {

    private final ServicoAutenticacao autenticacao;
    private final RegistroNotificacoes registro;

    public NotificacoesController(ServicoAutenticacao autenticacao, RegistroNotificacoes registro) {
        this.autenticacao = autenticacao;
        this.registro = registro;
    }

    @GetMapping
    public List<RegistroNotificacoes.Notificacao> minhas(
            @RequestHeader(value = "Authorization", required = false) String autorizacao) {
        Usuario ator = autenticacao.autenticar(autorizacao);
        return registro.de(ator.id());
    }
}
