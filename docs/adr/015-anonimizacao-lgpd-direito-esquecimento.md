# ADR-015: Anonimização LGPD — Direito ao Esquecimento

**Status:** Aprovado (Bloqueador: Teste LGPD H8)

**Data:** 2026-11-03

---

## Contexto

Lei 13.709/2018 (LGPD) exige "direito ao esquecimento": usuário pode solicitar exclusão de dados pessoais. Decisão: como implementar sem quebrar auditoria.

---

## Decisão

Implementar **anonimização de dados pessoais com auditoria de ação**:

### Dados Pessoais Identificados

- `user.name` (nome do usuário)
- `user.email` (email)
- `audit_event.actor_id` (quem fez ação)
- `fact_reservation_usage.requestor_id` (em analytics)
- `dim_user.user_id` (dimensão)

### Procedimento de Anonimização

**Operação:** `ANONYMIZE_USER(@user_id)`

```sql
-- 1. Marcar usuário como anonimizado
UPDATE user 
SET name = NULL, email = NULL, is_anonymized = TRUE 
WHERE id = @user_id;

-- 2. Remover ator de eventos auditoria
UPDATE audit_event 
SET actor_id = NULL 
WHERE actor_id = @user_id;

-- 3. Remover solicitante de reservas
UPDATE reservation 
SET requestor_id = NULL 
WHERE requestor_id = @user_id;

-- 4. Remover de analytics
UPDATE fact_reservation_usage 
SET requestor_id = NULL 
WHERE requestor_id = @user_id;

UPDATE dim_user 
SET is_anonymized = TRUE 
WHERE user_id = @user_id;

-- 5. Registrar ação em auditoria separada
INSERT INTO audit_event (
  action, entity_type, entity_id, 
  actor_id, old_value, new_value, result
) VALUES (
  'ANONYMIZE_USER', 'user', @user_id, 
  <admin_id>, @user_id, NULL, 'SUCCESS'
);
```

### Transação Atômica
- Todos os UPDATEs na mesma transação
- Se falhar, ROLLBACK desfaz tudo
- Registro de auditoria confirmado juntos

---

## Forças Consideradas

### Ganhos
- **Conformidade legal:** Lei 13.709/2018 atendida
- **Rastreabilidade:** Ação de anonimização é auditada
- **Reversibilidade:** Ação está no histórico (se necessário investigar)

### Trade-offs
- **Perda de contexto:** Eventos antigos mostram `actor_id = NULL` (sem saber quem fez)
- **Identificação indireta:** IP log ou timestamps podem ainda identificar (fora do escopo LGPD)
- **Replicação em analytics:** Se anonimizar OLTP, deve sincronizar analytics

---

## Alternativas Descartadas

1. **Hard delete:** Impossível rastrear ação (viola auditoria)
2. **Criptografia:** Complexo; LGPD exige "impossibilidade de identificação" (decriptação é possível)
3. **Negar direito:** Ilegal

---

## Consequências

### Acesso

**Quem pode solicitar anonimização:**
- Próprio usuário (via formulário)
- Admin (via interface admin)

**Quem pode executar:**
- Admin apenas
- Ação registrada com admin_id

### Verificação de Completude

**Teste bloqueador H8:**
1. Anonimizar `user_id = X`
2. Verificar:
   - `user.name = NULL` ✓
   - `user.email = NULL` ✓
   - `audit_event.actor_id` onde era X → NULL ✓
   - `reservation.requestor_id` onde era X → NULL ✓
   - `fact_reservation_usage.requestor_id` onde era X → NULL ✓
   - `audit_event` registra ANONYMIZE_USER ✓
3. Query: Buscar todas refer references de user_id fora desses resultados → deve ser 0

### Implementação

```java
@Transactional
public void anonymizeUser(UUID userId, UUID adminId) {
    logger.info("Iniciando anonimização do usuário {}", userId);
    
    // 1. Executar anonimização em todas as tabelas
    userRepo.anonymize(userId);
    auditRepo.removeActor(userId);
    reservationRepo.removeRequestor(userId);
    analyticsRepo.removeRequestor(userId);
    
    // 2. Registrar ação (mesma transação)
    auditService.recordEvent(
        action = "ANONYMIZE_USER",
        entity = "user",
        entityId = userId,
        actor = adminId,
        result = "SUCCESS"
    );
    
    logger.info("Anonimização concluída para usuário {}", userId);
}
```

### Conformidade

- **Lei 13.709/2018:** Artigo 18 (direito ao esquecimento)
- **Não cobre:** Logs de sistema, backups (fora do escopo aplicação)
- **Responsabilidade:** Documentar que anonimização foi executada

---

## Riscos

| Risco | Probabilidade | Impacto | Mitigação |
|-------|---------------|--------|-----------|
| Referência perdida em user_id | Média | Alto | Teste bloqueador H8; busca exhaustiva |
| Soft-delete incompleto | Média | Alto | Revisão SQL; verificar todas tabelas |
| Analytics desatualizado | Média | Médio | Anonimização bloqueia pipeline até sincronizar |
| Backup contém PII | Alta | Alto | Política de backup + retenção (fora do escopo) |
| Conformidade incompleta | Baixa | Crítico | Auditoria de conformidade externa |

**Bloqueador crítico:** H8 — teste anonimização: nenhuma referência a user_id persiste.

---

## Requisitos Relacionados

- Lei Geral de Proteção de Dados (Lei 13.709/2018)
- **Sem RF/RNF:** Conformidade legal obrigatória

---

## Condição de Revisão

Esta decisão será revisada se:
1. Conformidade exigir criptografia reversível (escopo expandir)
2. Analytics exigir histórico anonimizado (versioning)
3. Backup/disaster recovery exigir recuperação de PII

---

## Referências

- [arquitetura.md § 15A.6](../docs/arquitetura.md#15a6-conformidade-lgpd-ad-15)
- [ATAM § Cenário C-13](../docs/ATAM-analise.md#cenário-c-13-vazamento-de-dados-pessoais-lgpd)
- [Lei 13.709/2018 - LGPD](https://www.gov.br/cidadania/pt-br/acesso-a-informacao/lgpd)
