-- Esquema de reservas (PostgreSQL). Carregado por RepositorioReservasPostgres.criarEsquema().
-- Intervalos semiabertos [início, fim). ADR-016: a exclusão sobreposta é garantida pelo banco.
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE IF NOT EXISTS recurso (
    id   varchar(64) PRIMARY KEY,
    tipo varchar(20) NOT NULL
);

CREATE TABLE IF NOT EXISTS reserva (
    id             varchar(64) PRIMARY KEY,
    recurso_id     varchar(64) NOT NULL REFERENCES recurso (id),
    solicitante_id varchar(64) NOT NULL,
    periodo        tstzrange   NOT NULL,
    estado         varchar(20) NOT NULL,
    CONSTRAINT reserva_sem_sobreposicao EXCLUDE USING gist (
        recurso_id WITH =,
        periodo WITH &&
    ) WHERE (estado IN ('SOLICITADA', 'APROVADA', 'EM_USO'))
);
