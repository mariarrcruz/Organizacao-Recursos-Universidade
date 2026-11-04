package com.organizacao.recursos.dominio;

import java.time.LocalDateTime;

public record EventoAuditoria(
        String reservaId,
        String atorId,
        LocalDateTime instante,
        String acao,
        EstadoReserva estadoAnterior,
        EstadoReserva estadoNovo,
        String justificativa) {}
