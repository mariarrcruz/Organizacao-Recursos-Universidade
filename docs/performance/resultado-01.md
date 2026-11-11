# Resultado 01 — Rodada JMeter de disponibilidade e criação de reserva

**Plano:** `perf/reservas.jmx` (versionado em `002c2bc`)
**Requisitos avaliados:** RNF-03-P1, RNF-04-P1, RNF-06-P1, RNF-06-P2 (`docs/performance/rnf.md`)
**Dados brutos:** `perf/resultados/rodada-01.jtl` (16.090 amostras, não versionado)
**Relatório HTML do JMeter:** `perf/relatorio-01/` (não versionado)
**Script de análise:** cálculo feito com Python 3 sobre o `.jtl`; o script não foi versionado (ver seção 8).

---

## 1. Decisão

**INCONCLUSIVO.**

Nesta rodada única, os quatro limites propostos foram cumpridos: p95 de consulta 326 ms, p95 de criação 335 ms (ambos ≤ 500 ms), zero erro real e zero resposta fora de {200, 201, 409}. Não declaro o requisito como **atendido**, pelos motivos abaixo, todos registrados no próprio `rnf.md`, seção 5, ou verificados nesta análise:

1. **Uma rodada só.** O `rnf.md` (seção 5) exige pelo menos três rodadas para o grupo medir a variação antes de fixar o limite. Com uma rodada não há dispersão para comparar.
2. **Ambiente não é de referência.** Aplicação, PostgreSQL (container Docker) e JMeter rodaram na mesma máquina de 16 threads, sem isolamento. O `rnf.md` pede um ambiente de referência fixo.
3. **Volume de dados desprezível.** O banco tinha 4 reservas no total e cada recurso de carga tinha uma. A premissa S7 de `docs/RTM.md` fala em 10.000 reservas. Com esse volume o resultado pode mudar (ver seção 6).
4. **A criação medida é quase toda recusa.** Só 1 de 3.987 POSTs foi aceito (201), e ele ocorreu nos primeiros 2 s, fora da janela de medição. O p95 de criação de 335 ms é, portanto, o p95 do caminho 409, não o da criação bem-sucedida.

---

## 2. Ambiente

| Item | Valor |
|---|---|
| Máquina | Notebook/desktop local, AMD Ryzen 7 5700U (8 núcleos, 16 threads), 15 GiB de RAM, Linux 6.8.0-138-generic |
| Aplicação | `organizacao-recursos` 0.1.0-SNAPSHOT, Spring Boot 3.4.1, `app.dev.tokens=true`, `localhost:8080` |
| Versão da aplicação (código) | Commit `002c2bc` (HEAD no fim da análise). O `src/` e o `pom.xml` são idênticos entre `c62ee0c` e `002c2bc` (diff vazio). O `002c2bc` altera apenas `perf/` e `docs/`. O JAR (`target/organizacao-recursos-0.1.0-SNAPSHOT.jar`) é de 15:19, após `c62ee0c` (15:13). |
| Java | OpenJDK 17.0.21 (`java -version`). Presumo que foi esta a JVM que executou a aplicação; não há log de inicialização para confirmar. |
| Banco | PostgreSQL 16.14 (`postgres:16-alpine`) em Docker, mesma máquina, porta 5432 |
| Persistência | `ArmazenamentoPostgres` (PostgreSQL), conforme `ConfiguracaoAplicacao` |
| Gerador de carga | Apache JMeter 5.6.3, modo CLI, `-n`, mesma máquina, heap máx. 1 GB |
| Janela da rodada | Início da carga 15:29:20 (epoch 1794421760085 ms) → fim 15:33:19. Duração total 239,7 s |
| Commit avaliado | `002c2bc` (código igual a `c62ee0c`) |

**Limitação de rastreio:** o `jmeter.log` da raiz do repositório registra uma tentativa de 15:20 que falhou ao ler o `.jmx` (erro de XML em comentário com `--`). A rodada de 15:29 que gerou o `.jtl` não tem log próprio salvo. Além disso, o banco tem um recurso `PERF-20261111153332` criado às 15:33:32, sem reserva, de uma execução posterior sem `.jtl`. Não sei se foi uma rodada completa.

---

## 3. Carga aplicada

Conforme `perf/reservas.jmx`:

| Parâmetro | Valor |
|---|---|
| Usuários virtuais | 50 (1 thread de preparação à parte) |
| Ramp-up | 60 s |
| Duração do grupo | 240 s (contando o ramp-up) |
| Janela de medição (rnf.md, seção 3) | Amostras iniciadas a partir de t0 + 60 s, até o fim da carga. Duração ≈ 179,7 s. 13.263 amostras |
| Temporização | Constant timer de 500 ms por iteração |
| Mistura | 75% `GET /reservas?recursoId=` (disponibilidade, token ADMINISTRADOR), 25% `POST /reservas` (disputa) |
| Recurso | Um só, `PERF-20261111152919`, criado no setup |
| Intervalo | Um só, 2026-11-20T10:00 a 11:00 (UTC) |
| Usuários | `usuario01`–`usuario50` (`perf/usuarios.csv`) |

Os logins (`Login solicitante` e `Login administrador`) ocorrem no início das threads e não entram na janela de medição.

---

## 4. Resultados

Percentis por nearest-rank sobre o tempo de resposta (`elapsed`) das amostras. A janela de medição é a do rnf.md (seção 3).

### 4.1 Janela de medição (pós ramp-up, ≈ 179,7 s)

| Operação | n | Vazão (req/s) | p50 (ms) | p95 (ms) | p99 (ms) | Máx (ms) | Média (ms) | Erro real | Respostas |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---|
| GET disponibilidade (`RNF-03-P1`) | 9.948 | 55,36 | 168 | **326** | 354 | 463 | 171,3 | **0 (0,00%)** | 200: 9.948 |
| POST criar reserva (`RNF-04-P1`) | 3.315 | 18,45 | 192 | **335** | 362 | 440 | 191,2 | **0 (0,00%)** | 409: 3.315 (201: 0) |
| Operações do RNF (total) | 13.263 | 73,81 | 177 | 329 | 357 | 463 | 176,3 | **0 (0,00%)** | 200: 9.948, 409: 3.315 |

Dentro da janela, o p95 por bloco de 60 s fica estável, sem tendência de degradação:

| Faixa (s após t0) | GET p95 (ms) | POST p95 (ms) |
|---|---:|---:|
| 0–60 (ramp-up) | 181 | 191 |
| 60–120 | 326 | 335 |
| 120–180 | 324 | 335 |
| 180–240 | 327 | 336 |

### 4.2 Execução inteira, incluindo ramp-up (referência)

| Operação | n | Vazão (req/s, sobre 239,7 s) | p50 | p95 | p99 | Máx | Erro real | Respostas |
|---|---:|---:|---:|---:|---:|---:|---:|---|
| GET disponibilidade | 12.001 | 50,07 | 140 | 321 | 352 | 463 | 0 | 200: 12.001 |
| POST criar reserva | 3.987 | 16,63 | 165 | 332 | 359 | 440 | 0 | 201: 1, 409: 3.986 |

Os p95 de 321 ms (GET) e 332 ms (POST) batem com `perf/relatorio-01/statistics.json` (pct2, sobre todas as amostras). Esse arquivo usa a convenção do JMeter (pct1 = p90, pct2 = p95, pct3 = p99).

### 4.3 Erro: esperado × real

| Classe | Quantidade | Tratamento |
|---|---:|---|
| 200 (GET) | 12.001 | Sucesso |
| 201 (POST) | 1 | Sucesso (criação aceita, t = 2,04 s, 83 ms) |
| **409 (POST)** | **3.986** | **Esperado** (RN-02 / RN-03): horário já ocupado. Conta no tempo de resposta e não na taxa de erro |
| 5xx, timeout, erro de conexão, resposta fora de {200, 201, 409} | **0** | **Erro real** |
| Falha de asserção | 0 | — |

Observação sobre o `.jtl`: o `JSR223 PostProcessor` do plano (`reservas.jmx`, linha 315) marca como `success=true` toda resposta 409. Por isso o `errorPct` do JMeter (0,00%) não distingue os 409. A taxa de erro real foi recalculada a partir dos códigos de resposta, e não do campo `success`.

---

## 5. Comparação com `docs/performance/rnf.md`

| ID | Critério proposto | Medido (janela) | Situação nesta rodada |
|---|---|---|---|
| RNF-03-P1 | p95 da consulta ≤ 500 ms | 326 ms | Cumprido (margem de 174 ms), 1 rodada |
| RNF-04-P1 | p95 da criação ≤ 500 ms | 335 ms (somente 409) | Cumprido, mas mede o caminho de recusa; sem amostra suficiente de criação bem-sucedida |
| RNF-06-P1 | Taxa de erro < 1% | 0,00% | Cumprido |
| RNF-06-P2 | Respostas fora de {201, 409} = 0 | 0 | Cumprido |

Os números de `rnf.md` (seção 6, item 1: "326 ms na consulta e 335 ms na criação") conferem com esta análise.

**Ponto a corrigir em `rnf.md`:** a seção 6, item 1, marca o limite como "**Decidido**", mas o cabeçalho do documento diz "PROPOSTA — para o grupo validar". Com uma rodada só, a decisão não pode ser tomada. Não alterei o arquivo; sugiro a equipe ajustar.

---

## 6. Hipótese de gargalo

**Hipótese:** o custo de cada operação de reserva está no caminho de persistência, e não na lógica de conflito nem no volume de dados. Há duas causas candidatas no código: (a) cada operação abre uma conexão JDBC nova, sem pool (`ArmazenamentoPostgres.conectar()`, `DriverManager.getConnection`); (b) `conflita` e `reservasDoRecurso` leem a tabela `reserva` inteira (`listarReservas()`, sem `WHERE`) e filtram em Java (`ServicoReservas`).

**Dados que sustentam a hipótese:**

- A operação de login, que não acessa a tabela de reservas, tem p50 de 3 ms e p95 de 4 ms (`Login solicitante`, n=50). A consulta de disponibilidade tem p50 de 168 ms. A diferença de cerca de 165 ms está no caminho de reserva.
- A consulta de disponibilidade não grava nada e devolve no máximo 1 reserva para o recurso, e ainda assim custa 168 ms de mediana. O tempo não vem do tamanho da resposta.
- POST (192 ms p50) e GET (168 ms p50) têm custos próximos, apesar de o POST tratar a recusa antes do INSERT. Isso sugere que o custo fixo por operação domina e que a contenção no recurso pesa pouco nesta carga.
- A vazão observada (73,81 req/s) bate com a previsão de sistema em ciclo fechado: N / (R + Z) = 50 / (0,176 + 0,5) ≈ 73,9. A vazão é ditada pelo tempo de pensar (500 ms) e não por saturação do servidor. Portanto esta rodada não mede a capacidade máxima.

**Limite da evidência:** os dados não separam (a) custo fixo por conexão, (b) leitura integral da tabela e (c) disputa pelo monitor `synchronized` de `ServicoReservas`. Os três estão no caminho. Também não há medição de CPU, do PostgreSQL ou de `pg_stat_activity`.

**Ponto sobre RN-03:** pelo código, a serialização das solicitações dentro da JVM vem do `synchronized` de `ServicoReservas.solicitar`. A restrição `reserva_sem_sobreposicao` (EXCLUDE, SQLSTATE 23P01) só seria exercitada se duas JVMs disputassem o mesmo recurso. Esta rodada não prova a barreira no banco.

---

## 7. Próximo experimento

**Pergunta:** a latência do GET e do POST cresce com o número de reservas no banco?

**Desenho:** repetir `perf/reservas.jmx` sem mudar nada, após semear o banco com 10.000 reservas em outros recursos (volume da premissa S7), com pelo menos três rodadas, cada uma com recurso novo.

**Leitura do resultado:**
- Se o p95 do GET subir de forma clara em relação aos 326 ms, a leitura integral de `reserva` (causa b) é o gargalo. A correção seria filtrar no banco por `recurso_id` e intervalo.
- Se o p95 ficar parecido, o custo é fixo por operação (causa a) ou contenção no JVM (causa c). O próximo passo seria medir com um pool de conexões e com 1 usuário, para separar as duas.

Antes da rodada, a equipe deve decidir o volume-alvo (S7 ou outro) e registrar a configuração do banco no relatório.

---

## 8. Reprodutibilidade

```
docker compose up -d
./mvnw -B -DskipTests package
java -jar target/organizacao-recursos-0.1.0-SNAPSHOT.jar --app.dev.tokens=true
jmeter -n -t perf/reservas.jmx -l perf/resultados/rodada-01.jtl -e -o perf/relatorio-01
```

Consultas de verificação no banco:

```sql
-- Sobreposições entre reservas ativas (deve ser 0)
SELECT count(*) FROM reserva a JOIN reserva b
  ON a.recurso_id = b.recurso_id AND a.id < b.id AND a.periodo && b.periodo
 AND a.estado IN ('SOLICITADA','APROVADA','EM_USO')
 AND b.estado IN ('SOLICITADA','APROVADA','EM_USO');
-- Reservas do recurso da rodada (esperado: 1)
SELECT count(*) FROM reserva WHERE recurso_id = 'PERF-20261111152919'
 AND estado IN ('SOLICITADA','APROVADA','EM_USO');
```

---

## 9. Verificação no banco (PostgreSQL 16.14)

| Verificação | Resultado |
|---|---|
| Pares de reservas ativas sobrepostas, todos os recursos | **0** |
| Reservas ativas do recurso `PERF-20261111152919` (intervalo disputado 2026-11-20 10:00–11:00 UTC) | **1** (estado APROVADA, solicitante `usuario01`) |
| Restrição `reserva_sem_sobreposicao` (EXCLUDE USING gist) | Presente |
| Correspondência com o `.jtl` | 1 POST 201 no `.jtl` ↔ 1 reserva no banco |

Confirmado: no máximo uma reserva aceita por recurso e intervalo disputado, nesta rodada. Como a verificação é posterior à rodada, ela não prova que a restrição nunca foi violada em outros momentos.

---

## 10. Limitações

- Uma rodada, sem repetição. Não há variação entre rodadas.
- Ambiente local compartilhado (aplicação, banco e JMeter na mesma máquina).
- Volume de dados de 4 reservas, bem abaixo da premissa S7.
- A criação medida é quase toda recusa (409). Não há amostra suficiente para caracterizar a criação bem-sucedida.
- Consulta de disponibilidade com token de ADMINISTRADOR, como previsto em `rnf.md` seção 4. Solicitante não tem acesso a essa consulta.
- A rodada não foi repetida com o mesmo commit nem o log da execução de 15:29 foi salvo.
- Não há medição de CPU, memória, conexões ou `pg_stat_activity`.
- Os resultados se referem apenas a esta máquina e a esta configuração. **Nada aqui se aplica à produção.**
