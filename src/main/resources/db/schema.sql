-- Esquema da aplicação (PostgreSQL). ADR-016 (exclusão de reservas) e ADR-017 (persistência).
-- Intervalos semiabertos [início, fim). Carregado por ArmazenamentoPostgres.criarEsquema().
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE IF NOT EXISTS recurso (
    id           varchar(64)  PRIMARY KEY,
    tipo         varchar(20)  NOT NULL,
    nome         varchar(120) NOT NULL DEFAULT '',
    localizacao  varchar(120) NOT NULL DEFAULT '',
    capacidade   integer      NOT NULL DEFAULT 0,
    restrito     boolean      NOT NULL DEFAULT false,
    status       varchar(20)  NOT NULL DEFAULT 'DISPONIVEL',
    competencias varchar(500) NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS reserva (
    id                 varchar(64)  PRIMARY KEY,
    recurso_id         varchar(64)  NOT NULL REFERENCES recurso (id),
    solicitante_id     varchar(64)  NOT NULL,
    solicitante_perfis varchar(200) NOT NULL DEFAULT '',
    periodo            tstzrange    NOT NULL,
    estado             varchar(20)  NOT NULL,
    CONSTRAINT reserva_sem_sobreposicao EXCLUDE USING gist (
        recurso_id WITH =,
        periodo WITH &&
    ) WHERE (estado IN ('SOLICITADA', 'APROVADA', 'EM_USO'))
);

CREATE TABLE IF NOT EXISTS manutencao (
    id         bigserial   PRIMARY KEY,
    recurso_id varchar(64) NOT NULL,
    periodo    tstzrange   NOT NULL
);

CREATE TABLE IF NOT EXISTS evento_auditoria (
    id              bigserial    PRIMARY KEY,
    reserva_id      varchar(64)  NOT NULL,
    ator_id         varchar(64)  NOT NULL,
    instante        timestamptz  NOT NULL,
    acao            varchar(40)  NOT NULL,
    estado_anterior varchar(20),
    estado_novo     varchar(20),
    justificativa   varchar(500)
);

-- RN-09: auditoria é somente inserção. Alterar ou excluir evento falha no banco.
CREATE OR REPLACE FUNCTION auditoria_somente_insercao() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'Auditoria é append-only (RN-09)' USING ERRCODE = 'restrict_violation';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS evento_append_only ON evento_auditoria;
CREATE TRIGGER evento_append_only
    BEFORE UPDATE OR DELETE ON evento_auditoria
    FOR EACH ROW EXECUTE FUNCTION auditoria_somente_insercao();

-- Tentativas de reserva recusadas por sobreposição (conflitos evitados, RF-12). Não é auditoria: não altera estado.
CREATE TABLE IF NOT EXISTS conflito_reserva (
    id           bigserial    PRIMARY KEY,
    recurso_id   varchar(64)  NOT NULL,
    solicitante_id varchar(64) NOT NULL,
    inicio       timestamptz  NOT NULL,
    fim          timestamptz  NOT NULL
);
