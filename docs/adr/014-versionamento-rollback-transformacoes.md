# ADR-014: Versionamento de Transformações + Rollback Automático

**Status:** Aprovado (Futuro: Implementar se evoluir transformações)

**Data:** 2026-11-03

---

## Contexto

Pipeline batch (AD-10) executa transformações que podem mudar ao longo do tempo (refinar agregação, mudar lógica). Decisão: como controlar versões de transformações, como reverter se qualidade piora.

**Nota:** Sem requisito explícito. Proposto para MVP+1 se houver evolução rápida.

---

## Decisão

Implementar **versionamento de transformações com rollback automático**:

### Registry de Transformações

```sql
CREATE TABLE transformation_version (
  id BIGINT PRIMARY KEY,
  transformation_name VARCHAR(64),  -- 'reservation_usage_aggregation'
  version VARCHAR(10),              -- 'v1.0', 'v1.1'
  git_commit_sha VARCHAR(40),
  status VARCHAR(32),               -- ACTIVE, DEPRECATED, TESTING
  effective_date TIMESTAMP,
  quality_score_v_prev DECIMAL(3,2),
  quality_score_v_this DECIMAL(3,2),
  checksum VARCHAR(64),             -- SHA-256 da lógica
  rollback_if_quality_below DECIMAL(3,2)  -- 0.95 = se piora >5%
);
```

### Processo de Promoção

```
DEV (v1.0-dev)
    ↓ code review + testes
TEST (v1.0-rc1, 7 dias estável)
    ↓ quality score ≥0.95
PROD (v1.0, ACTIVE)
    ↓ monitoramento contínuo
    ├─ quality_score >= 0.95 × 0.9 = continue
    ├─ quality_score < 0.95 × 0.9 = ROLLBACK → v0.9
    └─ v1.0 → DEPRECATED
```

---

## Critérios de Promoção

| Critério | Passa | Bloqueador? |
|----------|-------|------------|
| Testes unitários | Pass | Sim |
| Cobertura | ≥80% linhas | Sim |
| 7 dias em TEST | Estável | Sim |
| Quality score | ≥0.95 | Sim |

---

## Rollback Automático

**Condição:** Se `quality_score` piora >10% vs. versão anterior

```java
if (previousVersion.qualityScore - currentVersion.qualityScore > 0.10) {
    // Rollback automático
    updateTransformationStatus(currentVersion, "DEPRECATED");
    updateTransformationStatus(previousVersion, "ACTIVE");
    alertAdmin("Rollback automático v1.1 → v1.0 (qualidade piora)");
}
```

---

## Forças Consideradas

### Ganhos
- **Auditoria completa:** Histórico de mudanças; rastreabilidade
- **Rollback defensivo:** Se qualidade piora, revert automático
- **Evolução segura:** Testes + monitoramento = mudanças confiantes

### Trade-offs
- **Complexidade:** Overhead de versionamento
- **Manual review:** Não pode ser completamente automatizado
- **Sem requisito:** MVP não precisa (usar v1.0 indefinidamente)

---

## Alternativas Descartadas

1. **Sem versionamento:** Simples; sem auditoria
2. **Versionar tudo (git history):** Já existe; redundante registrar no BD
3. **Blue-green deployment:** Overkill para batch

---

## Consequências

### MVP
- **Usar:** Simples v1.0; sem tabela `transformation_version`
- **Rationale:** Não há múltiplas transformações

### MVP+1
- **Se evoluir:** Implementar AD-14 quando houver mudanças frequentes
- **Exemplo:** Mudar fórmula de "carga horária" de `duracao_minutos / 60` para `duracao_minutos / 50` (padrão institucional)

### Observabilidade

```json
{
  "transformation": "reservation_usage_aggregation",
  "version": "v1.1",
  "status": "ROLLBACK_TRIGGERED",
  "reason": "quality_score degraded from 0.95 to 0.84 (-11%)",
  "action": "reverted to v1.0 (quality_score 0.95)",
  "alert_sent_to": "admin@example.com"
}
```

---

## Riscos

| Risco | Probabilidade | Impacto | Mitigação |
|-------|---------------|--------|-----------|
| Rollback automático muito agressivo | Média | Alto | Ajustar threshold (5% → 10%) |
| Versão antiga quebra produção | Baixa | Crítico | Testar fallback antes de aprovar |
| Falta de visibilidade em rollback | Média | Médio | Logs estruturados + alertas |

---

## Requisitos Relacionados

- **Sem RF associado:** Decisão arquitetural para evolução

---

## Condição de Revisão

Implementar quando:
1. Múltiplas transformações exigem rastreamento
2. Mudanças frequentes (>1 por mês)
3. ML alimentado por transformações (retraining requer versioning)

Descartar se:
1. Transformações são estáveis (sem mudanças)

---

## Referências

- [arquitetura.md § 15A.7](../docs/arquitetura.md#15a7-versionamento-e-promoção-ad-14)
- [ATAM § Cenário C-11](../docs/ATAM-analise.md#cenário-c-11-rollback-de-transformação--piora-de-qualidade--10)
