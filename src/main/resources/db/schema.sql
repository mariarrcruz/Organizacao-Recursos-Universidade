-- Esquema de reservas (PostgreSQL). Carregado por RepositorioReservasPostgres.criarEsquema().
CREATE TABLE IF NOT EXISTS recurso (
    id   varchar(64) PRIMARY KEY,
    tipo varchar(20) NOT NULL
);

CREATE TABLE IF NOT EXISTS reserva (
    id             varchar(64) PRIMARY KEY,
    recurso_id     varchar(64) NOT NULL REFERENCES recurso (id),
    solicitante_id varchar(64) NOT NULL,
    periodo        tstzrange   NOT NULL,
    estado         varchar(20) NOT NULL
);
