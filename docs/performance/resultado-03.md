# Resultado 03 — Três rodadas JMeter após a correção do DEF-008

**Plano:** `perf/reservas.jmx` (mesmo plano das rodadas 02 a 04: administrador por `/auth/login`).
**Critérios:** `docs/performance/rnf.md` (RNF-03-P1, RNF-04-P1, RNF-06-P1, RNF-06-P2). Análise em `scripts/analisar_jtl.py`, com os primeiros 60 s (ramp-up) descartados.
**Código medido:** commit da branch `feat/front-login-carga-java21` com a correção do DEF-008: remoção do `synchronized` do `ServicoReservas`, pool de conexões HikariCP compartilhado por URL e usuário, e verificação de sobreposição dentro de `ArmazenamentoMemoria.inserirReserva` (contrato da porta `Armazenamento`).
**Ambiente:** mesma máquina para aplicação, PostgreSQL 16 (container) e JMeter, Java 21.0.13. Duas IDEs Java (IntelliJ e VS Code) continuaram abertas durante as rodadas. **Não é o ambiente de referência do `rnf.md` §5**, então estes números valem como medição nesta máquina, não como limite fixado.
**Dados brutos:** `perf/resultados/rodada-0{5,6,7}.jtl` (não versionados, conforme `.gitignore`).

---

## 1. Decisão

**ATENDIDO** nas três rodadas, para os critérios de latência e de erro.

| Rodada | Consulta `GET /reservas?recursoId=` (p95) | Criação `POST /reservas` (p95) | Erro real | Respostas fora de {201, 409} | Veredito |
|---|---:|---:|---:|---:|---|
| 05 | 3 ms | 39 ms | 0 % | 0 | ATENDIDO |
| 06 | 3 ms | 38 ms | 0 % | 0 | ATENDIDO |
| 07 | 3 ms | 39 ms | 0 % | 0 | ATENDIDO |

Limite: p95 ≤ 500 ms e erro < 1 %. As três rodadas ficam bem abaixo dos dois limites.

**Comparação com as rodadas 02 a 04 (mesma máquina, código anterior):** consulta 870 a 950 ms e criação 906 a 995 ms, com erro zero. A correção reduziu o p95 em cerca de 300 vezes na consulta e em cerca de 25 vezes na criação.

## 2. Causa corrigida (DEF-008)

- `ServicoReservas` tinha métodos `synchronized` que faziam I/O de banco dentro do monitor Java. Isso serializava todas as operações de reserva e de disponibilidade.
- `ArmazenamentoPostgres` abria uma conexão JDBC nova a cada operação.

Correção:
1. O monitor Java foi removido do serviço. A atomicidade entre verificar disponibilidade e gravar fica com o armazenamento: no PostgreSQL, por transação, lock consultivo por recurso e a restrição `EXCLUDE` (já cobertas pelos testes de concorrência de DEF-001 e DEF-002, que continuam passando).
2. `ArmazenamentoMemoria.inserirReserva` passou a recusar com 409 sobreposição com reserva ativa ou manutenção, como o contrato da porta já exigia. Os testes de concorrência em memória (`RN03AtomicidadeConcorrenciaTest`) continuam passando.
3. Pool HikariCP (`maximumPoolSize=20`), compartilhado por URL e usuário.

## 3. Verificações de regressão

- `./mvnw -B verify` com JDK 21: 643 testes, 0 falhas, cobertura dentro das metas.
- Testes de concorrência (`ConcorrenciaReservaPostgresTest`, `ConcorrenciaReservaHttpPostgresTest`, `ConcorrenciaManutencaoReservaPostgresTest`) e de RN-02, RN-03 e RN-04: passam.

## 4. Limites desta medição

- Não é a condição de referência do `rnf.md` §5. A máquina tinha duas IDEs Java abertas e foi a mesma para aplicação, banco e JMeter.
- A carga do plano é moderada (50 usuários virtuais, 240 s). Não foram medidos volumes de produção (premissa S7, 10.000 reservas).
- As rodadas 05 a 07 foram feitas no mesmo código; a variação entre elas é pequena (p95 da criação entre 38 e 39 ms).
