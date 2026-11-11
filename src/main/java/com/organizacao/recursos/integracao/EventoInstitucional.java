package com.organizacao.recursos.integracao;

import java.time.LocalDateTime;

/** Modelo interno de um evento institucional, já validado contra o contrato externo. */
public record EventoInstitucional(String id, String titulo, LocalDateTime inicio, LocalDateTime fim) {
}
