package com.organizacao.recursos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Ponto de entrada da Organização de Recursos (ADR-001, Spring Boot).
 * Sobe o servidor HTTP e cria o esquema no PostgreSQL configurado em application.properties.
 */
@SpringBootApplication
public class AplicacaoRecursos {

    public static void main(String[] args) {
        SpringApplication.run(AplicacaoRecursos.class, args);
    }
}
