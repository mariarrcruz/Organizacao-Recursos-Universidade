package com.organizacao.recursos.servico;

import static com.organizacao.recursos.servico.Autorizacao.exigirAlgumPerfil;

import com.organizacao.recursos.dominio.*;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Relatórios gerenciais (RF-12). Acesso restrito a Responsável e Administrador;
 * o escopo do Solicitante segue pendente (AMB-06).
 */
public class ServicoRelatorios {

    /** Estados que contam como utilização efetiva do recurso. */
    private static final Set<EstadoReserva> UTILIZADAS =
            EnumSet.of(EstadoReserva.APROVADA, EstadoReserva.EM_USO, EstadoReserva.CONCLUIDA);

    private final ServicoReservas reservas;
    private final ServicoMovimentacoes movimentacoes;

    public ServicoRelatorios(ServicoReservas reservas, ServicoMovimentacoes movimentacoes) {
        this.reservas = reservas;
        this.movimentacoes = movimentacoes;
    }

    /** Horas usadas por recurso no período [inicio, fim] (dias inclusivos); cada reserva conta só a parte dentro dele. */
    public Map<String, Duration> utilizacaoPorRecurso(Usuario ator, LocalDate inicio, LocalDate fim) {
        exigirGerencial(ator);
        LocalDateTime ini = inicio.atStartOfDay();
        LocalDateTime fimExcl = fim.plusDays(1).atStartOfDay();
        Map<String, Duration> mapa = new HashMap<>();
        for (Reserva r : reservas.todas()) {
            Duration d = duracaoNoPeriodo(r, ini, fimExcl);
            if (!d.isZero()) {
                mapa.merge(r.getRecurso().id(), d, Duration::plus);
            }
        }
        return mapa;
    }

    /**
     * Relatório de utilização do endpoint {@code GET /relatorios/utilizacao}: horas por recurso, carga horária
     * por professor (recursos do tipo PROFESSOR) e conflitos evitados (recusas por sobreposição no período).
     */
    public RelatorioUtilizacao utilizacao(Usuario ator, LocalDate inicio, LocalDate fim) {
        exigirGerencial(ator);
        if (inicio == null || fim == null || fim.isBefore(inicio)) {
            throw new ErroAplicacao(400, "Período inválido: fim deve ser igual ou posterior ao início");
        }
        LocalDateTime ini = inicio.atStartOfDay();
        LocalDateTime fimExcl = fim.plusDays(1).atStartOfDay();

        Map<String, TipoRecurso> tipos = new HashMap<>();
        for (Reserva r : reservas.todas()) {
            tipos.put(r.getRecurso().id(), r.getRecurso().tipo());
        }
        List<UtilizacaoRecurso> recursos = utilizacaoPorRecurso(ator, inicio, fim).entrySet().stream()
                .map(e -> new UtilizacaoRecurso(e.getKey(), tipos.get(e.getKey()), e.getValue()))
                .sorted(Comparator.comparing(UtilizacaoRecurso::recursoId))
                .toList();
        List<UtilizacaoRecurso> professores = recursos.stream()
                .filter(u -> u.tipo() == TipoRecurso.PROFESSOR).toList();

        Map<String, Long> conflitos = new TreeMap<>();
        for (ConflitoReserva c : reservas.conflitosRegistrados()) {
            if (c.inicio().isBefore(fimExcl) && c.fim().isAfter(ini)) {
                conflitos.merge(c.recursoId(), 1L, Long::sum);
            }
        }
        List<ConflitosRecurso> porRecurso = conflitos.entrySet().stream()
                .map(e -> new ConflitosRecurso(e.getKey(), e.getValue())).toList();
        long total = porRecurso.stream().mapToLong(ConflitosRecurso::quantidade).sum();

        return new RelatorioUtilizacao(inicio, fim, recursos, professores, new ConflitosEvitados(total, porRecurso));
    }

    public record RelatorioUtilizacao(LocalDate inicio, LocalDate fim, List<UtilizacaoRecurso> recursos,
                                      List<UtilizacaoRecurso> professores, ConflitosEvitados conflitosEvitados) {}

    public record UtilizacaoRecurso(String recursoId, TipoRecurso tipo, Duration horas) {}

    public record ConflitosEvitados(long total, List<ConflitosRecurso> porRecurso) {}

    public record ConflitosRecurso(String recursoId, long quantidade) {}

    public Duration cargaHorariaSala(Usuario ator, String salaId, LocalDate inicio, LocalDate fim) {
        exigirGerencial(ator);
        // Limitação: sem o registro de recursos, "existe" é inferido das reservas.
        boolean conhecido = reservas.todas().stream().anyMatch(r -> r.getRecurso().id().equals(salaId));
        if (!conhecido) {
            throw new ErroAplicacao(404, "Sala não encontrada: " + salaId);
        }
        LocalDateTime ini = inicio.atStartOfDay();
        LocalDateTime fimExcl = fim.plusDays(1).atStartOfDay();
        Duration total = Duration.ZERO;
        for (Reserva r : reservas.todas()) {
            if (r.getRecurso().id().equals(salaId)) {
                total = total.plus(duracaoNoPeriodo(r, ini, fimExcl));
            }
        }
        return total;
    }

    public Map<EstadoReserva, Long> contarPorEstado(Usuario ator, Set<EstadoReserva> estados) {
        exigirGerencial(ator);
        Map<EstadoReserva, Long> contagem = new EnumMap<>(EstadoReserva.class);
        for (EstadoReserva e : estados) {
            contagem.put(e, 0L);
        }
        for (Reserva r : reservas.todas()) {
            if (estados.contains(r.getEstado())) {
                contagem.merge(r.getEstado(), 1L, Long::sum);
            }
        }
        return contagem;
    }

    public List<Movimentacao> historicoMovimentacoes(Usuario ator, String materialId, LocalDate inicio, LocalDate fim) {
        exigirGerencial(ator);
        return movimentacoes.historicoPeriodo(materialId, inicio, fim);
    }

    // ---------------------------------------------------------------- internos

    private static void exigirGerencial(Usuario ator) {
        exigirAlgumPerfil(ator, Perfil.RESPONSAVEL, Perfil.ADMINISTRADOR);
    }

    /** Parte da reserva que cai em [ini, fimExcl) e conta como uso. Zero se não for uso ou se estiver fora. */
    private static Duration duracaoNoPeriodo(Reserva r, LocalDateTime ini, LocalDateTime fimExcl) {
        if (!UTILIZADAS.contains(r.getEstado())) {
            return Duration.ZERO;
        }
        LocalDateTime a = r.getInicio().isAfter(ini) ? r.getInicio() : ini;
        LocalDateTime b = r.getFim().isBefore(fimExcl) ? r.getFim() : fimExcl;
        // Instantes em UTC, o mesmo referencial que o armazenamento usa para gravar os horários.
        return b.isAfter(a) ? Duration.between(a.toInstant(ZoneOffset.UTC), b.toInstant(ZoneOffset.UTC)) : Duration.ZERO;
    }
}
