# Respostas-base para a prova oral — Sistema de Organização de Recursos

**Finalidade:** apoio para o estudo de cada integrante. Reúne (A) as 12 perguntas do banco oficial (`docs/prova-oral/perguntas.md`) com resposta, evidência e pontos de atenção, e (B) um banco de perguntas gerais sobre o projeto que a banca pode fazer.

**Estado de referência:** tag `v1.0.0-rc1`, commit `14d766a`, branch `main`, em 2026-11-12. Os números vêm dos relatórios versionados, não de memória.

**Regra de uso:** cada integrante responde sobre o que não escreveu (regra do banco oficial). A resposta deve citar o arquivo e, quando possível, o teste.

---

## Antes de começar: três referências do banco oficial que mudaram

| Pergunta | O que o banco diz | Estado atual |
|---|---|---|
| T2 | `ValidacaoEntradaTest` usa `mock(Armazenamento.class)` | **Não é mais verdade.** Não há Mockito em `src/test` (verificado: 0 arquivos). O teste usa a classe `ArmazenamentoComFalhaEmListagem`, escrita à mão, que delega ao armazenamento em memória e falha só na listagem de reservas. |
| T4 | `ServicoMovimentacoes.java`, linha 63 | A linha mudou para 64, e o cálculo agora converte para `Instant` em UTC (DEF-010). |
| M4 | A constante `VAZAMENTO` fica em `MatrizAutorizacaoTest` | Está em `ValidacaoEntradaTest`. A `MatrizAutorizacaoTest` não a define. |

Outras mudanças depois da prova: `docs/RTM.md` virou `docs/rtm.md`, e o número de PRs é maior que 12 (há 19 até o PR #19). A divisão de autoria continua valendo.

---

# Parte A — Banco oficial (12 perguntas)

## Iago Souza — documentos de outros autores

### I1. Persona do Responsável x criação de reserva própria

**Resposta:** a persona (`docs/personas/responsavel.md`) não lista criar reserva entre as ações permitidas. Mas a decisão registrada é AMB-03, em `docs/testes/plano-tdd.md` §7.2, item 4: "Responsável pode criar reserva própria. Confirmado. Ativa RF-15.R.7". A decisão está no código, em `ServicoReservas.solicitar`, que aceita Solicitante ou Responsável, e no README (tabela de perfis).

**Quem confirmou:** o documento não registra o nome. Não se deve afirmar quem confirmou sem verificar.

**O que a matriz muda:** a matriz (`docs/seguranca/matriz-autorizacao.md`) não tem linha que cite RF-15.R.7. A regra existe no código e no README, mas a matriz não a registra. O cabeçalho da matriz também cita um commit antigo.

**Pontos de atenção:** a persona continua em conflito com o código. Dizer só "o código deveria bloquear" ignora a decisão registrada.

### I2. Cenário C-03 do ATAM

**Resposta:** C-03 é "Auditoria imutável de transição" (`docs/ATAM-analise.md`, linha 124). Protege **auditabilidade** e **confiabilidade**, pela atomicidade entre a mudança de estado e o evento de auditoria. Na matriz de sensibilidade (linha 570), o C-03 tem ⚠️ em confiabilidade e em testabilidade.

**Testes:**
- `ArmazenamentoPostgresTest.auditoriaEhSomenteInsercao`: `UPDATE` e `DELETE` em `evento_auditoria` são recusados pelo trigger, com a mensagem "append-only".
- `mudancaDeEstadoEEventoSaoGravadosJuntos` e `falhaNoEventoDesfazMudancaDeEstadoSemAuditoriaOrfa`: cobrem a atomicidade.

**O que o teste prova:** a aplicação, pelo usuário do banco, não altera nem apaga auditoria. Se o evento falha, a mudança de estado também não fica gravada.

**O que não prova:** que um administrador de infraestrutura com acesso direto ao banco não altera o histórico. O ADR-005 registra o limite: "alguém com acesso ao banco pode editar".

**Nota:** o ATAM descreve o cenário com H2. O projeto usa PostgreSQL.

### I3. RNF-12: o que é comprovado e o que não é

**Comprovado por teste:**
- Cada transição gera evento. Exemplo: `RN09RastreabilidadeTest`.
- O evento tem data (`instante`, em UTC), responsável (`atorId`), ação e estados anterior e posterior. Exemplo: o registro `EventoAuditoria` e a tabela `evento_auditoria`.
- A gravação é atômica com a mudança de estado. Exemplo: `mudancaDeEstadoEEventoSaoGravadosJuntos`.
- O histórico só é lido por Responsável e Administrador, inclusive para o dono da reserva. Exemplo: `RN09RastreabilidadeTest.rn09_11`.

**Sem prova:**
- **Ações administrativas.** O RNF-12 fala em "transições de estado e ações administrativas". Criar ou alterar recursos, usuários, vínculos e manutenções não gera auditoria. É uma lacuna real.
- **ID de correlação.** A RTM (§3.11) descreve o campo, mas `EventoAuditoria` não o tem.
- **Imutabilidade contra o administrador do banco.** O trigger protege contra a aplicação, não contra acesso direto.
- **Retenção de longo prazo.** O ADR-005 deixa isso para produção. Não há teste.

### I4. Limitadores de nota (PRD, RISK-01)

O PRD (linha 80) lista quatro: aplicação que não executa, pipeline ausente, falha de autorização e permissão de dupla reserva.

| Limitador | Estado | Evidência | Ressalva |
|---|---|---|---|
| Aplicação executa | Atendido | Build e execução em clone limpo com JDK 21 | README corrigido para JDK 21. Sem `APP_BOOTSTRAP_ADMIN_SENHA` não há administrador (PEN-03) |
| Pipeline | Atendido | CI verde em `14d766a` (run 43327161512); gate do SonarCloud OK | Não há revisão por pares |
| Autorização | Atendido com ressalvas | `MatrizAutorizacaoTest`, `RF15ControleAcessoPerfilTest`, `RF16PropriedadeReservaTest`, E2E com 401 e 403 | Credenciais em memória (PEN-01b) |
| Dupla reserva | Atendido com ressalva | `ConcorrenciaReservaPostgresTest` e `ConcorrenciaReservaHttpPostgresTest`; `ConcorrenciaManutencaoReservaPostgresTest` | Teste probabilístico; mutação não repetida no código atual |

**Pontos de atenção:** `docs/status-requisitos.md` §1 ainda mostra o estado antigo (372 testes, autorização parcial). A RTM §1.5 mapeia só D1 a D3 (aplicação, autorização e dupla reserva), e o pipeline não tem driver próprio.

---

## Maria Rita Ribeiro Cruz — código escrito pelo Iago

### M1. Mecanismo contra dupla reserva com duas instâncias

**Resposta:** há duas barreiras no PostgreSQL, e a verificação na aplicação não conta como barreira.

1. **Verificação na aplicação** (`ServicoReservas.verificarDisponibilidade`): é rápida e dá a mensagem de erro, mas tem corrida. Duas instâncias podem passar nela ao mesmo tempo.
2. **Transação com lock consultivo por recurso** (`ArmazenamentoPostgres.inserirReserva`, com `travarRecurso` = `pg_advisory_xact_lock(hashtext(recurso))`). Dentro da transação, também confere manutenção (`verificarManutencao`) e insere. O lock é liberado no commit.
3. **Restrição `EXCLUDE` com `tstzrange`** (`reserva_sem_sobreposicao`, `schema.sql`, ADR-016). É a barreira final. Se mesmo assim houver sobreposição, o banco devolve SQLSTATE 23P01, que vira 409.

**Teste:** `ConcorrenciaReservaPostgresTest.vinteSolicitacoesSimultaneasGeramUmaReserva`. Ele mede se 20 solicitações simultâneas, cada uma com sua instância do serviço, geram exatamente uma reserva no estado final do banco.

**Limite:** o teste é probabilístico. Ele mostra sensibilidade, não ausência de corrida. A mutação do lock e da restrição foi detectada no commit `b4c94b1`.

**Resposta fraca:** "usamos transações e verificamos a disponibilidade antes de gravar". Essa resposta não separa a verificação da aplicação, que tem corrida, da restrição do banco.

### M2. Remover a regra de aprovação de restrito

**Resposta:** o caso é a mutação M3 de `docs/testes/mutacao-manual.md`: em `ServicoReservas.exigirAutorizacaoTransicao`, a condição `tem(RESPONSAVEL) && !dono && vinculado` vira `!dono`, e qualquer não-dono aprova.

**Testes que falham:** `RN07AlcadaAprovacaoTest` (em especial `rn07_1_responsavelAprovaRecursoRestrito`, nos casos de Solicitante e de Responsável não vinculado), `VinculoResponsavelTest.naoVinculadoNaoAprova` e a matriz de autorização (`MatrizAutorizacaoTest`), porque ela também testa a aprovação, pela API.

**Testes que continuam verdes:** os de criação, cancelamento e transições fora de APROVADA, que não passam por essa condição.

**Resposta fraca:** "os testes de aprovação falham". Não diz quais, nem por quê.

### M3. Validação do JWT antes de operação protegida

**Resposta:** `ServicoAutenticacao.autenticar` (linha 95):
1. exige o cabeçalho `Authorization: Bearer ...`, senão 401 "Token ausente";
2. verifica a assinatura RS256 com a chave pública;
3. verifica expiração, emissor (`iss`), audiência (`aud`) e perfis (`roles`), que viram o enum `Perfil`.

**Teste:** `RF01AutenticacaoJwtTest.rf01_5_assinaturaAdulteradaEhRejeitada`.

**Pontos de atenção:**
- O token de laboratório (`POST /dev/tokens`) existe só com `app.dev.tokens=true`, e nunca emite ADMINISTRADOR.
- Login real: `POST /auth/login` emite token de 1 hora.
- A chave RSA é gerada na subida da aplicação e não é persistida. Reiniciar invalida todos os tokens. A arquitetura prevê chave persistida fora do repositório, e isso não foi feito.

### M4. Falha de banco vazar SQL ou nome de classe

**Resposta:** `TratadorErros.falhaInesperada` responde com uma mensagem genérica de erro interno (500). A exceção real fica no log do servidor. A resposta nunca traz o texto da exceção, o SQL nem o nome da classe.

**Teste:** `ValidacaoEntradaTest.falhaInternaNaoVazaDetalhes`. Ele usa o armazenamento escrito à mão, que lança uma exceção com SQL no texto. O teste exige 500 e verifica que o corpo da resposta não contém esse texto. A constante `VAZAMENTO` com os padrões que o teste procura fica também em `ValidacaoEntradaTest`.

**Resposta fraca:** "o tratador devolve mensagem genérica". Não diz que o detalhe vai para o log, nem qual padrão o teste usa.

---

## Thais Rodrigues — código escrito pelo Iago

### T1. `FOR UPDATE` ou `pg_advisory_xact_lock`

**Resposta:**
- **Caminho de produção:** `ArmazenamentoPostgres` usa o lock consultivo `pg_advisory_xact_lock`, por `travarRecurso`, em `inserirReserva`, `atualizarReserva` e `inserirManutencao`. A aplicação recebe esse armazenamento em `ConfiguracaoAplicacao`.
- **Outra estratégia:** `RepositorioReservasPostgres.solicitar` usa `SELECT ... FOR UPDATE` para travar a linha do recurso. Esse repositório aparece só nos testes de concorrência, para mostrar outra forma de evitar ciclo de espera (DEF-002). Na aplicação, a classe só fornece constantes de SQLSTATE.
- **Por que o lock consultivo:** ele trava uma chave lógica (o recurso), não uma linha. Assim cobre também a manutenção, que ainda não tem linha de recurso para travar. É liberado no commit ou no rollback.

**Resposta fraca:** "as duas evitam deadlock, então tanto faz". Não sabe qual é usada em produção.

### T2. Mock ou banco real

**Resposta:** o projeto não usa Mockito. O teste de falha interna usa um armazenamento escrito à mão (`ArmazenamentoComFalhaEmListagem`), que delega ao armazenamento em memória. Os testes de regra usam `ArmazenamentoMemoria`. Os testes de integração usam PostgreSQL real, via Testcontainers.

**Por que essa escolha:** o armazenamento em memória e o decorador são implementações de verdade da porta. Eles permitem testar regra e falha sem subir banco.

**O que só o banco real prova:** a restrição `EXCLUDE`, o trigger de auditoria, os lock consultivos e o SQLSTATE 23P01. Nenhum deles existe no armazenamento em memória. Por isso há testes em `persistencia/`.

**Resposta fraca:** "mock é mais rápido que o banco". Confunde mock com implementação em memória.

### T3. Conflitos evitados numa tabela própria

**Resposta:**
- A regra de negócio é que a recusa não muda o estado da reserva. Por isso não gera evento de auditoria. A PRD (RN-09) só exige auditoria para mudança de estado. Os itens numerados de RN-09 aparecem no plano de testes (`docs/testes/plano-tdd.md`, a partir da linha 203), e não consegui confirmar um item "RN-09.10" com esse conteúdo. Não se deve citar esse número sem verificar.
- "Conflitos evitados" é uma métrica do relatório de utilização (RF-12), não um evento de auditoria. Por isso fica numa tabela própria, `conflito_reserva`.
- **Rollback:** o conflito é gravado em conexão própria (`registrarConflito`, chamado por `registrarConflitoAposRecusa`). Assim, quando a transação da reserva sofre rollback, a contagem de conflitos continua. Sem isso, o relatório perderia as recusas.

**Resposta fraca:** "porque é outra métrica". Não explica o rollback.

### T4. Os três bugs `S8700`

**Resposta:**
- **O que a regra alerta:** `Duration.between` entre dois `LocalDateTime`. Como `LocalDateTime` não tem fuso, a duração pode depender de como o valor é interpretado.
- **Severidade:** MAJOR, não crítica. O alerta é sobre fuso horário, não sobre um cálculo errado.
- **Por que o quality gate não reprovou:** o gate avalia o código novo. Os bugs estavam em código antigo da `main`.
- **Risco real:** baixo. A aplicação grava instantes em UTC. Mesmo assim, o código foi corrigido: a conversão para `Instant` com `ZoneOffset.UTC` (DEF-010), em `ServicoReservas`, `ServicoMovimentacoes` e no teste correspondente.

**Resposta fraca:** "são bugs, e o projeto deveria ter zero bugs". Não distingue o alerta do erro de cálculo.

---

## ThalesConstruct — desempenho, BDD e rastreabilidade

### Th1. p95 de 326 ms e 335 ms

**Resposta:** o que a rodada 01 permite e não permite (`docs/performance/resultado-01.md`):
- **Permite:** que, naquela rodada, os dois p95 ficaram abaixo de 500 ms, com erro zero.
- **Não permite afirmar a meta:** foi uma rodada só (o `rnf.md` §5 pede três); o ambiente não é de referência; a criação mediu quase só recusas (3.986 de 3.987 POSTs deram 409); o commit medido é anterior às mudanças; e o plano com token de administrador não rodava mais com o código atual (D4).

**O que mudou depois:** o DEF-008 foi corrigido. Nas rodadas 05 a 07 (local), a consulta deu p95 de 3 ms e a criação, de 38 a 39 ms. No workflow do GitHub (run 43322490773), a consulta deu 1 ms e a criação, 2 ms. As rodadas antes da correção (02 a 04) deram 870 a 995 ms. A ressalva continua: não é ambiente de referência.

**Resposta fraca:** "sim, está abaixo de 500 ms, então a meta foi cumprida". Ignora a rodada única, o ambiente e a criação que mede recusa.

### Th2. BDD pela API HTTP

**Resposta:** os 80 cenários (`src/test/resources/features/`) rodam por `bdd/RodarCenariosTest`, passando pelo caminho HTTP, com PostgreSQL real. O objetivo é que o cenário verifique também a camada de API, a serialização, os códigos de resposta e a persistência, o que um teste de domínio não verificaria.

**O que é preparado diretamente:** só o relógio (`Clock`). Os dados são criados pela API.

**Resposta fraca:** "BDD é para o cliente entender". Não cita o que o caminho HTTP e o PostgreSQL verificam.

### Th3. Teste localizado x mutação detectada

**Resposta:** teste localizado é o teste existir, com nome e caminho, e ser executado no commit. Mutação detectada é uma regra ter sido quebrada de propósito e algum teste ter falhado.

Em `docs/relatorio-qualidade.md` §4.5, os 8 requisitos críticos têm teste localizado (100 %), mas só 5 têm mutação detectada (62,5 %). Os que ficaram sem mutação são **RF-01**, **RF-19** e **D1** (aplicação executável). Eles têm teste, mas nenhuma quebra foi feita para eles.

**Atenção:** o número 62,5 % foi calculado antes do teste de concorrência com manutenção (RN-04), e a mutação não foi repetida no código atual. Não apresentar esse número como estado atual sem essa ressalva.

**Resposta fraca:** "uma é o teste existir, a outra é o teste funcionar". Não diz quais requisitos ficaram de fora, nem por quê.

---

# Parte B — Banco de perguntas gerais sobre o projeto

Cada resposta traz o ponto de partida e, quando há, a evidência. Use como base para a fala, não como texto decorado.

## B1. Visão geral

**B1.1. O que o sistema faz?**
Aloca salas, professores, materiais e equipamentos sem conflito de horário, com histórico das reservas. Tem três perfis: Solicitante, Responsável e Administrador (README, tabela de perfis).

**B1.2. Qual é a stack?**
Java 21, Spring Boot 3.2.5, PostgreSQL 16, Testcontainers nos testes, JaCoCo, SonarCloud, JMeter e GitHub Actions. A interface é uma página estática servida em `/`.

**B1.3. Qual é o estado atual?**
Tag `v1.0.0-rc1` no commit `14d766a`, com CI verde e quality gate do SonarCloud OK. 643 testes passando. Cobertura de linhas 92,5 % e de ramos 83,7 % (`docs/qualidade/jacoco.xml`).

**B1.4. Qual é a decisão de qualidade e por quê?**
Liberar com ressalvas, como entrega acadêmica, não para produção (`docs/decisao-qualidade.md`). Os testes foram rodados em máquina de desenvolvimento e no runner do GitHub, sem ambiente de referência e sem usuários reais.

## B2. Arquitetura

**B2.1. Qual é o estilo arquitetural?**
Monolito modular em Spring Boot (ADR-001). Pacotes: `api` (controladores HTTP), `servico` (regras de negócio), `dominio` (entidades e enums), `persistencia` (porta `Armazenamento` com duas implementações).

**B2.2. Por que uma porta de armazenamento?**
Para que as regras de negócio não dependam do banco. Há duas implementações: `ArmazenamentoPostgres` (produção e testes de integração) e `ArmazenamentoMemoria` (testes unitários e demonstração). Decisão em ADR-017.

**B2.3. Quais decisões estão em ADR?**
Há 17 ADRs em `docs/adr/`. As mais relevantes para a prova: ADR-003 (JWT de 1 hora e refresh token), ADR-005 (auditoria append-only transacional), ADR-006 (notificações simuladas), ADR-016 (restrição `EXCLUDE` com `tstzrange`), ADR-017 (PostgreSQL atrás da porta).

**B2.4. O que a arquitetura prevê que ainda não está pronto?**
Persistência de usuários e de movimentações; chave JWT persistida fora do repositório; refresh token com rota HTTP; auditoria de ações administrativas. Está registrado em `docs/arquitetura.md` (quadro de estado no topo) e no CHANGELOG.

## B3. Regras de negócio e requisitos

**B3.1. Quais são os estados da reserva?**
SOLICITADA, APROVADA, EM_USO, CONCLUIDA, REJEITADA, CANCELADA e NAO_COMPARECEU. O fluxo principal é SOLICITADA → APROVADA → EM_USO → CONCLUIDA. Os estados alternativos são REJEITADA, CANCELADA e NAO_COMPARECEU.

**B3.2. Como um recurso restrito é aprovado?**
Reserva em recurso restrito nasce SOLICITADA. Só o Responsável vinculado ao recurso aprova ou rejeita, e nunca a própria reserva (`exigirAutorizacaoTransicao`). Recurso livre nasce APROVADA direto.

**B3.3. O que é o vínculo Responsável–recurso?**
Cadastrado pelo Administrador (`POST /vinculos`). Sem vínculo, o Responsável não aprova, não rejeita e não registra movimentação do recurso. Decisão D3, em `docs/rtm.md` §1.5.

**B3.4. Quem cria reserva?**
Solicitante e Responsável. O Administrador recebe 403. A persona do Responsável não lista essa ação, e há uma decisão registrada (AMB-03) em conflito com ela. Ver I1.

**B3.5. Como é checada a sobreposição de horários?**
Intervalos semiabertos `[início, fim)`. Há checagem na aplicação e, no PostgreSQL, a restrição `EXCLUDE` sobre `tstzrange`. Recurso em manutenção também recusa a reserva.

**B3.6. Qual é a duração mínima de uma reserva?**
Um minuto (AMB-04, RN-01.4).

**B3.7. Quando o uso pode começar e a reserva pode ser cancelada?**
O uso só começa a partir do horário de início (`inicio-uso` antes disso dá 409). O cancelamento de APROVADA só é permitido antes do início.

**B3.8. O que é uma movimentação de material?**
Retirada e devolução. A retirada exige reserva APROVADA ou EM_USO e um Responsável vinculado ao recurso. Não se retira o mesmo material duas vezes. A devolução só vale para o material retirado naquela reserva. Hoje fica em memória.

## B4. Autenticação e autorização

**B4.1. Como o usuário se autentica?**
`POST /auth/login` com usuário e senha. A senha é verificada com PBKDF2-HMAC-SHA256 (210 mil iterações, sal aleatório). Usuário inexistente e senha errada recebem a mesma resposta 401.

**B4.2. Quanto tempo o token vale?**
1 hora. O token é JWT assinado com RS256.

**B4.3. Como o primeiro administrador é criado?**
Pela variável de ambiente `APP_BOOTSTRAP_ADMIN_SENHA`. Sem ela, a aplicação sobe sem administrador. Não existe senha padrão no código.

**B4.4. O que é o token de laboratório?**
`POST /dev/tokens`, que existe só com `app.dev.tokens=true`. Serve para demonstração e carga. Não emite ADMINISTRADOR.

**B4.5. Onde está a matriz de autorização?**
Em `docs/seguranca/matriz-autorizacao.md`. Os testes que a executam são `api/MatrizAutorizacaoTest`, `testes/RF15ControleAcessoPerfilTest` e `testes/RF16PropriedadeReservaTest`. Atenção: o cabeçalho da matriz cita um commit antigo.

**B4.6. Um Solicitante pode ver o histórico da própria reserva?**
Não. O histórico é restrito a Responsável e Administrador, inclusive para o dono (`RN09RastreabilidadeTest.rn09_11`).

## B5. Concorrência e integridade

**B5.1. Como a dupla reserva é evitada?**
Três barreiras: verificação na aplicação, transação com lock consultivo por recurso e a restrição `EXCLUDE` no PostgreSQL. Detalhe em M1.

**B5.2. Como foi testada?**
`ConcorrenciaReservaPostgresTest` e `ConcorrenciaReservaHttpPostgresTest`: 20 solicitações simultâneas, com uma instância do serviço para cada uma, geram uma reserva. `ConcorrenciaManutencaoReservaPostgresTest`: reservas simultâneas em período de manutenção são todas recusadas.

**B5.3. Por que o serviço não tem mais `synchronized`?**
Porque o monitor Java segurava I/O de banco e serializava todas as operações. Isso causou a lentidão de DEF-008. A atomicidade passou para o armazenamento, que já a garante no PostgreSQL. No armazenamento em memória, a checagem de sobreposição foi para dentro de `inserirReserva`, como o contrato da porta exige.

**B5.4. O que os testes de concorrência não provam?**
Que não há corrida em todos os casos. São testes probabilísticos: mostram sensibilidade, não ausência de falha. A garantia vem do banco.

## B6. Auditoria, rastreabilidade e notificação

**B6.1. O que é auditado?**
Toda mudança de estado de reserva gera um evento em `evento_auditoria`, na mesma transação. O evento tem instante em UTC, ator, ação, estado anterior e estado novo, e justificativa quando houver.

**B6.2. Como a imutabilidade é garantida?**
Um trigger `BEFORE UPDATE OR DELETE` recusa alteração e exclusão. O teste é `ArmazenamentoPostgresTest.auditoriaEhSomenteInsercao`.

**B6.3. Qual é o limite dessa garantia?**
Ela protege contra a aplicação, não contra quem tem acesso direto ao banco. Ver I2 e I3.

**B6.4. O que não é auditado?**
Ações administrativas (recursos, usuários, vínculos, manutenções). Isso é uma lacuna em relação ao RNF-12. Ver I3.

**B6.5. Como funcionam as notificações?**
São simuladas (ADR-006): cada criação e cada mudança de estado de reserva gera uma mensagem, registrada no log e em memória. Cada usuário consulta as próprias em `GET /notificacoes`. Não há envio real de e-mail.

## B7. Persistência

**B7.1. Quais dados são persistidos?**
Reservas, recursos, manutenções, eventos de auditoria, conflitos e vínculos, no PostgreSQL.

**B7.2. Quais dados não são persistidos?**
Usuários com credenciais, movimentações de material e notificações. Estão em memória, e reiniciar a aplicação os apaga. Esse é o maior limite funcional.

**B7.3. Como o esquema é criado?**
`ArmazenamentoPostgres.criarEsquema()` executa `db/schema.sql` na subida. O script é idempotente.

**B7.4. O que é o pool de conexões?**
HikariCP, compartilhado por URL e usuário (`ArmazenamentoPostgres.POOLS`). Foi introduzido na correção de DEF-008.

## B8. Testes e qualidade

**B8.1. Quais tipos de teste existem?**
Unitários e parametrizados (JUnit 5), testes de domínio, API com MockMvc, integração com PostgreSQL real (Testcontainers), concorrência, BDD (80 cenários pela API), WireMock para o calendário institucional e end-to-end (4 cenários em porta real).

**B8.2. Quantos testes existem e quantos passam?**
643 testes, 0 falhas, no build com JDK 21.

**B8.3. A cobertura garante qualidade?**
Não. Cobertura mede execução, não verificação. A prova de que os testes detectam defeitos está na mutação e nos testes de concorrência e de regra. Essa é a posição do próprio relatório (`docs/relatorio-qualidade.md`).

**B8.4. Os testes usam mock?**
Não há Mockito em `src/test`. Ver B-T2 em Parte A.

**B8.5. O que é o teste end-to-end?**
`e2e/FluxoEndToEndTest`: sobe a aplicação em porta aleatória, com PostgreSQL em contêiner, e faz chamadas HTTP reais. Cobre o fluxo principal e três casos negativos.

**B8.6. Quais defeitos foram encontrados e corrigidos?**
Dez, em `docs/defeitos/`. Os mais importantes: DEF-001 (dupla reserva sob solicitações simultâneas), DEF-002 (deadlock), DEF-007 (Responsável via solicitações de recursos não vinculados), DEF-008 (serialização sob carga) e DEF-010 (bugs S8700 do SonarCloud). Todos estão corrigidos.

**B8.7. O que é a mutação e onde está?**
Sete quebras de regra feitas à mão, em `docs/testes/mutacao-manual.md`. Foram detectadas no commit `b4c94b1`. Não foram repetidas no código atual, e isso está registrado como pendência.

## B9. Pipeline, análise estática e desempenho

**B9.1. O que roda no CI?**
`ci.yml`: `./mvnw -B verify` e a análise do SonarCloud, em PR e em push para `main`. O CI do commit `14d766a` está em [run 43327161512](https://github.com/mariarrcruz/Organizacao-Recursos-Universidade/actions/runs/43327161512).

**B9.2. O que é o `carga.yml`?**
Um workflow manual que roda três rodadas do JMeter no runner do GitHub, com JMeter 5.6.3 do arquivo oficial, e aplica os critérios de `scripts/analisar_jtl.py`.

**B9.3. Qual é o resultado do SonarCloud?**
Quality gate OK. 0 bugs, 0 vulnerabilidades e 1 code smell aberto (S8786, `VinculoResponsavelTest.java:64`). A análise de `14d766a` está em `docs/qualidade/sonarcloud.json`.

**B9.4. Qual é o resultado de desempenho?**
Depois da correção do DEF-008: consulta p95 de 3 ms e criação de 38 a 39 ms nas rodadas locais 05 a 07; no GitHub, consulta de 1 ms e criação de 2 ms. Antes da correção, 870 a 995 ms. Ver Th1.

**B9.5. O desempenho atende o requisito?**
Nas medições feitas, sim. Mas o `rnf.md` pede três rodadas em ambiente de referência, e isso não foi feito. Por isso a resposta correta é "medido, com ressalva", não "atendido".

## B10. Processo e entrega

**B10.1. Como o grupo trabalhou?**
Pelo histórico do repositório: a maior parte dos commits em `main` está na conta de Iago Souza (com duas grafias de autor). Maria Rita Ribeiro Cruz, ThalesConstruct e Thais Rodrigues têm commits de documentação e de arquitetura. Os detalhes estão em `docs/decisao-qualidade.md` §6, e cada integrante deve confirmar a própria declaração.

**B10.2. Houve revisão por pares?**
Não há revisão formal em nenhum dos PRs (#1 a #19) no GitHub. Esta é a pendência mais importante para a entrega final.

**B10.3. Como o projeto é reproduzido?**
Pelo README: `docker compose up -d`, `./mvnw -B -DskipTests package`, `java -jar ...` com `APP_BOOTSTRAP_ADMIN_SENHA` definida e `./mvnw -B verify`. Requer JDK 21 e Docker.

**B10.4. O que é a tag e o que ela contém?**
`v1.0.0-rc1`, anotada, no commit `14d766a`. O CHANGELOG e o índice de evidências (`docs/evidencias/README.md`) apontam para esse commit. A tag não será movida. Correções vão para `v1.0.0-rc2`.

**B10.5. O que falta para a entrega final?**
Três condições (`docs/decisao-qualidade.md` §5): revisão por pares de outro integrante, repetição das sete quebras de mutação no código atual e decisão da equipe sobre o limite de desempenho e a medição em ambiente de referência.

**B10.6. Se a condição não for cumprida?**
A decisão passa a ser bloquear a entrega final.

## B11. Limitações e riscos

**B11.1. Quais são os riscos aceitos?**
Nove, listados em `docs/decisao-qualidade.md` §3. Os principais: credenciais e movimentações em memória (R1, R7), administrador depende de variável de ambiente (R2), falta de revisão por pares (R4) e desempenho sem ambiente de referência (R5).

**B11.2. O sistema está pronto para produção?**
Não. Os testes foram feitos em máquina de desenvolvimento e no runner do GitHub, sem usuários reais, sem persistência de usuários e sem ambiente de referência.

**B11.3. Quais são as funções parciais?**
Relatórios além do de utilização, auditoria de recursos e de ações administrativas, interface sem verificação em navegador real, e a rota de refresh token (existe no serviço, sem rota HTTP).

**B11.4. O que faria primeiro, se continuasse o projeto?**
Persistir usuários e movimentações no PostgreSQL. Depois, auditar ações administrativas, persistir a chave JWT e repetir a medição de desempenho em ambiente de referência.

---

## Anexo — referências para conferir antes da prova

| Assunto | Arquivo |
|---|---|
| Decisão de qualidade | `docs/decisao-qualidade.md` |
| Índice de evidências da tag | `docs/evidencias/README.md` |
| Relatório de qualidade | `docs/relatorio-qualidade.md` |
| Matriz de rastreabilidade | `docs/rtm.md` |
| Checklist de entrega | `docs/entrega/checklist.md` |
| Arquitetura | `docs/arquitetura.md` |
| Decisões (ADRs) | `docs/adr/` |
| Autorização | `docs/seguranca/matriz-autorizacao.md` |
| Desempenho | `docs/performance/` |
| Defeitos | `docs/defeitos/` |
| Mutação | `docs/testes/mutacao-manual.md` |
| Cobertura e análise estática | `docs/qualidade/` |
| Histórico de versões | `CHANGELOG.md` |
