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

    /** Corpo ausente ou JSON malformado: mensagem em português em vez da genérica do Spring (DEF-006). */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            org.springframework.http.converter.HttpMessageNotReadableException ex,
            org.springframework.http.HttpHeaders headers, org.springframework.http.HttpStatusCode status,
            org.springframework.web.context.request.WebRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Corpo da requisição ausente ou malformado: envie um JSON válido");
        pd.setType(URI.create("about:blank"));
        pd.setTitle(HttpStatus.BAD_REQUEST.getReasonPhrase());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(pd);
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
