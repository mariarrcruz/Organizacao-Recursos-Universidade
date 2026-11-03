# ADR-012: H2 Analítico Separado (OLAP Isolado)

**Status:** Aprovado (Futuro: Implementar se RNF-05 falhar)

**Data:** 2026-11-03

---

## Contexto

AD-07 permite consultar relatórios diretamente do OLTP. RNF-05 exige <5s. Decisão: se P95 >5s com JMeter, implementar schema analítico separado para desempenho garantido.

---

## Decisão

**MVP:** Consultar OLTP direto (AD-07)

**Se RNF-05 falhar:** Migrar para **H2 analítico separado**:

### Arquitetura

```
OLTP (H2)           Analytics (H2)
├─ reservations  →  ├─ fact_reservation_usage
├─ resources     →  ├─ fact_approval_metrics
├─ audit_event   →  ├─ fact_material_movement
└─ ...           →  ├─ dim_resource
                    └─ dim_user
                    
Extraído via:
└─ Pipeline batch diário (AD-10) com watermark (AD-11)
```

### Benefícios
- **Desempenho:** Índices otimizados para leitura; sem contention com OLTP
- **Esquema star:** Agregações rápidas (denormalizadas)
- **Isolamento:** Relatório pesado não afeta reservas em tempo real

---

## Forças Consideradas

### Ganhos
- **P95 < 5s garantido:** Índices compostos
- **Escalabilidade:** Dados históricos não crescem indefinidamente no OLTP
- **Conformidade RNF-05:** Design explícito para relatórios

### Trade-offs
- **Complexidade:** Dois bancos; replicação diária
- **Lag 24h:** Relatórios mostram dados de ontem
- **Custo futuro:** PostgreSQL + DW em produção

---

## Modelo de Dados Analítico

### Fact Tables

```sql
-- Uso de recurso
CREATE TABLE fact_reservation_usage (
  id BIGINT PRIMARY KEY,
  execution_id VARCHAR(36),
  event_date DATE,
  reservation_id UUID,
  resource_id UUID,
  resource_type VARCHAR(32),  -- SALA, PROFESSOR, MATERIAL
  requestor_id UUID,
  duration_hours DECIMAL(5,2),
  state VARCHAR(32),
  row_count INT
);

-- Métricas de aprovação
CREATE TABLE fact_approval_metrics (
  id BIGINT PRIMARY KEY,
  execution_id VARCHAR(36),
  event_date DATE,
  approver_id UUID,
  approval_count INT,
  rejection_count INT,
  avg_approval_time_minutes DECIMAL(8,2)
);

-- Movimentação de material
CREATE TABLE fact_material_movement (
  id BIGINT PRIMARY KEY,
  execution_id VARCHAR(36),
  event_date DATE,
  material_id UUID,
  withdrawal_count INT,
  return_count INT,
  damaged_items INT
);
```

### Dimension Tables

```sql
-- Dimensão: Recursos
CREATE TABLE dim_resource (
  resource_id UUID PRIMARY KEY,
  resource_type VARCHAR(32),
  description VARCHAR(255),
  location VARCHAR(128),
  is_restricted BOOLEAN,
  effective_date DATE,
  end_date DATE
);

-- Dimensão: Usuários (anonimizado se LGPD)
CREATE TABLE dim_user (
  user_id UUID PRIMARY KEY,
  role VARCHAR(32),
  effective_date DATE,
  is_anonymized BOOLEAN DEFAULT FALSE
);
```

### Índices Críticos

```sql
CREATE INDEX idx_fact_usage_date ON fact_reservation_usage(event_date, resource_type);
CREATE INDEX idx_fact_usage_resource ON fact_reservation_usage(resource_id, state);
CREATE INDEX idx_fact_approval_date ON fact_approval_metrics(event_date, approver_id);
```

---

## Processo de Migração (Se Necessário)

1. **Sprint N:** Detectar RNF-05 falha em JMeter
2. **Sprint N+1:** Implementar AD-12 (criar schema + índices)
3. **Sprint N+2:** Integrar pipeline (AD-10 popula analytics)
4. **Sprint N+3:** Redirecionar relatórios para analytics
5. **Validação:** P95 < 5s confirmado
6. **Cleanup:** Remover agregações do OLTP

---

## Alternativas

### Opção A: Cache (Redis)
- Ganho: Simples
- Perda: Deps externas; invalidação complexa

### Opção B: PostgreSQL com índices
- Ganho: Escalável
- Perda: Produção requer migração completa (reescrita)

### Opção C: Manter em OLTP
- Ganho: Simples
- Perda: RNF-05 pode falhar

---

## Riscos

| Risco | Probabilidade | Impacto | Mitigação |
|-------|---------------|--------|-----------|
| Analytics fica desatualizado | Média | Alto | Verificar checkpoint; alert se lag >48h |
| Inconsistência OLTP vs. Analytics | Média | Alto | Validação 3D (AD-13) antes de publicar |
| Índices não suficientes | Baixa | Médio | Profiling + tuning incrementa |
| Duas cópias crescem demais | Baixa | Baixo | Retenção + archive |

---

## Requisitos Relacionados

- RNF-05: Relatórios <5s (alvo 2.5s)
- RF-12: Relatórios gerenciais

---

## Condição de Revisão

Implementar quando:
1. RNF-05 falhar com JMeter (P95 >5s)
2. Produção migrar para PostgreSQL

Descartar se:
1. MVP passou em RNF-05
2. Relatórios não evoluem

---

## Referências

- [arquitetura.md § 15A.3B](../docs/arquitetura.md#15a3b-modelo-de-dados-analítico-fact-tables-e-dimensions)
- [ATAM § Cenário C-08](../docs/ATAM-analise.md#cenário-c-08-relatório-de-utilização--5s)
