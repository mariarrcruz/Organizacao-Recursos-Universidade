package com.organizacao.recursos.bdd;

import io.cucumber.java.ParameterType;
import java.time.LocalDateTime;

/** Tipos de parâmetro das features. Aceita instante com aspas (passos) ou sem aspas (Esquema do Cenário). */
public final class Tipos {

    private Tipos() {}

    @ParameterType("\"?\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}(?::\\d{2})?\"?")
    public static LocalDateTime instante(String texto) {
        return LocalDateTime.parse(texto.replace("\"", ""));
    }
}
