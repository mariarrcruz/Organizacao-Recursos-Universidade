# Pacote de evidências — v1.0.0-rc1

**Tag:** `v1.0.0-rc1` (anotada)
**Commit avaliado:** [`14d766a`](https://github.com/mariarrcruz/Organizacao-Recursos-Universidade/commit/14d766a)

Todas as evidências abaixo apontam para o commit `14d766a`. Os links para arquivos usam `blob/14d766a`, então o conteúdo mostrado é o da tag, não o da `main` atual.

## 1. Pipeline de integração contínua

| Evidência | Link |
|---|---|
| Execução do CI no commit da tag (sucesso, `Maven verify` e SonarCloud) | [run 43327161512](https://github.com/mariarrcruz/Organizacao-Recursos-Universidade/actions/runs/43327161512) |
| Workflow de CI | [`.github/workflows/ci.yml`](https://github.com/mariarrcruz/Organizacao-Recursos-Universidade/blob/14d766a/.github/workflows/ci.yml) |
| Workflow de carga (manual) | [`.github/workflows/carga.yml`](https://github.com/mariarrcruz/Organizacao-Recursos-Universidade/blob/14d766a/.github/workflows/carga.yml) |

## 2. Relatório do JaCoCo

- **Artefato do CI:** `jacoco-coverage-report`, anexado à [execução 43327161512](https://github.com/mariarrcruz/Organizacao-Recursos-Universidade/actions/runs/43327161512). O artefato expira em 7 dias após a execução.
- **Versão versionada:** [`docs/qualidade/jacoco.xml`](https://github.com/mariarrcruz/Organizacao-Recursos-Universidade/blob/14d766a/docs/qualidade/jacoco.xml). O código Java e o `pom.xml` são idênticos ao da tag.
- **Resultado:** linhas 92,5 % (meta ≥ 80 %), ramos 83,7 % (meta ≥ 70 %).

## 3. SonarCloud

- **Quality Gate:** [projeto no SonarCloud](https://sonarcloud.io/project/overview?id=mariarrcruz_Organizacao-Recursos-Universidade)
- **Exportação do estado (JSON):** [`docs/qualidade/sonarcloud.json`](https://github.com/mariarrcruz/Organizacao-Recursos-Universidade/blob/14d766a/docs/qualidade/sonarcloud.json), com `commit_analisado` igual a `14d766a`.
- **Resultado:** gate OK, 0 bugs, 0 vulnerabilidades, 1 code smell aberto (S8786).

## 4. Desempenho

- [`docs/performance/resultado-01.md`](https://github.com/mariarrcruz/Organizacao-Recursos-Universidade/blob/14d766a/docs/performance/resultado-01.md): primeira rodada, versão anterior, veredito INCONCLUSIVO.
- [`docs/performance/resultado-02.md`](https://github.com/mariarrcruz/Organizacao-Recursos-Universidade/blob/14d766a/docs/performance/resultado-02.md): rodadas 02 a 04, antes da correção (NÃO ATENDIDO).
- [`docs/performance/resultado-03.md`](https://github.com/mariarrcruz/Organizacao-Recursos-Universidade/blob/14d766a/docs/performance/resultado-03.md): rodadas 05 a 07, após a correção do DEF-008 (ATENDIDO, máquina local).
- [`docs/performance/github-carga-43322490773/`](https://github.com/mariarrcruz/Organizacao-Recursos-Universidade/blob/14d766a/docs/performance/github-carga-43322490773): carga no runner do GitHub, três rodadas, ATENDIDO. A execução foi disparada a partir da branch `fix/carga-jmeter-5.6.3`, cujo `carga.yml` está na tag.
- Critérios: [`docs/performance/rnf.md`](https://github.com/mariarrcruz/Organizacao-Recursos-Universidade/blob/14d766a/docs/performance/rnf.md).

## 5. Rastreabilidade

- [`docs/rtm.md`](https://github.com/mariarrcruz/Organizacao-Recursos-Universidade/blob/14d766a/docs/rtm.md): matriz requisito → teste → evidência → situação.

## 6. Registro de defeitos

- [`docs/defeitos/`](https://github.com/mariarrcruz/Organizacao-Recursos-Universidade/blob/14d766a/docs/defeitos): DEF-001 a DEF-010. Todos corrigidos. Os DEF-007 a DEF-010 foram tratados nesta versão.

## 7. Relatório final de qualidade

- [`docs/relatorio-qualidade.md`](https://github.com/mariarrcruz/Organizacao-Recursos-Universidade/blob/14d766a/docs/relatorio-qualidade.md)

## 8. Checklist de entrega

- [`docs/entrega/checklist.md`](https://github.com/mariarrcruz/Organizacao-Recursos-Universidade/blob/14d766a/docs/entrega/checklist.md): itens, situação e bloqueadores em aberto.

## 9. Histórico de mudanças

- [`CHANGELOG.md`](https://github.com/mariarrcruz/Organizacao-Recursos-Universidade/blob/14d766a/CHANGELOG.md)
