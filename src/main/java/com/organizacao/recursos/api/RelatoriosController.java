package com.organizacao.recursos.api;

import com.organizacao.recursos.dominio.ErroAplicacao;
import com.organizacao.recursos.dominio.Usuario;
import com.organizacao.recursos.servico.ServicoAutenticacao;
import com.organizacao.recursos.servico.ServicoRelatorios;
import com.organizacao.recursos.servico.ServicoRelatorios.RelatorioUtilizacao;
import com.organizacao.recursos.servico.ServicoRelatorios.UtilizacaoRecurso;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Relatórios gerenciais por HTTP (RF-12). Acesso só para Responsável e Administrador:
 * sem token, 401; Solicitante, 403.
 */
@RestController
@RequestMapping("/relatorios")
public class RelatoriosController {

    private final ServicoRelatorios relatorios;
    private final ServicoAutenticacao autenticacao;

    @Autowired
    public RelatoriosController(ServicoRelatorios relatorios, ServicoAutenticacao autenticacao) {
        this.relatorios = relatorios;
        this.autenticacao = autenticacao;
    }

    /** Horas por recurso, carga horária por professor e conflitos evitados no período [inicio, fim] (datas ISO, dias inclusivos). */
    @GetMapping("/utilizacao")
    public RelatorioUtilizacaoJson utilizacao(@RequestHeader(value = "Authorization", required = false) String autorizacao,
                                              @RequestParam("inicio") String inicio,
                                              @RequestParam("fim") String fim) {
        Usuario ator = autenticacao.autenticar(autorizacao);
        return RelatorioUtilizacaoJson.de(relatorios.utilizacao(ator, data(inicio), data(fim)));
    }

    private static LocalDate data(String texto) {
        try {
            return LocalDate.parse(texto);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new ErroAplicacao(400, "Data inválida: " + texto);
        }
    }

    /** Horas em decimal (ex.: 2,5 h = 2.5), com segundos de precisão. */
    static double emHoras(Duration d) {
        return d.toSeconds() / 3600.0;
    }

    public record RelatorioUtilizacaoJson(String inicio, String fim, List<RecursoJson> recursos,
                                          List<ProfessorJson> cargaHorariaPorProfessor,
                                          ConflitosJson conflitosEvitados) {

        static RelatorioUtilizacaoJson de(RelatorioUtilizacao r) {
            return new RelatorioUtilizacaoJson(
                    r.inicio().toString(), r.fim().toString(),
                    r.recursos().stream().map(RecursoJson::de).toList(),
                    r.professores().stream().map(ProfessorJson::de).toList(),
                    new ConflitosJson(r.conflitosEvitados().total(),
                            r.conflitosEvitados().porRecurso().stream()
                                    .map(c -> new ConflitoRecursoJson(c.recursoId(), c.quantidade())).toList()));
        }
    }

    public record RecursoJson(String recursoId, String tipo, double horas) {
        static RecursoJson de(UtilizacaoRecurso u) {
            return new RecursoJson(u.recursoId(), u.tipo().name(), emHoras(u.horas()));
        }
    }

    public record ProfessorJson(String professorId, double horas) {
        static ProfessorJson de(UtilizacaoRecurso u) {
            return new ProfessorJson(u.recursoId(), emHoras(u.horas()));
        }
    }

    public record ConflitosJson(long total, List<ConflitoRecursoJson> porRecurso) {}

    public record ConflitoRecursoJson(String recursoId, long quantidade) {}
}
