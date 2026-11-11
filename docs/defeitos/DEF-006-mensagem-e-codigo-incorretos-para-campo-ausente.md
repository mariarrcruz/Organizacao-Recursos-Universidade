# DEF-006 — Campo obrigatório ausente gera mensagem sem sentido ou 404 em vez de 400

**Status:** corrigido (commit `5ee2077`)
**Data da detecção:** 2026-11-11
**Severidade:** média — o cliente não sabe qual campo corrigir; "null" aparece na mensagem; recurso ausente vira "não encontrado"
**Regras afetadas:** validação de entrada (todas as operações com corpo ou parâmetro obrigatório)
**Testes que detectaram:** `ValidacaoEntradaTest.inicioAusente`, `ValidacaoEntradaTest.recursoAusente`

## Passos para reproduzir

1. `POST /reservas` sem `recursoId`, com `inicio` e `fim` válidos.
2. `POST /reservas` sem `inicio`.

**Esperado:** 400, com `detail` indicando o campo ausente (`recursoId` ou `inicio`).
**Obtido:**
- (1) 404 com `{"erro":"Recurso não encontrado: null"}`.
- (2) 400 com `{"erro":"Data inválida: null"}`.

## Evidência

```
recursoAusente: {"erro":"Recurso não encontrado: null"} ==> expected: <400> but was: <404>
inicioAusente: {"erro":"Data inválida: null"} ==> expected: <true> but was: <false>
```

## Causa raiz

- `ReservasController.solicitar` busca o recurso antes de verificar se `recursoId` foi informado; o `null` vira "não encontrado" e 404.
- `Datas.instante` formata a mensagem com o valor recebido, sem indicar qual campo falhou.
