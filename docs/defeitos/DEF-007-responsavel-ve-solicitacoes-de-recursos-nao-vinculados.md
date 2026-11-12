# DEF-007 — Responsável vê solicitações de recursos não vinculados a ele

**Status:** corrigido (commit da branch `feat/front-login-carga-java21`)
**Data da detecção:** 2026-11-12, durante a implementação de `GET /reservas/pendentes`
**Severidade:** alta — vazamento de solicitações de outros setores; relaciona-se ao limitador "falha de autorização"
**Regras afetadas:** RF-07 e RN-07 (aprovação só pelo Responsável vinculado), D3 (vínculo Responsável–recurso)
**Testes que detectaram:** `CoberturaRiscoReservasTest.pendentesSoDeRecursoVinculado` (novo) e `FluxoEndToEndTest.responsavelSemVinculoNaoAprova`

## Passos para reproduzir

1. Administrador vincula `resp` ao recurso `SALA-A`, mas não a `SALA-B`.
2. Solicitante cria reserva restrita em `SALA-B` (estado `SOLICITADA`).
3. `resp` consulta as solicitações pendentes.

**Esperado:** lista vazia (a solicitação é de recurso que `resp` não administra).
**Obtido:** a solicitação de `SALA-B` aparecia. `ServicoReservas.pendentesDeAnalise` filtrava só por estado, sem checar o vínculo.

## Evidência

- Identificado por leitura de código ao expor `pendentesDeAnalise` por HTTP. Não houve teste automatizado que o capturasse antes da correção.
- O teste existente `CoberturaRiscoReservasTest.pendentesDeAnaliseFiltraSolicitadas` assumia o comportamento defeituoso (Responsável sem vínculo vendo a solicitação). Ele foi ajustado para vincular o Responsável, que é a regra.
- Teste de regressão adicionado após a correção: `pendentesSoDeRecursoVinculado`. Ele não foi observado falhando contra a versão antiga; a afirmação de que a regra é esta está na especificação do vínculo (D3).

## Causa raiz

`pendentesDeAnalise` filtrava apenas `estado == SOLICITADA` e ignorava o vínculo, que é exigido por `exigirAutorizacaoTransicao` na aprovação. O método nunca tinha sido exposto por HTTP, o que deixou o defeito sem uso até a criação da rota.

## Correção

Filtro adicional `vinculado(ator, r)` em `pendentesDeAnalise`. Teste de regressão: `pendentesSoDeRecursoVinculado`.
