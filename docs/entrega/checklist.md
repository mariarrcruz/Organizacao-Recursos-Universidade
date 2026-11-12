# Checklist de entrega — avaliação externa

**Data da avaliação:** 2026-11-12. **Revisão após correções:** mesmo dia, na branch `fix/bloqueadores-entrega`.

Legenda: **existe** = o arquivo ou a evidência está no repositório. **atualizado** = conferido contra o código ou os resultados atuais.

---

## 1. Execução seguindo o README (avaliação inicial)

| Passo | Resultado inicial | Situação após a correção |
|---|---|---|
| `git clone` em diretório temporário | OK | — |
| Pré-requisito de JDK (README) | **FALHA**: README dizia "JDK 17 ou superior"; o build exige 21 | Corrigido: README pede JDK 21 |
| Build com JDK 21 | OK | — |
| `docker compose up -d` e `java -jar` com `--app.dev.tokens=true` | OK | README agora mostra também `APP_BOOTSTRAP_ADMIN_SENHA` |
| Exemplo de `POST /dev/tokens` | OK | — |
| `./mvnw -B verify` | OK: 643 testes, 0 falhas | Repetido após a correção: OK |

---

## 2. Entregáveis

| Item | Existe | Atualizado | Caminho |
|---|---|---|---|
| Repositório com histórico de trabalho | Sim | Sim | `git log` da `main` |
| Revisão por pares | Não | — | PRs #6 a #16 sem revisão formal. **Depende de colegas.** |
| Aplicação executável | Sim | Sim | `target/organizacao-recursos-0.1.0-SNAPSHOT.jar`, README "Como executar" |
| Instruções de instalação | Sim | Sim | README, JDK 21 e variável de administrador |
| README | Sim | Sim | `README.md` |
| Documentação da API | Sim | Sim | `docs/api.md` e tabela do `README.md` |
| `docs/rtm.md` | **Não** (era `RTM.md`) | Sim | Renomeado para `docs/rtm.md`, referências atualizadas |
| Diagrama de contexto | Sim | Parcial | `docs/arquitetura.md` §4.5.1 e `docs/rtm.md` §4.1 |
| Diagrama de componentes | Sim | Parcial | `docs/arquitetura.md` §4.5.2 e `docs/rtm.md` §4.3 |
| Diagramas de sequências críticas | Sim | Sim | `docs/arquitetura.md` §4.5.3 atualizado (refresh token sem rota HTTP); §4.5.4 a §4.5.6 e `docs/rtm.md` §4.4 a §4.7 |
| Plano de testes | Sim | Sim, com nota | `docs/testes/plano-tdd.md` tem nota de atualização; o inventário é anterior à implementação |
| Relatório de testes | Sim | Sim | `docs/relatorio-qualidade.md` §3 e §4.2 (643 testes) |
| Pipeline de CI | Sim | Sim | `.github/workflows/ci.yml` (PR #14: run `43321276037`, sucesso); `carga.yml` na `main` |
| Relatório do SonarCloud | Sim | Sim | `docs/qualidade/sonarcloud-pr14.json` (gate OK, 0 bugs, 0 vulnerabilidades) e `docs/qualidade/sonarcloud.json` (branch principal) |
| Relatório do JaCoCo | Sim | Sim | `docs/qualidade/jacoco.xml` (linhas 92,5 %, ramos 83,7 %) |
| Plano do teste de carga | Sim | Sim | `perf/reservas.jmx`, `scripts/analisar_jtl.py` |
| Critérios do teste de carga | Sim | Sim | `docs/performance/rnf.md` com status `MEDIDO` e pendência de ambiente de referência |
| Resultado do teste de carga | Sim | Sim | `docs/performance/resultado-01.md` (antigo), `resultado-02.md` (não atende, antes da correção), `resultado-03.md` (atende, máquina local); GitHub: `docs/performance/github-carga-43322490773/` (análises e logs, veredito ATENDIDO nas 3 rodadas) |
| Registro de defeitos | Sim | Sim | `docs/defeitos/` (DEF-001 a DEF-010, todos corrigidos) |
| Relatório final de qualidade | Sim | Sim | `docs/relatorio-qualidade.md` |

---

## 3. Bloqueadores, em ordem

Corrigidos nesta etapa:
1. ~~README pedia JDK 17~~ → README pede JDK 21.
2. ~~`docs/rtm.md` não existia~~ → arquivo renomeado para `docs/rtm.md`.
4. ~~Relatórios do SonarCloud e do JaCoCo não exportados~~ → `docs/qualidade/` com os dois.
5. ~~Carga do GitHub sem evidência baixável~~ → artefato baixado e versionado em `docs/performance/github-carga-43322490773/`; `rnf.md` atualizado.
6. ~~Plano de testes e mutação desatualizados~~ → notas de atualização nos dois documentos. As quebras de mutação **não foram repetidas**.
7. ~~Sequência de login com refresh token persistido~~ → corrigida na §4.5.3 do `arquitetura.md`.
8. ~~Administração sem variável de ambiente~~ → README explica a variável no comando de execução.

Pendentes, que não dependem só de mim:
3. **Revisão por pares.** Os PRs #6 a #16 não têm revisão formal. Isso precisa de colegas revisando e aprovando PRs futuros. Não consigo fazer isso sozinho, e não vou registrar revisões que não aconteceram.
5b. **Ambiente de referência.** A medição de referência (três rodadas em máquina fixa, `rnf.md` §5) ainda não foi feita. As medições atuais estão registradas com essa ressalva.
6b. **Mutação.** As sete quebras de `mutacao-manual.md` precisam ser repetidas sobre o código atual.
