# DEF-001 — Dupla reserva sob solicitações simultâneas (persistência sem proteção)

**Status:** corrigido (ver ADR-016; commit de correção indicado na seção Correção)
**Data da detecção:** 2026-11-11
**Severidade:** crítica — limitador de nota (PRD RISK-01, dupla reserva inviabiliza o sistema)
**Regras afetadas:** RN-02 (exclusividade de alocação), RN-03 (atomicidade sob concorrência), RNF-02
**Teste que detectou:** `src/test/java/com/organizacao/recursos/persistencia/ConcorrenciaReservaPostgresTest.java`

## Passos para reproduzir

1. Subir PostgreSQL 16 (Testcontainers), criar o esquema com `RepositorioReservasPostgres.criarEsquema()`.
2. Registrar um recurso (SALA ou PROFESSOR).
3. Preparar 20 threads que esperam um `CountDownLatch` e chamam `solicitar(recurso, solicitante, 2026-11-20T10:00, 2026-11-20T11:00)`.
4. Liberar o latch e coletar as respostas.
5. Consultar `reserva` pelo recurso e intervalo sobreposto.

**Esperado:** 1 reserva criada (201), 19 recusadas (409), 1 linha no banco.

## Evidência

Execução do teste contra a versão sem proteção (commit anterior à correção):

```
ConcorrenciaReservaPostgresTest.vinteSolicitacoesSimultaneasGeramUmaReserva(TipoRecurso)[1]
  org.opentest4j.AssertionFailedError: aceitas ==> expected: <1> but was: <20>
ConcorrenciaReservaPostgresTest.vinteSolicitacoesSimultaneasGeramUmaReserva(TipoRecurso)[2]
  org.opentest4j.AssertionFailedError: aceitas ==> expected: <1> but was: <5>
Tests run: 2, Failures: 2
```

Diagnóstico temporário com a mesma carga, repetido com consulta ao banco:

```
DIAG SALA      respostas={201=10, 409=10} linhasNoBanco=10
DIAG PROFESSOR respostas={201=3, 409=17}  linhasNoBanco=3
```

As linhas no banco batem com as respostas 201, portanto o banco gravou reservas sobrepostas, não é só uma resposta errada.

## Causa raiz

`RepositorioReservasPostgres.solicitar` fazia **verificar e depois inserir**: um `SELECT` procurava sobreposição e, se não achava nada, um `INSERT` gravava a reserva. Entre o `SELECT` de uma transação e o `INSERT` de outra, as 20 threads viam "nenhuma sobreposição" e todas inseriam. Nada no banco impedia isso: a tabela `reserva` não tinha restrição de exclusão.

## Correção

- Restrição `EXCLUDE USING gist (recurso_id WITH =, periodo WITH &&) WHERE (estado IN ('SOLICITADA','APROVADA','EM_USO'))` na tabela `reserva`, com a extensão `btree_gist`.
- `solicitar` passa a só fazer o `INSERT`; a violação (SQLSTATE `23P01`) vira `ErroAplicacao(409)`.
- Decisão registrada em `docs/adr/016-exclusao-postgres-tstzrange.md`.

Verificação: o mesmo teste passa nas cinco execuções seguidas (ver a seção de verificação do ADR-016 e o histórico de commits).
