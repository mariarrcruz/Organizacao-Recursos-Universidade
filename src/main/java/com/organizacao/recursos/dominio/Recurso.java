package com.organizacao.recursos.dominio;

import java.util.Set;

public record Recurso(
        String id,
        TipoRecurso tipo,
        String nome,
        String localizacao,
        int capacidade,
        boolean restrito,
        StatusRecurso status,
        Set<String> competencias) {}
