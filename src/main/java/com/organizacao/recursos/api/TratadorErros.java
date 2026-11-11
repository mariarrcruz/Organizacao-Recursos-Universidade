package com.organizacao.recursos.api;

import com.organizacao.recursos.dominio.ErroAplicacao;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger LOG = LoggerFactory.getLogger(TratadorErros.class);

    @ExceptionHandler(ErroAplicacao.class)
    public ResponseEntity<ProblemDetail> erroDeAplicacao(ErroAplicacao erro) {
        return problema(HttpStatus.valueOf(erro.getStatus()), erro.getMessage());
    }

    /** Falha não prevista (banco, bug): o detalhe técnico vai para o log do servidor, nunca para a resposta. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> falhaInesperada(Exception falha) {
        LOG.error("Falha não tratada na requisição", falha);
        return problema(HttpStatus.INTERNAL_SERVER_ERROR,
                "Erro interno do servidor. Tente novamente; se persistir, informe o horário ao suporte.");
    }

    static ResponseEntity<ProblemDetail> problema(HttpStatus status, String detalhe) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detalhe);
        pd.setType(URI.create("about:blank"));
        pd.setTitle(status.getReasonPhrase());
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(pd);
    }
}
