# ADR-013: Validação 3D: Completeness, Consistency, Uniqueness

**Status:** Aprovado (Bloqueador: Teste Validação H7)

**Data:** 2026-11-03

---

## Contexto

Pipeline batch (AD-10) consolida dados analíticos. Decisão: como garantir qualidade antes de publicar. Sem validação, dados ruins alimentam relatórios e futuro ML.

---

## Decisão

Implementar **validação 3D (3 dimensões)** em tabelas analíticas:

### Dimensão 1: Completeness (Completude)

**Regra:** Colunas essenciais NOT NULL em ≥99% das linhas.

| Tabela | Coluna | Critério |
|--------|--------|----------|
| `fact_reservation_usage` | resource_id, event_date, state | ≥99% NOT NULL |
| `fact_approval_metrics` | approver_id, approval_count | ≥99% NOT NULL |
| `dim_resource` | resource_id, resource_type | 100% NOT NULL |

**Ação se falhar:** WARN se 1-5% linhas incompletas; **FAIL** se >5%

### Dimensão 2: Consistency (Consistência)

**Regra:** Chaves estrangeiras referencia PKs válidas.

```sql
-- Validação
SELECT COUNT(*) FROM fact_reservation_usage
WHERE resource_id NOT IN (SELECT resource_id FROM dim_resource);
```

**Resultado esperado:** 0 (nenhum FK inválido)

**Ação se falhar:** **FAIL** imediatamente; não carregar dados

### Dimensão 3: Uniqueness (Unicidade)

**Regra:** Chaves primárias não duplicadas.

```sql
-- Validação
SELECT COUNT(DISTINCT pk) FROM fact_reservation_usage
HAVING COUNT(*) > COUNT(DISTINCT pk);
```

**Resultado esperado:** 0 (nenhuma duplicação)

**Ação se falhar:** **FAIL** imediatamente; investigar reprocessamento

---

## Modelo de Validação

### Pipeline de Validação

```
Extração → Transformação → VALIDAÇÃO → Carga
                              ↓
                         3D Checks
                              ↓
                        ✓ PASS → Carregar
                        ✗ FAIL → Rollback + Alerta
```

### Relatório de Validação

```json
{
  "execution_id": "batch-2026-11-03-02-00-00",
  "batch_date": "2026-11-03",
  "phase": "VALIDATION",
  "status": "SUCCESS",
  "quality_score": 0.98,
  "checks": {
    "completeness": {
      "fact_reservation_usage": {
        "total_rows": 333,
        "incomplete_rows": 2,
        "percentage": 99.4,
        "status": "PASS"
      }
    },
    "consistency": {
      "fact_reservation_usage_fk_resource": {
        "total_fk": 333,
        "invalid_fk": 0,
        "status": "PASS"
      }
    },
    "uniqueness": {
      "fact_reservation_usage_pk": {
        "total_pk": 333,
        "duplicate_pk": 0,
        "status": "PASS"
      }
    }
  }
}
```

---

## Forças Consideradas

### Ganhos
- **Confiabilidade:** Defeitos detectados antes de publicar
- **Rastreabilidade:** Relatório explícito de qualidade
- **Flexibilidade:** 3 dimensões independentes (pode falhar em uma, passar em outras)

### Trade-offs
- **Falsos positivos:** NULL válido em "justificativa" (precisa de exceção)
- **Tuning:** Critério de 99% é arbitrário (pode precisar ajuste)
- **Overhead:** Validação adiciona ~1-2 min ao pipeline

---

## Alternativas Descartadas

1. **Sem validação:** Rápido; inadequado (dados ruins)
2. **Validação em relatório:** Tarde demais; dados já publicados
3. **Validação complexa (ML):** Overkill para acadêmico

---

## Consequências

### Implementação

```java
public class DataValidator {
    
    public ValidationResult validateCompleteness(Table table, String... columns) {
        for (String col : columns) {
            long total = repo.count(table);
            long notNull = repo.countNotNull(table, col);
            double pct = (double) notNull / total;
            if (pct < 0.99) return FAIL;
        }
        return PASS;
    }
    
    public ValidationResult validateConsistency(
        String sourceTable, String fkColumn, 
        String refTable, String pkColumn) {
        long invalid = repo.countInvalidFK(sourceTable, fkColumn, refTable, pkColumn);
        if (invalid > 0) return FAIL;
        return PASS;
    }
    
    public ValidationResult validateUniqueness(
        String table, String pkColumn) {
        long duplicates = repo.countDuplicatePK(table, pkColumn);
        if (duplicates > 0) return FAIL;
        return PASS;
    }
}
```

### Testes

**Bloqueador H7:**
1. Criar dados com FK inválida
2. Validação deve detectar e FAIL
3. Confirmar ROLLBACK (nenhuma linha carregada)

### Exceptions (Aceitas)

- NULL em `justification` (coluna opcional)
- NULL em `end_date` (soft-delete de dimensão)

---

## Riscos

| Risco | Probabilidade | Impacto | Mitigação |
|-------|---------------|--------|-----------|
| Falso positivo (rejeita dados válidos) | Média | Alto | Exceções explícitas; manual review |
| Validação lenta (>5 min) | Baixa | Médio | Índices; query optimization |
| Critério 99% inadequado | Média | Médio | Ajustar conforme experiência |
| Correlação ID perdida | Baixa | Médio | Rastrear em relatório validação |

---

## Requisitos Relacionados

- **Sem RF associado:** Decisão arquitetural para confiabilidade

---

## Condição de Revisão

Esta decisão será revisada se:
1. Muitos falsos positivos (relaxar critério)
2. Validação ficar muito lenta (implementar sampling)
3. Análise de dados complexa requerer validação custom

---

## Referências

- [arquitetura.md § 15A.4](../docs/arquitetura.md#15a4-modelo-de-pipeline--propriedades-críticas)
- [ATAM § Cenário C-10](../docs/ATAM-analise.md#cenário-c-10-degradação-de-qualidade-de-dados)
