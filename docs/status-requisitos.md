# Status dos Requisitos — Auditoria de Qualidade

> **Atualização posterior à auditoria (2026-11-12).** Esta auditoria foi feita sobre um commit anterior. Estão resolvidos: login por senha (PEN-01 e PEN-01b), Java 21 no build, pesquisa, consulta, edição e exclusão de recursos (PEN-05), movimentações por HTTP (RF-09), notificações simuladas com `GET /notificacoes` (RF-11, PEN-08), interface em `/` (RF-13), teste end-to-end (TG-05), teste de concorrência entre manutenção e reserva (PEN-02), correção dos bugs `S8700` (confirmação no SonarCloud pendente) e troca do mock de persistência. O vínculo Responsável–recurso já estava implementado (PEN-01c). Pendências reais: desempenho (DEF-008, rodadas em `docs/performance/resultado-02.md`), persistência de usuários e movimentações, e relatórios HTTP além do de utilização.

Sistema de Organização de Recursos — auditoria em 11/11/2026 (semana ISO 46).

**Escopo:** os 14 requisitos funcionais obrigatórios do PRD (RF-01 a RF-14, `docs/PRD.md` §3), as regras de negócio RN-01 a RN-09 e os quatro limitadores de nota de RISK-01 (`docs/PRD.md` §6). Os RFs de `rfc/requisitos-funcionais.md` (RF-15 a RF-20) foram usados como evidência complementar, sem pontuação separada.

**Método:**
- Leitura do código em `src/main/java`, dos testes em `src/test/java`, dos `.feature` e de `docs/RTM.md`.
- `./mvnw -B verify` executado localmente: **BUILD SUCCESS, 372 testes, 0 falhas, 0 erros**.
- Mutações pontuais em cópias descartáveis fora do repositório, para verificar se os testes realmente falham quando a regra quebra (ver §3).
- Aplicação empacotada (`target/organizacao-recursos-0.1.0-SNAPSHOT.jar`) executada contra um PostgreSQL 16 descartável na porta 5433, com fluxo HTTP real (ver §4).
- Nenhum código de produção foi alterado.

**Legenda da situação:**
- **Feito:** a regra existe na aplicação (não só no serviço) e há teste que falharia se ela quebrasse.
- **Parcial:** a regra existe em parte, ou só como serviço sem rota HTTP, ou com lacuna declarada.
- **Ausente:** não há implementação na aplicação.

**Legenda da verificação (coluna Evidência):**
- **[M]** mutação executada nesta auditoria: a suíte ficou vermelha no teste indicado.
- **[L]** verificado por leitura: o teste faz asserção que falharia sem a regra, mas não foi mutado nesta auditoria.

---

## 1. Resumo

| Item | Resultado |
|---|---|
| `./mvnw -B verify` | BUILD SUCCESS — 372 testes, 0 falhas |
| Cobertura JaCoCo (aferida) | Linhas **92,8%** (meta ≥ 80%); ramos **87,4%** (meta ≥ 70%); gate ativo no `verify` |
| CI no GitHub Actions | Verde nos PRs #6 e #7 e nos pushes de `main`; passo SonarCloud concluído com sucesso (resultado do Quality Gate não auditado) |
| Aplicação executável | Sobe em ~2,7 s; fluxo principal responde corretamente (ver §4) |
| Requisitos funcionais (PRD, 14) | **5 feitos** (RF-04 a RF-08), **6 parciais** (RF-01, RF-02, RF-09, RF-10, RF-12, RF-14), **3 ausentes** (RF-03 na aplicação, RF-11, RF-13) (ver §2) |
| Regras RN-01 a RN-09 | **9 feitas** (RN-06 e RN-04 com ressalvas de política em aberto) |
| Limitadores de RISK-01 | Pipeline: atendido. Aplicação: atendida com ressalvas. Dupla reserva: atendida no caminho principal, com risco residual. **Autorização: parcial, com falhas reais** |

**Leitura geral:** a regra de negócio central está bem coberta e comprovada por mutação (autorização de aprovação, dupla reserva, JWT, transições, dono da reserva). O maior problema é de **exposição**: vários serviços e regras existem apenas em Java e nos testes, sem rota HTTP. Para o usuário final, a aplicação hoje é uma API de reservas com cadastro de recursos e manutenção. Não há login, interface, pesquisa, relatórios, movimentações nem notificação.

---

## 2. Requisitos funcionais obrigatórios (PRD RF-01 a RF-14)

| Requisito | Situação | Evidência | Risco se ficar de fora |
|---|---|---|---|
| **RF-01** Autenticação e autorização por perfil (JWT) | **Parcial** | Validação do JWT feita em `ServicoAutenticacao.autenticar` (assinatura RS256, expiração, emissor, audiência, perfis). Testes: `RF01AutenticacaoJwtTest` (incl. `rf01_5_assinaturaAdulteradaEhRejeitada`) e `CoberturaRiscoAutenticacaoTest` [M] — a mutação que desligou a verificação de assinatura derrubou 3 testes. **Login ausente:** `POST /auth/login` retorna 404 no jar [executado]. Tokens só existem via `POST /dev/tokens`, ativo com `app.dev.tokens=true` | Sem login, não há autenticação de usuário real; o sistema confia no `sub` e nos perfis declarados pelo emissor de token. Falha de autorização é limitador de nota (ver PEN-01) |
| **RF-02** Gestão de recursos (salas, professores, materiais) | **Parcial** | Na aplicação: apenas `POST /recursos` (cadastro, somente ADMINISTRADOR, `RecursosController`). `ServicoRecursos` (editar, excluir, consultar) **não é bean nem tem rota**; nenhum outro código o instancia. Testes: `RF02GestaoRecursosTest` (10 testes) exercitam o serviço em memória [L] | Administrador não edita nem remove recursos pela aplicação; não há consulta de recurso por API |
| **RF-03** Pesquisa parametrizada de recursos | **Ausente** (na aplicação) | Lógica existe em `ServicoRecursos.pesquisar` (tipo, localização, capacidade, competência, disponibilidade). Testes `RF03PesquisaRecursosTest` (8 testes) em memória [L]. `GET /recursos` retorna 405 no jar [executado] | Requisito central de uso (buscar sala livre) invisível ao usuário |
| **RF-04** Ciclo de vida das reservas | **Feito** | `ReservasController`: POST, GET, GET/{id}, PUT, DELETE, aprovação, rejeição, início de uso, conclusão, cancelamento, não comparecimento. 80 cenários BDD em `src/test/resources/features/*.feature` executados via HTTP (`RodarCenariosTest`). Verificado no jar: criação 201 com estado APROVADA (recurso não restrito), conflito 409 [executado] | Sem o ciclo, nada funciona |
| **RF-05** Detecção de sobreposição (salas, professores, materiais; agenda do professor) | **Feito** | `ServicoReservas.conflita` com intervalos semiabertos. Testes `RN02ExclusividadeAlocacaoTest` (rn02_3 a rn02_8: sobreposição parcial, deslocada, contenção, idêntica) [L]; `ConcorrenciaReservaHttpPostgresTest` cobre `PROFESSOR` além de `SALA` [M, via RF-06] | Reservas conflitantes aceitas |
| **RF-06** Proteção contra dupla reserva concorrente | **Feito** | Exclusion constraint `reserva_sem_sobreposicao` (GiST, `schema.sql`) + `pg_advisory_xact_lock` por recurso (`ArmazenamentoPostgres.travarRecurso`) + tradução SQLSTATE 23P01 → 409. Testes: `ConcorrenciaReservaHttpPostgresTest.vinteRequisicoesSimultaneasGeramUmaReserva` (20 HTTP simultâneas, 20 instâncias de serviço) e `ConcorrenciaReservaPostgresTest.vinteSolicitacoesSimultaneasGeramUmaReserva`. **Mutação [M]:** removidas a constraint e a trava → os dois testes falharam (ex.: `criadas (201) expected 1 but was 19`). Ressalva: teste de concorrência é probabilístico; a mutação mostra sensibilidade, não prova ausência de corrida | Dupla reserva é limitador de nota |
| **RF-07** Recursos restritos com aprovação obrigatória | **Feito** | `ServicoReservas.solicitar` (restrito → SOLICITADA, não restrito → APROVADA); transição a APROVADA exige RESPONSAVEL. `RN07AlcadaAprovacaoTest`, `aprovacao-bloqueio.feature` (via HTTP), `RecursoLivreAprovacaoTest`. **Mutação [M]:** permitir aprovação a qualquer ator → 10 falhas (`RN07AlcadaAprovacaoTest.rn07_2/3/5`, `AutoaprovacaoReservaTest`, `rf15_s6`, `rf15_a12` e cenários BDD). Restrito sem aprovação não entra em uso: cenário "Início de uso sem aprovação é bloqueado" [M] | Recurso sensível alocado sem Responsável |
| **RF-08** Bloqueio por manutenção | **Feito** | `POST /manutencoes` (ADMINISTRADOR) e checagem em `ServicoReservas.conflita` e em `ArmazenamentoPostgres.verificarManutencao` (tstzrange `[)`). Status MANUTENCAO bloqueia reserva (`StatusRecursoBloqueioTest`). `bloqueio-manutencao.feature` via HTTP; `RN04ManutencaoTest` com fronteiras (B1–B7) [L]. Mensagem de bloqueio na resposta (`{"erro": "..."}`) | Reserva em período de manutenção aceita. A exibição na interface depende de RF-13 |
| **RF-09** Retirada e devolução de materiais | **Parcial** | Lógica em `ServicoMovimentacoes` (retirada só em APROVADA/EM_USO; devolução só do material em aberto). Testes `RF10MovimentacoesMaterialTest` (8 testes) [L]. **Sem rota HTTP. Histórico em `ArrayList` em memória** (`ServicoMovimentacoes.movimentos`), sem persistência | Retirada não registrada na aplicação; histórico perdido ao reiniciar; RF-09 exige persistência para auditoria |
| **RF-10** Auditoria de modificações | **Parcial** | Gravação na mesma transação de cada transição (`ArmazenamentoPostgres.atualizarReserva`), tabela `evento_auditoria` append-only por trigger (`schema.sql`). Teste real do trigger: `ArmazenamentoPostgresTest.auditoriaEhSomenteInsercao` (UPDATE e DELETE devem falhar) [L]. Consulta: `GET /reservas/{id}/auditoria` retorna 200 para Responsável e Administrador e 403 para Solicitante, inclusive o dono [executado; teste HTTP `ReservasAuditoriaControllerTest`]. Cada registro expõe `instante` (ISO local, vindo do Clock) [L]. **Lacuna:** recursos não são auditados | Sem data na trilha de auditoria; alterações de recurso sem histórico |
| **RF-11** Comunicação e notificação simulada | **Ausente** | Nenhuma ocorrência de "notifica" em `src/main/java`; nenhum teste. `docs/RTM.md` §3.12 descreve AD-06 (logs) como planejamento | Requisito obrigatório sem nenhuma evidência; RF-17 depende dele |
| **RF-12** Relatórios gerenciais | **Parcial** | **Com rota:** `GET /relatorios/utilizacao?inicio=&fim=` devolve horas por recurso, carga horária por professor e conflitos evitados. Regras no README; testes de caixa-preta `RelatorioUtilizacaoMemoriaTest` e `RelatorioUtilizacaoPostgresTest` (massa semeada pela API, contas à mão nos comentários), com 401, 403 e 400 cobertos [M]. Mutações de regra detectadas. **Sem rota:** carga horária por sala, contagem por estado e movimentações. `ServicoRelatorios` continua com testes em memória (`RF12RelatoriosTest`, incluindo `rf12_8`, medido **sem HTTP** [L]). Sem exportação; filtro por unidade e por professor/material não implementado | Gestor não gera os demais relatórios; desempenho ainda não medido na aplicação |
| **RF-13** Interface do usuário | **Ausente** | Nenhum `.html`, template ou `static/` em `src/main/resources`; `pom.xml` sem Thymeleaf; `GET /` retorna 404 [executado]. Mensagens de erro existem só como JSON (`TratadorErros`) | Requisito obrigatório inteiro sem entrega; avaliação de usabilidade impossível |
| **RF-14** Documentação técnica e de uso | **Parcial** | `README.md` e `docs/execucao-local.md` (o segundo confere com o jar). **O README contradiz o código:** cita H2/JPA, `POST /auth/login`, `/health`, Thymeleaf e afirma que "não há ponto de entrada web ainda" (`README.md` ~l.147). Esses trechos não correspondem à aplicação | Documentação enganosa; RF-14 e RTM-01 penalizados |

**Resumo PRD RF-01 a RF-14:** feitos 5 (RF-04 a RF-08); parciais 6 (RF-01, RF-02, RF-09, RF-10, RF-12, RF-14); ausentes 3 (RF-03 na aplicação, RF-11, RF-13).

---

## 3. Regras de negócio críticas (PRD RN-01 a RN-09)

| Regra | Situação | Evidência | Risco se ficar de fora |
|---|---|---|---|
| **RN-01** Término posterior ao início | **Feito** | `ServicoReservas.validarPeriodo`; `RN01OrdemTemporalTest` (rn01_2 fim = início, rn01_3 fim < início, rn01_5/6 ausentes) [L]. Acréscimo: duração mínima de 1 min (AMB-04, decisão provisória) | Reserva de duração zero ou negativa |
| **RN-02** Exclusividade de alocação | **Feito** | `RN02ExclusividadeAlocacaoTest` (9 testes) [L]; BDD e HTTP | Sobreposição aceita |
| **RN-03** Atomicidade sob concorrência | **Feito** | Mesma evidência de RF-06 [M]. `RN03AtomicidadeConcorrenciaTest` testa só o serviço em memória | Dupla reserva (limitador) |
| **RN-04** Recurso em manutenção não reservável | **Feito** | `RN04ManutencaoTest`, `bloqueio-manutencao.feature` via HTTP [L]. **Risco residual:** ver PEN-02 (manutenção inserida em paralelo com reserva) | Reserva durante manutenção |
| **RN-05** Fluxo nominal estrito | **Feito** | `RN05FluxoPrincipalTest` e `transicoes-reserva.feature` (7 transições permitidas, 42 proibidas via HTTP). **Mutação [M]:** liberar SOLICITADA → EM_USO → 4 falhas (`rn05_4`, cenário "Exemplos #1.1", "Transição recusada não gera evento de auditoria") | Ciclo de vida inválido |
| **RN-06** Estados alternativos | **Feito** com ressalva | `RN06EstadosAlternativosTest` (estados terminais sem saída; justificativa obrigatória em rejeição e cancelamento) [L]; `NaoComparecimentoTest`. **Ressalva:** política de não comparecimento (P7) e de cancelamento (P6) em aberto no RTM; horário só implementado na unidade | Regra de negócio parcialmente definida |
| **RN-07** Somente Responsável aprova restritos | **Feito** | Mesma evidência de RF-07 [M]. **Ressalva:** o vínculo "sob responsabilidade" não existe (ver PEN-01c) | Aprovação indevida |
| **RN-08** Reserva EM_USO não pode ser excluída | **Feito** | `RN08ImutabilidadeExecucaoTest`, `RF16PropriedadeReservaTest.rf16_4` [L]; `ServicoReservas.excluir` lança 409 | Reserva em uso apagada |
| **RN-09** Toda mudança de estado gera auditoria imutável | **Feito** | Gravação por transição: `RN09RastreabilidadeTest` (rn09_1 a rn09_7) [L]. Imutabilidade real: `ArmazenamentoPostgresTest.auditoriaEhSomenteInsercao` [L]. **Atenção:** `RN09RastreabilidadeTest.rn09_8` e `rn09_9` chamam `ServicoReservas.alterarEvento`/`excluirEvento`, que são **stubs que sempre lançam 405**. Esses dois testes passariam mesmo sem o trigger do banco; quem prova a imutabilidade é o teste de Postgres | Trilha adulterável |

---

## 4. Limitadores de nota (PRD RISK-01)

| # | Limitador | Situação | Evidência | Risco |
|---|---|---|---|---|
| 1 | Aplicação que não executa | **Atendido com ressalvas** | Jar empacotado (`./mvnw -B -DskipTests package`) sobe em ~2,3 s com a configuração padrão, contra o PostgreSQL 16 do `docker-compose.yml` (container `organizacao-recursos-universidade-postgres-1`, porta 5432), sem override de URL, conforme `docs/execucao-local.md`. Verificado: cadastro de recurso restrito por ADMINISTRADOR 201; cadastro por SOLICITANTE 403; reserva 201 em SOLICITADA; conflito 409; `inicio-uso` antes da aprovação 409; aprovação por SOLICITANTE 403; aprovação por RESPONSAVEL 200; sem token 401; auditoria com 2 eventos 200; linha persistida no banco do compose; UPDATE direto em `evento_auditoria` recusado pelo trigger | Ressalva: sem login e sem interface, o avaliador não consegue usar as funções de RF-02/03/09/12/13 |
| 2 | Pipeline ausente | **Atendido** | `.github/workflows/ci.yml` roda `./mvnw -B verify` em `pull_request` e `push` para `main`, com JaCoCo, SonarCloud e upload de relatórios. Runs verdes: 43081300519 (PR #7) e 43080138293 / 43079847589 (PR #6) | Ressalva: o resultado do **Quality Gate** do SonarCloud (SEC-01) não foi auditado (sem acesso ao painel) |
| 3 | Falha de autorização | **Parcial — limitador ativo** | Validação de JWT, perfis e dono da reserva com testes que falham se quebrados [M]. **Falhas reais:** ver PEN-01 | Alto: nota máxima depende de fechar PEN-01 |
| 4 | Possibilidade de dupla reserva | **Atendido no caminho principal** | Ver RF-06 [M]. **Risco residual:** ver PEN-02 | Médio: caminho de manutenção sem teste de concorrência |

---

## 5. Pendências — ordenadas (limitadores primeiro)

### Limitadores de nota

**PEN-01 — Autorização (limitador 3). Prioridade máxima.**
- **a) `POST /dev/tokens` vem habilitado pelo comando padrão.** `docs/execucao-local.md` §2 manda subir com `--app.dev.tokens=true`. Verificado: sem nenhuma credencial, o endpoint emitiu token com perfil `ADMINISTRADOR` para um `sub` arbitrário. Ação: retirar o flag do comando padrão e documentar; idealmente remover o endpoint da entrega, ou restringi-lo a perfil `dev`.
- **b) Não há login real.** `POST /auth/login` → 404. A identidade é o que o emissor declara. Ação: endpoint de login com usuários semeados (ou a decisão documentada de não ter login), com teste negativo de senha errada.
- **c) Responsável aprova qualquer reserva.** O vínculo "sob responsabilidade" (RFC RF-15; RTM S8) não foi modelado: `ServicoReservas.exigirAutorizacaoTransicao` aceita qualquer RESPONSAVEL. Ação: decidir o vínculo (por recurso, por unidade ou por responsável cadastrado), implementar e cobrir com teste negativo. Se a equipe optar por não modelar, registrar a decisão no RTM como premissa.

**PEN-02 — Dupla reserva (limitador 4). Risco residual.**
- `ServicoReservas.criarManutencao` insere em `manutencao` sem `pg_advisory_xact_lock` do recurso. Uma manutenção confirmada em paralelo a uma reserva pode não ser vista pela checagem de `verificarManutencao` (READ COMMITTED). **Não reproduzido nesta auditoria**: é uma hipótese de leitura do código. Ação: teste de concorrência manutenção × reserva e, se confirmado, travar o recurso também na escrita de manutenção.

**PEN-03 — Aplicação executável (limitador 1). Atendido; ressalva de usabilidade.**
- O limitador técnico está atendido. A ressalva é que o usuário não tem como usar a maior parte das funções (ver PEN-05 a PEN-07). Verificado com o compose (`docs/execucao-local.md`, §1 a §3). Ação: manter o roteiro como demonstração.

**PEN-04 — Pipeline (limitador 2). Atendido; confirmar Quality Gate.**
- Confirmar no painel do SonarCloud que o Quality Gate está passando com 0 bugs e 0 vulnerabilidades críticos (SEC-01). Ação: quem administra o projeto no SonarCloud registra o resultado no RTM.

### Requisitos funcionais não limitadores

**PEN-05 — RF-02 e RF-03: gestão e pesquisa de recursos inacessíveis.**
- Há dois armazenamentos de recurso: `RecursosController` grava no PostgreSQL, enquanto `ServicoRecursos` mantém uma lista em memória e não é usado pela aplicação. Ação: unificar sobre o PostgreSQL; expor `GET /recursos` com filtros (tipo, localização, capacidade, competência, período), `PUT` e `DELETE` (este último recusado com reserva ativa); testes HTTP.

**PEN-06 — RF-13: interface inexistente.**
- Nenhuma página. Ação: no mínimo, páginas Thymeleaf ou HTML estático servido pelo Spring para pesquisa, criação de reserva, aprovação e consulta de auditoria, com mensagens de erro que mostrem o motivo (ex.: o 409 de conflito). Decisão de escopo: se não houver tempo, declarar RF-13 como parcial na entrega.

**PEN-07 — RF-09 e RF-12: movimentações e relatórios sem rota e sem persistência.** Parcialmente resolvido: utilização por HTTP com testes de caixa-preta (branch `feat/relatorio-utilizacao`). Continuam pendentes as movimentações, a carga por sala, a contagem por estado e o desempenho medido na aplicação.
- Ação: tabela `movimentacao` no PostgreSQL com a mesma disciplina de trigger da auditoria; rotas `POST /reservas/{id}/retirada` e `/devolucao`; `GET /relatorios/...` com filtros de período, recurso e status; exportação ou tabela legível. Os testes de desempenho de `rf12_8` devem rodar sobre a aplicação e não só o serviço em memória.

**PEN-08 — RF-11 e RF-17: notificação simulada e lista de pendentes.**
- Nenhuma notificação existe. `GET /reservas/pendentes` retorna 404 (o método `ServicoReservas.pendentesDeAnalise` existe sem rota). Ação: emitir evento de resultado em log estruturado no momento da decisão (AD-06) e expor a lista de pendentes para RESPONSAVEL.

**PEN-09 — RF-10: data ausente na auditoria.** ✅ **Resolvido:** `EventoJson` inclui `instante`, coberto por `ReservasAuditoriaControllerTest.responsavelConsultaHistorico`.

**PEN-10 — RF-14 e RTM-01: documentação desatualizada.** ✅ **Resolvido:** `README.md` reescrito a partir do código (sem H2, JPA, Thymeleaf ou `/auth/login`); `docs/RTM.md` com §5.3 e §6.1 atualizados e Anexo A alinhado a este status.
- README descreve H2/JPA/Thymeleaf/`/auth/login`/`/health` que não existem, e afirma que não há ponto de entrada web. `docs/RTM.md` marca como "Planejado" itens já implementados e verificados (Anexo A: RF-01, RF-06, RF-10, RNF-04, COV, SEC; §5.3: "CI pendente", embora o CI já esteja verde). Ação: reescrever as seções de estado do README e atualizar o Anexo A com as evidências deste documento.

**PEN-11 — QUAL-01: qualidade de alguns testes.**
- Nomes "esclarecer" ou "depende de estado" em `RF15ControleAcessoPerfilTest` (rf15_s8, rf15_r7, rf15_a12) e `RF12RelatoriosTest.rf12_6` — o teste fixa o comportamento atual sem decisão de regra. `RN09` 8 e 9 testam stubs (ver §3). `rf12_8` mede o serviço em memória. Ação: renomear com a decisão tomada e trocar os stubs pelo teste de banco.

---

## 6. Divisão proposta entre os integrantes até a Semana 46

Os integrantes foram tirados dos autores do git (`git shortlog`). A divisão é uma **proposta**; a atribuição final é decisão da equipe.

**Prazo — atenção:** hoje é a semana ISO 46 (9 a 15/11/2026). Se "Semana 46" for o prazo final, restam cerca de quatro dias úteis. Nesse caso, a prioridade estrita é: PEN-01, PEN-02, a verificação de PEN-04 e PEN-05 (pesquisa de recursos). PEN-06, PEN-07 e PEN-08 podem sair como "parcial" declarado. Confirmar o significado de "Semana 46" antes de fechar a divisão.

| Integrante | Pendências | Entregável até a Semana 46 |
|---|---|---|
| **Iago Souza** | PEN-01 (a, b, c) e PEN-02 | Dev-token fora do comando padrão; decisão e implementação do vínculo do Responsável; login mínimo ou decisão documentada; teste de concorrência manutenção × reserva; verificação do Quality Gate (PEN-04) |
| **Maria Rita Ribeiro Cruz** | PEN-05 (RF-02 e RF-03) | Unificar recursos no PostgreSQL; `GET /recursos` com filtros; `PUT`/`DELETE`; testes HTTP |
| **Thais Rodrigues** | PEN-07 (RF-09 e RF-12) | Persistência de movimentações; rotas de retirada e devolução; relatórios HTTP com filtros; desempenho medido na aplicação |
| **ThalesConstruct** | PEN-06 e PEN-08 (RF-13, RF-11, RF-17) | Interface mínima (pesquisa, reserva, aprovação, erro legível); notificação simulada em log; `GET /reservas/pendentes` |
| **Revisão cruzada** (todos) | PEN-09 (feito), PEN-10, PEN-11 | README e RTM reescritos a partir deste documento; renomear testes ambíguos e trocar stubs de RN-09 |

**Critério de aceite para cada pendência:** teste HTTP ou BDD que falharia se a regra quebrasse, com a mesma disciplina de mutação usada nesta auditoria. Após cada entrega, rodar `./mvnw -B verify` e registrar o resultado no RTM.

---

## 7. Mutações executadas

Cada mutação foi aplicada em uma cópia do projeto fora do repositório, e a suíte completa (`./mvnw -B test`) foi executada. A linha abaixo indica o que a mutação removeu e o que a suíte acusou.

| Mutação | Regra afetada | Resultado |
|---|---|---|
| M1 — `case APROVADA -> true` em `ServicoReservas.exigirAutorizacaoTransicao` | RN-07, RF-07, RF-15 | 10 falhas em 372. Ex.: `RN07AlcadaAprovacaoTest.rn07_2`, `AutoaprovacaoReservaTest.usuarioComDoisPerfisNaoAprovaPropriaReserva`, cenário BDD "Solicitante não consegue aprovar recurso restrito" |
| M2 — checagem de dono removida em `ServicoReservas.alterar` | RF-16 | 2 falhas. `RF16PropriedadeReservaTest.rf16_2_alterarReservaAlheiaEhNegado`, `RF15ControleAcessoPerfilTest$Solicitante.rf15_s5` |
| M3 — `if (false)` na verificação de assinatura em `ServicoAutenticacao.autenticar` | RF-01, RF-19 | 3 falhas. `RF01AutenticacaoJwtTest.rf01_5_assinaturaAdulteradaEhRejeitada`, `CoberturaRiscoAutenticacaoTest.assinaturaNaoCanonicaRejeita401`, `assinaturaComCaracteresInvalidosRejeita401` |
| M4 — exclusion constraint removida e trava de recurso neutralizada | RF-06, RN-03 | 4 falhas. `ConcorrenciaReservaHttpPostgresTest.vinteRequisicoesSimultaneasGeramUmaReserva[1..2]` (`criadas (201)` esperado 1, obtido 19 e 16), `ConcorrenciaReservaPostgresTest.vinteSolicitacoesSimultaneasGeramUmaReserva[1..2]` (`aceitas` esperado 1, obtido 20) |
| M5 — `SOLICITADA → EM_USO` liberado na matriz de transições | RN-05 | 4 falhas. `RN05FluxoPrincipalTest.rn05_4_solicitadaParaEmUsoEhProibida`, cenários BDD "Início de uso sem aprovação é bloqueado", "Exemplos #1.1" e "Transição recusada não gera evento de auditoria" |

Cópias de trabalho ficaram em `$CLAUDE_JOB_DIR/tmp` e serão apagadas com o job. O repositório permanece sem alterações (`git status` limpo antes da escrita deste arquivo).

---

## 8. Limitações desta auditoria

- Não foi auditado o painel do SonarCloud (Quality Gate, bugs, vulnerabilidades).
- A aplicação foi testada contra o PostgreSQL do `docker-compose.yml` (porta 5432). O banco do compose já continha dados de execuções anteriores; os testes desta auditoria usaram IDs e datas novos e não removeram nada.
- O teste de concorrência é probabilístico; as mutações mostram que ele detecta a quebra da regra nas execuções realizadas.
- Não foram verificados o caminho de manutenção concorrente (PEN-02) nem a carga (JMeter), que já tem relatório em `docs/performance/`.
- Os RFs de `rfc/requisitos-funcionais.md` com numeração diferente do PRD (RF-15 a RF-20) foram considerados como evidência, mas não pontuados separadamente.
