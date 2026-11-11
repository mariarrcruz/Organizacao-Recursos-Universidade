# DEF-005 — Cadastro de recurso aceita capacidade negativa

**Status:** aberto (correção no commit que referencia este defeito)
**Data da detecção:** 2026-11-11
**Severidade:** média — dado inválido persistido (recurso com capacidade -5 aparece em pesquisas e relatórios)
**Regras afetadas:** validação de entrada do cadastro de recursos (RF-02)
**Teste que detectou:** `ValidacaoEntradaTest.capacidadeNegativa`

## Passos para reproduzir

1. `POST /recursos` como administrador com `"capacidade": -5`.

**Esperado:** 400, com `detail` mencionando a capacidade e nenhum recurso gravado.
**Obtido:** 201, recurso gravado.

## Evidência

```
capacidadeNegativa: expected: <400> but was: <201>
```

## Causa raiz

`RecursosController` grava `corpo.capacidade()` sem validação. Não há checagem de intervalo em `Recurso` nem na camada de controle.
