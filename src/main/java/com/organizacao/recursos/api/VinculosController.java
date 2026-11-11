package com.organizacao.recursos.api;

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

/** Vínculo Responsável–recurso (D3). Só Administrador cadastra. */
@RestController
@RequestMapping("/vinculos")
public class VinculosController {

    private final ServicoReservas servico;
    private final ServicoAutenticacao autenticacao;

    public VinculosController(ServicoReservas servico, ServicoAutenticacao autenticacao) {
        this.servico = servico;
        this.autenticacao = autenticacao;
    }

    @PostMapping
    public ResponseEntity<Void> vincular(@RequestHeader(value = "Authorization", required = false) String autorizacao,
                                         @RequestBody VinculoJson corpo) {
        Usuario ator = autenticacao.autenticar(autorizacao);
        servico.vincularResponsavel(ator, corpo.responsavelId(), corpo.recursoId());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    public record VinculoJson(String responsavelId, String recursoId) {}
}
