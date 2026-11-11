package com.organizacao.recursos.dominio;

import java.time.LocalDateTime;

/**
 * Tentativa de reserva recusada por sobreposição com outra reserva do mesmo recurso.
 * Não é evento de auditoria (RN-09.10: recusa não muda estado); serve para contar conflitos evitados.
 */
public record ConflitoReserva(String recursoId, String solicitanteId, LocalDateTime inicio, LocalDateTime fim) {}
