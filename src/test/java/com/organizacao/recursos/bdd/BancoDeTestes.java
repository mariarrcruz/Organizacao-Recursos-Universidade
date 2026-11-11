package com.organizacao.recursos.bdd;

import com.organizacao.recursos.persistencia.ArmazenamentoPostgres;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * PostgreSQL real para os cenários BDD: um contêiner por execução da JVM, tabelas esvaziadas a cada cenário.
 */
final class BancoDeTestes {

    private static final PostgreSQLContainer<?> CONTAINER = new PostgreSQLContainer<>("postgres:16-alpine");
    private static ArmazenamentoPostgres armazenamento;

    private BancoDeTestes() {}

    /** Devolve o armazenamento com as tabelas vazias. */
    static synchronized ArmazenamentoPostgres novoArmazenamento() {
        if (armazenamento == null) {
            CONTAINER.start();
            armazenamento = new ArmazenamentoPostgres(CONTAINER.getJdbcUrl(), CONTAINER.getUsername(), CONTAINER.getPassword());
            armazenamento.criarEsquema();
        }
        armazenamento.limpar();
        return armazenamento;
    }
}
