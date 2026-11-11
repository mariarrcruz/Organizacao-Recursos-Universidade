package com.organizacao.recursos.api;

import com.organizacao.recursos.dominio.ErroAplicacao;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Converte erros em ProblemDetail (RFC 9457, application/problem+json). Só a mensagem de negócio
 * aparece no detalhe; erros inesperados viram 500 genérico, e o detalhe técnico fica no log do servidor
 * (DEF-003, DEF-004).
 */
@RestControllerAdvice
public class TratadorErros extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ErroAplicacao.class)
    public ResponseEntity<ProblemDetail> erroDeAplicacao(ErroAplicacao erro) {
        return problema(HttpStatus.valueOf(erro.getStatus()), erro.getMessage());
    }

    static ResponseEntity<ProblemDetail> problema(HttpStatus status, String detalhe) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detalhe);
        pd.setType(URI.create("about:blank"));
        pd.setTitle(status.getReasonPhrase());
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(pd);
    }
}
