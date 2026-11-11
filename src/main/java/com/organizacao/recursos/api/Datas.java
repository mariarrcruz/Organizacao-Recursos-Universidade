package com.organizacao.recursos.api;

import com.organizacao.recursos.dominio.ErroAplicacao;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

/** Conversão de datas do contrato HTTP (ISO-8601 local, ex.: 2026-11-20T10:00). */
final class Datas {

    private Datas() {}

    /** Converte o valor do campo; o erro nomeia o campo e o formato esperado (DEF-006). */
    static LocalDateTime instante(String texto, String campo) {
        if (texto == null || texto.isBlank()) {
            throw new ErroAplicacao(400, "Informe o campo " + campo + " no formato AAAA-MM-DDTHH:MM");
        }
        try {
            return LocalDateTime.parse(texto.trim());
        } catch (DateTimeParseException e) {
            throw new ErroAplicacao(400, "Data inválida no campo " + campo + ": " + texto
                    + " (use o formato AAAA-MM-DDTHH:MM)");
        }
    }
}
