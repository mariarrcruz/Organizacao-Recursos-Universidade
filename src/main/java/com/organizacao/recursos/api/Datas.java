package com.organizacao.recursos.api;

import com.organizacao.recursos.dominio.ErroAplicacao;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

/** Conversão de datas do contrato HTTP (ISO-8601 local, ex.: 2026-11-20T10:00). */
final class Datas {

    private Datas() {}

    static LocalDateTime instante(String texto) {
        try {
            return LocalDateTime.parse(texto);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new ErroAplicacao(400, "Data inválida: " + texto);
        }
    }
}
