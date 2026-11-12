# Resultado 02 — Três rodadas JMeter com a versão atual (Java 21)

**Plano:** `perf/reservas.jmx`, com o login de administrador trocado de `/dev/tokens` para `/auth/login` (a decisão D4 impedia o token de administrador no plano anterior).
**Critérios:** `docs/performance/rnf.md` (RNF-03-P1, RNF-04-P1, RNF-06-P1, RNF-06-P2). Análise automatizada em `scripts/analisar_jtl.py`.
**Ambiente:** mesma máquina para aplicação, PostgreSQL 16 (container) e JMeter. Java 21.0.13 (Temurin), Spring Boot 3.2.5.
**Dados brutos:** `perf/resultados/rodada-0{2,3,4}.jtl` (não versionados, conforme `.gitignore`).

---

## 1. Decisão

**NÃO ATENDIDO.** As três rodadas da versão atual ficaram acima do limite de p95 de 500 ms, nas duas operações. A taxa de erro ficou em 0 %, e não houve resposta fora de {201, 409}.

| Rodada | Consulta `GET /reservas?recursoId=` (p95) | Criação `POST /reservas` (p95) | Erro | Veredito |
|---|---|---|---|---|
| 01 (versão anterior, JDK 17, plano com `/dev/tokens`) | 326 ms | 335 ms | 0 % | INCONCLUSIVO, conforme `resultado-01.md` |
| 02 | 870 ms | 906 ms | 0 % | NÃO ATENDIDO |
| 03 | 880 ms | 909 ms | 0 % | NÃO ATENDIDO |
| 04 | 950 ms | 995 ms | 0 % | NÃO ATENDIDO |

As rodadas 02 a 04 são consistentes entre si: a diferença de p95 é de cerca de 10 %. A latência fica estável depois do ramp-up (em torno de 870 ms do segundo 60 até o fim, na rodada 03). Isso indica um custo constante por requisição, não um vazamento que cresce com o tempo.

## 2. Diagnóstico

O resultado não vem da ferramenta de carga. Três fatos sustentam isso:

1. **Teste sem JMeter.** Com 500 requisições de `GET /reservas?recursoId=` e concorrência 50, feitas com `curl`, o servidor respondeu com p50 de 1651 ms e p95 de 3436 ms. Ou seja, a aplicação já é mais lenta que o JMeter mediu.
2. **Thread dump durante carga.** Das threads HTTP, 76 estavam bloqueadas em `ServicoReservas` (monitor de `synchronized`). A thread que segurava o lock estava dentro de `solicitar → verificarDisponibilidade → registrarConflito`, abrindo conexão JDBC com `DriverManager.getConnection`.
3. **Causa estrutural.** `ServicoReservas` tem métodos `synchronized` que fazem I/O de banco dentro do lock. `ArmazenamentoPostgres` abre uma conexão nova a cada operação, sem pool. Por isso todas as operações de reserva e de disponibilidade passam em fila, uma de cada vez.

Não isolei por que a mesma consulta leva ~450 ms em chamadas seriais, sem concorrência. `GET /recursos/{id}`, que também abre conexão, leva ~14 ms. A diferença pode estar no caminho de `listarReservas`, mas não confirmei.

**O que não explica a diferença para a rodada 01:** a troca do login do plano (o login é feito uma única vez por usuário virtual, fora do laço) e o aumento de reservas no banco (de 4 para 9 reservas).

**Fatores de ambiente registrados.** Durante as rodadas, a máquina tinha duas IDEs Java ativas (IntelliJ e o servidor Java do VS Code), consumindo CPU e cerca de 33 % da memória. O `rnf.md` exige ambiente de referência fixo. Essas rodadas não atendem a essa exigência, então não servem para fixar o limite. O diagnóstico acima, porém, não depende dessas IDEs: o bloqueio no monitor aparece no thread dump.

## 3. O que falta para atender ao critério

O `rnf.md` pede pelo menos três rodadas em ambiente de referência, e as três rodadas já foram feitas nesta máquina. Para tratar o requisito como atendido, é preciso antes:

- **Tirar o I/O de dentro do lock** do `ServicoReservas`. A garantia de dupla reserva já está no banco (constraint `EXCLUDE` e advisory lock em `ArmazenamentoPostgres`), então o `synchronized` em volta de I/O é redundante no caminho Postgres.
- **Usar pool de conexões** (por exemplo, HikariCP), em vez de uma conexão por operação.
- **Repetir as três rodadas** com a máquina fechada para as IDEs.

Essas mudanças alteram o código de produção e foram deixadas de fora desta rodada de medição, para que o resultado registrado corresponda ao código atual.
