# DEF-002 — Deadlock em solicitações simultâneas (sem lock por recurso)

**Status:** corrigido (lock pessimista por recurso, ver ADR-016)
**Data da detecção:** 2026-11-11
**Severidade:** alta — a solicitação concorrente recebe erro 500 em vez de 409; a integridade não é violada
**Regras afetadas:** RN-03 (atomicidade sob concorrência), RNF-02
**Teste que detectou:** `ConcorrenciaReservaPostgresTest` (suíte completa de `./mvnw -B verify`)

## Passos para reproduzir

1. Com a restrição `EXCLUDE` já ativa (correção de DEF-001), rodar `./mvnw -B verify` com a suíte completa.
2. Repetir até o teste de concorrência falhar (em 2 de 3 execuções da suíte completa; o teste isolado passou nas cinco rodadas).

## Evidência

Execuções da suíte completa com `ConcorrenciaReservaPostgresTest` falhando:

```
[ERROR] ConcorrenciaReservaPostgresTest.vinteSolicitacoesSimultaneasGeramUmaReserva:87
        recusadas com 409 ==> expected: <19> but was: <0>
```

Com a asserção de falhas inesperadas movida para antes (diagnóstico), a causa aparece:

```
falhas inesperadas: org.postgresql.util.PSQLException: ERROR: deadlock detected
```

## Causa raiz

Com 20 INSERTs conflitantes, as transações esperam umas pelas outras na verificação da restrição `EXCLUDE` (cada uma espera a outra terminar). O PostgreSQL detecta o ciclo e aborta uma delas com SQLSTATE `40P01`. O código tratava só `23P01` como 409, então o deadlock virou erro interno. A integridade se manteve (nenhuma duplicata), mas a resposta estava errada.

## Correção

Antes do `INSERT`, a transação trava a linha do recurso (`SELECT ... FOR UPDATE`). Solicitações para o mesmo recurso passam em fila, sem ciclo de espera. Quando a transação seguinte entra, a anterior já está confirmada, e a restrição recusa com `23P01` → 409.
