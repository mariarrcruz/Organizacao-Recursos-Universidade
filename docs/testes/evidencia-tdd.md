# Evidência TDD — Aprovação, Bloqueio por Manutenção e Estados da Reserva

**Especificação:** `docs/testes/bdd-aprovacao-bloqueio.md`
**Regra:** um cenário por vez. Commit `test(red)` com teste que falha por asserção, commit `feat(green)` com o mínimo para a suíte inteira ficar verde, commit `refactor` só se houver algo a limpar sem mudar comportamento.

## Tabela de evidências

| Cenário | Commit red | Commit green | Commit refactor | Trecho da saída do teste (red) |
|---|---|---|---|---|
| F2 — Usuário com perfis SOLICITANTE e RESPONSAVEL não aprova a própria reserva (403, estado continua SOLICITADA) | `c5b34a7` | `18e4e51` | não aplicável: nada a limpar sem mudar comportamento | `org.opentest4j.AssertionFailedError: Expected com.organizacao.recursos.dominio.ErroAplicacao to be thrown, but nothing was thrown.` |

## Suíte completa

| Momento | Comando | Resultado |
|---|---|---|
| Antes do ciclo (baseline) | `./mvnw -B test` | 273 testes, 0 falhas, `BUILD SUCCESS` |
| Com o teste red | `./mvnw -B test` | 274 testes, 1 falha (`AutoaprovacaoReservaTest`), esperada |
| Após o green | `./mvnw -B test` | 274 testes, 0 falhas, `BUILD SUCCESS` |

## Mudança mínima (green)

`ServicoReservas.exigirAutorizacaoTransicao`: para `APROVADA`, a permissão agora exige também que o ator não seja o dono da reserva. `REJEITADA` e `NAO_COMPARECEU` mantêm a regra anterior (ver Q2 no documento de cenários).

## Cenários ainda não percorridos

Não foram escritos testes para os demais cenários. Ao ler o código, a maioria já passa hoje, e um teste que já passa não pode ser visto vermelho, o que quebra a regra do ciclo. Os cenários que podem ser vermelhos dependem de decisões de produto ainda abertas no documento (Q1, Q5, Q9), ou já estão cobertos pelo comportamento atual.
