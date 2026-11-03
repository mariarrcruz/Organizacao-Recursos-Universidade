# ADR-006: Notificações Simuladas por Logs Locais

**Status:** Aprovado

**Data:** 2026-11-03

---

## Contexto

RNF-16 exige notificações simuladas sem dependências externas (sem email, SMS, Slack obrigatório). Decisão: logs estruturados vs. tabela de notificações vs. message broker.

---

## Decisão

Implementar **notificações por logs estruturados em JSON**, com eventos críticos registrados via SLF4J.

### Eventos Mínimos
- `RESERVATION_REQUESTED`: Criação de solicitação
- `RESERVATION_APPROVED`: Aprovação
- `RESERVATION_REJECTED`: Rejeição
- `RESERVATION_CANCELLED`: Cancelamento
- `RESERVATION_IN_USE`: Início da utilização
- `RESERVATION_COMPLETED`: Conclusão
- `MATERIAL_WITHDRAWAL`: Retirada de material
- `MATERIAL_RETURN`: Devolução de material

### Formato
```json
{
  "timestamp": "2026-11-03T14:30:45.123Z",
  "level": "INFO",
  "logger": "com.organizacaorecursos.notifications",
  "correlation_id": "req-2026-11-03-001",
  "event": "RESERVATION_APPROVED",
  "entity_id": "res-uuid-123",
  "actor_id": "user-uuid-456",
  "old_state": "SOLICITADA",
  "new_state": "APROVADA",
  "status": "SUCCESS"
}
```

---

## Regra Crítica

**Falha de logging NÃO deve reverter operação confirmada.**

```java
@Transactional
public void approveReservation(UUID id, UUID approverId) {
    // Operação de negócio
    reservation.setState(APPROVED);
    repo.save(reservation);
    
    // Notificação (fora de transação, best-effort)
    try {
        notificationService.logEvent("RESERVATION_APPROVED", ...);
    } catch (Exception e) {
        logger.error("Falha ao registrar notificação", e);
        // NÃO propaga; operação já confirmada
    }
}
```

---

## Forças Consideradas

### Ganhos
- **Zero dependências:** Sem Kafka, SQS, email server
- **Simplificidade:** Logs padrão; funciona imediatamente
- **Observabilidade:** Correlação via correlation_id
- **Sem requisito de sucesso:** Falha de logging não reverte operação

### Trade-offs
- **Sem persistência de notificações:** Apenas logs; não há tabela de status
- **Sem retry:** Se log falha, evento é perdido
- **Sem consumer externo:** Integração com email/SMS é externa (futuro)

---

## Alternativas Descartadas

1. **Message broker (Kafka, RabbitMQ):** Complexidade; deps externas
2. **Tabela de notificações:** Requer consumer separado; complexity
3. **Webhook externo:** Requer autenticação + retry; deps externas
4. **Nenhuma notificação:** Inadequado; usuários sem feedback

---

## Consequências

### Implementação
- `NotificationService` com método `logEvent(event, ...)`
- SLF4J configurado com padrão JSON (Jackson ou Logback)
- Correlação rastreada via `MDC.put("correlation_id", requestId)`
- Logs armazenados em `logs/app.log` (rotação diária)

### Observabilidade
- Admin pode tail logs: `tail -f logs/app.log | grep RESERVATION_APPROVED`
- Para produção: integrar com ELK ou CloudWatch
- Alertas críticos: procurar "FAILED" nos logs

### Testes
- Teste: verificar que evento foi logado
- Não bloqueia teste: logging é assíncrono

### Retenção
- Logs rotados diariamente
- Retenção: 7 dias (acadêmico); 90 dias (produção)
- Compressão após 7 dias

---

## Riscos

| Risco | Probabilidade | Impacto | Mitigação |
|-------|---------------|--------|-----------|
| Log falha, evento perdido | Baixa | Médio | Aceitar; não bloqueia operação |
| Logs crescem muito | Alta | Médio | Rotação automática + compressão |
| Correlação_id perdida | Baixa | Médio | MDC gerenciado por interceptor |
| Sem notificação em tempo real | Alta | Baixo | Aceitar para acadêmico; futuro: email |

---

## Requisitos Relacionados

- RNF-16: Notificações simuladas sem deps externas
- RNF-15: Observabilidade + logs estruturados
- RF-12: Relatórios incluem eventos de auditoria

---

## Condição de Revisão

Esta decisão será revisada se:
1. Produção exigir notificação em tempo real (implementar webhook + consumer)
2. Conformidade exigir persistent audit trail (mover logs para database)
3. Escala exigir streaming (Kafka + consumer separado)

---

## Referências

- [arquitetura.md § 9.2](../docs/arquitetura.md#92-notificações-simuladas)
- [RFC - RNF-16](../rfc/requisitos-nao-funcionais.md#rnf-16--notificações-simuladas-para-observabilidade)
