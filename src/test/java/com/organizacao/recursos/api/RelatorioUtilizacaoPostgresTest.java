package com.organizacao.recursos.api;

import com.organizacao.recursos.persistencia.Armazenamento;
import com.organizacao.recursos.persistencia.ArmazenamentoPostgres;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Contrato de {@code /relatorios/utilizacao} contra PostgreSQL real (Testcontainers). */
@Testcontainers
class RelatorioUtilizacaoPostgresTest extends RelatorioUtilizacaoContrato {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Override
    protected Armazenamento novoArmazenamento() {
        ArmazenamentoPostgres armazenamento =
                new ArmazenamentoPostgres(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        armazenamento.criarEsquema();
        armazenamento.limpar();
        return armazenamento;
    }
}
