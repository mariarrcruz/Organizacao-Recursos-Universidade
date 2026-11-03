# ADR-010: Pipeline Batch Diário para Consolidação de Dados

**Status:** Aprovado (Extensão)

**Data:** 2026-11-03

---

## Contexto

RF-12 (relatórios) pode se beneficiar de dados históricos consolidados. Decisão: processar online vs. batch diário. Sem requisito explícito, mas habilita futuras capacidades de ML.

---

## Decisão

Implementar **pipeline batch diário** que consolida auditoria e transações em schema analítico:

### Agendamento
- **Horário:** 02:00 UTC (baixa demanda)
- **Frequência:** Diário
- **Duração esperada:** ~10 minutos
- **Lag máximo:** 24 horas em analytics

### 5 Fases

```
1. EXTRAÇÃO     → Ler audit_event + transações desde last watermark
2. TRANSFORMAÇÃO → Denormalizar em fact tables + dimensions
3. VALIDAÇÃO    → Verificar completeness, consistency, uniqueness
4. CARGA        → UPSERT em H2 analítico
5. CHECKPOINT   → Registrar novo watermark; marcar SUCCESS
```

### Resultado
- Schema `analytics_*` em H2 separado
- Fact tables: `fact_reservation_usage`, `fact_approval_metrics`, `fact_material_movement`
- Dimension tables: `dim_resource`, `dim_user`
- Controle: tabela `batch_checkpoint` para idempotência

---

## Forças Consideradas

### Ganhos
- **Relatórios otimizados:** Dados pré-agregados; sem impacto OLTP
- **Histórico persistido:** Análise de tendências (futuro)
- **Habilitador ML:** Pipeline fornece dados limpos para modelos
- **Sem requisito explícito:** Escolha arquitetural defensável

### Trade-offs
- **Lag 24h:** Relatórios não são real-time
- **Replicação diária:** Mantém dois bancos (complexidade)
- **Falha de pipeline:** Analytics fica desatualizado (até 48h se retry falha)

---

## Alternativas Descartadas

1. **Sem batch:** MVP simples; relatórios consultam OLTP
   - **Aceito para:** MVP (usar AD-07 + OLTP direto)
   - **Problema:** Se RNF-05 falhar, implementar AD-12 + AD-10

2. **Real-time analytics:** Streaming via Kafka
   - **Complexidade:** Deps externas; infra
   - **Inadequado:** Acadêmico

3. **Nightly full reload:** Reprocessar tudo diariamente
   - **Ineficiente:** Perda de dados se falha
   - **Descartado:** Watermark é melhor

---

## Consequências

### Implementação
- Scheduler Spring `@Scheduled(cron = "0 2 * * *")` dispara `BatchService.runPipeline()`
- 5 serviços: Extract, Transform, Validate, Load, Checkpoint
- Transação ACID na fase LOAD + CHECKPOINT (atomicidade garantida)
- Retry logic: se falhar, próximo dia retoma do mesmo watermark

### Modelo de Dados Analítico

```sql
-- Fato: Uso de recurso
CREATE TABLE fact_reservation_usage (
  id BIGINT PRIMARY KEY,
  execution_id VARCHAR(36),  -- Rastreamento batch
  event_date DATE,
  reservation_id UUID,
  resource_id UUID,
  resource_type VARCHAR(32),
  requestor_id UUID,
  duration_hours DECIMAL(5,2),
  state VARCHAR(32),
  row_count INT DEFAULT 1
);

-- Dimensão: Recursos
CREATE TABLE dim_resource (
  resource_id UUID PRIMARY KEY,
  type VARCHAR(32),
  description VARCHAR(255),
  location VARCHAR(128),
  is_restricted BOOLEAN,
  effective_date DATE
);
```

### Volume Esperado
- 10K reservas / 30 dias = 333 linhas/dia em `fact_reservation_usage`
- 30 linhas/dia em `fact_approval_metrics`
- 150 linhas/dia em `fact_material_movement`
- **Total:** ~750 linhas/dia; ~22.5K/mês; ~270K/ano (insignificante)

### Observabilidade
- Logs estruturados JSON em cada fase
- Alertas críticos se:
  - Duração >15 min (lentidão)
  - Erros de validação (dados inválidos)
  - Checkpoint lag >48h (pipeline travado)

---

## Riscos

| Risco | Probabilidade | Impacto | Mitigação |
|-------|---------------|--------|-----------|
| Pipeline falha, lag cresce | Média | Alto | Checkpoint transacional; alert >48h |
| Dados OLTP alterados entre runs | Baixa | Alto | Snapshot isolado (READ_COMMITTED) |
| Replicação desatualiza | Alta | Médio | Aceitar lag 24h; futuro: real-time |
| Falso positivo validação | Média | Médio | Tunar critérios; manual review |
| H2 analítico fica grande | Baixa | Baixo | Retenção 2 anos; archive após |

---

## Requisitos Relacionados

- RF-12: Relatórios gerenciais (sem requisito explícito para batch, mas habilita)
- **Sem RNF associado:** Decisão arquitetural pura

---

## Condição de Revisão

Esta decisão será revisada se:
1. Produção exigir real-time analytics (implementar Kafka + consumer)
2. ML adotado (confirmar pipeline como habilitador)
3. Volume crescer (considerar data warehouse cloud)

---

## Referências

- [arquitetura.md § 15A](../docs/arquitetura.md#15a-extensão-camada-de-dados-e-pipeline-batch)
- [ATAM § Cenário C-09](../docs/ATAM-analise.md#cenário-c-09-dados-novos--falha-parcial-do-pipeline-batch)
