package com.organizacao.recursos.api;

import com.organizacao.recursos.dominio.ErroAplicacao;
import com.organizacao.recursos.dominio.Movimentacao;
import com.organizacao.recursos.dominio.Reserva;
import com.organizacao.recursos.dominio.Usuario;
import com.organizacao.recursos.servico.ServicoAutenticacao;
import com.organizacao.recursos.servico.ServicoMovimentacoes;
import com.organizacao.recursos.servico.ServicoReservas;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Retirada e devolução de materiais vinculadas a uma reserva (RF-09). Somente Responsável vinculado ao
 * recurso da reserva registra movimentações; a regra de vínculo é aplicada por {@link ServicoReservas#consultar}.
 */
@RestController
@RequestMapping("/movimentacoes")
public class MovimentacoesController {

    private final ServicoAutenticacao autenticacao;
    private final ServicoMovimentacoes movimentacoes;
    private final ServicoReservas reservas;

    public MovimentacoesController(ServicoAutenticacao autenticacao, ServicoMovimentacoes movimentacoes,
                                   ServicoReservas reservas) {
        this.autenticacao = autenticacao;
        this.movimentacoes = movimentacoes;
        this.reservas = reservas;
    }

    @PostMapping("/retirada")
    public Movimentacao retirar(@RequestHeader(value = "Authorization", required = false) String autorizacao,
                                @RequestBody MovimentacaoJson corpo) {
        Usuario ator = autenticacao.autenticar(autorizacao);
        Reserva reserva = reservaDe(ator, corpo);
        return movimentacoes.registrarRetirada(ator, reserva, materialDe(corpo), condicaoDe(corpo));
    }

    @PostMapping("/devolucao")
    public Movimentacao devolver(@RequestHeader(value = "Authorization", required = false) String autorizacao,
                                 @RequestBody MovimentacaoJson corpo) {
        Usuario ator = autenticacao.autenticar(autorizacao);
        Reserva reserva = reservaDe(ator, corpo);
        return movimentacoes.registrarDevolucao(ator, reserva, materialDe(corpo), condicaoDe(corpo));
    }

    /** Histórico de movimentações de um material, da mais antiga para a mais recente. */
    @GetMapping("/{materialId}")
    public List<Movimentacao> historico(@RequestHeader(value = "Authorization", required = false) String autorizacao,
                                        @PathVariable("materialId") String materialId) {
        Usuario ator = autenticacao.autenticar(autorizacao);
        if (!ator.perfis().contains(com.organizacao.recursos.dominio.Perfil.RESPONSAVEL)
                && !ator.perfis().contains(com.organizacao.recursos.dominio.Perfil.ADMINISTRADOR)) {
            throw new ErroAplicacao(403, "Perfil RESPONSAVEL ou ADMINISTRADOR exigido");
        }
        return movimentacoes.historico(materialId);
    }

    private Reserva reservaDe(Usuario ator, MovimentacaoJson corpo) {
        if (corpo == null || corpo.reservaId() == null || corpo.reservaId().isBlank()) {
            throw new ErroAplicacao(400, "Informe o campo reservaId");
        }
        return reservas.consultar(ator, corpo.reservaId());
    }

    private static String materialDe(MovimentacaoJson corpo) {
        if (corpo.materialId() == null || corpo.materialId().isBlank()) {
            throw new ErroAplicacao(400, "Informe o campo materialId");
        }
        return corpo.materialId();
    }

    private static String condicaoDe(MovimentacaoJson corpo) {
        return corpo.condicao() == null ? "" : corpo.condicao();
    }

    public record MovimentacaoJson(String reservaId, String materialId, String condicao) {}
}
