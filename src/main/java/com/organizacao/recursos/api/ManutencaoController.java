package com.organizacao.recursos.api;

import com.organizacao.recursos.dominio.Manutencao;
import com.organizacao.recursos.dominio.Usuario;
import com.organizacao.recursos.servico.ServicoAutenticacao;
import com.organizacao.recursos.servico.ServicoReservas;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Cadastro de períodos de manutenção (RF-08, RN-04). Restrito a Administrador pelo serviço. */
@RestController
@RequestMapping("/manutencoes")
public class ManutencaoController {

    private final ServicoReservas servico;
    private final ServicoAutenticacao autenticacao;

    public ManutencaoController(ServicoReservas servico, ServicoAutenticacao autenticacao) {
        this.servico = servico;
        this.autenticacao = autenticacao;
    }

    @PostMapping
    public ResponseEntity<Void> criar(@RequestHeader(value = "Authorization", required = false) String autorizacao,
                                      @RequestBody ManutencaoJson corpo) {
        Usuario ator = autenticacao.autenticar(autorizacao);
        servico.criarManutencao(ator, new Manutencao(corpo.recursoId(),
                Datas.instante(corpo.inicio()), Datas.instante(corpo.fim())));
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    public record ManutencaoJson(String recursoId, String inicio, String fim) {}
}
