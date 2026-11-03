# ADR-007: Relatórios com Filtros e Leitura Separada

**Status:** Aprovado (Futuro: AD-12 — H2 Analítico Separado)

**Data:** 2026-11-03

---

## Contexto

RF-12 exige relatórios gerenciais com múltiplos filtros (data, recurso, usuário, estado). Decisão: consultar OLTP direto vs. separar OLAP.

---

## Decisão

Implementar **relatórios com leitura separada do OLTP**, com suporte a filtros complexos:

### Filtros Suportados
- `start_date`, `end_date` (intervalo)
- `resource_type` (SALA, PROFESSOR, MATERIAL, EQUIPAMENTO)
- `resource_id` (específico)
- `user_id` (solicitante ou responsável)
- `state` (SOLICITADA, APROVADA, CONCLUÍDA, etc.)
- `unit_id` (unidade organizacional, se existir)

### Tipos de Relatório
1. **Utilização por Recurso:** Quantidade de reservas aprovadas + concluídas por recurso
2. **Carga Horária:** Horas alocadas por sala/professor/material
3. **Status Summary:** Contagem por estado (aprovadas, rejeitadas, canceladas, concluídas)
4. **Conflitos Evitados:** Volume de requisições rejeitadas por sobreposição
5. **Histórico Material:** Retiradas e devoluções por material
6. **Manutenção e Bloqueios:** Periodos bloqueados registrados

---

## Trade-off: OLTP Direto vs. OLAP Separado

### Opção A: Consultar OLTP Direto (MVP)
- **Ganho:** Simplicidade; sem replicação
- **Perda:** Performance degrada com carga (RNF-05: <5s pode falhar)

### Opção B: H2 Analítico Separado (Futuro - AD-12)
- **Ganho:** OLAP isolado; desempenho garantido
- **Perda:** Replicação diária; lag 24h

**Decisão: MVP usa OLTP direto; migrar para AD-12 se RNF-05 falhar.**

---

## Implementação (MVP)

```java
@GetMapping("/reports/utilization")
public ReportResponse getUtilizationReport(
    @RequestParam LocalDate startDate,
    @RequestParam LocalDate endDate,
    @RequestParam(required=false) String resourceType,
    @RequestParam(required=false) UUID resourceId
) {
    // Query OLTP com filtros
    List<Reservation> reservations = repo.findReservations(
        startDate, endDate, resourceType, resourceId, 
        state = APPROVED || COMPLETED
    );
    
    // Agregação em memória
    Map<ResourceId, Long> utilization = reservations
        .stream()
        .collect(groupingBy(r -> r.getResourceId(), counting()));
    
    return ReportResponse.builder()
        .filters(filtersSummary)
        .data(utilization)
        .generatedAt(now())
        .build();
}
```

---

## Forças Consideradas

### Ganhos
- **Simples:** Uma query, sem ETL
- **Dados sempre frescos:** Relatório = estado atual
- **Filtros dinâmicos:** Sem restrição

### Trade-offs
- **Perda de performance:** Agregação em banco vs. aplicação
- **Sem cache:** Cada requisição refaz cálculo
- **Sem histórico:** Relatório só mostra estado atual (não permite "histórico de utilização")

---

## Alternativas Descartadas

1. **Cache em Redis:** Deps externas; complexidade
2. **Pré-cálculos batch:** Requer batch pipeline (AD-10)
3. **SQL puro:** Sem ORM; maintenance harder
4. **Excel export:** Apenas export, sem visualização web

---

## Consequências

### Modelo de Dados
- `Reservation` + `Resource` + `AuditEvent` (joins complexos possíveis)
- Índices necessários: (resource_id, state, created_at), (user_id, state)

### Performance
- **Esperado:** P95 < 5s com 10K registros, 20 usuarios (RNF-05)
- **Cenário crítico:** Filtro vazio (todos os dados) pode ser lento
- **Solução:** Filtro obrigatório de data (last 90 days default)

### Testes
- JMeter: relatório com 3 filtros diferentes
- Verificar P95 < 5s
- Se falhar: considerar índices ou migrar para AD-12

### Segurança
- Admin + Responsável podem gerar relatórios
- Relatório NÃO revela dados de terceiros (filtro por user_id obrigatório para Solicitante)

---

## Riscos

| Risco | Probabilidade | Impacto | Mitigação |
|-------|---------------|--------|-----------|
| Query lenta (P95 >5s) | Média | Alto | Índices; migrar para AD-12 |
| Memory usage alto em agregação | Baixa | Médio | Paginação; limite 10K linhas |
| Sem histórico de relatórios | Alta | Baixo | Aceitar; futuro: arquivo PDF |
| Concorrência entre OLTP + relatório | Baixa | Médio | Isolamento READ_COMMITTED OK |

---

## Requisitos Relacionados

- RF-12: Relatórios obrigatórios
- RNF-05: Tempo de resposta <5s (alvo 2.5s)
- RNF-08: Ambiente realista (SQL realista)

---

## Condição de Revisão

Esta decisão será revisada se:
1. RNF-05 falhar: implementar AD-12 (H2 analítico)
2. Requisitos exigirem histórico: implementar archive
3. Produção exigir BI integrado: considerar Tableau + data warehouse

---

## Referências

- [arquitetura.md § 10](../docs/arquitetura.md#10-relatórios)
- [ATAM § Cenário C-08](../docs/ATAM-analise.md#cenário-c-08-relatório-de-utilização--5s)
- [RFC - RF-12](../rfc/requisitos-funcionais.md#rf-12--relatórios-gerenciais)
