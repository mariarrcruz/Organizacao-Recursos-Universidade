package com.organizacao.recursos.dominio;

import java.time.LocalDateTime;

public record Movimentacao(
        String materialId,
        String reservaId,
        TipoMovimentacao tipo,
        String condicao,
        LocalDateTime instante,
        String atorId) {}
