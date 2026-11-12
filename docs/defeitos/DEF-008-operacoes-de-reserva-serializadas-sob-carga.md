# DEF-008 — Operações de reserva serializadas: latência alta sob carga

**Status:** corrigido (rodadas 05 a 07, `docs/performance/resultado-03.md`)
**Data da detecção:** 2026-11-12, rodadas JMeter 02, 03 e 04
**Severidade:** alta para o requisito de desempenho (RNF-03-P1 e RNF-04-P1, p95 ≤ 500 ms); sem impacto em integridade
**Regras afetadas:** RNF-03, RNF-04, RNF-06 (desempenho sob carga)
**Testes que detectaram:** `perf/reservas.jmx` com `scripts/analisar_jtl.py` (p95 de 870 a 995 ms nas três rodadas, erro 0 %)

## Passos para reproduzir

1. `docker compose up -d`; subir a aplicação com JDK 21.
2. `jmeter -n -t perf/reservas.jmx -Jadmin.senha=<senha>` (três rodadas).
3. `python3 scripts/analisar_jtl.py perf/resultados/rodada-02.jtl`.

**Esperado:** p95 ≤ 500 ms nas duas operações.
**Obtido:** p95 entre 870 e 995 ms; teste direto com `curl` (50 simultâneas, sem JMeter) deu p95 de 3,4 s.

## Evidência

- Thread dump durante carga: 76 threads HTTP bloqueadas no monitor de `ServicoReservas`; a thread dona do lock executava `solicitar → verificarDisponibilidade → registrarConflito`, com `DriverManager.getConnection` dentro do lock.

## Causa raiz

1. Métodos de `ServicoReservas` são `synchronized` e fazem I/O de banco dentro do lock, serializando todas as operações de reserva e de disponibilidade.
2. `ArmazenamentoPostgres` abre uma conexão JDBC nova a cada operação, sem pool.

A integridade não depende desse lock no caminho PostgreSQL (há constraint `EXCLUDE` e advisory lock por recurso), o que torna a serialização desnecessária nesse caminho.

## Correção

1. Removido o monitor Java (`synchronized`) dos métodos de `ServicoReservas`; a atomicidade fica com o armazenamento (transação, lock consultivo e `EXCLUDE` no PostgreSQL).
2. `ArmazenamentoMemoria.inserirReserva` passou a recusar sobreposição, cumprindo o contrato da porta.
3. Pool HikariCP compartilhado por URL e usuário em `ArmazenamentoPostgres`.

Resultado: consulta p95 de 3 ms e criação p95 de 38 a 39 ms, nas rodadas 05 a 07. Testes de concorrência e de regra continuam passando. Ver `docs/performance/resultado-03.md`, com a ressalva de que a medição não foi feita em ambiente de referência.
