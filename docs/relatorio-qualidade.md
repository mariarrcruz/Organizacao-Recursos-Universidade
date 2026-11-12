# Relatório de qualidade — Sistema de Organização de Recursos Universitários

## 1. Identificação

| Item | Valor |
|---|---|
| Repositório | `mariarrcruz/Organizacao-Recursos-Universidade` |
| Base da avaliação | `main` em `b4c94b1`, mais a branch `feat/front-login-carga-java21` (PR para `main`) |
| Data da avaliação | 2026-11-12 |
| Build local | `./mvnw -B verify` com OpenJDK 21.0.13 (Temurin); 643 testes, 0 falhas |
| Ambiente de CI | Ubuntu (runner hospedado), Temurin JDK 21, `./mvnw -B verify`, análise SonarCloud em PR e push na `main` |
| Ambiente local de execução | Linux 6.8.0-138-generic; Docker; PostgreSQL 16-alpine (Testcontainers nos testes, `docker-compose.yml` na execução) |
| Ambiente de desempenho | AMD Ryzen 7 5700U, 16 threads, 15 GiB RAM, mesma máquina para aplicação, banco e JMeter, com duas IDEs Java ativas durante as rodadas. Não é ambiente de referência (`docs/performance/rnf.md`, §5). |
| Integrantes | Ver histórico git. A divisão de responsabilidades é a proposta em `docs/status-requisitos.md`, §6, e não foi confirmada pela equipe. |

**Referências cruzadas:** a matriz de rastreabilidade é `docs/rtm.md`.

## 2. Escopo

### 2.1 Avaliado

- API HTTP completa: autenticação por senha (`/auth/login`), usuários, recursos (cadastro, pesquisa, consulta, edição, exclusão), vínculos, manutenções, reservas (criação, consulta, alteração, cancelamento, aprovação, rejeição, uso, conclusão, não comparecimento, auditoria), disponibilidade, movimentações de material, notificações simuladas e relatório de utilização.
- Interface web em `/`, servida pela mesma aplicação (verificada pelo teste end-to-end e pelo conteúdo da página; não foi aberta num navegador).
- Regras de negócio RN-01 a RN-09.
- Matriz de autorização (`docs/seguranca/matriz-autorizacao.md`).
- Contrato de erro (ProblemDetail, sem detalhe interno).
- Persistência PostgreSQL: restrição de exclusão, lock por recurso, trigger de auditoria, atomicidade.
- Cenários BDD (80) em `src/test/resources/features/`.
- Testes end-to-end com aplicação em porta real e PostgreSQL em contêiner (`e2e/FluxoEndToEndTest`).
- Mutação manual das regras críticas (7 quebras, registro em `docs/testes/mutacao-manual.md`).
- Cobertura (JaCoCo), análise estática (SonarCloud) e carga (JMeter).

### 2.2 Fora de escopo, e por quê

| Item | Motivo |
|---|---|
| Segurança de infraestrutura (TLS, gestão de segredos, rede) | Fora do código da aplicação. |
| LGPD (ADR-015) | Apenas decisão documentada; sem implementação verificada. |
| Volume de produção (10.000 reservas, premissa S7) | Não medido. |
| Persistência de usuários, movimentações e notificações | Em memória por decisão do grupo, conforme `docs/api.md` e o README (limitações conhecidas). |
| Verificação visual da interface em navegador | Não feita; a página foi validada por teste end-to-end e por checagem de conteúdo. |

## 3. Estratégia de teste por nível

| Nível (TG) | O que cobre | Onde está | Situação |
|---|---|---|---|
| TG-01/02 Unitário e parametrizado | Regras de domínio e serviço, parametrizadas | `testes/`, `servico/` | Executado |
| TG-03 Integração com PostgreSQL real | Restrição de exclusão, lock, trigger, atomicidade, manutenção | `persistencia/` (Testcontainers) | Executado |
| TG-04 API caixa-preta | Matriz de autorização, validação de entrada, contratos HTTP | `api/` (MockMvc) | Executado |
| TG-05 End-to-end | Fluxo completo por HTTP com aplicação real: login, pesquisa, reserva restrita, aprovação, uso, movimentação, auditoria, notificações; casos negativos de 401, 403 e vínculo | `e2e/FluxoEndToEndTest` (4 cenários) | Executado |
| TG-06 Autorização e erros | Perfis, propriedade e tratamento de erro | `testes/RF15…`, `RF16…`, `api/MatrizAutorizacaoTest` | Executado |
| TG-07 Concorrência | 20 solicitações simultâneas (dupla reserva); 20 simultâneas em período de manutenção | `persistencia/ConcorrenciaReservaPostgresTest`, `ConcorrenciaReservaHttpPostgresTest`, `ConcorrenciaManutencaoReservaPostgresTest` | Executado |
| TG-08 WireMock | Calendário institucional simulado | `testes/IntegracaoCalendarioWireMockTest` | Executado |
| TG-09 BDD/TDD | 80 cenários de transições, aprovação e bloqueio | `bdd/RodarCenariosTest`, `src/test/resources/features/` | Executado |
| TG-10 Regressão | Teste de cada defeito corrigido | `docs/defeitos/` (DEF-007, DEF-009, DEF-010) | Executado |
| TG-11 Carga | Consulta de disponibilidade e criação simultânea | `perf/reservas.jmx`, `scripts/analisar_jtl.py`, `.github/workflows/carga.yml` | Executado (3 rodadas); critério não atendido (seção 4.6) |
| Mocks | Restrição: mock só em teste unitário isolado | Nenhum mock do Mockito em `src/test` (verificado por `grep`) | Atendida |

## 4. Resultados comparados às metas

### 4.1 Cobertura (meta: linhas ≥ 80 %, ramos ≥ 70 %)

Fonte: `docs/qualidade/jacoco.xml` (versionado; gerado por `./mvnw -B verify` com JDK 21).

| Métrica | Coberto / total | Percentual | Meta | Situação |
|---|---:|---:|---:|---|
| Linhas | 1286 / 1391 | 92,5 % | ≥ 80 % | **Atendida** |
| Ramos | 556 / 664 | 83,7 % | ≥ 70 % | **Atendida** |
| Instruções | 7080 / 7577 | 93,4 % | — | Informativo |
| Métodos | 363 / 368 | 98,6 % | — | Informativo |
| Classes | 73 / 73 | 100 % | — | Informativo |

**Interpretação:** cobertura alta não prova qualidade. A evidência de que os testes detectam defeitos está na mutação manual (seção 4.3) e nos testes de concorrência e end-to-end.

### 4.2 Testes e CI

| Verificação | Resultado | Evidência |
|---|---|---|
| `./mvnw -B verify` local, JDK 21 | BUILD SUCCESS; 643 testes, 0 falhas, 0 erros, 0 ignorados | Saída do Maven e `target/surefire-reports/` |
| CI no GitHub do PR #14 para `main` | Sucesso (run `43315686130`, `Maven verify` e SonarCloud concluídos) | `gh run view 43315686130` |
| Quality gate SonarCloud do PR #14 | OK (run `43315686130`) | API `qualitygates/project_status?pullRequest=14` |
| Carga no GitHub (workflow manual `carga.yml`) | Não executado ainda: o GitHub só aceita `workflow_dispatch` com o arquivo na branch padrão | — |

### 4.3 Eficácia dos testes: mutação manual

Registro de 7 quebras feitas à mão em código de produção (`docs/testes/mutacao-manual.md`). **Essas quebras foram feitas sobre o commit `b4c94b1`, antes das mudanças desta entrega.** Não foram repetidas depois.

| Regra | Quebra | Detectada? |
|---|---|---|
| Sobreposição de horários | `&&` por `\|\|` no predicado | Sim (RN-02, RN-03) |
| Dupla reserva | Remoção do lock e da restrição EXCLUDE | Sim |
| Aprovação de restrito | Qualquer não-dono aprova | Sim (RN-07, RF-15, matriz) |
| Manutenção | Verificação de manutenção desligada | Sim (RN-04, revalidação) |
| Autorização de cancelamento | Qualquer usuário cancela | Sim (matriz, RF-16, RF-15) |
| Registro de auditoria | Transição sem evento no PostgreSQL | Sim |
| Imutabilidade da auditoria | Trigger removido | Sim (`auditoriaEhSomenteInsercao`) |

**Limite:** são 7 quebras, uma por vez, feitas à mão. Não é mutação automática, e não cobre a validação de assinatura do JWT.

### 4.4 Análise estática (meta: zero bugs ou vulnerabilidades críticas)

Fonte: `docs/qualidade/sonarcloud-pr14.json` (análise do PR #14, exportada por `scripts/exportar_sonarcloud.py --pr 14`). A branch principal está em `docs/qualidade/sonarcloud.json`.

| Métrica | Valor no PR #14 | Meta | Situação |
|---|---|---|---|
| Quality gate | OK | — | Atendido |
| Bugs | 0 | Zero | **Atendida** |
| Vulnerabilidades | 0 | Zero críticas | **Atendida** |
| Code smells | 0 | — | Informativo |
| Rating de confiabilidade | 1,0 (A) | — | Informativo |
| Rating de segurança | 1,0 (A) | — | Informativo |
| Cobertura (Sonar, código total) | 89,4 % | — | Informativo; o JaCoCo mede de outra forma (seção 4.1) |
| Cobertura do código novo | 80,9 % | ≥ 80 % (gate) | Atendida |
| Duplicação | 0,0 % | ≤ 3 % (gate) | Atendida |

**Histórico nesta entrega:** a primeira análise do PR (run `43315068599`) reprovou o gate com um bug `java:S2259` (possível `NullPointerException`) em `AutenticacaoController.java:42`. Foi corrigido com variáveis locais no commit `21e8a53`. A segunda análise passou sem bugs.

Os três bugs `java:S8700` de `main` (DEF-010) não aparecem mais no PR.

### 4.5 Requisitos críticos na RTM (meta: 100 %)

Requisitos críticos = os marcados "Crítica" em `docs/rtm.md` §1.2, mais RNF-02, o driver D1 e a regra RN-04 sob concorrência. Detalhamento em `docs/rtm.md`, Anexo A.

| Requisito | Teste localizado | Situação |
|---|---|---|
| RF-01 | `testes/RF01AutenticacaoJwtTest`; `api/CatalogoLoginNotificacoesTest` (login) | Atendido |
| RF-05 | `testes/RN02ExclusividadeAlocacaoTest` | Atendido |
| RF-06 / RNF-02 | `persistencia/ConcorrenciaReservaPostgresTest`; `ConcorrenciaReservaHttpPostgresTest` | Atendido |
| RF-15 | `testes/RF15ControleAcessoPerfilTest`; `api/MatrizAutorizacaoTest` | Atendido |
| RF-16 | `testes/RF16PropriedadeReservaTest` | Atendido |
| RF-19 | `testes/RF01AutenticacaoJwtTest`; `e2e/FluxoEndToEndTest` (`semTokenRecusa`) | Atendido |
| RN-04 sob concorrência | `persistencia/ConcorrenciaManutencaoReservaPostgresTest` | Atendido (novo nesta entrega) |
| D1 | `AplicacaoContextoTest` | Atendido |

**Porcentagem:** 8 de 8 requisitos críticos com teste localizado e executado no build local = **100 %**. A detecção por mutação cobre 5 dos 8 (seção 4.3, não repetida).

### 4.6 Desempenho (meta de referência: p95 ≤ 500 ms, erro < 1 %)

Fontes: `docs/performance/resultado-01.md` (rodada anterior, JDK 17), `resultado-02.md` (rodadas 02 a 04, antes da correção) e `resultado-03.md` (rodadas 05 a 07, após a correção do DEF-008).

| Rodada | Consulta (p95) | Criação (p95) | Erro | Veredito |
|---|---:|---:|---:|---|
| 01 (versão anterior) | 326 ms | 335 ms | 0 % | INCONCLUSIVO |
| 02 a 04 (antes da correção) | 870 a 950 ms | 906 a 995 ms | 0 % | NÃO ATENDIDO |
| 05 | 3 ms | 39 ms | 0 % | ATENDIDO |
| 06 | 3 ms | 38 ms | 0 % | ATENDIDO |
| 07 | 3 ms | 39 ms | 0 % | ATENDIDO |
| GitHub, workflow `carga.yml` (execução `43322490773`, 3 rodadas, runner do GitHub) | 1 ms | 2 ms | 0 % | ATENDIDO |

**Causa corrigida (DEF-008):** o serviço de reservas serializava todas as operações com um monitor Java em volta de I/O de banco, e cada operação abria uma conexão nova. A correção remove o monitor, usa pool de conexões e deixa a atomicidade com o armazenamento (transação, lock consultivo e `EXCLUDE` no PostgreSQL). Os testes de concorrência e de regra continuam passando.

**Carga no GitHub:** o workflow `carga.yml` (manual, na `main` após o PR #15) rodou as três rodadas no runner hospedado, com JMeter 5.6.3 do arquivo oficial da Apache. A execução `43322490773` terminou com sucesso. Consulta com p95 de 1 ms e criação com p95 de 2 ms nas três rodadas, erro 0 % e só respostas 200 (consulta) e 409 (criação). As análises e os logs estão em `docs/performance/github-carga-43322490773/`. Os `.jtl` brutos não são versionados. Esse runner tem outra máquina e 4 núcleos, então não substitui a medição de referência.

**Ressalva:** as rodadas 05 a 07 foram feitas na mesma máquina de desenvolvimento, com duas IDEs Java abertas. Não é o ambiente de referência do `rnf.md` §5. O requisito está atendido nessa medição, mas o limite não deve ser tratado como fixado até uma medição em ambiente de referência.

### 4.7 CI do PR

PR #14 (`feat/front-login-carga-java21` → `main`). Runs: `43315068599` (reprovou o Sonar por S2259), `43315686130` (sucesso, gate OK) e `43321276037` (commit `da22eba`, correção de desempenho: sucesso, gate OK, 0 issues abertos de bug, vulnerabilidade ou code smell, cobertura do código novo 82,1 %).

## 5. Defeitos

Fonte: `docs/defeitos/`.

| Defeito | Severidade | Situação | Teste de regressão |
|---|---|---|---|
| DEF-001 — Dupla reserva sob solicitações simultâneas | Crítica | Corrigido | `ConcorrenciaReservaPostgresTest` |
| DEF-002 — Deadlock em solicitações simultâneas | Alta | Corrigido | Testes de concorrência |
| DEF-003 — Erros fora do ProblemDetail | Média | Corrigido | `ValidacaoEntradaTest` |
| DEF-004 — Exceção vazando detalhe interno | Alta | Corrigido | `ValidacaoEntradaTest.falhaInternaNaoVazaDetalhes` |
| DEF-005 — Capacidade negativa aceita | Média | Corrigido | `ValidacaoEntradaTest` |
| DEF-006 — Campo ausente sem mensagem clara | Média | Corrigido | `ValidacaoEntradaTest.inicioAusente`, `recursoAusente` |
| DEF-007 — Responsável vê solicitações de recursos não vinculados | Alta | Corrigido | `CoberturaRiscoReservasTest.pendentesSoDeRecursoVinculado`; `FluxoEndToEndTest.responsavelSemVinculoNaoAprova` |
| DEF-008 — Operações de reserva serializadas: latência alta sob carga | Alta (desempenho) | Corrigido | Rodadas 05 a 07 (`resultado-03.md`) |
| DEF-009 — README descreve funcionalidades inexistentes | Média | Corrigido | Verificação manual; README e `docs/api.md` |
| DEF-010 — Sonar S8700: duração entre datas sem fuso | Média (MAJOR) | Corrigido; SonarCloud do PR #14 sem bugs | `RF10MovimentacoesMaterialTest` |

**Totais:** 10 defeitos documentados. Abertos: 0.

**Limite:** o registro cobre defeitos encontrados por testes, por mutação, por análise de código e pelas rodadas de carga. Não há contagem de defeitos de produção.

## 6. Riscos residuais

| Risco | Evidência | Impacto | Mitigação |
|---|---|---|---|
| Desempenho medido fora do ambiente de referência | Seção 4.6 | Limite pode mudar numa medição controlada | Repetir em ambiente de referência |
| Usuários e movimentações em memória | `docs/api.md`; README | Perda de dados ao reiniciar | Decisão do grupo, documentada |
| Interface não verificada visualmente | Seção 2.2 | Possível problema de layout em telas reais | Teste de conteúdo e end-to-end |
| Mutação de 2026-11-11 não repetida | Seção 4.3 | Regressão possível não detectada pela mutação | Testes de regressão e E2E cobrem os mesmos fluxos |
| Quality gate do SonarCloud | Seção 4.4 | Bugs S8700 podem permanecer se a correção não for reconhecida | Confirmação no PR |

## 7. Recomendação

**Apresentar como entrega**, com as ressalvas abaixo. O CI e o quality gate do SonarCloud passam no PR #14:

1. Login, autorização por perfil, catálogo, reservas, aprovação, movimentações, notificações, interface e end-to-end estão implementados e testados.
2. Dupla reserva e reserva em manutenção são protegidas e testadas sob concorrência.
3. O requisito de desempenho (p95 ≤ 500 ms) é atendido nas rodadas 05 a 07, em máquina sem ambiente de referência (seção 4.6).
4. Usuários e movimentações são mantidos em memória.

**Próximos passos recomendados:** repetir as três rodadas em ambiente de referência fixo e persistir usuários e movimentações.
