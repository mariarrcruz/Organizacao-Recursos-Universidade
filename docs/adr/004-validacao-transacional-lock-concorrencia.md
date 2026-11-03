# ADR-004: Validação Transacional + Lock por Recurso para Concorrência

**Status:** Aprovado (Bloqueador: Teste Concorrência H1)

**Data:** 2026-11-03

---

## Contexto

RF-06 e RF-07 exigem proteção absoluta contra dupla reserva. Problema: duas requisições simultâneas podem ler mesma disponibilidade e ambas confirmar alocação. Solução: transação + verificação de conflito + serialização.

---

## Decisão

Implementar **lock pessimista por recurso** dentro de transação ACID:

1. **Transação:** `@Transactional(isolation = READ_COMMITTED)`
2. **Lock:** `SELECT ... FOR UPDATE` por recurso (ordem determinística por ID)
3. **Validação:** Verificar sobreposição de intervalo **dentro da transação**
4. **Resultado:** Uma requisição confirma; outra recebe 409 Conflict

---

## Algoritmo

```java
@Transactional
public Reservation createReservation(ReservationRequest req) {
    // 1. Ordena recursos por ID (evita deadlock)
    List<Resource> resources = repo.findByIds(req.getResourceIds())
        .stream().sorted(comparing(Resource::getId)).collect(toList());
    
    // 2. Adquire lock exclusivo em cada recurso
    for (Resource resource : resources) {
        repo.lockForUpdate(resource.getId());
    }
    
    // 3. Verifica sobreposição (sem race condition)
    for (Resource resource : resources) {
        if (hasConflict(resource, req.getStartTime(), req.getEndTime())) {
            throw new ConflictException("Recurso indisponível");
        }
    }
    
    // 4. Cria reserva (transação garante atomicidade)
    Reservation reservation = new Reservation(req);
    return repo.save(reservation);
    
    // 5. Lock liberado ao fim da transação (COMMIT ou ROLLBACK)
}
```

---

## Regra Temporal

Dois intervalos entram em conflito se:
```
inicio_existente < fim_novo
AND
fim_existente > inicio_novo
```

Intervalos são **semiabertos** `[início, fim)`: reserva que termina às 10h NÃO conflita com reserva que começa às 10h.

---

## Forças Consideradas

### Ganhos
- **Integridade garantida:** Lock pessimista impede race condition
- **Determinístico:** Ordem por ID evita deadlock
- **Simples:** Uma transação; sem polling ou retry
- **Testável:** Comportamento previsível

### Trade-offs
- **Performance:** Lock bloqueia outras requisições (serializa criação de reservas)
- **Bloqueio por recurso:** Se múltiplos recursos, duração de lock é soma
- **H2 limitações:** Concorrência >20 usuarios pode saturar
- **Mensagem de erro:** Deve informar qual recurso causou conflito

---

## Alternativas Descartadas

1. **Otimista (versionamento):** Requer retry em caso de conflito; complexo
2. **Sem lock:** Dupla alocação possível; inadequado
3. **Lock global:** Serializa TODAS as operações; inadequado
4. **Unique constraint:** Não detecta sobreposição de intervalo arbitrário

---

## Consequências

### Implementação
- Repositório com método `lockForUpdate(resourceId)` que executa `SELECT ... FOR UPDATE`
- Serviço de domínio valida regra temporal
- Controller retorna 409 Conflict com mensagem clara

### Testes
- **Unitário:** Regra de sobreposição com 4 casos (antes, dentro, depois, contém)
- **Integração:** Transação + lock com Testcontainers ou H2 isolado
- **Concorrência:** 10+ threads competem pelo mesmo recurso (bloqueador H1)
- **Carga:** JMeter com 20 usuarios; medir contention

### Performance
- P95 <1s esperado mesmo com lock (H2 é rápido para operação simples)
- Se degradar: considerar índices em (resource_id, start_time, end_time)

---

## Riscos

| Risco | Probabilidade | Impacto | Mitigação |
|-------|---------------|--------|-----------|
| H2 não suporta lock pessimista | Baixa | Crítico | Validar em teste H1 |
| Deadlock com múltiplos recursos | Média | Alto | Ordem determinística por ID |
| Lock contention serializa operações | Alta | Médio | Aceitar para acadêmico; monitorar P95 |
| Intervalo [início, fim) confunde usuários | Baixa | Baixo | Documentar em RFC |
| Múltiplos recursos complexos query | Média | Médio | Limitar a 3 recursos por reserva |

**Bloqueador crítico:** H1 — teste com 10+ threads disputando mesmo recurso.

---

## Requisitos Relacionados

- RF-06: Detecção sobreposição de horários
- RF-07: Proteção contra dupla reserva
- RNF-02: Integridade transacional garantida
- RNF-04: Tempo de resposta <1s em ações de disparo

---

## Condição de Revisão

Esta decisão será revisada se:
1. Teste H1 falhar (H2 não suportar lock)
2. Bloqueio tornar P95 >1s com 20 usuarios (considerar índices ou READ_UNCOMMITTED)
3. Produção exigir escalabilidade (múltiplos servidores com lock global distribuído)

---

## Referências

- [arquitetura.md § 7.1-7.3](../docs/arquitetura.md#seção-7-reservas-concorrência-e-integridade-temporal)
- [ATAM § Cenário C-01](../docs/ATAM-analise.md#cenário-c-01-dupla-reserva-concorrente-crítico)
- [RFC - RF-06, RF-07](../rfc/requisitos-funcionais.md)
