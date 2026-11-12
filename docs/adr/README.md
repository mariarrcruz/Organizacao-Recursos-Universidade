# Architecture Decision Records (ADRs)

Este diretório contém todas as decisões arquiteturais (ADRs) do sistema de Organização de Recursos da Universidade.

**Total:** 15 ADRs | **Status:** Aprovadas (com bloqueadores)

---

## Index

### Online (Operacional) — AD-01 a AD-09

| # | Título | Status | Bloqueador? | Requisitos |
|---|--------|--------|------------|-----------|
| [AD-01](001-monolito-modular-spring-boot.md) | Monólito Modular Spring Boot | ✅ Aprovado | — | PRD §1.1 |
| [AD-02](002-h2-relacional-local.md) | H2 Relacional Local | ✅ Aprovado | — | PRD §1.1, RNF-02, RNF-06 |
| [AD-03](003-jwt-1h-refresh-7d.md) | JWT 1h + Refresh 7d | ✅ Aprovado | 🔴 Auditoria JWT | RF-01, RNF-01 |
| [AD-04](004-validacao-transacional-lock-concorrencia.md) | Validação Transacional + Lock | ✅ Aprovado | 🔴 Teste Concorrência (H1) | RF-06, RF-07, RNF-02 |
| [AD-05](005-auditoria-append-only-transacional.md) | Auditoria Append-Only | ✅ Aprovado | 🔴 Teste Rollback (H4) | RF-10, RF-11, RNF-12 |
| [AD-06](006-notificacoes-simuladas-logs.md) | Notificações Simuladas por Logs | ✅ Aprovado | — | RNF-15, RNF-16 |
| [AD-07](007-relatorios-com-filtros-leitura-separada.md) | Relatórios + Filtros | ✅ Aprovado | 🟡 Teste JMeter (H5) | RF-12, RNF-05 |
| [AD-08](008-testes-automatizados-ci-cd.md) | Testes + CI/CD GitHub Actions | ✅ Aprovado | — | RNF-09, RNF-10, RNF-11 |
| [AD-09](009-cenario-academico-carga.md) | Cenário Acadêmico de Carga | ✅ Aprovado | 🟡 JMeter (H5) | RNF-03, RNF-04, RNF-05, RNF-06 |

### Batch e Dados — AD-10 a AD-15

| # | Título | Status | Bloqueador? | Requisitos |
|---|--------|--------|------------|-----------|
| [AD-10](010-pipeline-batch-diario.md) | Pipeline Batch Diário 02:00 UTC | ✅ Aprovado | — | RF-12 (parcial) |
| [AD-11](011-watermark-checkpoint-idempotencia.md) | Watermark + Checkpoint | ✅ Aprovado | 🔴 Teste Idempotência (H6) | Confiabilidade pipeline |
| [AD-12](012-h2-analitico-separado.md) | H2 Analítico Separado | ✅ Aprovado (Futuro) | — | RNF-05 (se OLTP falhar) |
| [AD-13](013-validacao-3d-qualidade-dados.md) | Validação 3D: Completeness, Consistency, Uniqueness | ✅ Aprovado | 🔴 Teste Validação (H7) | Qualidade dados |
| [AD-14](014-versionamento-rollback-transformacoes.md) | Versionamento + Rollback Transformações | ✅ Aprovado (MVP+1) | — | Evolução transformações |
| [AD-15](015-anonimizacao-lgpd-direito-esquecimento.md) | Anonimização LGPD | ✅ Aprovado | 🔴 Teste LGPD (H8) | Lei 13.709/2018 |

---

## Blockers Críticos (Devem ser Validados Antes da Implementação)

### 🔴 Críticos (Bloqueiam Início)

| ID | Hipótese | Verificação | Risco |
|----|----------|-------------|-------|
| **H1** | Bloqueio pessimista H2 funciona | Teste: 10+ threads disputam recurso → 1 sucesso, 9+ 409 | Dupla reserva permitida |
| **H2** | JWT RS256 bem protegido | Auditoria: chave privada fora repo; SonarCloud SECRETS | Token falsificável |
| **H3** | Verificação propriedade em serviço | Teste negativo: Solicitante A não acessa reserva de B | Vazamento de dados |
| **H4** | Transação atômica para auditoria | Teste: rollback → ambos Reservation E AuditEvent revertem | Eventos órfãos |
| **H6** | Watermark + checkpoint (idempotência) | Teste: falha injected → reexecução = mesmo resultado | Duplicação de dados |
| **H7** | Validação 3D detecta defeitos | Teste: FK inválida → FAIL | Dados ruins em analytics |
| **H8** | Anonimização LGPD completa | Audit: nenhuma referência user_id persiste | Conformidade legal |

### 🟡 Altos (Impactam Performance)

| ID | Hipótese | Verificação | Risco |
|----|----------|-------------|-------|
| **H5** | Queries sobreposição <1s | JMeter: p95 ≤1s com 10K registros, 20 usuarios | RNF-03, RNF-04 falham |
| **H9** | H2 arquivo tolera concorrência | Teste 24h; sem corrupção | Indisponibilidade crítica |
| **H10** | NTP sync mantém watermark preciso | Configurar NTP; monitorar drift | Perda ou duplicação de dados |

---

## Decisões SEM Requisito Explícito

| AD | Razão | Status |
|----|-------|--------|
| AD-10 | Pipeline batch habilita RF-12 parcial + futuro ML | Proposto; não obrigatório MVP |
| AD-12 | H2 analítico é otimização (se RNF-05 falhar) | Futuro; não para MVP |
| AD-14 | Versionamento transformações é boa prática | Futuro; não para MVP |

---

## Matriz Requisito → ADR

| Requisito | AD |
|-----------|-----|
| RF-01 (Autenticação) | AD-03 |
| RF-06, RF-07 (Dupla reserva) | AD-04 |
| RF-10, RF-11 (Auditoria) | AD-05 |
| RF-12 (Relatórios) | AD-07, AD-10, AD-12 |
| RNF-01 (Segurança) | AD-03 |
| RNF-02 (Integridade) | AD-04, AD-05 |
| RNF-03, RNF-04, RNF-05 (Performance) | AD-09, AD-07, AD-12 |
| RNF-06 (Robustez carga) | AD-09 |
| RNF-09 (CI/CD) | AD-08 |
| RNF-10, RNF-11 (Testes) | AD-08 |
| RNF-12 (Auditoria) | AD-05 |
| RNF-15, RNF-16 (Observabilidade) | AD-06 |
| Lei 13.709/2018 (LGPD) | AD-15 |

---

## Como Usar Este Diretório

1. **Leitura inicial:** Comece com [AD-01](001-monolito-modular-spring-boot.md) (contexto geral)
2. **Bloqueadores:** Verifique seção "Blockers Críticos" acima
3. **Específico:** Navegue por ADR se interessado em decisão particular
4. **Rastreabilidade:** Cada ADR lista RF/RNF relacionados

---

## Próximos Passos

### Sprint 1: Validar Bloqueadores Críticos (H1-H4)
- [ ] Teste concorrência (H1): 10 threads, 1 sucesso, 9 conflicts
- [ ] Auditoria JWT (H2): chave privada, SonarCloud SECRETS
- [ ] Teste autorização (H3): negativo
- [ ] Teste rollback (H4): transação atômica

### Sprint 2: Validar Bloqueadores Altos (H5, H6, H7, H8)
- [ ] JMeter (H5): p95 <1s (consultas/ações), <5s (relatórios)
- [ ] Teste idempotência (H6): falha + reexecução
- [ ] Teste validação 3D (H7): FK inválida → FAIL
- [ ] Teste LGPD (H8): anonimização completa

### Sprint 3: Implementação
- [ ] Modularização (AD-01)
- [ ] H2 + schema (AD-02)
- [ ] JWT + Spring Security (AD-03)
- [ ] Lock transacional (AD-04)
- [ ] Auditoria (AD-05)

---

## Referências

- [arquitetura.md](../docs/arquitetura.md) — Contexto completo
- [ATAM-analise.md](../docs/ATAM-analise.md) — Análise arquitetural independente
- [RFC Requisitos](../rfc/) — Funcionais e não-funcionais
- [rtm.md](../docs/rtm.md) — Matriz rastreabilidade

---

**Versão:** 1.0  
**Data:** 2026-11-03  
**Status:** Pronto para implementação (com validação de bloqueadores)
