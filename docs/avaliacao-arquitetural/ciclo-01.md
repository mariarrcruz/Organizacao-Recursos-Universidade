# Avaliação Arquitetural — Ciclo 01 (ATAM)

**Sistema:** Organização de Recursos Universitários
**Método:** ATAM (Bass, Clements, Kazman)
**Branch avaliada:** `ci/github-actions-maven` (HEAD `7206b06`)
**Data:** 2026-11-03
**Avaliador:** agente de arquitetura (Claude), a partir de leitura de fontes; sem alteração de código

---

## 0. Escopo, método e limites

Este é o primeiro ciclo. Ele cobre as etapas de apresentação de drivers, utility tree, análise dos cenários de maior prioridade, pontos de sensibilidade, trade-offs, riscos e não riscos, e comparação entre o que a documentação promete e o que o código faz.

**Fontes lidas**

- Requisitos: `docs/PRD.md`, `rfc/requisitos-funcionais.md`, `rfc/requisitos-nao-funcionais.md`.
- Personas: `docs/personas/{solicitante,responsavel,administrador}.md`.
- Arquitetura: `docs/arquitetura.md` (seções 4 a 15, incluindo a extensão 15A).
- ADRs: `docs/adr/001` a `015` e `README.md`.
- Código: `src/main/java/**` (domínio e serviços, aproximadamente 1.100 linhas).
- Testes: `src/test/java/**` (146 métodos `@Test`).
- Build e CI: `pom.xml`, `.github/workflows/ci.yml`, `README.md`.
- Runs do GitHub Actions consultados pela API pública.

**Não foram usados como evidência:** `docs/ATAM-analise.md` (análise anterior, que não foi reconciliada aqui), `docs/RTM.md` e `docs/testes/plano-tdd.md`. Eles podem ser cruzados no próximo ciclo.

**Verificação executada:** `./mvnw -B -o -q test` (offline, dependências já no cache local) terminou sem falhas. O `target/surefire-reports` existente registra 146 testes, 0 falhas, 0 erros, 0 ignorados, mas são relatórios de uma execução anterior e não foram usados como prova da situação atual.

**Limites**

- A `gh` CLI não está autenticada. Os runs foram lidos pela API pública (`api.github.com`), que não expõe logs de execução, só metadados.
- Não há métricas de desempenho, cobertura ou análise estática no repositório. Todas as medidas abaixo são **metas** dos documentos, não valores observados.
- Premissas de negócio que os documentos marcam como "a confirmar" (AMB-01, AMB-04, AMB-06, referenciadas no código) estão tratadas como decisões em aberto, não como fatos.

---

## 1. Direcionadores de negócio

Os direcionadores vêm do PRD (§4, §6), das RFCs e das personas. Cada um aponta para o que o negócio não pode tolerar.

| ID | Direcionador | Origem | Consequência se falhar |
|---|---|---|---|
| DN-01 | Nenhuma dupla alocação de sala, professor ou material | RN-02, RN-03, RNF-02, RISK-01 | Critério eliminatório do projeto ("permissão de dupla reserva") |
| DN-02 | Autorização por perfil e por propriedade | RF-15, RF-16, RNF-01, RISK-01 | Critério eliminatório ("falha de autorização") |
| DN-03 | Aplicação executa e tem pipeline de CI a cada PR | RISK-01, RNF-09 | Critério eliminatório ("aplicação que não executa", "pipeline ausente") |
| DN-04 | Rastreabilidade de toda mudança de estado | RN-09, RF-11, RNF-12, RNF-15 | Impossibilidade de diagnosticar conflitos e decisões |
| DN-05 | Aprovação obrigatória para recursos restritos | RF-08, RN-07, arquitetura §8.3 | Uso indevido de recurso sensível |
| DN-06 | Bloqueio de recurso em manutenção | RN-04, RF-09 | Reserva sobre recurso indisponível |
| DN-07 | Tempo de resposta: consulta e ação até 1 s (alvo 500 ms); relatório até 5 s | RNF-03, RNF-04, RNF-05 | Degradação percebida pelo usuário e falha de aceite de desempenho |
| DN-08 | Persistência e disponibilidade dos dados (H2 local) | PRD §1.1, ADR-002, arquitetura §5 | Perda de reservas e de auditoria a cada reinício |
| DN-09 | Conformidade com LGPD (direito ao esquecimento) | ADR-015 (aprovado com bloqueador H8) | Risco legal |
| DN-10 | Evidências verificáveis de qualidade (cobertura 80/70, TDD em ao menos uma funcionalidade) | RNF-10, RNF-11, COV-01/02 | Projeto não aprovado pelos critérios acadêmicos |

---

## 2. Cenários de atributo de qualidade (seis partes)

Cada cenário tem fonte, estímulo, artefato, ambiente, resposta e medida. Quando a medida depende de algo que o documento não fixa, a linha indica isso. "Premissa" significa valor proposto pela arquitetura (§11.2–11.3), não requisito.

### S-01 — Dupla reserva sob concorrência (DN-01)

| Parte | Conteúdo |
|---|---|
| **Fonte** | Dois ou mais Solicitantes autenticados (usuários reais ou automação). |
| **Estímulo** | Requisições simultâneas de `solicitar` para o mesmo recurso e intervalo sobreposto (de 2 a 10 chamadas). |
| **Artefato** | `ServicoReservas.solicitar` e conflito em `ServicoReservas.conflita`. |
| **Ambiente** | Operação normal; instância única; armazenamento em memória (estado atual). |
| **Resposta** | Exatamente uma reserva é aceita; as demais recebem erro de conflito (409). Estado final sem sobreposição. |
| **Medida** | Reservas sobrepostas confirmadas = 0 (RNF-02). Rejeição determinística (409). Tempo de resposta ≤ 1 s (RNF-04). |

### S-02 — Sobreposição e regra temporal de borda (DN-01)

| Parte | Conteúdo |
|---|---|
| **Fonte** | Solicitante. |
| **Estímulo** | Reserva que termina exatamente no início de outra, reserva que contém outra, reserva com fim anterior ou igual ao início, reserva menor que 1 minuto. |
| **Artefato** | `ServicoReservas.validarPeriodo`, `sobrepoe`, `conflita`. |
| **Ambiente** | Operação normal. |
| **Resposta** | Intervalo semiaberto `[início, fim)`: encostar é permitido; qualquer interseção é 409; entrada inválida é 400. |
| **Medida** | 100% dos casos de borda do conjunto acordado passam (RF-20). Convenção de intervalo documentada em testes (arquitetura §7.2). |

### S-03 — Bloqueio por manutenção (DN-06)

| Parte | Conteúdo |
|---|---|
| **Fonte** | Administrador (cadastro) e Solicitante (reserva). |
| **Estímulo** | Administrador cadastra manutenção para o recurso; Solicitante tenta reservar dentro do período. |
| **Artefato** | `ServicoReservas.criarManutencao` (linhas 178–184) e `conflita` (252–258). |
| **Ambiente** | Operação normal. |
| **Resposta** | Reserva rejeitada (409). Reservas existentes que colidem com a manutenção: comportamento a definir (ver R-08). |
| **Medida** | 0 reservas aceitas em período de manutenção (RN-04). |

### S-04 — Autorização por perfil e propriedade (DN-02)

| Parte | Conteúdo |
|---|---|
| **Fonte** | Solicitante A, Responsável, Administrador, usuário anônimo. |
| **Estímulo** | Solicitante A altera, cancela ou aprova reserva de B; anônimo chama operação protegida; Administrador tenta aprovar. |
| **Artefato** | `ServicoReservas.transicionar` (128–149), `exigirAutorizacaoTransicao` (164–174), `Autorizacao` (273–275). |
| **Ambiente** | Operação normal, identidade já derivada do token. |
| **Resposta** | 401 para anônimo; 403 para perfil ou propriedade insuficiente. |
| **Medida** | 0 operações indevidas aceitas (RNF-01). Aprovação somente por Responsável **que seja responsável pela solicitação** (arquitetura §6.6). |

### S-05 — Autenticação por token (DN-02)

| Parte | Conteúdo |
|---|---|
| **Fonte** | Cliente com token (válido, expirado, adulterado, emitido para outra audiência). |
| **Estímulo** | Envio de `Authorization: Bearer ...` nas operações protegidas. |
| **Artefato** | `ServicoAutenticacao.autenticar` (87–129), `assinaturaValida` (170–185). |
| **Ambiente** | Chave RSA gerada na inicialização do processo. |
| **Resposta** | Rejeição com 401 antes de qualquer operação. |
| **Medida** | 100% dos tokens inválidos rejeitados (RNF-01). Vida do access token = 1 h; refresh = 7 dias (ADR-003). |

### S-06 — Recurso restrito exige aprovação (DN-05)

| Parte | Conteúdo |
|---|---|
| **Fonte** | Solicitante; Responsável. |
| **Estímulo** | Solicitação para recurso com `restrito = true`; tentativa de levar a reserva a uso sem aprovação; solicitação para recurso livre. |
| **Artefato** | `ServicoReservas.solicitar` (51–65), `transicaoPermitida` (151–162), `Recurso.restrito` (`dominio/Recurso.java:11`). |
| **Ambiente** | Operação normal. |
| **Resposta** | Restrito: sem aprovação de Responsável, não entra em uso. Livre: fluxo sem aprovação obrigatória, se essa for a regra (ver R-02). |
| **Medida** | 0 reservas restritas em `EM_USO` sem aprovação registrada (RF-08). Aprovação registrada com usuário, data, decisão e justificativa (arquitetura §8.3). |

### S-07 — Auditoria completa e imutável (DN-04)

| Parte | Conteúdo |
|---|---|
| **Fonte** | Responsável, Administrador, auditor. |
| **Estímulo** | Qualquer transição de estado, criação ou alteração de reserva, cadastro de manutenção, alteração de recurso, tentativa de editar ou excluir evento. |
| **Artefato** | `ServicoReservas.registrarEvento` (304–307), `EventoAuditoria`, `alterarEvento` e `excluirEvento` (207–214). |
| **Ambiente** | Operação normal. |
| **Resposta** | Evento gravado na mesma operação; edição e exclusão recusadas (405). |
| **Medida** | 100% das mudanças de estado com evento (RN-09). Campos: data em UTC, responsável, ação, estado anterior e posterior, justificativa, correlação (arquitetura §9.1). |

### S-08 — Persistência e recuperação após reinício (DN-08)

| Parte | Conteúdo |
|---|---|
| **Fonte** | Operação (reinício planejado ou falha do processo). |
| **Estímulo** | Reinício do processo com reservas, auditoria, refresh tokens e recursos cadastrados. |
| **Artefato** | Listas em memória em `ServicoReservas` (33–35), `ServicoRecursos` (19), `ServicoMovimentacoes` (17), `ServicoAutenticacao` (51), chave em `ServicoAutenticacao` (56–60). |
| **Ambiente** | Dados existentes; H2 previsto (ADR-002). |
| **Resposta** | Dados disponíveis após o reinício; tokens de refresh continuam válidos, ou a invalidação é explicitamente documentada. |
| **Medida** | Perda de dados = 0 (medida não definida nos documentos; propor RPO no próximo ciclo). |

### S-09 — Consulta de disponibilidade e pesquisa (DN-07)

| Parte | Conteúdo |
|---|---|
| **Fonte** | Solicitantes. |
| **Estímulo** | Pesquisa por tipo, localização, capacidade, competência e período, com 20 usuários virtuais e 10 req/s. |
| **Artefato** | `ServicoRecursos.pesquisar` (68–103), `ServicoReservas.temConflito` (240), `conflita` (252–264). |
| **Ambiente** | Dados de referência: 100 usuários, 50 salas, 200 materiais, 10.000 reservas históricas, 20.000 auditorias (premissa, arquitetura §11.2). |
| **Resposta** | Resultado correto em até 1 s. |
| **Medida** | p95 ≤ 1 s (premissa de aceite, arquitetura §11.3). **Não medido**. |

### S-10 — Relatórios gerenciais (DN-07, DN-04)

| Parte | Conteúdo |
|---|---|
| **Fonte** | Responsável, Administrador. |
| **Estímulo** | Relatório de utilização por recurso e de carga horária por período, no mesmo volume de S-09. |
| **Artefato** | `ServicoRelatorios` (33–58, 60–72), `ServicoMovimentacoes.historicoPeriodo` (75–84). |
| **Ambiente** | Dados de referência. |
| **Resposta** | Dados corretos e consistentes; autorização aplicada ao conjunto. |
| **Medida** | p95 ≤ 5 s (RNF-05). **Não medido**. |

### S-11 — Pipeline de integração contínua (DN-03, DN-10)

| Parte | Conteúdo |
|---|---|
| **Fonte** | Desenvolvedor abre pull request. |
| **Estímulo** | Push ou PR em `main`/`ci/**`. |
| **Artefato** | `.github/workflows/ci.yml`, `pom.xml`. |
| **Ambiente** | GitHub Actions, JDK 21 (CI) e `release 17` (pom). |
| **Resposta** | Compilação e testes executados; falha bloqueia a revisão. |
| **Medida** | Runs verdes e vermelhos registrados (ver §6). Cobertura JaCoCo 80%/70% e análise SonarCloud previstas (RNF-09, arquitetura §12.4): **não existem no pom nem no pipeline**. |

### S-12 — Entrada inválida e erros seguros (RNF-07)

| Parte | Conteúdo |
|---|---|
| **Fonte** | Cliente malicioso ou com erro. |
| **Estímulo** | Campos nulos, vazios, fora do domínio; capacidade não numérica; tipo de recurso inexistente. |
| **Artefato** | `ServicoRecursos.pesquisar` (72–89), `parseTipo` (120–126), `ErroAplicacao` (`dominio/ErroAplicacao.java`). |
| **Ambiente** | Sem camada HTTP. |
| **Resposta** | Rejeição com código e mensagem sem detalhes internos. |
| **Medida** | 100% das entradas inválidas rejeitadas (RNF-07). Mensagens sem stack trace (arquitetura §13.1). |

---

## 3. Utility tree

Prioridade: **Importância** (do ponto de vista do negócio) e **Dificuldade** (de atingir ou de verificar, dada a arquitetura atual). Escala A = alta, M = média, B = baixa. A prioridade de tratamento combina os dois, com destaque para importância A e dificuldade que já é conhecida pelo código.

```
Utilidade
├── Integridade de dados
│   ├── Sem dupla alocação sob concorrência .............. S-01  (A, M)
│   ├── Regras temporais de borda ........................ S-02  (A, B)
│   └── Bloqueio por manutenção .......................... S-03  (A, B)
├── Segurança
│   ├── Autorização por perfil e propriedade ............. S-04  (A, B)
│   ├── Autenticação por token ........................... S-05  (A, M)
│   ├── Restrição e aprovação de recurso sensível ........ S-06  (A, M)
│   └── Privacidade e LGPD (esquecimento) ................ DN-09  (A, A)
├── Rastreabilidade
│   └── Auditoria completa e imutável .................... S-07  (A, M)
├── Disponibilidade e durabilidade
│   └── Persistência após reinício ....................... S-08  (A, A)
├── Desempenho
│   ├── Consulta de disponibilidade e pesquisa ........... S-09  (A, M)
│   └── Relatórios sob volume ............................ S-10  (M, M)
├── Operabilidade e qualidade de entrega
│   ├── Pipeline de CI com gates ......................... S-11  (A, B)
│   ├── Entrada inválida e erros seguros ................. S-12  (M, B)
│   └── Observabilidade (logs e notificações) ............ RNF-15/16  (M, B)
└── Funcionalidade
    └── Relatórios gerenciais completos (conflitos evitados, CSV) (M, M)
```

**Ordem de tratamento proposta para o ciclo 02:** S-01, S-04, S-06, S-07, S-08, S-05, S-09, S-11, S-03, S-02, S-12, S-10. Os cenários com importância A e dificuldade A (S-08, DN-09) não são analisados em profundidade aqui porque a arquitetura ainda não tem a camada que os resolveria; eles aparecem como riscos.

---

## 4. Análise dos cenários de maior prioridade

Para cada cenário: abordagens e táticas encontradas no código, com evidência em `arquivo:linha`, e avaliação (atende, parcial ou não atende).

### 4.1. S-01 — Dupla reserva sob concorrência

| Tática | Evidência | Avaliação |
|---|---|---|
| Introduzir concorrência controlada (exclusão mútua) | `src/main/java/com/organizacao/recursos/servico/ServicoReservas.java:51` (`synchronized`), monitor comentado em `:21–23` | Atende para a instância única. Serializa **todos** os recursos num único monitor. |
| Verificar antes de gravar (check-then-act na mesma seção crítica) | `ServicoReservas.java:60` chama `verificarDisponibilidade`, `:61–62` grava | Atende, porque check e gravação estão no mesmo monitor. |
| Regra de intervalo semiaberto | `ServicoReservas.java:267–269` (`sobrepoe`) | Atende e é consistente com `arquitetura.md` §7.2. |
| Conflito considera manutenção | `ServicoReservas.java:252–258` | Atende. |
| Teste de concorrência real com threads | `src/test/java/.../RN03AtomicidadeConcorrenciaTest.java` (RN-03.1–.5); `Fixtures.java:59–70` (`simultaneas`, pool e `CountDownLatch`) | Atende para o caso em memória. |
| Tática de transação e bloqueio por recurso (ADR-004) | Ausente: não há banco nem transação | **Não atende** ao desenho previsto em ADR-004/AD-04. O monitor global é uma divergência declarada no comentário de `ServicoReservas.java:22–23`. |

**Conclusão:** para a instância única em memória, S-01 é satisfeito e tem teste. A garantia não se sustenta com dois processos, com reinício ou com persistência em banco.

### 4.2. S-04 — Autorização por perfil e propriedade

| Tática | Evidência | Avaliação |
|---|---|---|
| Autenticação obrigatória antes de qualquer ação | `Autorizacao.java:13–17` (`exigirAutenticado`, 401), chamada em `ServicoReservas.java:52,68,86,129,198` | Atende. |
| Checagem central de perfil | `Autorizacao.java:19–23` (`exigirPerfil`, 403), `Autorizacao.java:33–35` (`tem`) | Atende. |
| Checagem de propriedade | `ServicoReservas.java:286–288` (`ehDono`), usado em `:70`, `:88`, `:167`, `:168`, `:200` | Atende para alterar, excluir e cancelar. |
| Matriz de transição por perfil | `ServicoReservas.java:164–174` | Parcial (ver abaixo). |

**Divergências de autorização:**

- `ServicoReservas.java:166`: `APROVADA`, `REJEITADA` e `NAO_COMPARECEU` exigem apenas perfil RESPONSAVEL. Qualquer Responsável aprova qualquer reserva. A arquitetura (§6.6) diz "Permitido quando responsável pela solicitação", e o §14 exige que perfil e propriedade/responsabilidade sejam verificados separadamente. **Não atende**.
- Não há verificação de escopo de responsabilidade para retirada e devolução (`ServicoMovimentacoes.java:23–24`, `:38–39`). Qualquer Responsável registra movimento em qualquer reserva. **Não atende** à matriz §6.6, que fala em "escopo de responsabilidade".
- Os métodos públicos `registrarExistente` (`ServicoReservas.java:218–219`), `registrarManutencaoExistente` (`:221`) e `registrarRecursoExistente` (`ServicoRecursos.java:26`) inserem dados **sem nenhuma autorização**. Hoje são "hooks de teste" em código de produção. Qualquer chamador de Java do mesmo classpath pode criar reserva ou recurso sem passar pelas regras, sem gerar auditoria. Ver R-05.
- Administrador: a persona diz "sem restrição operacional", o código nega aprovação ao Administrador (`RN07AlcadaAprovacaoTest` RN-07.3; `RF15ControleAcessoPerfilTest` RF-15.A.12). A matriz de §6.6 diz "Não presumido". Consistente com o documento, mas a decisão está aberta no próprio teste ("esclarecer escopo", "negado ou escopo?"). Registrada como decisão pendente, não como defeito.

**Conclusão:** S-04 atende a autenticação e à propriedade do solicitante, mas não atende o escopo de responsabilidade do Responsável, que é a parte de maior risco.

### 4.3. S-06 — Recurso restrito exige aprovação

| Tática | Evidência | Avaliação |
|---|---|---|
| Atributo explícito de restrição | `dominio/Recurso.java:11` (`boolean restrito`) | Atende (estrutura). |
| Verificação do atributo no fluxo da reserva | Nenhuma ocorrência de `restrito()` em `ServicoReservas.java`. O único uso é cópia em `ServicoRecursos.java:50`. | **Não atende**. |
| Aprovação obrigatória para todos os recursos | `ServicoReservas.java:153–154` (`SOLICITADA` só vai a `APROVADA`, `REJEITADA` ou `CANCELADA`), `:159` (`EM_USO` só a partir de `EM_USO`) | Atende RF-08 **de forma mais forte**, mas por um caminho diferente: a aprovação vale para **qualquer** reserva, não só para restritos. |
| Registro da aprovação (usuário, data, decisão, justificativa) | Auditoria registra `atorId` e `instante` (`EventoAuditoria.java:5–12`). A justificativa de aprovação é `null` (`ServicoReservas.java:105`). `Reserva` não guarda aprovador (`Reserva.java:8–13`). | Parcial. |

**Conclusão:** o requisito RF-08 é atendido na direção "restrito não entra em uso sem aprovação", mas o flag é ignorado, então o sistema se comporta como se **todo** recurso fosse restrito. Isso conflita com §8.2 ("aprovação ... quando aplicável"). Ver R-02.

### 4.4. S-07 — Auditoria completa e imutável

| Tática | Evidência | Avaliação |
|---|---|---|
| Registro de evento em toda transição | `ServicoReservas.java:63`, `:81`, `:99`, `:147` | Atende para reservas. |
| Append-only na API | `ServicoReservas.java:207–214` (405 em alterar e excluir) | Atende no nível de serviço. |
| Testes de imutabilidade e de transição inválida | `RN09RastreabilidadeTest` (RN-09.8, .9, .10) | Atende. |
| Campos completos (UTC, entidade, correlação, resultado) | `EventoAuditoria.java:5–12` tem `instante` local (`ServicoReservas.java:306` usa `LocalDateTime.now(relogio)`), sem tipo de entidade, sem correlação e sem resultado | **Não atende** arquitetura §9.1 e RNF-12 ("data, responsável, ação e efeito") no que diz respeito a "efeito" e "UTC". |
| Auditoria de ações administrativas | Não há evento em `criarManutencao` (`ServicoReservas.java:178–184`), `cadastrarRecurso` (`ServicoRecursos.java:28–39`), `atualizarRecurso` (`:45–53`), `excluirRecurso` (`:55–62`), `cadastrarUsuario` (`ServicoUsuarios.java:16–22`), `alterarParametro` (`ServicoAdministracao.java:16–22`) | **Não atende** RNF-12 ("ações administrativas") nem arquitetura §9.1 (lista de eventos). |
| Auditoria transacional com a transição | `transicionar` grava a reserva (`:146`) e depois o evento (`:147`), sem transação nem rollback | **Não atende** AD-05 ("mesma transação"). Teste de rollback (H4) inexistente. |
| Movimentações com trilha própria | `ServicoMovimentacoes.java:17`, `:32–34`, `:45–47`: lista própria, sem vínculo com `EventoAuditoria` | Parcial. |

### 4.5. S-08 — Persistência após reinício

| Tática | Evidência | Avaliação |
|---|---|---|
| Replicação | Não há | Não atende. |
| Backup ou persistência | Não há. Todas as coleções são `ArrayList`/`HashMap` em memória (`ServicoReservas.java:33–35`, `ServicoRecursos.java:19`, `ServicoMovimentacoes.java:17`, `ServicoUsuarios.java:14`, `ServicoAdministracao.java:14`, `ServicoAutenticacao.java:51`). | **Não atende** DN-08 e ADR-002. |
| Chave de assinatura fora do repositório | Gerada na inicialização (`ServicoAutenticacao.java:56–60`); a própria classe declara "chave de desenvolvimento" (`:32–33`). | Atende o "fora do repositório", mas a consequência é que **todos os tokens emitidos deixam de valer a cada reinício**. |
| Refresh token com revogação | Armazenado em memória (`ServicoAutenticacao.java:51`); rotação sim (`:143–153`); não há logout (`grep` sem resultado para `logout`). | Parcial. |

**Conclusão:** S-08 não é atendido. É o maior risco de aderência ao documento de arquitetura e é o que mais muda o restante do desenho quando for implementado.

### 4.6. S-05 — Autenticação por token

| Tática | Evidência | Avaliação |
|---|---|---|
| Verificação de assinatura RS256 | `ServicoAutenticacao.java:159–168` (assinar), `:170–185` (verificar) | Atende. |
| Proteção contra maleabilidade de Base64 | `ServicoAutenticacao.java:176–180` (exige codificação canônica antes de validar) | Atende, e é uma tática não óbvia bem aplicada. |
| Validação de expiração, emissor e audiência | `:105–111` | Atende. |
| Validação do campo `sub` e perfis | `:112–127` (perfil desconhecido → 401) | Atende. |
| Hash de refresh token, não o token em claro | `ServicoAutenticacao.java:135`, `:187–194` | Atende. |
| Rotação e reuso rejeitado | `:143–153`, rejeição em `:148` | Atende. Não revoga a cadeia inteira após reuso. |
| Autenticação por credencial (login) | Ausente. `gerarToken` não tem chamador de produção (`grep` mostra só a definição). Não há senha nem hash (arquitetura §14: BCrypt). | **Não atende** RF-01 e §14. |
| Parsing de JWT | Feito por expressão regular sobre JSON (`:42–47`, `:216–237`) em vez de biblioteca. | Risco de manutenção. Ver R-07. |
| Clock injetável para testar expiração | `ServicoAutenticacao` usa `Instant.now()` diretamente (`:77`, `:106`, `:148`) | Parcial: testes de expiração dependem do relógio real. |

### 4.7. S-09 — Consulta de disponibilidade e pesquisa

| Tática | Evidência | Avaliação |
|---|---|---|
| Índice ou estrutura de busca | Nenhuma. `conflita` percorre todas as reservas (`ServicoReservas.java:259–263`). `pesquisar` chama `temConflito` por recurso (`ServicoRecursos.java:101`). | **Não atende** a expectativa de custo sublinear. Custo O(R × N), com R recursos e N reservas, dentro de um monitor global. |
| Cache | Não há | Não aplicável. |
| Medição | Não há testes de carga, nem JMH, nem JMeter. | **Não medido**. |

### 4.8. S-11 — Pipeline de CI

| Tática | Evidência | Avaliação |
|---|---|---|
| Pipeline em PR e em `main` | `.github/workflows/ci.yml:3–6` (`pull_request`, `push: main`) | Atende. |
| Build e testes | `ci.yml:31–32` (`./mvnw -B verify`) | Atende. |
| Concorrência e timeout | `ci.yml:11–13`, `:19` | Atende. |
| Upload de relatórios | `ci.yml:34–41` (caminho `target/failsafe-reports/` sem plugin failsafe configurado) | Parcial. |
| Cobertura JaCoCo | Ausente no `pom.xml` (só `maven-surefire-plugin`, linha 31–33) | **Não atende** RNF-11 e COV-01/02. |
| Análise estática SonarCloud | Ausente no pipeline. `README.md` (linhas 827–828) reporta "0 bugs" como meta, sem evidência. | **Não atende** RNF-09 e SEC-01. |
| Testes de integração com Testcontainers e WireMock | Ausentes no `pom.xml`. | **Não atende** RNF-08. |

### 4.9. S-03 — Bloqueio por manutenção

| Tática | Evidência | Avaliação |
|---|---|---|
| Entidade de manutenção validada | `ServicoReservas.java:180–182` | Atende. |
| Checagem na solicitação | `ServicoReservas.java:246–250`, `:252–258` | Atende. |
| Status de recurso | `StatusRecurso.MANUTENCAO` existe (`dominio/StatusRecurso.java:1`), mas **nenhum método altera o status** (`ServicoRecursos.java:45–53` só muda o nome). Só `solicitar` verifica `status != DISPONIVEL` (`ServicoReservas.java:57`), com o objeto passado pelo chamador. | Não atende de forma confiável. Ver R-03. |
| Efeito sobre reservas existentes | `criarManutencao` não verifica reservas ativas no período (`ServicoReservas.java:178–184`). | **Não atende** a definição de "bloqueio" para reservas já existentes. Ver R-08. |

### 4.10. S-02 — Regras temporais de borda

| Tática | Evidência | Avaliação |
|---|---|---|
| Validação de ordem e duração mínima | `ServicoReservas.java:271–284` (`DURACAO_MINIMA` de 1 min, `:39`) | Atende RN-01 e a decisão provisória AMB-04. |
| Relógio injetável | `ServicoReservas.java:41–47`, `:136`, `:306` | Atende no serviço de reservas. |
| Reserva no passado | `solicitar` não rejeita `inicio` anterior a agora (`ServicoReservas.java:51–65`). Requisito não explícito. | Lacuna a confirmar (ver R-11). |
| Cancelamento só antes do início | `ServicoReservas.java:135–138` (só para `APROVADA`) | Atende a §8.2. |

### 4.11. S-12 — Entrada inválida e erros seguros

| Tática | Evidência | Avaliação |
|---|---|---|
| Validação de entrada nula e vazia | `ServicoRecursos.java:114–118` (`validarNome`), `ServicoReservas.java:272–277` | Atende. |
| Tipos de domínio em vez de string livre | `TipoRecurso`, `StatusRecurso`, `Perfil`, `EstadoReserva` (enums em `dominio/`) | Atende. |
| Erro com código HTTP semântico | `dominio/ErroAplicacao.java:4–13` | Atende o contrato de código, sem camada HTTP ainda. |
| Tratamento centralizado de exceção | Ausente (não há controlador) | Não aplicável agora. |
| Revalidação na camada de domínio | `ServicoRecursos.pesquisar` converte `capacidade_min` e `inicio`/`fim` (`:75–89`) | Atende. |

---

## 5. Pontos de sensibilidade

Ponto de sensibilidade é uma decisão que afeta vários atributos de qualidade ao mesmo tempo.

| ID | Local | Atributos afetados | Por que é sensível |
|---|---|---|---|
| PS-01 | `ServicoReservas.java:21–23`, `:51`, e demais `synchronized` | Integridade (S-01), desempenho (S-09), disponibilidade | Um único monitor garante a exclusão mútua, mas bloqueia todo o sistema. Qualquer mudança aqui afeta concorrência e throughput. |
| PS-02 | `ServicoReservas.java:252–269` (`conflita`, `sobrepoe`) | Integridade (S-01, S-02), desempenho | A regra de conflito é o núcleo do sistema. Erro de borda, como `<` vs `<=`, faz o sistema aceitar ou recusar errado. Está coberta por teste. |
| PS-03 | `ServicoReservas.java:128–174` (`transicionar`, `transicaoPermitida`, `exigirAutorizacaoTransicao`) | Autorização (S-04), ciclo de vida (RN-05) | Matriz de estado e de perfil no mesmo método. É onde se decide quem aprova. |
| PS-04 | `Autorizacao.java:33–35` (`tem`) | Autorização (S-04) | Todas as decisões de perfil passam por aqui. Um `null` em `perfis()` vira negação, o que é seguro. |
| PS-05 | `ServicoAutenticacao.java:98–128` (parsing) e `:170–185` (verificação) | Autenticação (S-05) | Parsing por regex e verificação de assinatura. Mudança em um lado pode quebrar o outro silenciosamente. |
| PS-06 | `ServicoReservas.java:45–47` (`Clock`) e `Movimentacoes:33,46` (`LocalDateTime.now()`) | Testabilidade, rastreabilidade (S-07) | O relógio é injetado num serviço e ignorado no outro. Testes de tempo ficam dependentes do horário real. |
| PS-07 | `Recurso` como parâmetro de `solicitar` (`ServicoReservas.java:51`) | Integridade (S-01, S-06, S-03) | O serviço confia no objeto recebido, e não busca o recurso no registro. Ver R-03. |
| PS-08 | `ServicoRecursos.java:55–62` (`excluirRecurso`) | Integridade | Verificação de reservas ativas e remoção em sequência, sem trava compartilhada com `ServicoReservas`. Ver R-04. |

---

## 6. Pontos de trade-off

| ID | Trade-off | Escolha atual | Ganho | Custo | Referência |
|---|---|---|---|---|---|
| TO-01 | Simplicidade vs desempenho de concorrência | Monitor global em `ServicoReservas` | Corretude sem banco; facilidade de teste | Serialização total; sem escalabilidade horizontal | ADR-004 prevê lock por recurso |
| TO-02 | Velocidade de desenvolvimento vs durabilidade | Armazenamento em memória | Testes rápidos e sem infraestrutura | Perda de dados a cada reinício (S-08) | ADR-002 |
| TO-03 | Controle de acesso forte vs fricção | Aprovação para todas as reservas | Ninguém usa recurso sem aprovação | Fluxo lento para recursos livres; diverge de §8.2 | RF-08, arquitetura §8.2 |
| TO-04 | Flexibilidade de API vs integridade | `solicitar` aceita `Recurso` do chamador | Chamador monta cenários de teste sem registro | Possível bypass de `restrito` e de `status` (R-03) | — |
| TO-05 | Rapidez de validação vs confiança na chave | Chave RSA gerada em memória | Sem segredo no repositório | Tokens invalidados a cada reinício; sem rotação de chave | ADR-003, arquitetura §6.3 |
| TO-06 | Evidência de teste vs qualidade de métrica | Testes de concorrência com threads reais | Evidência real, não mockada | Tempo de execução e não determinismo | RNF-08 (mock só em unitário) |
| TO-07 | Escopo do primeiro ciclo | Camada de serviço Java pura, sem Spring | Entrega regras e testes cedo | Não há API, UI, segurança HTTP nem persistência | README.md:147 |

---

## 7. Riscos

Riscos são decisões ou situações que podem produzir resultado indesejado em um atributo de qualidade. Severidade: Alta (A), Média (M), Baixa (B). Probabilidade é estimada a partir do código, não de medição.

| ID | Risco | Atributo | Severidade | Evidência | Temas |
|---|---|---|---|---|---|
| R-01 | **Perda total de dados** a cada reinício: reservas, auditoria, recursos, refresh tokens e chave. | Disponibilidade, durabilidade | A | `ServicoReservas.java:33–35`; `ServicoAutenticacao.java:51`, `:56–60` | T3 |
| R-02 | **Regra de aprovação incorreta:** o flag `restrito` é ignorado. Todas as reservas exigem aprovação, e qualquer Responsável aprova qualquer reserva. | Autorização, funcionalidade | A | `ServicoReservas.java:153–154`, `:166`; `Recurso.java:11` | T2 |
| R-03 | **Bypass por objeto forjado:** `solicitar` aceita um `Recurso` construído pelo chamador. Um objeto com `status = DISPONIVEL` e `restrito = false` é aceito mesmo que o registro diga o contrário. Hoje o risco é latente, porque não há camada HTTP. | Integridade (S-01, S-03, S-06) | A | `ServicoReservas.java:51`, `:57`, `:61`; `ServicoRecursos.java:45–53` (nenhum método altera status) | T1, T2 |
| R-04 | **Condição de corrida em `excluirRecurso`:** a verificação de reservas ativas (`ServicoRecursos.java:58`) e a remoção (`:61`) não são atômicas em relação a `solicitar` em `ServicoReservas`. Uma reserva pode ser criada entre a checagem e a remoção, gerando reserva órfã. **A confirmar com teste.** | Integridade | M | `ServicoRecursos.java:55–62` | T1 |
| R-05 | **Hooks de teste públicos em produção** sem autorização (`registrarExistente`, `registrarManutencaoExistente`, `registrarRecursoExistente`). Permitem inserir reservas e recursos sem regras e sem auditoria. | Autorização, rastreabilidade | A | `ServicoReservas.java:218–221`; `ServicoRecursos.java:26` | T2, T4 |
| R-06 | **Auditoria não atômica** com a transição de estado (sem transação ou compensação). Falha entre as duas escritas gera estado sem evento. Probabilidade baixa em memória, mas o teste H4 não existe. | Rastreabilidade | M | `ServicoReservas.java:146–147` | T4 |
| R-07 | **Parsing de JWT por regex** em vez de biblioteca testada. Não há falha explícita conhecida (a assinatura é verificada antes), mas aumenta a superfície de erro em manutenção. | Segurança | M | `ServicoAutenticacao.java:42–47`, `:216–237` | T2 |
| R-08 | **Manutenção sobre reservas existentes:** `criarManutencao` aceita período que colide com reservas `APROVADA` ou `EM_USO` sem tratá-las. | Integridade (RN-04) | M | `ServicoReservas.java:178–184` | T1 |
| R-09 | **Desempenho sem medição e com custo linear** dentro de um monitor global. Com o volume de referência (10.000 reservas), cada `solicitar` e cada `pesquisar` percorre todas as reservas, com o sistema travado. | Desempenho | A | `ServicoReservas.java:252–264`; `ServicoRecursos.java:101` | T5 |
| R-10 | **Relógios inconsistentes:** `ServicoMovimentacoes` e `ServicoAutenticacao` usam relógio do sistema. Testes de tempo são frágeis e a auditoria mistura fusos. | Testabilidade, rastreabilidade | M | `ServicoMovimentacoes.java:33`, `:46`; `ServicoAutenticacao.java:77`, `:106`, `:148` | T4 |
| R-11 | **Reservas no passado aceitas:** `solicitar` não impede `inicio` anterior ao instante atual. Pode poluir relatórios. Requisito não explícito, deve ser confirmado. | Integridade de dados | B | `ServicoReservas.java:51–65` | T3 |
| R-12 | **Login inexistente:** não há como autenticar com credencial. Quem emite tokens é código interno. Impede S-05 em produção. | Autenticação | A | `ServicoAutenticacao.java:67` (sem chamador de produção) | T2 |
| R-13 | **Logs e notificações inexistentes:** sem trilha operacional fora da auditoria em memória. Falhas críticas não são registradas. | Observabilidade (RNF-15/16) | M | Ausência de logger em `src/main` (`grep` vazio) | T4 |
| R-14 | **Gates de qualidade ausentes:** sem JaCoCo, SonarCloud ou failsafe. Pode-se publicar código abaixo das metas sem falhar o pipeline. | Qualidade (DN-10) | M | `pom.xml` (sem plugin de cobertura); `ci.yml:31–32` | T6 |
| R-15 | **Testes que documentam decisões em aberto** no lugar de exigências, como `"esclarecer"`, `"confirmar"` e `"negado ou escopo?"`. A suíte passa, mas não prova a regra final. | Corretude da suíte (RNF-10) | M | `RN07AlcadaAprovacaoTest.java:45`, `:55`; `RF15ControleAcessoPerfilTest.java:201`, `:328`; `RN08ImutabilidadeExecucaoTest.java:50` | T6 |

---

## 8. Não riscos

Decisões que foram bem tratadas e que não exigem ação no próximo ciclo além de manutenção.

| ID | Não risco | Evidência |
|---|---|---|
| NR-01 | Exclusão mútua na instância única, com teste de concorrência real (1 aceita, demais 409). | `ServicoReservas.java:51`; `RN03AtomicidadeConcorrenciaTest.java` (RN-03.1 a .5); `Fixtures.java:59–70` |
| NR-02 | Regra de intervalo semiaberto implementada de forma simétrica e testada. | `ServicoReservas.java:267–269` |
| NR-03 | Verificação de assinatura RS256 com proteção contra maleabilidade de Base64. | `ServicoAutenticacao.java:170–185` |
| NR-04 | Validação de emissor, audiência, expiração e presença de `sub` e perfis. | `ServicoAutenticacao.java:105–127` |
| NR-05 | Refresh token armazenado como hash SHA-256 e rotacionado; reuso é recusado. | `ServicoAutenticacao.java:135`, `:143–153` |
| NR-06 | Auditoria append-only recusa edição e exclusão no serviço. | `ServicoReservas.java:207–214`; `RN09RastreabilidadeTest` (RN-09.8, .9) |
| NR-07 | Proteção de propriedade para alterar, excluir e cancelar reserva. | `ServicoReservas.java:70`, `:88`, `:167` |
| NR-08 | Validação de período e duração mínima. | `ServicoReservas.java:271–284` |
| NR-09 | Cancelamento após o início é recusado para reservas aprovadas. | `ServicoReservas.java:135–138` |
| NR-10 | Exclusão de reserva em uso é recusada (RN-08). | `ServicoReservas.java:91–93` |
| NR-11 | Suíte com 146 testes; execução local sem falhas; pipeline verde em `main` e em PR (§9). | Ver §9 e a verificação na §0 |
| NR-12 | Pipeline de CI existente, com timeout e concorrência por ref. | `ci.yml:11–19` |

---

## 9. Divergências entre `docs/arquitetura.md` (e README) e o código

Para cada item: o que o documento promete, o que o código faz, e a classificação. Classificação: **Não implementado** (o documento descreve, o código não tem); **Parcial**; **Divergente** (código faz outra coisa); **Declarado** (a divergência está registrada no próprio código ou no README).

| # | Documento | Promessa | Código | Classificação |
|---|---|---|---|---|
| D-01 | arquitetura §4.1, §4.2, §4.4 | Monólito Spring Boot com camadas de API, segurança, aplicação, persistência e UI | Java puro, sem Spring. `pom.xml` tem só `junit-jupiter` (linhas 20–23). | Não implementado. **Declarado** em `README.md:147` |
| D-02 | arquitetura §4.2, ADR-002, PRD §1.1 | Persistência em H2 | Listas em memória (`ServicoReservas.java:33–35`) | Não implementado |
| D-03 | arquitetura §6.2, §6.5 | Endpoints `/auth/login`, `/auth/refresh`, `/auth/logout`, `/auth/me` | Só `renovar` (`ServicoAutenticacao.java:143`). Sem login, logout nem `/me`. | Parcial |
| D-04 | arquitetura §14 | Senhas com BCrypt; nenhuma senha em texto puro | Não há senha. `ServicoUsuarios` não guarda credencial (`:14`). | Não implementado |
| D-05 | arquitetura §6.5 | Logout revoga a sessão de renovação | Não há método de logout. | Não implementado |
| D-06 | arquitetura §6.6 | Aprovar "quando responsável pela solicitação" | Qualquer Responsável (`ServicoReservas.java:166`) | Divergente |
| D-07 | arquitetura §6.6 | Registrar retirada e devolução "no escopo de responsabilidade" | Qualquer Responsável (`ServicoMovimentacoes.java:23–24`) | Divergente |
| D-08 | arquitetura §8.2 | Aprovação "quando aplicável" | Aprovação sempre exigida (`ServicoReservas.java:153–154`) | Divergente |
| D-09 | arquitetura §8.3, RF-08 | Atributo de restrição consultado no fluxo | `restrito` não é lido em `ServicoReservas` | Divergente |
| D-10 | arquitetura §8.3 | Aprovação registrada com usuário, data, decisão e justificativa | Só auditoria genérica; sem campo de aprovador em `Reserva` (`Reserva.java:8–13`) | Parcial |
| D-11 | arquitetura §7.1, ADR-004 | Lock transacional por recurso, com ordenação determinística | Monitor global (`ServicoReservas.java:21–23`) | Declarado (no código) |
| D-12 | arquitetura §7.4 | Manutenção e bloqueio como indisponibilidade temporal | Existe por lista de intervalos, mas `StatusRecurso.MANUTENCAO` não é atribuído por nenhum método | Parcial |
| D-13 | arquitetura §9.1, ADR-005 | Auditoria persistida em H2, na mesma transação | Lista em memória, sem transação | Não implementado |
| D-14 | arquitetura §9.1 | Campos: UTC, tipo de entidade, correlação, resultado | `EventoAuditoria.java:5–12`; `ServicoReservas.java:306` usa relógio local | Parcial |
| D-15 | arquitetura §9.1, RNF-12 | Auditoria de ações administrativas | Não há (ver S-07) | Não implementado |
| D-16 | arquitetura §9.2, ADR-006, RF-11, RNF-16 | Notificações simuladas por logs | Não há logger nem notificação em `src/main` | Não implementado |
| D-17 | arquitetura §13.2, RNF-15 | Logs estruturados com correlação | Não há logs | Não implementado |
| D-18 | arquitetura §10.1 | Relatórios: conflitos evitados, carga horária por professor e material, manutenção e bloqueios | Existem utilização por recurso (`ServicoRelatorios.java:33`), carga só por sala (`:44`), contagem por estado (`:60`), histórico de movimentações (`:74`). Faltam conflitos evitados, carga por professor e material, indicadores de manutenção. | Parcial |
| D-19 | arquitetura §10.2, RF-12 | Filtros por unidade e por professor; exportação CSV | Sem unidade, sem professor e sem CSV. A própria arquitetura admite a unidade como "indisponível" até existir (§10.2). | Parcial |
| D-20 | arquitetura §12.1 | Testes de integração com Testcontainers e WireMock | Ausentes no `pom.xml` e nos testes | Não implementado |
| D-21 | arquitetura §12.2, RNF-11, COV-01/02 | JaCoCo com 80% de linhas e 70% de ramos | Sem JaCoCo | Não implementado |
| D-22 | arquitetura §12.2, RNF-09, SEC-01 | SonarCloud com Quality Gate | Sem SonarCloud no `ci.yml`. README traz "0 bugs" como meta (`README.md:827–828`). | Não implementado |
| D-23 | arquitetura §11, RNF-03/04/05 | Medição de p95 com JMeter, cenário de 10.000 reservas | Nada medido; sem `docs/` de carga e sem JMeter | Não implementado |
| D-24 | arquitetura §12.4 | Pipeline com cobertura, análise estática e Quality Gate | `ci.yml:31–32` só compila e testa | Parcial |
| D-25 | arquitetura §12.4 | Relatórios de testes publicados (failsafe) | Upload de `target/failsafe-reports/` sem plugin failsafe | Parcial |
| D-26 | arquitetura §15A, ADR-010 a ADR-015 | Pipeline batch diário, watermark, validação 3D, versionamento de transformações, anonimização LGPD | Nenhum arquivo ou classe relacionada (`grep` em `src/` vazio para batch, watermark, anonim, lgpd). ADRs marcadas como "Aprovado" com blockers H6, H7 e H8 sem teste. | Não implementado |
| D-27 | ADR-README, blockers | H1 a H8 validados antes da implementação | H1 (concorrência): teste existe (RN-03). H2 (chave fora do repo): parcial. H3 (propriedade): teste existe (`RF16PropriedadeReservaTest`). H4 (rollback): inexistente. H5 (JMeter): inexistente. H6, H7, H8: inexistentes. | Parcial |
| D-28 | README (linhas 35–43) | "Autenticação com JWT (access 1h + refresh 7 dias)", "Prevenção de dupla reserva com transações ACID", "Auditoria append-only de todas as alterações críticas", "Relatórios com filtros avançados", "Cobertura ≥80%", "Conformidade LGPD (direito ao esquecimento)" | Partes existem em memória (ver linhas acima). Transações ACID, LGPD e cobertura não existem. Cobertura não medida. | Divergente |
| D-29 | README (linhas 147–152) | Declara que a aplicação atual é camada de serviço sem Spring nem HTTP | Coerente com o código | **Declarado** (não é divergência, é o registro correto) |
| D-30 | README (linhas 283–546) | Endpoints `/auth/*`, `/reservations`, `/reports/*`, `/movements/*`, `/availability/*` | Nenhum endpoint existe. O README marca como "planejado" só nos comandos de execução (linha 147 e seguintes). | Divergente (sem marcação de planejado nos endpoints) |
| D-31 | README (linhas 99–114) | Variáveis `SPRING_DATASOURCE_*`, `JWT_SECRET_KEY`, `SERVER_PORT` | Nenhuma configuração lida em código. Não há `application.yml` em `src/main/resources`. | Não implementado |
| D-32 | arquitetura §4.4 | Pacotes por módulo (`identity`, `resources`, `reservations`...), dentro de `organizacaorecursos` | Pacote único `servico`, em `com.organizacao.recursos` | Divergente (organização) |
| D-33 | arquitetura §4.3 | Módulo `notifications` e `audit` separados | Auditoria dentro de `ServicoReservas` (`:304–307`). Sem módulo de notificação. | Divergente |
| D-34 | README (linha 50) | Java 17+ e CI em JDK 21 | `pom.xml:13–15` compila em 17; `ci.yml:24–26` usa 21. Compatível. | Coerente (sem ação) |

---

## 10. Temas de risco transversais

| Tema | Riscos ligados | Direcionadores | Síntese |
|---|---|---|---|
| **T1 — Concorrência e integridade de estado** | R-03, R-04, R-08, PS-01, PS-07, PS-08 | DN-01, DN-06 | A exclusão mútua é correta para uma instância, mas a integridade depende de objetos recebidos do chamador e de checagens fora da seção crítica. |
| **T2 — Autorização e propriedade** | R-02, R-05, R-07, R-12, D-06, D-07 | DN-02, DN-05 | O perfil é verificado de forma consistente. A propriedade e o escopo de responsabilidade não são. Há caminhos que contornam a autorização. |
| **T3 — Persistência e durabilidade** | R-01, R-11, D-02, D-13 | DN-08 | Todo o estado é volátil. Esse é o tema que mais altera o restante do desenho quando for resolvido. |
| **T4 — Observabilidade e rastreabilidade** | R-06, R-10, R-13, D-14, D-15, D-16, D-17 | DN-04 | A auditoria existe só para reservas e em memória. Não há logs, correlação nem relógio único. |
| **T5 — Desempenho sob carga** | R-09 | DN-07 | Sem medição e com custo linear sob um monitor global. Só será visível quando houver volume. |
| **T6 — Evidência de qualidade** | R-14, R-15, D-21, D-22, D-24 | DN-03, DN-10 | Testes passam, mas os gates prometidos não existem e a suíte registra decisões em aberto. |

---

## 11. Decisões em aberto que afetam esta avaliação

Estas não são falhas do código. São perguntas que o próprio código e os documentos deixam sem resposta, e que mudam a classificação de vários riscos.

1. **AMB-01 / aprovação (R-02):** a aprovação é exigida só para recurso restrito ou para toda reserva?
2. **Escopo do Responsável (D-06, D-07):** quem é "responsável pela solicitação" e como é atribuído?
3. **Administrador e aprovação (RF-15.A.12):** o Administrador pode aprovar, ou só gerencia?
4. **AMB-04:** a duração mínima de 1 minuto é definitiva?
5. **AMB-06:** qual é o escopo de relatórios do Solicitante?
6. **Reserva no passado (R-11):** é permitida?
7. **Manutenção sobre reservas existentes (R-08):** cancelar, rejeitar ou alertar?

---

## 12. Ações priorizadas para o ciclo 02

Ordem por severidade do risco e por dependência. Cada ação indica o cenário que ela fecha.

| Prioridade | Ação | Fecha | Esforço estimado | Depende de |
|---|---|---|---|---|
| 1 | Decidir as perguntas da §11 (1, 2, 3) com os stakeholders e registrar o resultado no `docs/adr/` ou em nova ADR. | R-02, D-06, D-08, D-09 | Baixo | Stakeholders |
| 2 | Fazer `solicitar` buscar o `Recurso` no registro (`ServicoRecursos`) em vez de aceitar o objeto do chamador. Escrever teste negativo com objeto forjado. | R-03, S-06, S-03 | Médio | — |
| 3 | Remover os hooks `registrar*Existente` de código de produção. Mover a preparação de cenários para os testes (pacote de teste ou fábrica). | R-05 | Baixo | — |
| 4 | Escrever teste que reproduza a corrida entre `excluirRecurso` e `solicitar` (R-04). Corrigir com trava compartilhada ou validação no registro. | R-04 | Médio | Item 2 |
| 5 | Substituir `LocalDateTime.now()` e `Instant.now()` pelo relógio injetado em todos os serviços. Usar UTC na auditoria. | R-10, D-14 | Baixo | — |
| 6 | Auditar ações administrativas e cadastro de manutenção, com os campos de §9.1. | D-15, S-07 | Médio | Item 5 |
| 7 | Definir a estratégia de persistência (H2 com repositórios, ou formalizar exceção documentada) e escrever o teste de rollback H4. | R-01, R-06, D-02, D-13, S-08 | Alto | Item 6 |
| 8 | Adicionar JaCoCo ao `pom.xml` e uma etapa de cobertura no `ci.yml`, com limiar 80/70, e registrar o estado atual do repositório. Decidir se o SonarCloud entra no pipeline. | R-14, D-21, D-22, D-24 | Baixo | — |
| 9 | Medir S-01 e S-09 com volume de referência (10.000 reservas, 10 requisições simultâneas), usando um benchmark simples em Java antes do JMeter. Registrar p95. | R-09, D-23 | Médio | Item 7 (para volume real) |
| 10 | Introduzir login por credencial (BCrypt) ou formalizar que o login ficará fora do escopo do ciclo atual. | R-12, D-03, D-04 | Médio | Item 7 |
| 11 | Revisar os testes que documentam decisões em aberto (RN-07, RF-15.A.12) e transformá-los em asserções finais após a decisão do item 1. | R-15 | Baixo | Item 1 |
| 12 | Atualizar `README.md` e `arquitetura.md` para separar "implementado" de "planejado", seguindo RNF-18 ("a documentação deve corresponder ao comportamento efetivo"). | D-28, D-30, D-31 | Baixo | — |
| 13 | Medir o estado de cada gate (cobertura, Sonar, JMeter) no ciclo 02 e reconciliar com `docs/ATAM-analise.md`, que não foi usado nesta avaliação. | DN-10, T6 | Baixo | Item 8 |

**Próximo ciclo (ATAM ciclo 02):** aprofundar S-01, S-04 e S-06 com cenários de teste de caminho negativo, e refazer a utility tree com as decisões da §11.

---

## Anexo — Evidências de execução

- Build e testes offline: `./mvnw -B -o -q test` executado sem saída (modo silencioso) e sem falhas reportadas. O código de saída não foi capturado diretamente, porque a saída passou por `tail`.
- Runs do GitHub Actions (API pública, `mariarrcruz/Organizacao-Recursos-Universidade`):

| Run | Branch | Evento | Conclusão | Commit | Observação |
|---|---|---|---|---|---|
| 1 | `ci/github-actions-maven` | pull_request | success | `8be661d` | Workflow inicial |
| 2 | `ci/github-actions-maven` | pull_request | **failure** | `8d3e9a9` | Quebra deliberada de asserção para validar CI em vermelho |
| 3 | `ci/github-actions-maven` | pull_request | success | `4d36099` | Reversão da quebra |
| 4 | `ci/github-actions-maven` | pull_request | success | `7206b06` | Registro dos IDs dos runs |
| 5 | `main` | push | success | `79b5149` | Merge do PR #1 |

Esses runs mostram que o pipeline executa e reage a falha. Não mostram cobertura, análise estática nem desempenho.
