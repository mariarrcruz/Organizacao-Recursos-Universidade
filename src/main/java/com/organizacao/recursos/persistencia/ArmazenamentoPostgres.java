package com.organizacao.recursos.persistencia;

import com.organizacao.recursos.dominio.ConflitoReserva;
import com.organizacao.recursos.dominio.ErroAplicacao;
import com.organizacao.recursos.dominio.EstadoReserva;
import com.organizacao.recursos.dominio.EventoAuditoria;
import com.organizacao.recursos.dominio.Manutencao;
import com.organizacao.recursos.dominio.Perfil;
import com.organizacao.recursos.dominio.Recurso;
import com.organizacao.recursos.dominio.Reserva;
import com.organizacao.recursos.dominio.StatusRecurso;
import com.organizacao.recursos.dominio.TipoRecurso;
import com.organizacao.recursos.dominio.Usuario;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Armazenamento em PostgreSQL (ADR-016 e ADR-017).
 *
 * <p>Cada escrita de reserva ou manutenção roda numa transação que primeiro toma um lock consultivo
 * por recurso ({@code pg_advisory_xact_lock}). Solicitações para o mesmo recurso passam uma de cada vez,
 * sem ciclo de espera (DEF-002). A restrição {@code reserva_sem_sobreposicao} é a barreira final contra a
 * dupla reserva (DEF-001); a manutenção é checada dentro da mesma transação, sob o mesmo lock.
 */
public final class ArmazenamentoPostgres implements Armazenamento {

    private static final String ESQUEMA = "/db/schema.sql";
    private static final String MENSAGEM_CONFLITO = "Conflito de horário para o recurso ";

    private final String url;
    private final String usuario;
    private final String senha;

    public ArmazenamentoPostgres(String url, String usuario, String senha) {
        this.url = url;
        this.usuario = usuario;
        this.senha = senha;
    }

    /** Cria as tabelas, a extensão, o trigger de auditoria (idempotente). */
    public void criarEsquema() {
        String script = lerEsquema();
        try (Connection c = conectar(); Statement st = c.createStatement()) {
            st.execute(script);
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao criar esquema", e);
        }
    }

    /** Esvazia as tabelas (usado entre cenários de teste). */
    public void limpar() {
        try (Connection c = conectar(); Statement st = c.createStatement()) {
            st.execute("TRUNCATE reserva, manutencao, evento_auditoria, conflito_reserva, vinculo_responsavel, recurso");
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao limpar tabelas", e);
        }
    }

    // ------------------------------------------------------------------ recursos

    @Override
    public void gravarRecurso(Recurso r) {
        String sql = "INSERT INTO recurso (id, tipo, nome, localizacao, capacidade, restrito, status, competencias) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (id) DO UPDATE SET tipo = EXCLUDED.tipo, "
                + "nome = EXCLUDED.nome, localizacao = EXCLUDED.localizacao, capacidade = EXCLUDED.capacidade, "
                + "restrito = EXCLUDED.restrito, status = EXCLUDED.status, competencias = EXCLUDED.competencias";
        try (Connection c = conectar(); PreparedStatement ps = c.prepareStatement(sql)) {
            preencherRecurso(ps, r);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao gravar recurso " + r.id(), e);
        }
    }

    @Override
    public Optional<Recurso> buscarRecurso(String id) {
        String sql = "SELECT id, tipo, nome, localizacao, capacidade, restrito, status, competencias "
                + "FROM recurso WHERE id = ?";
        try (Connection c = conectar(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(reconstruirRecurso(rs, "id")) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao buscar recurso " + id, e);
        }
    }

    @Override
    public List<Recurso> listarRecursos() {
        String sql = "SELECT id, tipo, nome, localizacao, capacidade, restrito, status, competencias "
                + "FROM recurso ORDER BY id";
        try (Connection c = conectar(); PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<Recurso> lista = new java.util.ArrayList<>();
            while (rs.next()) {
                lista.add(reconstruirRecurso(rs, "id"));
            }
            return lista;
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao listar recursos", e);
        }
    }

    @Override
    public void removerRecurso(String id) {
        try (Connection c = conectar(); PreparedStatement ps = c.prepareStatement("DELETE FROM recurso WHERE id = ?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao remover recurso " + id, e);
        }
    }

    // ------------------------------------------------------------------ reservas

    @Override
    public Optional<Reserva> buscarReserva(String id) {
        try (Connection c = conectar();
             PreparedStatement ps = c.prepareStatement(SELECT_RESERVA + " WHERE r.id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(reconstruirReserva(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao buscar reserva " + id, e);
        }
    }

    @Override
    public List<Reserva> listarReservas() {
        try (Connection c = conectar();
             PreparedStatement ps = c.prepareStatement(SELECT_RESERVA + " ORDER BY r.id");
             ResultSet rs = ps.executeQuery()) {
            List<Reserva> lista = new ArrayList<>();
            while (rs.next()) {
                lista.add(reconstruirReserva(rs));
            }
            return lista;
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao listar reservas", e);
        }
    }

    @Override
    public void inserirReserva(Reserva reserva, EventoAuditoria evento) {
        String recursoId = reserva.getRecurso().id();
        boolean[] corrida = {false};
        try {
            transacao(recursoId, c -> {
                garantirRecurso(c, reserva.getRecurso());
                travarRecurso(c, recursoId);
                verificarManutencao(c, recursoId, reserva.getInicio(), reserva.getFim());
                try {
                    inserirLinhaReserva(c, reserva);
                } catch (SQLException e) {
                    corrida[0] = RepositorioReservasPostgres.SQLSTATE_EXCLUSAO.equals(e.getSQLState());
                    throw e;
                }
                if (evento != null) {
                    inserirEvento(c, evento);
                }
            });
        } catch (ErroAplicacao e) {
            if (corrida[0]) {
                registrarConflitoAposRecusa(reserva);
            }
            throw e;
        }
    }

    /**
     * Recusa pela restrição do banco em corrida (SQLSTATE 23P01). A transação já foi desfeita; o conflito
     * é gravado em conexão própria, para que a contagem de conflitos evitados não desapareça com o rollback.
     */
    private void registrarConflitoAposRecusa(Reserva reserva) {
        registrarConflito(new ConflitoReserva(reserva.getRecurso().id(), reserva.getSolicitante().id(),
                reserva.getInicio(), reserva.getFim()));
    }

    @Override
    public void atualizarReserva(Reserva reserva, EventoAuditoria evento, boolean revalidarManutencao) {
        String recursoId = reserva.getRecurso().id();
        boolean[] corrida = {false};
        try {
            transacao(recursoId, c -> {
                travarRecurso(c, recursoId);
                if (revalidarManutencao && ativa(reserva.getEstado())) {
                    verificarManutencao(c, recursoId, reserva.getInicio(), reserva.getFim());
                }
                try {
                    atualizarLinhaReserva(c, reserva);
                } catch (SQLException e) {
                    corrida[0] = RepositorioReservasPostgres.SQLSTATE_EXCLUSAO.equals(e.getSQLState());
                    throw e;
                }
                inserirEvento(c, evento);
            });
        } catch (ErroAplicacao e) {
            if (corrida[0]) {
                registrarConflitoAposRecusa(reserva);
            }
            throw e;
        }
    }

    @Override
    public void registrarConflito(ConflitoReserva conflito) {
        try (Connection c = conectar();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO conflito_reserva (recurso_id, solicitante_id, inicio, fim) VALUES (?, ?, ?, ?)")) {
            ps.setString(1, conflito.recursoId());
            ps.setString(2, conflito.solicitanteId());
            ps.setObject(3, conflito.inicio().atOffset(ZoneOffset.UTC));
            ps.setObject(4, conflito.fim().atOffset(ZoneOffset.UTC));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao registrar conflito", e);
        }
    }

    @Override
    public void gravarVinculo(String responsavelId, String recursoId) {
        try (Connection c = conectar();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO vinculo_responsavel (responsavel_id, recurso_id) VALUES (?, ?) ON CONFLICT DO NOTHING")) {
            ps.setString(1, responsavelId);
            ps.setString(2, recursoId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao gravar vínculo", e);
        }
    }

    @Override
    public boolean existeVinculo(String responsavelId, String recursoId) {
        try (Connection c = conectar();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT 1 FROM vinculo_responsavel WHERE responsavel_id = ? AND recurso_id = ?")) {
            ps.setString(1, responsavelId);
            ps.setString(2, recursoId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao consultar vínculo", e);
        }
    }

    @Override
    public List<ConflitoReserva> listarConflitos() {
        String sql = "SELECT recurso_id, solicitante_id, inicio, fim FROM conflito_reserva ORDER BY id";
        try (Connection c = conectar();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<ConflitoReserva> lista = new ArrayList<>();
            while (rs.next()) {
                lista.add(new ConflitoReserva(rs.getString("recurso_id"), rs.getString("solicitante_id"),
                        instante(rs, "inicio"), instante(rs, "fim")));
            }
            return lista;
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao listar conflitos", e);
        }
    }

    @Override
    public void removerReserva(String id, EventoAuditoria evento) {
        transacao(id, c -> {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM reserva WHERE id = ?")) {
                ps.setString(1, id);
                if (ps.executeUpdate() == 0) {
                    throw new ErroAplicacao(404, "Reserva não encontrada: " + id);
                }
            }
            inserirEvento(c, evento);
        });
    }

    // ------------------------------------------------------------------ manutenção e auditoria

    @Override
    public void inserirManutencao(Manutencao manutencao) {
        transacao(manutencao.recursoId(), c -> {
            travarRecurso(c, manutencao.recursoId());
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO manutencao (recurso_id, periodo) VALUES (?, tstzrange(?, ?, '[)'))")) {
                ps.setString(1, manutencao.recursoId());
                ps.setObject(2, manutencao.inicio().atOffset(ZoneOffset.UTC));
                ps.setObject(3, manutencao.fim().atOffset(ZoneOffset.UTC));
                ps.executeUpdate();
            }
        });
    }

    @Override
    public List<Manutencao> listarManutencoes() {
        String sql = "SELECT recurso_id, lower(periodo) AS ini, upper(periodo) AS fim FROM manutencao ORDER BY id";
        try (Connection c = conectar();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<Manutencao> lista = new ArrayList<>();
            while (rs.next()) {
                lista.add(new Manutencao(rs.getString("recurso_id"), instante(rs, "ini"), instante(rs, "fim")));
            }
            return lista;
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao listar manutenções", e);
        }
    }

    @Override
    public List<EventoAuditoria> listarEventos() {
        String sql = "SELECT reserva_id, ator_id, instante, acao, estado_anterior, estado_novo, justificativa "
                + "FROM evento_auditoria ORDER BY id";
        try (Connection c = conectar();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<EventoAuditoria> lista = new ArrayList<>();
            while (rs.next()) {
                lista.add(new EventoAuditoria(rs.getString("reserva_id"), rs.getString("ator_id"),
                        instante(rs, "instante"), rs.getString("acao"),
                        estadoOpcional(rs.getString("estado_anterior")),
                        estadoOpcional(rs.getString("estado_novo")),
                        rs.getString("justificativa")));
            }
            return lista;
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao listar eventos", e);
        }
    }

    // ------------------------------------------------------------------ internos: transação e SQL

    private static final String SELECT_RESERVA = "SELECT r.id AS r_id, r.solicitante_id, r.solicitante_perfis, "
            + "lower(r.periodo) AS ini, upper(r.periodo) AS fim, r.estado, "
            + "rc.id AS c_id, rc.tipo, rc.nome, rc.localizacao, rc.capacidade, rc.restrito, rc.status, rc.competencias "
            + "FROM reserva r JOIN recurso rc ON rc.id = r.recurso_id";

    @FunctionalInterface
    private interface Passo {
        void executar(Connection c) throws SQLException;
    }

    /** Uma transação por escrita. SQLSTATE 23P01 (exclusão sobreposta) vira 409. */
    private void transacao(String recursoParaMensagem, Passo passo) {
        try (Connection c = conectar()) {
            c.setAutoCommit(false);
            try {
                passo.executar(c);
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                if (RepositorioReservasPostgres.SQLSTATE_EXCLUSAO.equals(e.getSQLState())) {
                    throw new ErroAplicacao(409, MENSAGEM_CONFLITO + recursoParaMensagem);
                }
                throw new IllegalStateException("Falha de banco", e);
            } catch (RuntimeException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Falha de conexão com o banco", e);
        }
    }

    /** Lock consultivo por recurso: uma transação por vez para o mesmo recurso. */
    private static void travarRecurso(Connection c, String recursoId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT pg_advisory_xact_lock(hashtext(?))")) {
            ps.setString(1, recursoId);
            ps.executeQuery().close();
        }
    }

    private static void garantirRecurso(Connection c, Recurso r) throws SQLException {
        String sql = "INSERT INTO recurso (id, tipo, nome, localizacao, capacidade, restrito, status, competencias) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (id) DO NOTHING";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            preencherRecurso(ps, r);
            ps.executeUpdate();
        }
    }

    private static void verificarManutencao(Connection c, String recursoId, LocalDateTime inicio, LocalDateTime fim)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM manutencao WHERE recurso_id = ? AND periodo && tstzrange(?, ?, '[)') LIMIT 1")) {
            ps.setString(1, recursoId);
            ps.setObject(2, inicio.atOffset(ZoneOffset.UTC));
            ps.setObject(3, fim.atOffset(ZoneOffset.UTC));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    throw new ErroAplicacao(409, MENSAGEM_CONFLITO + recursoId);
                }
            }
        }
    }

    private static void inserirLinhaReserva(Connection c, Reserva r) throws SQLException {
        String perfis = r.getSolicitante().perfis().stream().map(Enum::name).sorted()
                .collect(Collectors.joining(","));
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO reserva (id, recurso_id, solicitante_id, solicitante_perfis, periodo, estado) "
                        + "VALUES (?, ?, ?, ?, tstzrange(?, ?, '[)'), ?)")) {
            ps.setString(1, r.getId());
            ps.setString(2, r.getRecurso().id());
            ps.setString(3, r.getSolicitante().id());
            ps.setString(4, perfis);
            ps.setObject(5, r.getInicio().atOffset(ZoneOffset.UTC));
            ps.setObject(6, r.getFim().atOffset(ZoneOffset.UTC));
            ps.setString(7, r.getEstado().name());
            ps.executeUpdate();
        }
    }

    private static void atualizarLinhaReserva(Connection c, Reserva r) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE reserva SET estado = ?, periodo = tstzrange(?, ?, '[)') WHERE id = ?")) {
            ps.setString(1, r.getEstado().name());
            ps.setObject(2, r.getInicio().atOffset(ZoneOffset.UTC));
            ps.setObject(3, r.getFim().atOffset(ZoneOffset.UTC));
            ps.setString(4, r.getId());
            if (ps.executeUpdate() == 0) {
                throw new ErroAplicacao(404, "Reserva não encontrada: " + r.getId());
            }
        }
    }

    private static void inserirEvento(Connection c, EventoAuditoria e) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO evento_auditoria (reserva_id, ator_id, instante, acao, estado_anterior, estado_novo, "
                        + "justificativa) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, e.reservaId());
            ps.setString(2, e.atorId());
            ps.setObject(3, e.instante().atOffset(ZoneOffset.UTC));
            ps.setString(4, e.acao());
            ps.setString(5, e.estadoAnterior() == null ? null : e.estadoAnterior().name());
            ps.setString(6, e.estadoNovo() == null ? null : e.estadoNovo().name());
            ps.setString(7, e.justificativa());
            ps.executeUpdate();
        }
    }

    private static void preencherRecurso(PreparedStatement ps, Recurso r) throws SQLException {
        ps.setString(1, r.id());
        ps.setString(2, r.tipo().name());
        ps.setString(3, r.nome());
        ps.setString(4, r.localizacao());
        ps.setInt(5, r.capacidade());
        ps.setBoolean(6, r.restrito());
        ps.setString(7, r.status().name());
        ps.setString(8, String.join(",", r.competencias()));
    }

    private static Reserva reconstruirReserva(ResultSet rs) throws SQLException {
        Usuario solicitante = new Usuario(rs.getString("solicitante_id"), perfisDe(rs.getString("solicitante_perfis")));
        Recurso recurso = reconstruirRecurso(rs, "c_id");
        return new Reserva(rs.getString("r_id"), solicitante, recurso, instante(rs, "ini"), instante(rs, "fim"),
                EstadoReserva.valueOf(rs.getString("estado")));
    }

    /** Lê as colunas do recurso; {@code colunaId} é "id" em SELECT direto ou "c_id" em JOIN com reserva. */
    private static Recurso reconstruirRecurso(ResultSet rs, String colunaId) throws SQLException {
        String id = rs.getString(colunaId);
        return new Recurso(id, TipoRecurso.valueOf(rs.getString("tipo")), rs.getString("nome"),
                rs.getString("localizacao"), rs.getInt("capacidade"), rs.getBoolean("restrito"),
                StatusRecurso.valueOf(rs.getString("status")), competenciasDe(rs.getString("competencias")));
    }

    private static LocalDateTime instante(ResultSet rs, String coluna) throws SQLException {
        return rs.getObject(coluna, OffsetDateTime.class).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    private static EstadoReserva estadoOpcional(String valor) {
        return valor == null ? null : EstadoReserva.valueOf(valor);
    }

    private static Set<Perfil> perfisDe(String texto) {
        Set<Perfil> perfis = EnumSet.noneOf(Perfil.class);
        if (texto != null && !texto.isBlank()) {
            for (String p : texto.split(",")) {
                perfis.add(Perfil.valueOf(p));
            }
        }
        return perfis;
    }

    private static Set<String> competenciasDe(String texto) {
        if (texto == null || texto.isBlank()) {
            return new LinkedHashSet<>();
        }
        return new LinkedHashSet<>(Arrays.asList(texto.split(",")));
    }

    private static boolean ativa(EstadoReserva estado) {
        return estado == EstadoReserva.SOLICITADA || estado == EstadoReserva.APROVADA || estado == EstadoReserva.EM_USO;
    }

    private Connection conectar() throws SQLException {
        return DriverManager.getConnection(url, usuario, senha);
    }

    private static String lerEsquema() {
        try (InputStream in = ArmazenamentoPostgres.class.getResourceAsStream(ESQUEMA)) {
            if (in == null) {
                throw new IllegalStateException("Recurso não encontrado: " + ESQUEMA);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao ler " + ESQUEMA, e);
        }
    }
}
