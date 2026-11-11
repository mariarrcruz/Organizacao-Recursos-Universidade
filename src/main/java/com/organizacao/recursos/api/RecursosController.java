package com.organizacao.recursos.api;

import com.organizacao.recursos.dominio.ErroAplicacao;
import com.organizacao.recursos.dominio.Perfil;
import com.organizacao.recursos.dominio.Recurso;
import com.organizacao.recursos.dominio.StatusRecurso;
import com.organizacao.recursos.dominio.TipoRecurso;
import com.organizacao.recursos.dominio.Usuario;
import com.organizacao.recursos.persistencia.Armazenamento;
import com.organizacao.recursos.servico.ServicoAutenticacao;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Cadastro de recursos (salas, professores, materiais, equipamentos). Restrito a Administrador. */
@RestController
@RequestMapping("/recursos")
public class RecursosController {

    private final ServicoAutenticacao autenticacao;
    private final Armazenamento armazenamento;

    public RecursosController(ServicoAutenticacao autenticacao, Armazenamento armazenamento) {
        this.autenticacao = autenticacao;
        this.armazenamento = armazenamento;
    }

    @PostMapping
    public ResponseEntity<Void> cadastrar(@RequestHeader(value = "Authorization", required = false) String autorizacao,
                                          @RequestBody RecursoJson corpo) {
        Usuario ator = autenticacao.autenticar(autorizacao);
        if (!ator.perfis().contains(Perfil.ADMINISTRADOR)) {
            throw new ErroAplicacao(403, "Perfil ADMINISTRADOR exigido");
        }
        if (corpo.id() == null || corpo.id().isBlank()) {
            throw new ErroAplicacao(400, "Informe o id do recurso (campo id)");
        }
        if (corpo.capacidade() < 0) {
            throw new ErroAplicacao(400, "Capacidade não pode ser negativa (campo capacidade)");
        }
        TipoRecurso tipo = enumDe(TipoRecurso.class, corpo.tipo(), "tipo", "SALA, PROFESSOR, MATERIAL ou EQUIPAMENTO");
        StatusRecurso status = enumDe(StatusRecurso.class, corpo.status(), "status", "DISPONIVEL, INDISPONIVEL ou MANUTENCAO");
        armazenamento.gravarRecurso(new Recurso(corpo.id(), tipo, corpo.nome(),
                corpo.localizacao(), corpo.capacidade(), corpo.restrito(), status,
                corpo.competencias() == null ? Set.of() : Set.copyOf(corpo.competencias())));
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    public record RecursoJson(String id, String tipo, String nome, String localizacao, int capacidade,
                              boolean restrito, String status, List<String> competencias) {}

    /** Converte o texto do campo no enum; valor ausente ou desconhecido vira 400 com os valores aceitos. */
    private static <E extends Enum<E>> E enumDe(Class<E> tipo, String valor, String campo, String aceitos) {
        if (valor == null || valor.isBlank()) {
            throw new ErroAplicacao(400, "Informe o campo " + campo + " (valores: " + aceitos + ")");
        }
        try {
            return Enum.valueOf(tipo, valor.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ErroAplicacao(400, "Valor inválido para " + campo + ": " + valor + " (valores: " + aceitos + ")");
        }
    }
}
