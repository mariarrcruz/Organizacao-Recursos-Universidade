package com.organizacao.recursos.api;

import com.organizacao.recursos.dominio.ErroAplicacao;
import com.organizacao.recursos.dominio.EstadoReserva;
import com.organizacao.recursos.dominio.EventoAuditoria;
import com.organizacao.recursos.dominio.Recurso;
import com.organizacao.recursos.dominio.Reserva;
import com.organizacao.recursos.dominio.Usuario;
import com.organizacao.recursos.persistencia.Armazenamento;
import com.organizacao.recursos.servico.ServicoAutenticacao;
import com.organizacao.recursos.servico.ServicoReservas;
import java.util.List;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de reservas (RF-04 a RF-07, RN-05 a RN-09). Cada operação autentica pelo token JWT e
 * delega ao {@link ServicoReservas}, que decide transição (409), perfil (403) e validade (400).
 */
@RestController
@RequestMapping("/reservas")
public class ReservasController {

    private final ServicoReservas servico;
    private final ServicoAutenticacao autenticacao;
    private final Function<String, Recurso> recursos;

    /** Construtor da aplicação: o recurso é lido do armazenamento. */
    @Autowired
    public ReservasController(ServicoReservas servico, ServicoAutenticacao autenticacao, Armazenamento armazenamento) {
        this(servico, autenticacao, id -> armazenamento.buscarRecurso(id).orElse(null));
    }

    /** Construtor para testes: o recurso vem de uma função (cadastro em memória). */
    public ReservasController(ServicoReservas servico, ServicoAutenticacao autenticacao,
                              Function<String, Recurso> recursos) {
        this.servico = servico;
        this.autenticacao = autenticacao;
        this.recursos = recursos;
    }

    @PostMapping
    public ResponseEntity<ReservaJson> solicitar(@RequestHeader(value = "Authorization", required = false) String autorizacao,
                                                 @RequestBody SolicitacaoJson corpo) {
        Usuario ator = autenticacao.autenticar(autorizacao);
        Recurso recurso = recursos.apply(corpo.recursoId());
        if (recurso == null) {
            throw new ErroAplicacao(404, "Recurso não encontrado: " + corpo.recursoId());
        }
        Reserva nova = servico.solicitar(ator, recurso, Datas.instante(corpo.inicio()), Datas.instante(corpo.fim()));
        return ResponseEntity.status(HttpStatus.CREATED).body(ReservaJson.de(nova));
    }

    @GetMapping
    public List<ReservaJson> listarDoRecurso(@RequestHeader(value = "Authorization", required = false) String autorizacao,
                                             @RequestParam("recursoId") String recursoId) {
        Usuario ator = autenticacao.autenticar(autorizacao);
        return servico.listarDoRecurso(ator, recursoId).stream().map(ReservaJson::de).toList();
    }

    @GetMapping("/{id}")
    public ReservaJson consultar(@RequestHeader(value = "Authorization", required = false) String autorizacao,
                                 @PathVariable("id") String id) {
        Usuario ator = autenticacao.autenticar(autorizacao);
        return ReservaJson.de(servico.consultar(ator, id));
    }

    @PutMapping("/{id}")
    public ReservaJson alterar(@RequestHeader(value = "Authorization", required = false) String autorizacao,
                               @PathVariable("id") String id, @RequestBody PeriodoJson corpo) {
        Usuario ator = autenticacao.autenticar(autorizacao);
        return ReservaJson.de(servico.alterar(ator, referencia(id),
                Datas.instante(corpo.inicio()), Datas.instante(corpo.fim())));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@RequestHeader(value = "Authorization", required = false) String autorizacao,
                                        @PathVariable("id") String id) {
        Usuario ator = autenticacao.autenticar(autorizacao);
        servico.excluir(ator, referencia(id));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/aprovacao")
    public ReservaJson aprovar(@RequestHeader(value = "Authorization", required = false) String autorizacao,
                               @PathVariable("id") String id) {
        Usuario ator = autenticacao.autenticar(autorizacao);
        return ReservaJson.de(servico.aprovar(ator, referencia(id)));
    }

    @PostMapping("/{id}/rejeicao")
    public ReservaJson rejeitar(@RequestHeader(value = "Authorization", required = false) String autorizacao,
                                @PathVariable("id") String id, @RequestBody(required = false) JustificativaJson corpo) {
        Usuario ator = autenticacao.autenticar(autorizacao);
        return ReservaJson.de(servico.rejeitar(ator, referencia(id), justificativa(corpo)));
    }

    @PostMapping("/{id}/inicio-uso")
    public ReservaJson iniciarUso(@RequestHeader(value = "Authorization", required = false) String autorizacao,
                                  @PathVariable("id") String id) {
        Usuario ator = autenticacao.autenticar(autorizacao);
        return ReservaJson.de(servico.iniciarUso(ator, referencia(id)));
    }

    @PostMapping("/{id}/conclusao")
    public ReservaJson concluir(@RequestHeader(value = "Authorization", required = false) String autorizacao,
                                @PathVariable("id") String id) {
        Usuario ator = autenticacao.autenticar(autorizacao);
        return ReservaJson.de(servico.concluir(ator, referencia(id)));
    }

    @PostMapping("/{id}/cancelamento")
    public ReservaJson cancelar(@RequestHeader(value = "Authorization", required = false) String autorizacao,
                                @PathVariable("id") String id, @RequestBody(required = false) JustificativaJson corpo) {
        Usuario ator = autenticacao.autenticar(autorizacao);
        return ReservaJson.de(servico.cancelar(ator, referencia(id), justificativa(corpo)));
    }

    @PostMapping("/{id}/nao-comparecimento")
    public ReservaJson registrarNaoComparecimento(@RequestHeader(value = "Authorization", required = false) String autorizacao,
                                                  @PathVariable("id") String id) {
        Usuario ator = autenticacao.autenticar(autorizacao);
        return ReservaJson.de(servico.registrarNaoComparecimento(ator, referencia(id)));
    }

    /** Transição genérica: permite testar qualquer par de estados, inclusive os proibidos (RN-05). */
    @PostMapping("/{id}/transicoes")
    public ReservaJson transicionar(@RequestHeader(value = "Authorization", required = false) String autorizacao,
                                    @PathVariable("id") String id, @RequestBody TransicaoJson corpo) {
        Usuario ator = autenticacao.autenticar(autorizacao);
        EstadoReserva destino = destino(corpo.destino());
        return ReservaJson.de(servico.transicionar(ator, referencia(id), destino, corpo.justificativa()));
    }

    @GetMapping("/{id}/auditoria")
    public List<EventoJson> auditoria(@RequestHeader(value = "Authorization", required = false) String autorizacao,
                                      @PathVariable("id") String id) {
        Usuario ator = autenticacao.autenticar(autorizacao);
        return servico.eventosDe(ator, id).stream().map(EventoJson::de).toList();
    }

    /**
     * Referência a uma reserva existente. Os métodos do serviço que alteram ou transitam usam só o id
     * da reserva; a regra de decisão (409 antes de 403) continua no serviço.
     */
    private static Reserva referencia(String id) {
        return new Reserva(id, null, null, null, null, null);
    }

    private static String justificativa(JustificativaJson corpo) {
        return corpo == null ? null : corpo.justificativa();
    }

    private static EstadoReserva destino(String texto) {
        try {
            return EstadoReserva.valueOf(texto);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ErroAplicacao(400, "Estado de destino inválido: " + texto);
        }
    }

    // ------------------------------------------------------------ contrato JSON

    public record SolicitacaoJson(String recursoId, String inicio, String fim) {}

    public record PeriodoJson(String inicio, String fim) {}

    public record JustificativaJson(String justificativa) {}

    public record TransicaoJson(String destino, String justificativa) {}

    public record ReservaJson(String id, String recursoId, String solicitanteId, String inicio, String fim,
                              String estado) {
        static ReservaJson de(Reserva r) {
            return new ReservaJson(r.getId(), r.getRecurso().id(), r.getSolicitante().id(),
                    r.getInicio().toString(), r.getFim().toString(), r.getEstado().name());
        }
    }

    public record EventoJson(String acao, String atorId, String estadoAnterior, String estadoNovo,
                             String justificativa) {
        static EventoJson de(EventoAuditoria e) {
            return new EventoJson(e.acao(), e.atorId(),
                    e.estadoAnterior() == null ? null : e.estadoAnterior().name(),
                    e.estadoNovo() == null ? null : e.estadoNovo().name(), e.justificativa());
        }
    }
}
