# Decisão de qualidade — v1.0.0-rc1

**Tag avaliada:** `v1.0.0-rc1`, commit [`14d766a`](https://github.com/mariarrcruz/Organizacao-Recursos-Universidade/commit/14d766a)
**Repositório:** https://github.com/mariarrcruz/Organizacao-Recursos-Universidade (público)
**Pacote de evidências:** [`docs/evidencias/README.md`](https://github.com/mariarrcruz/Organizacao-Recursos-Universidade/blob/14d766a/docs/evidencias/README.md)
**Data:** 2026-11-12

---

## 1. Decisão

**LIBERAR COM RESSALVAS**, como candidata à entrega acadêmica (rc1).

Esta decisão **não** autoriza uso em produção. Os testes foram rodados em máquina de desenvolvimento e no runner do GitHub, sem ambiente de referência e sem usuários reais. O sistema não tem persistência de usuários, movimentações e notificações, e a autenticação depende de credenciais em memória.

A entrega acadêmica só fica completa depois que a revisão por pares for feita (critério C7 abaixo). Esse item depende de outros integrantes e não pode ser resolvido por esta decisão.

---

## 2. Critérios e evidência

Os limitadores de nota da especificação entram como critérios obrigatórios. Os demais critérios medem a qualidade do que foi entregue.

| # | Critério | Limite | Resultado | Evidência | Situação |
|---|---|---|---|---|---|
| C1 | Aplicação executa (limitador) | Sobe e responde seguindo o README | Sobe, responde a `/` e ao exemplo de `POST /dev/tokens`. O build exigia JDK 21, mas o README dizia 17; corrigido. | Clone limpo, `checklist.md` §1 | **Atendido** |
| C2 | Pipeline (limitador) | CI verde no commit avaliado | Run [43327161512](https://github.com/mariarrcruz/Organizacao-Recursos-Universidade/actions/runs/43327161512): sucesso | `docs/evidencias/README.md` §1 | **Atendido** |
| C3 | Autorização (limitador) | Respostas conforme a matriz de perfis; sem token → 401 | `MatrizAutorizacaoTest`, `RF15ControleAcessoPerfilTest`, `RF16PropriedadeReservaTest`, E2E (`semTokenRecusa`, `solicitanteNaoAdministra`, `responsavelSemVinculoNaoAprova`) | Testes passam no build do commit | **Atendido com ressalva** (R1, R2) |
| C4 | Dupla reserva (limitador) | No máximo uma reserva por alocação sob concorrência | `ConcorrenciaReservaPostgresTest`, `ConcorrenciaReservaHttpPostgresTest` (20 requisições simultâneas, PostgreSQL real) | Testes passam; mutação do lock detectada em `b4c94b1` | **Atendido com ressalva** (R3) |
| C5 | Testes automatizados | Build verde | `./mvnw -B verify`: 643 testes, 0 falhas | Clone limpo e CI | **Atendido** |
| C6 | Cobertura | Linhas ≥ 80 %, ramos ≥ 70 % | Linhas 92,5 %, ramos 83,7 % | [`docs/qualidade/jacoco.xml`](https://github.com/mariarrcruz/Organizacao-Recursos-Universidade/blob/14d766a/docs/qualidade/jacoco.xml) | **Atendido** |
| C7 | Revisão por pares | Revisão de outro integrante em cada alteração | Os PRs #1 a #18 não têm revisão formal no GitHub. Todos têm como autor a conta `IagoSouzas`. | `gh pr view` nos PRs | **Não atendido** (R4) |
| C8 | Análise estática | Zero bugs e vulnerabilidades | Quality gate OK, 0 bugs, 0 vulnerabilidades, 1 code smell (S8786) | [`docs/qualidade/sonarcloud.json`](https://github.com/mariarrcruz/Organizacao-Recursos-Universidade/blob/14d766a/docs/qualidade/sonarcloud.json) (análise do commit `14d766a`) | **Atendido** (code smell aberto) |
| C9 | Desempenho | p95 ≤ 500 ms, erro < 1 %, três rodadas | Rodadas locais 05 a 07: consulta p95 3 ms, criação p95 38 a 39 ms. Carga no GitHub (run `43322490773`): consulta p95 1 ms, criação p95 2 ms. | `docs/performance/resultado-03.md`, `docs/performance/github-carga-43322490773/` | **Atendido, sem ambiente de referência** (R5) |
| C10 | Mutação das regras críticas | Sete quebras detectadas | Detectadas no commit `b4c94b1`. Não repetidas no código atual. | `docs/testes/mutacao-manual.md` | **Não confirmado no código atual** (R6) |
| C11 | Rastreabilidade | Requisitos críticos com teste e evidência | RTM com linhas para os requisitos críticos e testes que existem no repositório | [`docs/rtm.md`](https://github.com/mariarrcruz/Organizacao-Recursos-Universidade/blob/14d766a/docs/rtm.md) | **Atendido** (requisitos parciais listados no próprio RTM) |
| C12 | Defeitos | Defeitos registrados e tratados | DEF-001 a DEF-010, todos corrigidos | [`docs/defeitos/`](https://github.com/mariarrcruz/Organizacao-Recursos-Universidade/tree/14d766a/docs/defeitos) | **Atendido** |
| C13 | Requisitos funcionais | Todos os obrigatórios atendidos | Parciais: movimentações e usuários em memória; auditoria só de reservas; interface não aberta em navegador | `docs/relatorio-qualidade.md` §2.2, `CHANGELOG.md` (pendências) | **Parcial** (R7, R8, R9) |

---

## 3. Riscos aceitos

| ID | Risco | Impacto | Probabilidade | Justificativa para aceitar | Responsável pelo acompanhamento |
|---|---|---|---|---|---|
| R1 | Credenciais em memória: usuários somem ao reiniciar; o login não tem persistência | Alto em produção; baixo na demonstração | Certa a cada reinício | Fora do escopo desta entrega; documentado no README e no CHANGELOG | A definir pela equipe |
| R2 | Administrador só existe se `APP_BOOTSTRAP_ADMIN_SENHA` for definida | Médio: sem a variável, as telas de administração ficam inacessíveis | Certa sem a variável | Documentado no README; é um comportamento de segurança (sem senha padrão no código) | A definir pela equipe |
| R3 | Teste de concorrência é probabilístico: mostra sensibilidade, não ausência de corrida | Baixo: a garantia vem da constraint `EXCLUDE` e do lock consultivo | Baixa | Dois mecanismos independentes no PostgreSQL; mutação detectada | A definir pela equipe |
| R4 | Sem revisão por pares | Médio para o processo acadêmico; baixo para o código | Certa | **Não aceito como encerrado.** É exigência da especificação e precisa ser resolvida antes da entrega final | A definir pela equipe (ver §5) |
| R5 | Desempenho medido sem ambiente de referência | Médio: o limite de 500 ms não está fixado pela equipe | Média | Medição em duas condições diferentes (local e GitHub) com o mesmo resultado; o critério ainda não é de aceite formal | A definir pela equipe |
| R6 | Mutação não repetida sobre o código atual | Médio: pode haver regressão não detectada pelos testes atuais | Baixa a média | Os testes de concorrência e de regra passaram depois das mudanças; falta a prova de sensibilidade atual | A definir pela equipe |
| R7 | Movimentações de material em memória | Médio: retirada e devolução se perdem ao reiniciar | Certa a cada reinício | Documentado no CHANGELOG | A definir pela equipe |
| R8 | Auditoria só de reservas | Baixo a médio, conforme a leitura de "histórico de mudanças" | Certa | Documentado no CHANGELOG e no relatório | A definir pela equipe |
| R9 | Interface não verificada em navegador | Baixo a médio: possível problema de layout | Média | Testes de conteúdo e end-to-end passam; a verificação visual é pendência | A definir pela equipe |

---

## 4. Justificativa

- Os quatro limitadores da especificação (aplicação executa, pipeline, autorização, dupla reserva) têm evidência reproduzível no commit `14d766a`. Por isso a decisão não é "bloquear".
- A evidência de desempenho e de análise estática vem de mais de uma fonte, e as duas dão o mesmo resultado. Isso sustenta o "atendido" com ressalva.
- A decisão não é "liberar" sem ressalvas. Três pontos ainda estão abertos e afetam a entrega: a revisão por pares (C7), a repetição da mutação (C10) e a falta de ambiente de referência (C9). Além disso, há requisitos funcionais parciais (C13).
- Nada aqui permite dizer que o sistema está pronto para produção.

---

## 5. Condições para a entrega final

1. **Revisão por pares** de pelo menos um integrante, registrada no GitHub, em alterações posteriores à `v1.0.0-rc1`. Não é possível registrar retroativamente a revisão dos PRs já mergeados sem contrariar o histórico.
2. **Repetição das sete quebras** de `docs/testes/mutacao-manual.md` sobre o código atual, com o resultado registrado.
3. **Decisão da equipe** sobre o limite de desempenho e sobre a medição em ambiente de referência.

Se alguma dessas condições não for cumprida até a entrega, a decisão passa a ser **bloquear** a entrega final.

---

## 6. Declaração dos integrantes

Cada integrante deve confirmar ou corrigir o registro abaixo. Não há declaração feita pelo redator em nome de outra pessoa.

| Integrante | O que consta no registro do repositório | Declaração |
|---|---|---|
| Iago Souza | 94 commits em `main`, com as duas grafias de autor; autor de todos os PRs #1 a #18 no GitHub, incluindo os PRs #11 e #12 (mutação e Anexo A da RTM), cujos ramos estão no repositório `mariarrcruz` | _a confirmar pelo integrante_ |
| Maria Rita Ribeiro Cruz | 3 commits em `main`, de 8 a 9 de outubro (documentação de arquitetura e personas; commit inicial) | _a confirmar pelo integrante_ |
| ThalesConstruct | 2 commits em `main`, de 23 de outubro (análise ATAM e ajustes de arquitetura) | _a confirmar pelo integrante_ |
| Thais Rodrigues | 1 commit em `main`, de 16 de outubro (refatoração de estrutura de código) | _a confirmar pelo integrante_ |

**Observação:** o registro do GitHub não mostra revisões de PRs por nenhum integrante, e não mostra quais partes de cada commit foram de cada pessoa. Os textos de declaração devem ser preenchidos por cada integrante, com o que fez de fato.

---

## 7. Responsável pela decisão

- **Redator:** assistente de IA, a pedido de Iago Souza. Preparou o texto a partir das evidências listadas.
- **Decisão:** a equipe, após revisão dos critérios e das ressalvas. _Confirmação pendente._
