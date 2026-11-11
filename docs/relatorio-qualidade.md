# Relatório de qualidade — Sistema de Organização de Recursos Universitários

## 1. Identificação

| Item | Valor |
|---|---|
| Repositório | `mariarrcruz/Organizacao-Recursos-Universidade` |
| Commit avaliado | `b4c94b1` (`main`, merge do PR #12) |
| Data da avaliação | 2026-11-11 |
| CI do commit | `Maven verify` — sucesso (run `43103127446`, verificado com `gh run list`) |
| Integrantes (autores no histórico git da `main`) | Iago Souza (duas grafias de autor: 83 + 11 commits); Maria Rita Ribeiro Cruz (6); ThalesConstruct (2); Thais Rodrigues (1). A divisão de responsabilidades é a proposta em `docs/status-requisitos.md`, §6, e não foi confirmada pela equipe. |
| Ambiente de execução local | Linux 6.8.0-138-generic; OpenJDK 17.0.21 (pom compila para 17); Maven Wrapper 3.9.9; Docker 29.9.3; PostgreSQL 16-alpine via Testcontainers |
| Ambiente de CI | Ubuntu (runner hospedado), Temurin JDK 21, `./mvnw -B verify`, análise SonarCloud em push e PR |
| Ambiente de desempenho | Declarado em `docs/performance/resultado-01.md` (AMD Ryzen 7 5700U, 16 threads, 15 GiB RAM, mesma máquina para aplicação, banco e JMeter). **Não foi remedido nesta avaliação.** |

**Referências cruzadas:** o pedido cita `docs/rtm.md`; o arquivo do repositório é `docs/RTM.md`. Este relatório usa o nome real.

## 2. Escopo

### 2.1 Avaliado

- API HTTP de reservas, manutenção, recursos, disponibilidade, vínculo Responsável–recurso, auditoria e relatório de utilização.
- Regras de negócio RN-01 a RN-09 nas partes implementadas.
- Matriz de autorização (`docs/seguranca/matriz-autorizacao.md`) executada célula a célula.
- Contrato de erro (ProblemDetail, sem vazamento de detalhe interno).
- Persistência PostgreSQL: restrição de exclusão, lock por recurso, trigger de auditoria, atomicidade transação e auditoria.
- Cenários BDD (80) em `src/test/resources/features/`.
- Mutação manual das regras críticas (7 quebras).
- Cobertura (JaCoCo), análise estática (SonarCloud) e desempenho de uma rodada JMeter (resultado anterior).

### 2.2 Fora de escopo, e por quê

| Item | Motivo |
|---|---|
| Interface web (RF-13) | Não existe: nenhum template ou página estática em `src/main/resources`. |
| Notificações (RF-11) | Não existe. |
| Movimentações de retirada e devolução (RF-09) por HTTP | Lógica em `ServicoMovimentacoes`, sem rota nem persistência (PEN-07). |
| Relatórios além de utilização (carga por sala, contagem por estado) por HTTP | Sem rota (PEN-07). |
| Pesquisa parametrizada de recursos (RF-03) e gestão de recursos (edição, exclusão) (RF-02, RF-18) | Sem rota (PEN-05). |
| Login real por credenciais | Inexistente (PEN-01b). A autenticação atual aceita tokens emitidos pelo próprio endpoint de laboratório. |
| Segurança de infraestrutura (TLS, segredos, rede) | Fora do código da aplicação; não avaliada. |
| LGPD (ADR-015) | Apenas decisão documentada; sem implementação verificada. |
| Acessibilidade | Não há interface a avaliar. |
| Volume de produção (10.000 reservas, premissa S7) | Não medido. |

## 3. Estratégia de teste por nível

| Nível | O que cobre | Onde está | Situação |
|---|---|---|---|
| Unitário | Regras de domínio e serviço: ordem temporal, sobreposição, manutenção, transições, autorização por perfil | `src/test/java/com/organizacao/recursos/testes/`, `servico/` | Executado: parte de 624 testes |
| Integração com PostgreSQL real | Restrição de exclusão, lock, trigger de auditoria, atomicidade, vínculo | `persistencia/` (Testcontainers) | Executado |
| API caixa-preta | Matriz de autorização (98 células de status e 75 de erro), posse pelo id, validação de entrada, contrato de relatório | `api/` (MockMvc, MatrizAutorizacaoTest, ValidacaoEntradaTest, RelatorioUtilizacaoContrato) | Executado |
| BDD | 80 cenários de aprovação, bloqueio e transições, executados contra PostgreSQL pela API | `bdd/RodarCenariosTest` | Executado |
| Mutação manual | 7 quebras de regra crítica, cada uma com o teste ligado | `docs/testes/mutacao-manual.md` | Executado (registro no PR #11) |
| Desempenho | Carga de disponibilidade e criação, uma rodada | `perf/reservas.jmx`, `docs/performance/` | Executado uma vez, em commit anterior |
| Análise estática | SonarCloud, em push e PR | `.github/workflows/ci.yml` | Executado |

## 4. Resultados comparados às metas

### 4.1 Cobertura (meta: linhas ≥ 80%, ramos ≥ 70%)

Fonte: `target/site/jacoco/jacoco.xml` gerado por `./mvnw -B verify` no commit `b4c94b1`.

| Métrica | Coberto / total | Percentual | Meta | Situação |
|---|---|---:|---:|---|
| Linhas | 1090 / 1172 | 93,0% | ≥ 80% | **Atendida** |
| Ramos | 452 / 518 | 87,3% | ≥ 70% | **Atendida** |
| Instruções | 5979 / 6325 | 94,5% | — | Informativo |
| Métodos | 303 / 306 | 99,0% | — | Informativo |
| Classes | 61 / 61 | 100% | — | Informativo |

**Interpretação:** a meta de cobertura é atendida nos dois critérios. Cobertura alta não prova qualidade: as métricas são de execução, não de verificação. A prova de que os testes detectam defeitos está na mutação manual (seção 4.3).

**Limite:** o relatório HTML foi lido em `target/site/jacoco/index.html` (totais), e não aberto num navegador.

**Sonar, mesmo commit (`main`):** cobertura 91,2% e 3 bugs (ver seção 4.4). Os percentuais do Sonar e do JaCoCo diferem porque medem coisas diferentes.

### 4.2 Testes e CI

| Verificação | Resultado | Evidência |
|---|---|---|
| `./mvnw -B verify` local, commit `b4c94b1` | BUILD SUCCESS; 624 testes, 0 falhas, 0 erros, 0 ignorados | Saída do Maven |
| CI no GitHub, mesmo commit | Sucesso (run `43103127446`) | `gh run list --branch main` |
| Quality gate SonarCloud, `main` | OK | API `qualitygates/project_status` |

**Limite:** o CI roda `verify` no runner hospedado, e a execução local usa Docker local. As duas passaram; não há diferença observada entre elas.

### 4.3 Eficácia dos testes: mutação manual

Sete regras críticas foram quebradas uma de cada vez, em código de produção, e restauradas com `git restore` (`docs/testes/mutacao-manual.md`).

| Regra | Quebra | Detectada? |
|---|---|---|
| Sobreposição de horários | `&&` por `\|\|` no predicado | Sim (RN-02, RN-03) |
| Dupla reserva | Remoção do lock e da restrição EXCLUDE | Sim (4 de 4 execuções, `criadas (201): expected 1 but was 20`) |
| Aprovação de restrito | Qualquer não-dono aprova | Sim (RN-07, RF-15, matriz) |
| Manutenção | Verificação de manutenção desligada | Sim (RN-04, revalidação) |
| Autorização de cancelamento | Qualquer usuário cancela | Sim (matriz, RF-16, RF-15) |
| Registro de auditoria | Transição sem evento no PostgreSQL | Sim (teste de atomicidade e BDD) |
| Imutabilidade da auditoria | Trigger removido | Sim (`auditoriaEhSomenteInsercao`) |

**Resultado:** 7 de 7 quebras detectadas. Nenhuma quebra ficou verde, então nenhuma lacuna nova exigiu teste.

**Limite:** são 7 quebras, feitas à mão, e cada uma só testa uma alteração. Não é mutação automática. A mutação não cobre RF-01 (validação de assinatura do JWT) nem RF-19 (validação do token antes da operação), que tiveram execução mas não quebra.

### 4.4 Análise estática (meta: zero bugs ou vulnerabilidades críticas)

Fonte: API do SonarCloud, projeto inteiro, branch `main`, em 2026-11-11.

| Métrica | Valor | Meta | Situação |
|---|---|---|---|
| Vulnerabilidades | 0 | Zero críticas | **Atendida** |
| Bugs críticos | 0 (todos os bugs são MAJOR) | Zero críticos | **Atendida** |
| Bugs (todos) | **3** | Zero bugs (meta do pedido) | **Não atendida** |
| Rating de segurança | 1,0 (A) | — | Informativo |
| Rating de confiabilidade | 3,0 (C) | — | Informativo |
| Code smells | 1 | — | Informativo |
| Quality gate | OK | — | Atendido (o gate avalia código novo) |

Os três bugs são todos da regra `java:S8700` (duração calculada entre `LocalDateTime` sem fuso):
- `ServicoMovimentacoes.java:63`, aberto em 2026-11-04;
- `ServicoReservas.java:348`, aberto em 2026-11-04;
- `RF10MovimentacoesMaterialTest.java:107`, aberto em 2026-11-04.

**Interpretação:** nenhum deles é crítico, e a regra alerta sobre fuso horário, não sobre cálculo errado (a aplicação usa `LocalDateTime` por contrato, com instantes em UTC no armazenamento). A correção aplicada em `ServicoRelatorios` (commit `4abcae1`) segue o mesmo raciocínio e foi aceita pelo gate. Os três permanecem abertos porque ninguém os tratou.

**Limite:** a meta do pedido diz "zero bugs ou vulnerabilidades críticas". Pelo texto, a meta de bugs não é cumprida; pela leitura de "críticas", é cumprida. Este relatório reporta o fato e não escolhe a leitura.

Também há um code smell (`S8786`, expressão regular com backtracking) em `VinculoResponsavelTest.java:64`, aberto em 2026-11-11.

### 4.5 Requisitos críticos na RTM (meta: 100%)

Requisitos críticos = os marcados "Crítica" em `docs/RTM.md` §1.2, mais RNF-02 e o driver D1 (§1.5). São 8 itens. Detalhamento em `docs/testes/mutacao-manual.md`, seção 2.

| Requisito | Teste localizado (caminho :: método) | Execução no commit | Mutação |
|---|---|---|---|
| RF-01 | `testes/RF01AutenticacaoJwtTest :: rf01_5_assinaturaAdulteradaEhRejeitada` | Sim | Não |
| RF-05 | `testes/RN02ExclusividadeAlocacaoTest :: rn02_1_…`, `rn02_2_…` | Sim | Sim |
| RF-06 | `persistencia/ConcorrenciaReservaPostgresTest :: vinteSolicitacoesSimultaneasGeramUmaReserva` | Sim | Sim |
| RF-15 | `testes/RF15ControleAcessoPerfilTest :: rf15_s6_…`; `api/MatrizAutorizacaoTest :: respostaConformeAMatriz` | Sim | Sim |
| RF-16 | `testes/RF16PropriedadeReservaTest :: rf16_2_alterarReservaAlheiaEhNegado` | Sim | Sim |
| RF-19 | `testes/CoberturaRiscoAutenticacaoTest :: assinaturaNaoCanonicaRejeita401` | Sim | Não |
| RNF-02 | Mesmos testes de RF-06 | Sim | Sim |
| D1 | `AplicacaoContextoTest :: aplicacaoAtendeSolicitacaoDeReservaComBancoReal` | Sim | Não |

**Porcentagens:**
- Com teste localizado por caminho e nome, executado no commit: **8 de 8 = 100%**.
- Com detecção por mutação: **5 de 8 = 62,5%**.
- Anexo A da RTM (`docs/RTM.md`, §Anexo A) com caminhos de teste: 3 dos 8 críticos (RF-01, RF-06, RNF-02). RF-05, RF-15, RF-16, RF-19 e D1 não têm linha no Anexo A. A meta de 100% é cumprida pela matriz de mutação, não pelo Anexo A.

**Limite:** "executado no commit" usa o CI do próprio commit `b4c94b1`, que passou. A meta de 100% na RTM depende da definição de "rastreado"; com a definição mais estrita (linha no Anexo A), a meta não é cumprida.

### 4.6 Desempenho (meta de referência: p95 ≤ 500 ms, erro < 1%)

Fonte: `docs/performance/resultado-01.md`.

| Operação | p95 medido | Erro real | Situação |
|---|---:|---:|---|
| Consulta de disponibilidade | 326 ms | 0% | Dentro da meta, em uma rodada |
| Criação de reserva | 335 ms (todas respostas 409) | 0% | Dentro da meta, mas sem criação bem-sucedida suficiente |

**Decisão do próprio documento: INCONCLUSIVO.** Motivos: uma rodada só (o `rnf.md` exige três), ambiente não isolado, 4 reservas no banco (premissa era 10.000), e 3.986 de 3.987 POSTs foram recusados.

**Limite que pesa mais:** a medição é de um commit anterior. O código mudou em D3, D4 e D5. Em particular, o plano usa token de administrador para a consulta (`GET /reservas?recursoId=`), e a decisão D4 impede esse token. **Com o código atual, o plano de carga não roda como está.** O desempenho do commit atual não foi medido.

## 5. Defeitos

Fonte: `docs/defeitos/`.

| Defeito | Severidade | Situação | Commit da correção |
|---|---|---|---|
| DEF-001 — Dupla reserva sob solicitações simultâneas | Crítica | Corrigido | Ver ADR-016 e DEF-002 |
| DEF-002 — Deadlock em solicitações simultâneas | Alta | Corrigido | Lock pessimista por recurso |
| DEF-003 — Erros fora do ProblemDetail | Média | Corrigido | `dbe1fd4` |
| DEF-004 — Exceção vazando detalhe interno | Alta | Corrigido | `93d7dc5` |
| DEF-005 — Capacidade negativa aceita | Média | Corrigido | `568f352` |
| DEF-006 — Campo ausente sem mensagem clara | Média | Corrigido | `5ee2077` |

**Totais:** 6 defeitos documentados. Por severidade: 1 crítica, 2 altas, 3 médias. Abertos: 0.

**Limite:** o registro cobre só defeitos encontrados pelos testes deste projeto e pela mutação manual. Os 3 bugs do Sonar (seção 4.4) não estão em `docs/defeitos/`. Não há contagem de defeitos de produção, porque a aplicação não tem usuários reais.

## 6. Riscos residuais

| Risco | Evidência | Impacto | Mitigação atual |
|---|---|---|---|
| Sem login real: não há como um usuário obter token sem o endpoint de laboratório | `POST /dev/tokens` é o único emissor, e é desligado por padrão (PEN-01b) | Alto para uso real | Nenhuma no código |
| Sem token de administrador pela API (decisão D4) | `AplicacaoContextoTest`, `DesenvolvimentoTokensControllerTest` | Operações de cadastro, manutenção e vínculo só por banco | Roteiro de execução manual |
| Laboratório de carga quebrado pelo D4 | `perf/reservas.jmx` usa token de administrador | Não é possível repetir a medição de desempenho | Nenhuma no código |
| Desempenho não medido no commit atual | Seção 4.6 | Não se sabe se a meta vale hoje | Rodada única, anterior |
| Concorrência entre manutenção e reserva sem teste | PEN-02; não há teste concorrente de manutenção (`grep` nas classes de teste) | Possível reserva em horário bloqueado sob concorrência | O código trava o recurso nos dois caminhos do PostgreSQL, mas não há teste que prove isso |
| Funções ausentes da especificação | D2 e seção 2.2 | Escopo incompleto | Registrado em PEN-05 a PEN-08 |
| Decisões de produto abertas | Quem vê histórico e relatórios; ator de início de uso, conclusão e não comparecimento (P7); AMB-02 | Regras podem mudar | Decisões do projeto registradas |
| Bugs S8700 abertos | Seção 4.4 | Baixo | Nenhum |
| Mutação não cobre validação de JWT | Seção 4.3 | Possível regressão na validação de assinatura | Testes existem e passam |

## 7. Recomendação

**Não recomendo apresentar o sistema como entrega completa.** O núcleo de reservas, a autorização por perfil e o contrato de erro têm evidência forte: testes em vários níveis, cobertura acima da meta, CI verde, mutação das regras críticas detectada e nenhum defeito aberto.

**Recomendo apresentar com as seguintes ressalvas explícitas:**
1. Não há login real. A segurança depende de um endpoint de laboratório, que a documentação já marca como não-produtivo.
2. As funções de gestão de recursos, usuários, movimentações, relatórios além do de utilização e notificações não existem por HTTP.
3. O desempenho não foi medido no commit atual, e o plano de carga precisa de atualização antes de ser executado de novo.
4. Os três bugs de regra S8700 continuam abertos. A meta literal de zero bugs não é cumprida.

**Ações antes de uma entrega completa, em ordem de prioridade:**
1. Implementar autenticação real (PEN-01b).
2. Atualizar o plano JMeter para o novo fluxo de autenticação e repetir a medição em três rodadas no commit atual (`rnf.md`, seção 5).
3. Tratar os três bugs S8700 ou registrar a decisão de aceitá-los.
4. Escrever o teste de concorrência manutenção × reserva (PEN-02).
5. Completar as rotas de movimentação e relatórios (PEN-07) e de gestão de recursos (PEN-05).
