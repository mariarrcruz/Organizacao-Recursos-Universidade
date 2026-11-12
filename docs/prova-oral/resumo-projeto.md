# Resumo do projeto para a prova oral

**Estado de referência:** tag `v1.0.0-rc1`, commit `14d766a`. Cada afirmação aponta para o arquivo onde está comprovada. Quando algo foi decidido mas não foi implementado, este resumo diz isso.

---

## 1. Visão geral

### O que é o sistema?

Sistema que aloca salas, professores, materiais e equipamentos sem conflito de horário, com histórico das reservas. É um monólito Spring Boot em Java 21, com PostgreSQL e uma página estática em `/`.

### Quem usa o sistema?

- **Solicitante:** consulta disponibilidade, pesquisa recursos e cria, altera ou cancela as próprias reservas.
- **Responsável:** aprova ou rejeita reservas dos recursos a que está vinculado, registra retirada e devolução de material e registra não comparecimento.
- **Administrador:** cadastra recursos, usuários, vínculos e manutenções. Não cria reservas (D5).

### Qual é o estado atual?

- Tag `v1.0.0-rc1`, CI verde, quality gate do SonarCloud OK.
- 643 testes, 0 falhas. Cobertura de linhas 92,5 % e de ramos 83,7 % (`docs/qualidade/jacoco.xml`).
- Decisão de qualidade: **liberar com ressalvas**, como entrega acadêmica (`docs/decisao-qualidade.md`).

---

## 2. Arquitetura

### Como o código está organizado?

Quatro camadas, em `src/main/java/com/organizacao/recursos/`:

| Pacote | Papel | Exemplos |
|---|---|---|
| `api` | Controladores HTTP, tradução de JSON e erros | `ReservasController`, `CatalogoRecursosController`, `RelatoriosController` |
| `servico` | Regras de negócio | `ServicoReservas`, `ServicoAutenticacao`, `ServicoUsuarios`, `ServicoMovimentacoes` |
| `dominio` | Entidades e enums | `Reserva`, `Recurso`, `EventoAuditoria`, `EstadoReserva`, `Perfil` |
| `persistencia` | Porta `Armazenamento` e duas implementações | `ArmazenamentoPostgres`, `ArmazenamentoMemoria` |

A configuração de beans está em `ConfiguracaoAplicacao`.

### Como uma requisição chega ao banco?

1. O controlador lê o cabeçalho `Authorization` e chama `ServicoAutenticacao.autenticar`, que devolve o usuário com os perfis do token.
2. O controlador chama o serviço de regras (por exemplo, `ServicoReservas.solicitar`).
3. O serviço valida a regra e chama a porta `Armazenamento`.
4. `ArmazenamentoPostgres` abre uma conexão do pool HikariCP, executa a transação e devolve o resultado.
5. Erros de negócio viram `ErroAplicacao` com código HTTP, que o `TratadorErros` converte em `ProblemDetail` (RFC 9457).

### Por que existe uma porta de armazenamento?

Para que as regras não dependam do banco. A mesma regra roda sobre `ArmazenamentoMemoria` nos testes unitários e sobre `ArmazenamentoPostgres` na aplicação e nos testes de integração (ADR-017). O contrato da porta é o mesmo nas duas implementações: a recusa por sobreposição com status 409 acontece dentro de `inserirReserva`.

### Há mais de uma implementação de serviço de recursos?

Não em produção. `ServicoRecursos` existe, mas não é bean e só é usado nos testes. A pesquisa e a edição de recursos da API são feitas por `CatalogoRecursosController`, que acessa o armazenamento diretamente.

---

## 3. Decisões técnicas

### Quais decisões estão documentadas e o que foi implementado?

Os ADRs estão em `docs/adr/`. A tabela mostra o que existe no código hoje.

| ADR | Decisão | Situação no código |
|---|---|---|
| 001 | Monólito modular Spring Boot | **Implementado** |
| 002 | H2 relacional local | **Não implementado.** Não há H2 no `pom.xml`. O banco é PostgreSQL (ADR-017 é a decisão vigente). |
| 003 | JWT com access de 1 h e refresh de 7 dias | **Parcial.** Access token de 1 h implementado. O refresh token existe em `ServicoAutenticacao`, mas não tem rota HTTP. |
| 004 | Validação transacional com lock por recurso | **Implementado** (lock consultivo no PostgreSQL) |
| 005 | Auditoria append-only transacional | **Implementado** para reservas. Ações administrativas não são auditadas. |
| 006 | Notificações simuladas por logs | **Implementado** (log e memória) |
| 007 | Relatórios com filtros e leitura separada | **Parcial.** Só o relatório de utilização tem rota. A leitura separada não foi feita. |
| 008 | Testes automatizados e CI/CD no GitHub Actions | **Implementado** |
| 009 | Cenário acadêmico de carga | **Implementado** (plano JMeter, medições e workflow manual) |
| 010 | Pipeline batch diário | **Não implementado.** Não há código de batch. |
| 011 | Watermark e checkpoint para idempotência | **Não implementado** (depende de 010) |
| 012 | H2 analítico separado | **Não implementado.** Decisão marcada como futura. |
| 013 | Validação 3D de qualidade de dados | **Não implementado** (depende de 010) |
| 014 | Versionamento de transformações e rollback | **Não implementado.** Decisão marcada como futura. |
| 015 | Anonimização LGPD | **Não implementado.** Sem código de anonimização. |
| 016 | Restrição EXCLUDE com `tstzrange` no PostgreSQL | **Implementado** (`reserva_sem_sobreposicao`) |
| 017 | Persistência em PostgreSQL atrás de porta | **Implementado** |

**Atenção:** o `docs/adr/README.md` diz "Total: 15 ADRs", mas há 17 arquivos. A contagem está desatualizada.

### Quais decisões de produto foram tomadas?

Registradas em `docs/testes/plano-tdd.md` §7.2 e em `docs/seguranca/matriz-autorizacao.md` §4:

- **D1:** Solicitante consulta disponibilidade (`GET /disponibilidade`).
- **D3:** o Responsável só aprova, rejeita e consulta reservas de recursos a que está vinculado. O vínculo é cadastrado pelo Administrador (`POST /vinculos`).
- **D4:** o token de laboratório (`POST /dev/tokens`) não emite ADMINISTRADOR. Por isso o Administrador precisa de login.
- **D5:** Solicitante e Responsável criam reservas. O Administrador recebe 403.
- **AMB-01:** o dono pode excluir a própria reserva em SOLICITADA.
- **AMB-03:** o Responsável pode criar reserva própria. Essa decisão contradiz a persona do Responsável, que não lista essa ação (`docs/personas/responsavel.md`).
- **AMB-04:** duração mínima de reserva de um minuto.
- **AMB-06:** Solicitante não acessa o relatório gerencial (403).
- **RF-12.7:** recurso inexistente em relatório de sala retorna 404.

### Quais drivers de decisão a RTM registra?

Em `docs/rtm.md` §1.5: D1 aplicação executável, D2 autorização por perfil, D3 prevenção de dupla reserva (são os três limitadores), D4 auditoria imutável, D5 desempenho, D6 testabilidade e evidências, D7 segurança de entrada e erros, D8 simplicidade operacional (monólito local) e D9 rastreabilidade. Os D1 a D5 da matriz de autorização e os D1 a D5 do RTM **não são a mesma lista**; a banca pode confundir os dois.

---

## 4. Como cada parte funciona

### Como é criada uma reserva?

`ServicoReservas.solicitar`:
1. Exige usuário autenticado com perfil Solicitante ou Responsável (senão 403).
2. Valida o período: início e fim obrigatórios, fim depois do início, duração mínima de um minuto.
3. Recusa recurso que não esteja DISPONIVEL (409).
4. Faz a verificação de disponibilidade na aplicação. Se houver conflito, registra o conflito e recusa (409).
5. Define o estado inicial: SOLICITADA para recurso restrito, APROVADA para recurso livre.
6. Chama `armazenamento.inserirReserva`, que grava a reserva e o evento de auditoria na mesma transação.
7. Notifica o solicitante.

### Como uma reserva é aprovada?

- Só o Responsável vinculado ao recurso, e que não seja o dono da reserva, aprova ou rejeita (`exigirAutorizacaoTransicao`).
- Na aprovação, a disponibilidade é checada de novo (`revalidarManutencao`).
- A rejeição exige justificativa.
- Cada transição gera um evento de auditoria e uma notificação.

### Quais transições são permitidas?

Definidas em `transicaoPermitida`, com testes em BDD (`transicoes-reserva.feature`: 7 permitidas, 42 proibidas). Quem pode disparar cada transição:
- **Aprovar e rejeitar:** Responsável vinculado, não dono.
- **Não comparecimento:** Responsável, só depois do início.
- **Cancelar:** dono, com justificativa. Em APROVADA, só antes do início.
- **Iniciar uso e concluir:** dono ou Responsável. Iniciar uso só a partir do horário.

### Como se evita a dupla reserva?

Três barreiras, da mais fraca para a mais forte:
1. **Verificação na aplicação** (`verificarDisponibilidade`): rápida, dá a mensagem de erro, mas tem corrida.
2. **Transação com lock consultivo por recurso:** `ArmazenamentoPostgres.inserirReserva` chama `travarRecurso`, que executa `pg_advisory_xact_lock(hashtext(recurso))`. Dentro da transação, confere manutenção e insere. O lock sai no commit ou no rollback.
3. **Restrição `EXCLUDE`** (`reserva_sem_sobreposicao`): `EXCLUDE USING gist (recurso_id WITH =, periodo WITH &&)`, só para estados ativos. Se a sobreposição passar pelas duas barreiras anteriores, o banco recusa e o código devolve 409 (SQLSTATE 23P01).

Os intervalos são semiabertos `[início, fim)`, então reservas encostadas não conflitam.

**Teste:** 20 solicitações simultâneas, cada uma com sua instância do serviço, geram uma reserva (`ConcorrenciaReservaPostgresTest`). Reserva em período de manutenção é recusada sob concorrência (`ConcorrenciaManutencaoReservaPostgresTest`).

**Limite:** o teste é probabilístico. Mostra sensibilidade, não ausência de corrida.

### Como funciona o bloqueio por manutenção?

Manutenção é um período registrado pelo Administrador (`POST /manutencoes`), em `tstzrange`. Reserva que se sobrepõe a ela é recusada. Na aprovação, a manutenção é checada de novo. A gravação de manutenção também usa o lock do recurso.

### Como funciona a pesquisa e a disponibilidade?

- `GET /recursos` filtra por tipo, localização, capacidade mínima e competência. Só retorna recursos DISPONIVEIS.
- Com `inicio` e `fim`, exclui os recursos que têm reserva ativa ou manutenção sobreposta.
- `GET /disponibilidade` responde só se o recurso está livre. Não revela quem reservou.

### Como funciona a autenticação?

- `POST /auth/login`: a senha é verificada com PBKDF2-HMAC-SHA256, 210 mil iterações e sal aleatório (`ServicoUsuarios`). Usuário inexistente e senha errada recebem a mesma resposta 401.
- O token é JWT assinado com RS256, válido por 1 hora (`AutenticacaoController`).
- `ServicoAutenticacao.autenticar` exige o cabeçalho `Authorization: Bearer`, verifica a assinatura, expiração, emissor, audiência e perfis.
- A chave RSA é gerada na subida e não é persistida. Reiniciar a aplicação invalida todos os tokens.

### Como funciona a autorização?

Por perfil, em cada controlador e em cada serviço. A matriz completa está em `docs/seguranca/matriz-autorizacao.md`, e os testes que a executam são `api/MatrizAutorizacaoTest`, `testes/RF15ControleAcessoPerfilTest` e `testes/RF16PropriedadeReservaTest`. Sem token: 401. Perfil sem permissão: 403. Transição inválida: 409.

### Como o primeiro administrador é criado?

Pela variável `APP_BOOTSTRAP_ADMIN_SENHA`, lida em `ConfiguracaoAplicacao`. Se ela estiver definida e ainda não existir administrador, o usuário é criado na subida. Não há senha padrão no código.

**Atenção:** `application.properties` tem usuário e senha padrão do banco (`organizacao`/`organizacao`). Isso é um risco aceito para o ambiente local.

### Como funciona a auditoria?

- Cada mudança de estado grava um `EventoAuditoria` na mesma transação da mudança. Campos: reserva, ator, instante em UTC, ação, estado anterior, estado novo e justificativa.
- Um trigger (`evento_append_only`) recusa `UPDATE` e `DELETE` em `evento_auditoria`.
- Só Responsável e Administrador leem o histórico (`GET /reservas/{id}/auditoria`). O dono da reserva recebe 403.
- **Limites:** ações administrativas não são auditadas; não há ID de correlação; quem tem acesso direto ao banco pode alterar o histórico (ADR-005).

### Como funciona o registro de conflitos?

Cada recusa por sobreposição grava uma linha em `conflito_reserva`, em conexão própria, para que a contagem sobreviva ao rollback da transação da reserva. Esses dados alimentam a métrica "conflitos evitados" do relatório.

### Como funcionam as movimentações de material?

- **Retirada:** exige reserva APROVADA ou EM_USO e Responsável vinculado ao recurso. Não retira o mesmo material duas vezes.
- **Devolução:** só vale para o material retirado naquela reserva.
- **Histórico:** `GET /movimentacoes/{materialId}`, para Responsável e Administrador.
- **Limite:** as movimentações ficam em memória e se perdem ao reiniciar.

### Como funcionam as notificações?

Simuladas (ADR-006). Cada criação de reserva e cada mudança de estado gera uma mensagem para o solicitante, gravada no log e em memória. Cada usuário consulta as próprias em `GET /notificacoes`. Não há envio de e-mail nem chamada a API externa.

### Como funciona o relatório de utilização?

`GET /relatorios/utilizacao?inicio=&fim=`, para Responsável e Administrador.
- Horas por recurso contam só reservas APROVADA, EM_USO e CONCLUIDA.
- Cada reserva conta só a parte que cai dentro do período, com as datas tratadas como dias inclusivos.
- Carga horária por professor é o total de horas dos recursos do tipo PROFESSOR.
- Conflitos evitados são as recusas por sobreposição no período. Recusas por manutenção não entram.

### Como funciona a interface?

Página estática em `src/main/resources/static/index.html`, servida em `/`. Mostra telas por perfil: login, pesquisa, solicitação, minhas reservas, aprovações, movimentações e administração. O token fica só na aba (`sessionStorage`). Não foi verificada num navegador real.

---

## 5. Persistência

### Quais tabelas existem?

Em `src/main/resources/db/schema.sql`:
- `recurso`: salas, professores, materiais e equipamentos.
- `reserva`: com a restrição `EXCLUDE`.
- `manutencao`: período em `tstzrange`.
- `evento_auditoria`: append-only, com trigger.
- `conflito_reserva`: contagem de conflitos evitados.
- `vinculo_responsavel`: Responsável por recurso (D3).

A extensão `btree_gist` é criada no início do script. O script é idempotente e é executado na subida (`criarEsquema`).

### Quais dados não são persistidos?

Usuários e credenciais, movimentações de material, notificações e a chave JWT. Esse é o maior limite funcional.

### Como funciona o pool de conexões?

HikariCP, com no máximo 20 conexões e 2 ociosas. O pool é compartilhado por URL e usuário (`ArmazenamentoPostgres.POOLS`). Antes, cada operação abria uma conexão nova, e o monitor Java segurava I/O de banco. Isso causou a lentidão de DEF-008.

### Quando as operações são serializadas?

No PostgreSQL, só dentro de cada recurso, pelo lock consultivo. Operações de recursos diferentes rodam em paralelo. O serviço de reservas não usa mais `synchronized`.

---

## 6. Configuração e execução

### Como o projeto é configurado?

`application.properties`, com variáveis de ambiente:
- `APP_BOOTSTRAP_ADMIN_ID` e `APP_BOOTSTRAP_ADMIN_SENHA`: primeiro administrador.
- `app.dev.tokens`: habilita o token de laboratório. Padrão `false`.
- `app.banco.url`, `usuario` e `senha`: conexão com o PostgreSQL. Padrão local.
- `server.port`: porta 8080.

### Como o projeto é executado?

Pelo README: `docker compose up -d` para o PostgreSQL, `./mvnw -B -DskipTests package` e `java -jar` com `APP_BOOTSTRAP_ADMIN_SENHA` definida. Requer JDK 21 e Docker.

### Como os testes são executados?

`./mvnw -B verify`. Os testes de persistência e de concorrência sobem PostgreSQL com Testcontainers, então também precisam de Docker.

---

## 7. Testes e qualidade

### Quais tipos de teste existem?

- **Unitários e parametrizados** (JUnit 5): regras de domínio e de serviço.
- **API com MockMvc:** contratos HTTP, validação de entrada, matriz de autorização.
- **Integração com PostgreSQL real** (Testcontainers): constraint, lock, trigger, atomicidade.
- **Concorrência:** dupla reserva e manutenção, com threads.
- **BDD:** 80 cenários em `src/test/resources/features/`, executados pela API HTTP.
- **WireMock:** calendário institucional simulado.
- **End-to-end:** 4 cenários com a aplicação em porta aleatória e PostgreSQL em contêiner (`e2e/FluxoEndToEndTest`).

### Há mock nos testes?

Não há Mockito em `src/test`. Quando é preciso simular falha do armazenamento, usa-se uma classe escrita à mão (`ArmazenamentoComFalhaEmListagem`) que delega ao armazenamento em memória.

### Quais são os números de qualidade?

- Testes: 643, 0 falhas.
- Cobertura: linhas 92,5 % (meta 80 %), ramos 83,7 % (meta 70 %). Cobertura mede execução, não verificação.
- SonarCloud: quality gate OK, 0 bugs, 0 vulnerabilidades, 1 code smell aberto (S8786).
- Mutação: sete quebras de regra, detectadas no commit `b4c94b1`. Não repetidas no código atual.

### Quais defeitos foram encontrados?

Dez, em `docs/defeitos/`, todos corrigidos. Os mais importantes:
- **DEF-001:** dupla reserva sob solicitações simultâneas.
- **DEF-002:** deadlock em solicitações simultâneas.
- **DEF-007:** Responsável via solicitações de recursos não vinculados.
- **DEF-008:** serialização que deixava o sistema lento sob carga.
- **DEF-010:** bugs S8700 do SonarCloud (duração entre datas sem fuso).

---

## 8. Desempenho

### Como foi medido?

Plano JMeter em `perf/reservas.jmx`: 50 usuários virtuais, 240 segundos, 75 % de consultas e 25 % de criações de reserva no mesmo horário. Os critérios estão em `docs/performance/rnf.md`, e a análise em `scripts/analisar_jtl.py`.

### Quais foram os resultados?

- **Antes da correção (rodadas 02 a 04):** consulta e criação entre 870 e 995 ms (p95).
- **Depois da correção (rodadas 05 a 07, local):** consulta de 3 ms e criação de 38 a 39 ms.
- **No GitHub (run 43322490773):** consulta de 1 ms e criação de 2 ms.

### O requisito de desempenho está atendido?

Nas medições feitas, sim. O `rnf.md` pede três rodadas em ambiente de referência, e isso não foi feito. Por isso a resposta correta é "medido, com ressalva".

---

## 9. Pipeline e entrega

### O que roda no CI?

- `ci.yml`: `./mvnw -B verify` e a análise do SonarCloud, em PR e em push para a `main`. Run do commit da tag: 43327161512 (sucesso).
- `carga.yml`: manual. Roda o JMeter 5.6.3 no GitHub, com os critérios de `scripts/analisar_jtl.py`.

### Como os artefatos são controlados?

- Tag anotada `v1.0.0-rc1` no commit `14d766a`. A tag não será movida. Correções vão para `v1.0.0-rc2`.
- Índice de evidências em `docs/evidencias/README.md`, com links para o commit da tag.
- Exportação do SonarCloud e do JaCoCo versionada em `docs/qualidade/`.

### Como o grupo trabalhou?

Pelo histórico, a maior parte dos commits em `main` é de Iago Souza. Maria Rita Ribeiro Cruz, ThalesConstruct e Thais Rodrigues têm commits de documentação e de arquitetura. As declarações ficam em `docs/decisao-qualidade.md` §6, para cada integrante confirmar.

### Houve revisão por pares?

Não. Nenhum dos PRs #1 a #21 tem revisão formal no GitHub. Esta é a pendência mais importante para a entrega final.

---

## 10. Decisão e pendências

### Qual é a decisão de qualidade?

**Liberar com ressalvas**, como entrega acadêmica, não para produção (`docs/decisao-qualidade.md`). Os testes foram rodados em máquina de desenvolvimento e no runner do GitHub, sem ambiente de referência e sem usuários reais.

### O que falta para a entrega final?

1. Revisão por pares de outro integrante, registrada no GitHub.
2. Repetir as sete quebras de mutação no código atual.
3. Decisão da equipe sobre o limite de desempenho e medição em ambiente de referência.

### Quais são as limitações?

- Usuários, movimentações e notificações não são persistidos.
- Auditoria só cobre reservas.
- Relatórios além do de utilização não têm rota.
- Refresh token existe no serviço, sem rota HTTP.
- Chave JWT não é persistida.
- Interface não foi verificada em navegador real.
- ADRs de batch, dados analíticos, validação de qualidade de dados e LGPD (010 a 015) não têm código.

### O que faria primeiro, se continuasse?

Persistir usuários e movimentações no PostgreSQL. Depois, auditar ações administrativas, persistir a chave JWT, expor o refresh token e repetir a medição de desempenho em ambiente de referência.

---

## Onde conferir cada coisa

| Assunto | Arquivo |
|---|---|
| Decisão de qualidade | `docs/decisao-qualidade.md` |
| Evidências da tag | `docs/evidencias/README.md` |
| Relatório de qualidade | `docs/relatorio-qualidade.md` |
| Matriz de rastreabilidade | `docs/rtm.md` |
| Arquitetura | `docs/arquitetura.md` |
| ADRs | `docs/adr/` |
| Autorização | `docs/seguranca/matriz-autorizacao.md` |
| Transições de reserva | `src/test/resources/features/transicoes-reserva.feature` |
| Esquema do banco | `src/main/resources/db/schema.sql` |
| API | `docs/api.md` |
| Desempenho | `docs/performance/` |
| Defeitos | `docs/defeitos/` |
| Mutação | `docs/testes/mutacao-manual.md` |
| Perguntas da banca | `docs/prova-oral/perguntas.md` |
