# DEF-010 — SonarCloud: duração calculada entre LocalDateTime sem fuso (S8700)

**Status:** corrigido (branch `feat/front-login-carga-java21`); aguarda confirmação no SonarCloud do PR
**Data da detecção:** 2026-11-04 (issue aberta no SonarCloud, regra `java:S8700`)
**Severidade:** MAJOR, não crítica; a meta do pedido é zero bugs
**Regras afetadas:** RN de duração mínima de reserva e duração de uso de material
**Testes que detectaram:** análise estática; teste de regressão existente `RF10MovimentacoesMaterialTest` (duração de uso) e `ServicoReservas` (duração mínima)

## Causa raiz

`Duration.between` recebia `LocalDateTime`, que não tem fuso. A regra alerta que o resultado depende de fuso.

## Correção

Conversão explícita para `Instant` em UTC (`ZoneOffset.UTC`) antes de `Duration.between`, em `ServicoReservas`, `ServicoMovimentacoes` e no teste correspondente. O comportamento é o mesmo, sem depender do fuso local.
