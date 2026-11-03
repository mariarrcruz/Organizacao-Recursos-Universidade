# ADR-005: Auditoria Append-Only Transacional

**Status:** Aprovado (Bloqueador: Teste Rollback H4)

**Data:** 2026-11-03

---

## Contexto

RNF-12 exige rastreabilidade completa e imutável de todas as alterações críticas (transições de estado, aprovações, retiradas). Decisão: auditoria centralizada vs. distribuída; imutabilidade de aplicação vs. criptografia de banco.

---

## Decisão

Implementar **auditoria append-only** em tabela `AuditEvent`:

### Modelo
```sql
CREATE TABLE audit_event (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  event_date TIMESTAMP UTC NOT NULL,
  actor_id UUID NOT NULL,
  action VARCHAR(64) NOT NULL,
  entity_type VARCHAR(64) NOT NULL,
  entity_id UUID NOT NULL,
  old_value VARCHAR(1000),
  new_value VARCHAR(1000),
  result VARCHAR(32),
  justification VARCHAR(500),
  correlation_id VARCHAR(36) -- rastreamento de requisição
);
```

### Regras
1. **Apenas INSERT:** Nenhuma operação UPDATE ou DELETE comum
2. **Transação atômica:** AuditEvent criado na mesma transação da alteração
3. **Se transação aborta:** Ambos (entidade + evento) revertidos; nenhum orfão
4. **Sem retenção purge:** Política de retenção é responsabilidade admin

---

## Fluxo (Exemplo: Aprovação de Reserva)

```java
@Transactional
public void approveReservation(UUID reservationId, UUID approverId) {
    // 1. Modificar estado
    Reservation reservation = repo.findById(reservationId);
    String oldState = reservation.getState();
    reservation.setState(APPROVED);
    
    // 2. Persiste entidade + auditoria (mesma transação)
    repo.save(reservation);
    auditService.recordEvent(
        action = "RESERVATION_APPROVED",
        entity = "Reservation",
        entityId = reservationId,
        oldValue = oldState,
        newValue = "APPROVED",
        actor = approverId
    );
    
    // 3. Se erro dentro da transação, ROLLBACK desfaz ambos
}
```

---

## Forças Consideradas

### Ganhos
- **Integridade referencial:** Sem eventos orfãos
- **Rastreabilidade completa:** Histórico imutável para auditoria
- **Conformidade legal:** Suporta conformidade LGPD (acesso negado a edição)
- **Debugging facilitado:** Reconstrução de histórico de estados

### Trade-offs
- **Crescimento indefinido:** Tabela cresce sem purge
- **Sem edição:** Impossível corrigir eventos errados (apenas admin override raro)
- **Imutabilidade de aplicação:** Não é criptografia; alguém com acesso ao banco pode editar

---

## Alternativas Descartadas

1. **Soft delete:** Apenas marca como deletado; não impede edição
2. **Event sourcing:** Toda operação é evento; complexo demais para acadêmico
3. **Audit trail distribuída:** Cada serviço registra sua auditoria; hard to consolidate
4. **Apenas logs:** Não persistidos no banco; perdem-se ao reiniciar

---

## Consequências

### Implementação
- `AuditService` com método `recordEvent(...)` 
- Controller injeta `AuditService` em operações críticas (aprovação, retirada, cancelamento)
- `@Transactional` garante atomicidade
- Teste: rollback verifica ambos reverstos

### Consultas de Auditoria
- Endpoint `GET /audit?entityId=<id>` retorna histórico
- Apenas Admin + Responsável podem acessar (autorização)
- Resultado: lista ordenada por `event_date DESC`

### Performance
- INSERT em `AuditEvent` é rápido
- Query de histórico pode ser lento se milhões de eventos (index em entity_id, event_date)
- Relatório de auditoria (RF-12) consulta essa tabela

### Política de Retenção
- Retenção permanente para acadêmico (simplifica)
- Para produção: retenção 7 anos (conformidade legal) + archive

---

## Riscos

| Risco | Probabilidade | Impacto | Mitigação |
|-------|---------------|--------|-----------|
| Evento órfão se transação falha | Baixa | Alto | Teste H4: verify rollback |
| Tabela cresce sem limites | Alta | Médio | Aceitar para acadêmico; documentar política |
| Admin edita auditoria manualmente | Baixa | Crítico | Procedimento manual com log separado |
| Consulta de auditoria é lenta | Média | Médio | Índices em (entity_id, event_date) |
| Aplicação tenta UPDATE auditoria | Média | Alto | Implementar restrição em código (sem DELETE) |

**Bloqueador:** H4 — teste de rollback: se transação aborta, ambos devem reverter.

---

## Requisitos Relacionados

- RF-10: Retirada + devolução com registro
- RF-11: Auditoria com alterações + usuário + data
- RNF-12: Rastreabilidade de transições de estado
- RNF-15: Observabilidade e logs estruturados

---

## Condição de Revisão

Esta decisão será revisada se:
1. Conformidade exigir criptografia de auditoria (adicionar coluna `checksum`)
2. Purge automático necessário (implementar arquivo + retention policy)
3. Escalabilidade exigir particionamento de tabela (por data)

---

## Referências

- [arquitetura.md § 9.1](../docs/arquitetura.md#91-auditoria)
- [ATAM § Cenário C-03](../docs/ATAM-analise.md#cenário-c-03-auditoria-imutável-de-transição)
- [RFC - RNF-12](../rfc/requisitos-nao-funcionais.md#rnf-12--auditoria-rastreabilidade-e-histórico)
