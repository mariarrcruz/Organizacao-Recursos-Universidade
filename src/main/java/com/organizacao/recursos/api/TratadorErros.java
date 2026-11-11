package com.organizacao.recursos.api;

import com.organizacao.recursos.dominio.ErroAplicacao;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Converte as regras de domínio em respostas HTTP com o código do serviço e corpo {"erro": "..."}. */
@RestControllerAdvice
public class TratadorErros {

    @ExceptionHandler(ErroAplicacao.class)
    public ResponseEntity<Map<String, String>> erroDeAplicacao(ErroAplicacao erro) {
        return ResponseEntity.status(erro.getStatus()).body(Map.of("erro", erro.getMessage()));
    }
}
