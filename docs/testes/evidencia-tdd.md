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

- **Q1 (recurso livre precisa de aprovação?):** exige decisão de produto. Não foi escolhido.
- **Q9 (cancelamento no instante do início):** já passa com o código atual, que recusa com 409 quando `agora >= início`. Não gera vermelho.
- **Demais cenários do documento:** a maioria já passa com o código atual. Um teste que já passa não pode ser visto vermelho, o que quebra a regra do ciclo.
- **Injeção de `Clock` nas regras de manutenção:** não há teste vermelho que a exija hoje.
