package com.organizacao.recursos.dominio;

import java.util.Set;

/** Usuário autenticado. Ator nulo representa acesso anônimo. */
public record Usuario(String id, Set<Perfil> perfis) {}
