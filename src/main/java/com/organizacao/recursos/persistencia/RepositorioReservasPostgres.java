package com.organizacao.recursos.persistencia;

import com.organizacao.recursos.dominio.ErroAplicacao;
import com.organizacao.recursos.dominio.TipoRecurso;
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
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Persistência de reservas em PostgreSQL (ADR-016). Intervalos são semiabertos [início, fim).
 *
 * <p>Estados que ocupam o recurso: SOLICITADA, APROVADA e EM_USO (mesma regra de {@code ServicoReservas}).
 */
public class RepositorioReservasPostgres {

    /** SQLSTATE 23P01: exclusion_violation (restrição EXCLUDE). */
    static final String SQLSTATE_EXCLUSAO = "23P01";

    static final String ESTADOS_ATIVOS = "('SOLICITADA', 'APROVADA', 'EM_USO')";

    private final String url;
    private final String usuario;
    private final String senha;

    public RepositorioReservasPostgres(String url, String usuario, String senha) {
        this.url = url;
        this.usuario = usuario;
        this.senha = senha;
    }

    /** Cria as tabelas a partir de db/schema.sql (idempotente). */
    public void criarEsquema() {
        String script = lerRecurso("/db/schema.sql");
        try (Connection c = conexao(); Statement st = c.createStatement()) {
            for (String comando : script.split(";")) {
                String limpo = comando.replaceAll("(?m)^--.*$", "").trim();
                if (!limpo.isEmpty()) {
                    st.execute(limpo);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao criar esquema", e);
        }
    }

    public void registrarRecurso(String recursoId, TipoRecurso tipo) {
        try (Connection c = conexao();
             PreparedStatement ps = c.prepareStatement("INSERT INTO recurso (id, tipo) VALUES (?, ?)")) {
            ps.setString(1, recursoId);
            ps.setString(2, tipo.name());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao registrar recurso " + recursoId, e);
        }
    }

    /**
     * Cria uma reserva SOLICITADA numa transação. Antes do INSERT, trava a linha do recurso
     * ({@code SELECT ... FOR UPDATE}): solicitações para o mesmo recurso passam uma de cada vez e não
     * formam ciclo de espera (DEF-002). A restrição {@code reserva_sem_sobreposicao} é a barreira final
     * (ADR-016); sua violação (SQLSTATE 23P01) vira 409.
     *
     * @return identificador da reserva criada
     */
    public String solicitar(String recursoId, String solicitanteId, LocalDateTime inicio, LocalDateTime fim) {
        String id = UUID.randomUUID().toString();
        try (Connection c = conexao()) {
            c.setAutoCommit(false);
            try {
                travarRecurso(c, recursoId);
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO reserva (id, recurso_id, solicitante_id, periodo, estado) "
                                + "VALUES (?, ?, ?, tstzrange(?, ?, '[)'), 'SOLICITADA')")) {
                    ps.setString(1, id);
                    ps.setString(2, recursoId);
                    ps.setString(3, solicitanteId);
                    ps.setObject(4, inicio.atOffset(ZoneOffset.UTC));
                    ps.setObject(5, fim.atOffset(ZoneOffset.UTC));
                    ps.executeUpdate();
                }
                c.commit();
                return id;
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                if (e instanceof SQLException sql && SQLSTATE_EXCLUSAO.equals(sql.getSQLState())) {
                    throw new ErroAplicacao(409, "Conflito de horário para o recurso " + recursoId);
                }
                if (e instanceof ErroAplicacao erro) {
                    throw erro;
                }
                throw new IllegalStateException("Falha ao solicitar reserva", e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao solicitar reserva", e);
        }
    }

    private static void travarRecurso(Connection c, String recursoId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT id FROM recurso WHERE id = ? FOR UPDATE")) {
            ps.setString(1, recursoId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ErroAplicacao(404, "Recurso não encontrado: " + recursoId);
                }
            }
        }
    }

    /** Consulta direta: quantas reservas ativas do recurso se sobrepõem ao intervalo. */
    public long contarReservasAtivas(String recursoId, LocalDateTime inicio, LocalDateTime fim) {
        String sql = "SELECT count(*) FROM reserva WHERE recurso_id = ? AND estado IN " + ESTADOS_ATIVOS
                + " AND periodo && tstzrange(?, ?, '[)')";
        try (Connection c = conexao(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, recursoId);
            ps.setObject(2, inicio.atOffset(ZoneOffset.UTC));
            ps.setObject(3, fim.atOffset(ZoneOffset.UTC));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao contar reservas", e);
        }
    }

    private Connection conexao() throws SQLException {
        return DriverManager.getConnection(url, usuario, senha);
    }

    private static String lerRecurso(String caminho) {
        try (InputStream in = RepositorioReservasPostgres.class.getResourceAsStream(caminho)) {
            if (in == null) {
                throw new IllegalStateException("Recurso não encontrado: " + caminho);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao ler " + caminho, e);
        }
    }
}
