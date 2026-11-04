package com.organizacao.recursos.dominio;

import java.time.LocalDateTime;

/** Período de manutenção (ou bloqueio) de um recurso. */
public record Manutencao(String recursoId, LocalDateTime inicio, LocalDateTime fim) {}
