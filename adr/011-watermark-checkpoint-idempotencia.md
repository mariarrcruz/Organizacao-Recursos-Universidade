# ADR-011: Watermark + Checkpoint Transacional para Idempotência

**Status:** Aprovado (Bloqueador: Teste Idempotência H6)

**Data:** 2026-11-03

---

## Contexto

Pipeline batch (AD-10) necessita recuperação de falhas parciais sem duplicação. Decisão: como rastrear progresso, como garantir reexecução não duplica dados.

---

## Decisão

Implementar **watermark + checkpoint transacional**:

### Watermark
- **Tipo:** Timestamp UTC (YYYY-MM-DD HH:MM:SS)
- **Significado:** Última data/hora processada com sucesso
- **Inicialização:** NULL ou 1900-01-01 (processa tudo na primeira vez)

### Checkpoint
- **Armazenamento:** Tabela `batch_checkpoint` (um registro por batch)
- **Atomicidade:** COMMIT checkpoint + COMMIT dados na mesma transação
- **Proteção:** Se carga falha, checkpoint anterior é preservado

### Extração
```sql
SELECT * FROM audit_event 
WHERE created_at > @last_watermark
  AND created_at <= @new_watermark
ORDER BY created_at ASC;
```

### Carga + Checkpoint (Mesma Transação)
```java
@Transactional
public void loadAndCheckpoint(List<Row> rows, Timestamp newWatermark) {
    // 1. Carregar dados (UPSERT para idempotência)
    for (Row row : rows) {
        analyticsRepo.upsert(row);  // INSERT OR UPDATE
    }
    
    // 2. Registrar novo checkpoint (mesma transação)
    checkpointRepo.save(
        new Checkpoint(
            batchName = "daily_pipeline",
            executionId = UUID.randomUUID(),
            newWatermark = newWatermark,
            status = "SUCCESS"
        )
    );
    
    // COMMIT ambos ou ROLLBACK ambos
}
```

---

## Idempotência Garantida

Se carga falha:
1. **Antes do COMMIT:** Nenhuma linha foi escrita; checkpoint anterior é preservado
2. **Reexecução:** Mesmo `new_watermark` extrai mesmas linhas (determinístico)
3. **UPSERT:** Chave `(execution_id, entity_id, event_date)` garante sem duplicação

**Propriedade:** Executar 2x com mesmos dados = resultado final idêntico

---

## Forças Consideradas

### Ganhos
- **Recuperação automática:** Próximo dia retoma do mesmo ponto
- **Idempotência 100%:** Sem duplicação mesmo com reexecução
- **Correlação:** `execution_id` permite rastreamento de tentativas

### Trade-offs
- **Dependência de NTP:** Relógios devem estar sincronizados (UTC)
- **Watermark é único ponto de falha:** Se corrompido, reprocessar ou perder dados
- **Histórico de tentativas:** Pode acumular múltiplos `execution_id` para mesmo período

---

## Alternativas Descartadas

1. **Full reload diário:** Reprocessar tudo
   - **Ineficiente:** Refaz computação
   - **Risco:** Se DB cresce, reprocessar fica lento

2. **Offset / sequence number:** Rastrear por ID incremental
   - **Problema:** Se há delete, gaps aparecem
   - **Inadequado:** Auditor_event é append-only, mas eventos podem ser soft-deleted

3. **Sem checkpoint:** Confiar em scheduler
   - **Risco:** Se scheduler falha 2x, lag cresce indefinidamente

---

## Consequências

### Tabela batch_checkpoint

```sql
CREATE TABLE batch_checkpoint (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  batch_name VARCHAR(64),
  execution_id VARCHAR(36) UNIQUE,
  last_processed_watermark TIMESTAMP,
  new_watermark TIMESTAMP,
  status VARCHAR(32),  -- RUNNING, SUCCESS, FAILURE
  error_message VARCHAR(2000),
  started_at TIMESTAMP,
  completed_at TIMESTAMP,
  rows_processed INT,
  duration_seconds INT,
  quality_score DECIMAL(3,2)  -- 0.0-1.0
);
```

### Monitoramento

**Alertas críticos:**
- Checkpoint lag >48h (pipeline não progride)
- Duração >15 min (pipeline lento)
- Qualidade score <0.95 (dados ruins)

### Testes

**Teste de idempotência (bloqueador H6):**
1. Executar pipeline uma vez; registrar resultado
2. Simular falha em carga (ex: delete alguns dados)
3. Reexecutar pipeline
4. Verificar: resultado final idêntico (nenhuma duplicação)

---

## Riscos

| Risco | Probabilidade | Impacto | Mitigação |
|-------|---------------|--------|-----------|
| Relógios dessincronizados (lag de NTP) | Baixa | Alto | NTP sync obrigatório; monitorar drift |
| Watermark corrompido | Muito Baixa | Crítico | Backup de checkpoint; reset manual se necessário |
| Múltiplos batches simultâneos | Baixa | Alto | Scheduler garante serial (cron não permite overlap) |
| Execução interrompida durante COMMIT | Muito Baixa | Crítico | Banco garante atomicidade; transação ou reverte |

**Bloqueador crítico:** H6 — teste idempotência com falha injected.

---

## Requisitos Relacionados

- **Sem RF associado:** Decisão arquitetural para confiabilidade

---

## Condição de Revisão

Esta decisão será revisada se:
1. Pipeline exigir sharding (múltiplos bancos)
2. Escalabilidade exigir processamento paralelo (considerar watermark per shard)
3. Volume muito alto (considerar incremental checkpoint por tabela)

---

## Referências

- [arquitetura.md § 15A.4](../docs/arquitetura.md#15a4-modelo-de-pipeline--propriedades-críticas)
- [ATAM § Cenário C-09](../docs/ATAM-analise.md#cenário-c-09-dados-novos--falha-parcial-do-pipeline-batch)
