# Evidência TDD — Aprovação, Bloqueio por Manutenção e Estados da Reserva

**Especificação:** `docs/testes/bdd-aprovacao-bloqueio.md`
**Regra:** um cenário por vez. Commit `test(red)` com teste que falha por asserção, commit `feat(green)` com o mínimo para a suíte inteira ficar verde, commit `refactor` só se houver algo a limpar sem mudar comportamento.

## Tabela de evidências

| Cenário | Commit red | Commit green | Commit refactor | Trecho da saída do teste (red) |
|---|---|---|---|---|
| F2 — Usuário com perfis SOLICITANTE e RESPONSAVEL não aprova a própria reserva (403, estado continua SOLICITADA) | `c5b34a7` | `18e4e51` | não aplicável: nada a limpar sem mudar comportamento | `org.opentest4j.AssertionFailedError: Expected com.organizacao.recursos.dominio.ErroAplicacao to be thrown, but nothing was thrown.` |
| Q5 — Manutenção posterior sobrepõe reserva SOLICITADA: aprovação recusada com 409 e estado continua SOLICITADA (proposta do RTM, fluxo de aprovação) | `592fb3d` | `d796aae` | não aplicável: nada a limpar sem mudar comportamento | `org.opentest4j.AssertionFailedError: Expected com.organizacao.recursos.dominio.ErroAplicacao to be thrown, but nothing was thrown.` |

## Suíte completa

| Momento | Comando | Resultado |
|---|---|---|
| Antes do ciclo (baseline) | `./mvnw -B test` | 273 testes, 0 falhas, `BUILD SUCCESS` |
| Ciclo F2, com o teste red | `./mvnw -B test` | 274 testes, 1 falha (`AutoaprovacaoReservaTest`), esperada |
| Ciclo F2, após o green | `./mvnw -B test` | 274 testes, 0 falhas, `BUILD SUCCESS` |
| Ciclo Q5, com o teste red | `./mvnw -B test` | 275 testes, 1 falha (`RevalidacaoAprovacaoTest`), esperada |
| Ciclo Q5, após o green | `./mvnw -B test` | 275 testes, 0 falhas, `BUILD SUCCESS` |

## Mudanças mínimas (green)

- **F2:** `ServicoReservas.exigirAutorizacaoTransicao`: para `APROVADA`, a permissão exige também que o ator não seja o dono da reserva. `REJEITADA` e `NAO_COMPARECEU` mantêm a regra anterior (ver Q2).
- **Q5:** `ServicoReservas.transicionar`: na aprovação (`APROVADA`), revalida disponibilidade e manutenção ignorando a própria reserva; conflito responde 409. Segue o fluxo proposto no RTM. A escolha da opção "recusar com 409" (e não cancelar ou apenas sinalizar) é uma decisão minha, tomada por ser a única opção descrita no RTM; o PRD não decide.

## Cenários ainda não percorridos

- **Q1 (recurso livre precisa de aprovação?):** decidida pelo PRD (ver ciclo abaixo): recurso livre entra APROVADA.
- **Q9 (cancelamento no instante do início):** já passa com o código atual, que recusa com 409 quando `agora >= início`. Não gera vermelho.
- **Demais cenários do documento:** a maioria já passa com o código atual. Um teste que já passa não pode ser visto vermelho, o que quebra a regra do ciclo.
- **Injeção de `Clock` nas regras de manutenção:** não há teste vermelho que a exija hoje.

## Ciclo — Recurso livre entra APROVADA (PRD RF-07, RN-07)

Decisão do usuário: seguir o PRD. Aprovação é exigida só para recurso restrito; recurso livre entra já `APROVADA`.

| Cenário | Commit red | Commit green | Commit refactor | Trecho da saída do teste (red) |
|---|---|---|---|---|
| Reserva de recurso livre nasce APROVADA sem aprovação de Responsável | `f4f87a1` | `ac22ade` | não aplicável | `expected: <APROVADA> but was: <SOLICITADA>` |

**Ajustes de expectativa exigidos pela regra nova** (não são alterações para fazer teste passar; a regra mudou):
- Testes que criam reserva em recurso **livre** e esperavam `SOLICITADA` passam a esperar `APROVADA`: `RN01OrdemTemporalTest` (1), `RN02ExclusividadeAlocacaoTest` (3), `RN04ManutencaoTest` (4), `RF15ControleAcessoPerfilTest` (2).
- `AutoaprovacaoReservaTest` e `RevalidacaoAprovacaoTest` usam recurso **restrito**, porque o cenário pressupõe reserva aguardando aprovação (`SOLICITADA`).

**Suíte:** `./mvnw -B test` — 276 testes, 0 falhas, `BUILD SUCCESS`.
