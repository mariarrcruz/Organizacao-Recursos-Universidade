package com.organizacao.recursos.api;

import com.organizacao.recursos.dominio.ErroAplicacao;
import com.organizacao.recursos.dominio.Recurso;
import com.organizacao.recursos.dominio.StatusRecurso;
import com.organizacao.recursos.dominio.Usuario;
import com.organizacao.recursos.persistencia.Armazenamento;
import com.organizacao.recursos.servico.ServicoAutenticacao;
import com.organizacao.recursos.servico.ServicoReservas;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consulta de disponibilidade de um recurso em um intervalo. Exige autenticação (401 sem token).
 * Responde só se está livre: não expõe reservas nem solicitantes de terceiros.
 */
@RestController
@RequestMapping("/disponibilidade")
public class DisponibilidadeController {

    private final ServicoReservas servico;
    private final ServicoAutenticacao autenticacao;
    private final Armazenamento armazenamento;

    @Autowired
    public DisponibilidadeController(ServicoReservas servico, ServicoAutenticacao autenticacao, Armazenamento armazenamento) {
        this.servico = servico;
        this.autenticacao = autenticacao;
        this.armazenamento = armazenamento;
    }

    @GetMapping
    public DisponibilidadeJson consultar(@RequestHeader(value = "Authorization", required = false) String autorizacao,
                                         @RequestParam("recursoId") String recursoId,
                                         @RequestParam("inicio") String inicio,
                                         @RequestParam("fim") String fim) {
        Usuario ator = autenticacao.autenticar(autorizacao);
        Recurso recurso = armazenamento.buscarRecurso(recursoId)
                .orElseThrow(() -> new ErroAplicacao(404, "Recurso não encontrado: " + recursoId));
        LocalDateTime ini = Datas.instante(inicio, "inicio");
        LocalDateTime termino = Datas.instante(fim, "fim");
        boolean livre = recurso.status() == StatusRecurso.DISPONIVEL && servico.disponivel(ator, recursoId, ini, termino);
        return new DisponibilidadeJson(recursoId, inicio, fim, livre);
    }

    public record DisponibilidadeJson(String recursoId, String inicio, String fim, boolean disponivel) {}
}
