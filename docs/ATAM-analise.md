# Análise ATAM — Sistema de Organização de Recursos
**Status:** Avaliação Arquitetural Independente  
**Data:** 2026-11-03  
**Avaliador:** Equipe Independente ATAM  
**Escopo:** Validação de decisões arquiteturais contra requisitos, riscos e cenários críticos

---

## 1. Apresentação da Arquitetura

A arquitetura proposta (arquitetura.md, versão 1.0, AD-01 a AD-15) é um **monólito modular Spring Boot** com:
- **Persistência:** H2 relacional (local, arquivo ou memória)
- **Autenticação:** JWT com access token 1h + refresh token 7d
- **Concorrência:** Controle transacional por recurso, serialização
- **Auditoria:** Append-only em banco, transação atômica
- **Extensão batch:** Pipeline diário (02:00 UTC) com watermark e checkpoint

**Tecnologias base:** Java, Spring Boot, Spring Security, H2, HTML, CSS, JUnit 5, JaCoCo, SonarCloud, GitHub Actions, JMeter.

---

## 2. Drivers Arquiteturais

Ordenados por criticidade (referência: PRD RISK-01, RF-01 a RF-20, RNF-01 a RNF-18):

| ID | Driver | Origem | Impacto | Justificativa |
|----|----|-----|----|-----|
| **D1** | Prevenção de dupla reserva em concorrência | RF-06, RF-07, RN-03, RISK-01 | Eliminatório | Sem isso, a aplicação não executa ou falha integralmente |
| **D2** | Autorização correta por perfil | RF-01, RF-15, RN-07, RISK-01 | Eliminatório | Violação pode expor dados de terceiros ou permitir ações indevidas |
| **D3** | Integridade transacional (ACID) | RN-05 a RN-09, RNF-12 | Alto | Auditorias devem ser imutáveis; transições atômicas |
| **D4** | Desempenho < 1s (consultas), < 1s (ações), < 5s (relatórios) | RNF-03, RNF-04, RNF-05 | Alto | Usabilidade em ambiente acadêmico; requer validação |
| **D5** | Rastreabilidade completa (auditoria) | RF-10, RF-11, RN-09, RNF-12 | Alto | Conformidade acadêmica; reconstrução de histórico |
| **D6** | Testabilidade com >80% linhas, >70% branches | COV-01, COV-02, RNF-10 | Alto | Qualidade verificável; critério de aceite |
| **D7** | Confiabilidade operacional (H2 local) | PRD §1.1, RNF-06 | Médio | Escopo acadêmico; sem infraestrutura cloud |
| **D8** | Extensibilidade batch (futuro ML) | RF-12 (parcial), sem RNF | Baixo | Habilita relatórios analíticos; não é requisito explícito |

---

## 3. Atributos de Qualidade Prioritários

### 3.1. Utility Tree — Hierarquia de Atributos

```
                    Qualidade Global
                    ├── Reliability (Confiabilidade)
                    │   ├── Integridade Transacional (D1, D3)
                    │   ├── Prevenção Duplicação (D1, D2)
                    │   └── Disponibilidade Local (D7)
                    ├── Security (Segurança)
                    │   ├── Autenticação JWT (D2, RNF-01)
                    │   ├── Autorização por Perfil (D2, RF-15)
                    │   └── Proteção de Entrada (RNF-07)
                    ├── Performance (Desempenho)
                    │   ├── Consultas < 1s (D4, RNF-03)
                    │   ├── Ações < 1s (D4, RNF-04)
                    │   └── Relatórios < 5s (D4, RNF-05)
                    ├── Maintainability (Manutenibilidade)
                    │   ├── Separação Modular (AD-01)
                    │   ├── Documentação (RF-14, RNF-18)
                    │   └── Logs Estruturados (RNF-15)
                    ├── Testability (Testabilidade)
                    │   ├── Cobertura Automática (D6)
                    │   ├── Testes Integrados (RNF-08, RNF-10)
                    │   └── CI/CD Pipeline (RNF-09)
                    └── Auditability (Rastreabilidade)
                        ├── Append-only (D5, AD-05)
                        ├── Imutabilidade (RN-09)
                        └── Correlação (RNF-15)
```

---

## 4. Cenários ATAM e Avaliação da Arquitetura

Cada cenário segue o formato: **Fonte | Estímulo | Ambiente | Artefato | Resposta Esperada | Métrica**

### Cenário C-01: Dupla Reserva Concorrente (Crítico)

**Fonte:** 2 Solicitantes simultâneos  
**Estímulo:** POST /reservations (mesmo recurso, intervalo sobreposto, Δt < 10ms)  
**Ambiente:** 20 usuários simultâneos; H2 em arquivo; sem rede externa  
**Artefato:** Serviço `reservations`, controle de concorrência (AD-04), repositório  
**Resposta Esperada:**
- Uma requisição: 201 Created + reservation confirmada
- Outra requisição: 409 Conflict + mensagem de indisponibilidade
- Banco final: exatamente 1 reserva para o par (recurso, intervalo)

**Métrica:** Integridade funcional = 100% (nenhuma dupla alocação permitida); Throughput = medido; P95 < 1s

**Avaliação da Arquitetura:**
- ✅ **Decisão AD-04 aborda:** Transação + validação em mesma transação + serialização (bloqueio por recurso)
- ✅ **Teste proposto:** Seção 12.2 (testes de concorrência); seção 11.2 (10 requisições simultâneas para mesmo recurso)
- ⚠️ **Premissa:** Bloqueio por recurso em ordem determinística previne deadlock; sem validação explícita no documento
- ⚠️ **Hipótese:** H2 em arquivo suporta lock pessimista compatível; deve ser verificado em testes

**Risco Associado:** RISK-01 (dupla reserva = critério eliminatório)

---

### Cenário C-02: Autorização Corrета por Perfil

**Fonte:** 3 usuários com papéis diferentes (Solicitante, Responsável, Admin)  
**Estímulo:** Tentar PATCH /reservations/123 (alterar reserva de terceiro, negar rejeição)  
**Ambiente:** H2; JWT válido; spring-security configurado  
**Artefato:** `identity` (autenticação JWT), `reservations` (autorização), matriz de aceite (seção 6.6)  
**Resposta Esperada:**
- Solicitante: 403 Forbidden (tentou alterar reserva alheia)
- Responsável: 200 OK (se autorizado sobre aquela reserva)
- Admin: 200 OK (privilégio global)

**Métrica:** Taxa de rejeição correta = 100%; Nenhuma vazamento de dados sensíveis em 403

**Avaliação da Arquitetura:**
- ✅ **Decisão AD-03 aborda:** JWT com claims de papel (`roles`)
- ✅ **Matriz proposta:** Seção 6.6 lista permissões por operação
- ✅ **Teste proposto:** Seção 12.1 (testes de segurança negativos)
- ⚠️ **Premissa:** Responsável = verificação de propriedade (não apenas papel); necessário em camada de serviço, não apenas Spring Security
- ⚠️ **Hipótese:** A validação "é responsável por esse recurso?" não está explicitamente codificada em AD-03

**Risco Associado:** RISK-01 (falha de autorização = critério eliminatório)

---

### Cenário C-03: Auditoria Imutável de Transição

**Fonte:** Admin aprova reserva  
**Estímulo:** POST /reservations/456/approve (estado SOLICITADA → APROVADA)  
**Ambiente:** H2; transação em progresso  
**Artefato:** `reservations` (lógica), `audit` (persistência), AD-05 (transação atômica)  
**Resposta Esperada:**
- Reservation.state = APROVADA
- AuditEvent criado na mesma transação
- Se transação aborta: ambos revertidos (nenhum evento orfão)
- Verificação: select count(*) from audit_event where reservation_id=456 = 1

**Métrica:** Integridade referencial = 100%; Lag entre transição e auditoria < 10ms

**Avaliação da Arquitetura:**
- ✅ **Decisão AD-05 aborda:** Persistência transacional (mesma transação)
- ✅ **Diagrama:** Seção 4.5.5 mostra sequência com SVC→AUD na mesma transação
- ✅ **Imutabilidade:** Seção 9.1 declara append-only; sem operações de edição comum
- ⚠️ **Hipótese:** Prova de atomicidade em testes de integração; nenhum teste listed em seção 12 especificamente para verificar reversion conjunta

---

### Cenário C-04: Desempenho — Consulta Disponibilidade < 1s

**Fonte:** Solicitante abre página de reserva  
**Estímulo:** GET /resources?type=SALA&available=true&start=2026-11-10T08:00&end=2026-11-10T17:00  
**Ambiente:** H2 em arquivo; 50 salas, 10K reservas históricas, 20 usuários simultâneos  
**Artefato:** `availability` (serviço), `resources` (repositório), índices (não documentados)  
**Resposta Esperada:** HTTP 200 + lista de salas disponíveis, tempo < 1s; P95 alvo 500ms

**Métrica:** P95 response time ≤ 1s (aceitável); alvo 500ms (desejado)

**Avaliação da Arquitetura:**
- ✅ **Requisito:** RNF-03 define 1s máximo, 500ms alvo
- ✅ **Cenário de teste:** Seção 11.2 propõe ambiente acadêmico (50 salas, 10K reservas)
- ⚠️ **Estratégia:** Sem menção a índices, query optimization ou caching
- ⚠️ **Hipótese 1:** Query de sobreposição "inicio_existente < fim_novo AND fim_existente > inicio_novo" pode ser lenta em H2 sem índice composto em (resource_id, start_time, end_time)
- ⚠️ **Hipótese 2:** 10K registros em arquivo H2 é viável, mas requer teste JMeter (seção 11.3) antes de validação

**Risco Associado:** RNF-03 não atendido → usuários abandonam; sem verificação em CI/CD

---

### Cenário C-05: Prevenção Manutenção — Bloqueio Automático

**Fonte:** Admin cadastra manutenção  
**Estímulo:** 
1. POST /maintenance (recurso SALA-01, 2026-11-15 08:00 a 12:00)
2. Solicitante tenta POST /reservations (SALA-01, 2026-11-15 09:00 a 11:00)

**Ambiente:** H2; transação  
**Artefato:** `availability`, `maintenance` (modelo)  
**Resposta Esperada:** 409 Conflict + mensagem "Sala indisponível por manutenção (2026-11-15 08:00-12:00)"

**Métrica:** Taxa de rejeição correta = 100%

**Avaliação da Arquitetura:**
- ✅ **Decisão:** RN-04, RF-08, RF-09 exigem bloqueio
- ✅ **Regra temporal:** Seção 7.2 aplica mesmo critério de sobreposição
- ⚠️ **Implementação:** Seção 7.4 descreve verificação, mas não código específico
- ✅ **Teste:** Seção 12.2 inclui "bloqueio por manutenção" em testes parametrizados

---

### Cenário C-06: Aprovação de Recurso Restrito

**Fonte:** Solicitante solicita recurso com `restricted=true`  
**Estímulo:** 
1. POST /reservations (recurso restrito, Solicitante)
   - Reserva criada em estado SOLICITADA
2. Responsável analisa e POST /reservations/789/approve

**Ambiente:** H2  
**Artefato:** `reservations` (lógica de ciclo de vida), `resources` (flag `restricted`)  
**Resposta Esperada:**
- Passo 1: 201 Created; state=SOLICITADA
- Passo 2: 200 OK; state=APROVADA; approval registrado com responsável, data, decisão

**Métrica:** Ciclo completo respeita RN-07 (só Responsável aprova); auditoria registra aprovador

**Avaliação da Arquitetura:**
- ✅ **Decisão:** Seção 8.3 (aprovação de recursos restritos), matriz transições (seção 8.2)
- ✅ **Modelo:** ReservationApproval (não listado em 5.2, mas implícito)
- ⚠️ **Hipótese:** Responsável deve estar "vinculado" ao recurso; seção 3.2 lista como premissa, não como decisão codificada

---

### Cenário C-07: Retirada e Devolução de Material

**Fonte:** Responsável regista fluxo de material  
**Estímulo:** 
1. POST /movements (retirada, material_id, reserva_id, condição_inicial)
2. POST /movements/{id}/devolucao (condição_final)

**Ambiente:** H2  
**Artefato:** `movements` (serviço), MaterialMovement (modelo)  
**Resposta Esperada:**
- Retirada: 201; MaterialMovement.withdrawal_timestamp preenchido
- Devolução: 200; MaterialMovement.return_timestamp + condition preenchidos
- Auditoria: 2 eventos registrados (um por operação)

**Métrica:** Ciclo completo em P95 < 1s; auditoria presente

**Avaliação da Arquitetura:**
- ✅ **Decisão:** RF-09, RF-10; sequência 4.5.6
- ✅ **Modelo:** MaterialMovement em 5.2
- ⚠️ **Hipótese:** Validação "reserva está EM_USO?" não está explícita em AD-04; inferred de seção 4.5.6

---

### Cenário C-08: Relatório de Utilização < 5s

**Fonte:** Admin gera relatório  
**Estímulo:** GET /reports/utilization?start=2026-10-01&end=2026-10-31&resource_type=SALA

**Ambiente:** H2 com 10K reservas, 20 usuários simultâneos  
**Artefato:** `reports` (serviço), AD-12 (H2 analítico separado, se implementado)  
**Resposta Esperada:** 200 + dados agregados + tempo < 5s; P95 alvo 2.5s

**Métrica:** P95 ≤ 5s; dados consistentes com OLTP

**Avaliação da Arquitetura:**
- ✅ **Requisito:** RNF-05
- ✅ **Extensão batch:** Seção 15A.3 propõe H2 analítico separado (AD-12) para desempenho
- ⚠️ **Premissa:** Se relatórios consultam OLTP direto (sem AD-12), risco de degradação com carga
- ⚠️ **Hipótese:** Implementação de AD-12 é "futuro"; atual status desconhecido

---

### Cenário C-09: Dados Novos — Falha Parcial do Pipeline Batch

**Fonte:** Scheduler dispara 02:00 UTC; erro em fase de carga  
**Estímulo:** Falha de conexão ou violação FK durante INSERT em H2 analítico

**Ambiente:** H2 OLTP + H2 analítico; checkpoint anterior em 2026-11-02 02:00  
**Artefato:** AD-11 (watermark + checkpoint), AD-13 (validação)  
**Resposta Esperada:**
- ROLLBACK automático; nenhuma linha inserida
- Checkpoint mantém valor anterior (2026-11-02 02:00)
- Próxima execução retoma do mesmo ponto
- Alerta crítico enviado para admin

**Métrica:** Idempotência = 100%; lag não piora; dados analíticos permanecem consistentes

**Avaliação da Arquitetura:**
- ✅ **Decisão:** AD-11 (checkpoint transacional), AD-13 (validação)
- ✅ **Recuperação:** Seção 15A.4 detalha retry e transação atômica
- ⚠️ **Implementação:** Não há código; apenas blueprint
- ⚠️ **Teste:** Seção 15A.4 sugere "simular falha em carga"; nenhum test listed

---

### Cenário C-10: Degradação de Qualidade de Dados

**Fonte:** Pipeline detecta anomalia  
**Estímulo:** Completeness < 99% ou consistency falha (FK inválida)

**Ambiente:** H2; validação 3D (AD-13)  
**Artefato:** Validador (não implementado)  
**Resposta Esperada:**
- FAIL com descrição específica (ex: "1 FK inválida em fact_reservation_usage.resource_id")
- ROLLBACK; checkpoint não avança
- Email crítico para admin

**Métrica:** Defeitos de qualidade detectados antes de publicação; taxa = 100%

**Avaliação da Arquitetura:**
- ✅ **Decisão:** AD-13 propõe validação 3D
- ⚠️ **Implementação:** Critérios específicos (seção 15A.4, tabela "Qualidade de Dados") não estão codificados
- ⚠️ **Hipótese:** Implementação pode revelar falsos positivos (ex: NULL em "justificativa" é válido)

---

### Cenário C-11: Rollback de Transformação — Piora de Qualidade > 10%

**Fonte:** Nova versão de transformação (v1.1) lançada; qualidade piora  
**Estímulo:** V1.1 em produção; quality_score cai de 0.95 (v1.0) para 0.84 (v1.1)

**Ambiente:** H2; versionamento (AD-14); monitoring (15A.5)  
**Artefato:** transformation_version registry (proposto, não implementado)  
**Resposta Esperada:**
- Detecção automática: 0.84 < 0.95 * 0.9 (threshold 10%)
- ROLLBACK para v1.0
- Notificação: "Rollback automático v1.1 → v1.0 (qualidade degradou)"

**Métrica:** Degradação evitada; continuidade operacional mantida

**Avaliação da Arquitetura:**
- ⚠️ **Decisão:** AD-14 propõe versionamento e rollback, mas sem requisito explícito
- ⚠️ **Implementação:** Não há código; mecanismo de detecção automática não descrito
- ⚠️ **Hipótese:** Requer observabilidade robusta (seção 15A.5) para monitorar quality_score em tempo real

---

### Cenário C-12: Indisponibilidade Operacional — Recuperação

**Fonte:** Admin quer reverter mudança; checkpoint travou  
**Estímulo:** Checkpoint não progrediu em 48h; lag de dados = 48h

**Ambiente:** H2; operação manual (human override)  
**Artefato:** Procedimento de recuperação (não documentado)  
**Resposta Esperada:**
- Admin acessa tabela batch_checkpoint
- Identifica última execução bem-sucedida
- Reset manual do watermark
- Reexecução controlada

**Métrica:** Indisponibilidade ≤ 1 ciclo (≤24h); RTO ≤ 1h

**Avaliação da Arquitetura:**
- ⚠️ **Procedimento:** Seção 15A.4 menciona "human review", mas não detalha steps
- ⚠️ **Tooling:** Sem runbook de operação; requer conhecimento de SQL

---

### Cenário C-13: Vazamento de Dados Pessoais (LGPD)

**Fonte:** Usuário solicita direito ao esquecimento  
**Estímulo:** Admin executa procedimento de anonimização (AD-15)

**Ambiente:** H2 OLTP + analítico; soft-delete  
**Artefato:** Procedimento ANONYMIZE_USER (seção 15A.6)  
**Resposta Esperada:**
- audit_event.actor_id = NULL (onde actor_id = @user_id)
- fact_reservation_usage.requestor_id = NULL
- dim_user.is_anonymized = TRUE
- Registro de ação em auditoria (AuditEvent com action='ANONYMIZE_USER')

**Métrica:** Conformidade LGPD = 100%; dados pessoais removidos; rastreabilidade mantida

**Avaliação da Arquitetura:**
- ✅ **Decisão:** AD-15 (anonimização)
- ⚠️ **Implementação:** Pseudocódigo em seção 15A.6; sem testes de regressão
- ⚠️ **Hipótese:** Busca de referências de user_id pode ser incompleta (ex: se stored em JSONB)

---

## 5. Pontos de Sensibilidade Críticos

Decisões cuja mudança tem **impacto desproporcional** no sistema:

| ID | Ponto Sensível | Dependências | Impacto se Mudar | Proprietário |
|----|----|----|-----|-----|
| **S1** | Bloqueio transacional por recurso (AD-04) | Concorrência; RN-03; RISK-01 | Falha total: dupla reserva permitida | Engenharia |
| **S2** | Verificação de propriedade em autorização (D2) | RF-15, RF-16, RN-07 | Vazamento de dados ou elevação de privilégio | Engenharia + Segurança |
| **S3** | Transação atômica para auditoria (AD-05) | Integridade; RN-09; RNF-12 | Eventos órfãos; histórico incompleto | Engenharia |
| **S4** | H2 local (AD-02) | Escalabilidade; RNF-06, RNF-13 | Perda de dados se arquivo corrompido; sem backup automático | DevOps + Engenharia |
| **S5** | JWT RS256 com chave privada fora do repo | Segurança; RNF-01, RNF-07 | Token falsificável; comprometimento de autorização | Segurança |
| **S6** | Watermark + checkpoint (AD-11) | Idempotência; consistência analítica | Duplicação de dados ou perda de dados em pipeline | Dados |
| **S7** | Validação 3D antes de publicar (AD-13) | Qualidade; confiabilidade | Dados ruins em analytics; relatórios errados | Dados |

---

## 6. Trade-offs Identificados

### Trade-off T-01: Monólito vs. Microsserviços

**Decisão:** Monólito modular (AD-01)  
**Ganho:** Simplicidade operacional, sem complexidade de rede, deploy simples, transações ACID locais  
**Perda:** Escalabilidade horizontal limitada; modificações globais exigem downtime  
**Condição de revisão:** Se escopo acadêmico evoluir para ambiente de produção distribuído

---

### Trade-off T-02: H2 Local vs. PostgreSQL/MySQL

**Decisão:** H2 em arquivo (AD-02)  
**Ganho:** Zero dependências externas; instalação instantânea; escopo acadêmico  
**Perda:** Concorrência limitada; sem backup automático; sem replicação  
**Validação:** Cenários de concorrência (C-01) devem validar efetivamente em H2

---

### Trade-off T-03: Auditoria Append-only vs. Soft Delete

**Decisão:** Append-only (AD-05) + imutabilidade de aplicação  
**Ganho:** Rastreabilidade completa; impossível editar histórico por erro  
**Perda:** Crescimento indefinido de tabela; sem purge sem análise de compliance  
**Mitigação:** Política de retenção (seção 9.1, 15A.4)

---

### Trade-off T-04: Watermark Incremental vs. Full Reload

**Decisão:** Watermark + checkpoint (AD-11)  
**Ganho:** Eficiência; não reprocessa dados antigos  
**Perda:** Dependência crítica de precisão de timestamp; risco se relógios divergem  
**Mitigação:** NTP sync obrigatório (seção 15A.4, Lag relógio)

---

### Trade-off T-05: Batch Diário vs. Real-time Analytics

**Decisão:** Pipeline diário 02:00 UTC (AD-10)  
**Ganho:** Sem impacto em OLTP; sem streaming infrastructure  
**Perda:** Lag máximo 24h em analytics; não adequado para ML real-time  
**Aceitável para:** Acadêmico; não para operação 24/7 crítica

---

## 7. Riscos e Mitigações

### RISCO-01: Dupla Reserva em Concorrência (Crítico)

**Cenário:** C-01  
**Probabilidade:** Alta (2+ requisições simultâneas é comum)  
**Impacto:** Crítico (RISK-01: critério eliminatório)  
**Mitigação Proposta:**
- AD-04: Transação + validação + bloqueio por recurso
- Seção 11.2: Teste com 10 requisições simultâneas por 5 minutos, repetido 3x
- Seção 12.2: Teste de concorrência obrigatório

**Status de Validação:** ⚠️ Ainda não implementado; hipótese em revisão

---

### RISCO-02: Falha de Autorização (Crítico)

**Cenário:** C-02  
**Probabilidade:** Média (implementação pode omitir verificação de propriedade)  
**Impacto:** Crítico (RISK-01: critério eliminatório)  
**Mitigação Proposta:**
- AD-03: JWT + Spring Security
- Seção 6.6: Matriz de autorização
- Seção 12.1: Testes de segurança negativos obrigatórios

**Status de Validação:** ⚠️ Matriz clara, mas "verificação de responsabilidade" (Responsável vinculado a recurso) não está codificada em decisão

---

### RISCO-03: Auditoria Incompleta ou Órfã

**Cenário:** C-03  
**Probabilidade:** Média (aplicação pode não usar transação atômica)  
**Impacto:** Alto (RNF-12: rastreabilidade obrigatória)  
**Mitigação Proposta:**
- AD-05: Transação atômica para alteração + auditoria
- Seção 12.2: Teste de rollback verificando ambos OR nenhum

**Status de Validação:** ⚠️ Conceito correto; implementação pendente

---

### RISCO-04: Desempenho Degrada com Carga

**Cenário:** C-04, C-08  
**Probabilidade:** Média (H2 sem índices tuning)  
**Impacto:** Alto (RNF-03, RNF-04, RNF-05 não atendidos)  
**Mitigação Proposta:**
- Seção 11.2: Teste JMeter com cenário acadêmico (50 salas, 10K reservas, 20 usuários)
- Seção 11.3: Medição de P95; rejeitar se > 1s
- AD-12: H2 analítico separado para relatórios pesados

**Status de Validação:** ⚠️ Cenário definido, mas teste não executado; hipótese em revisão

---

### RISCO-05: Manutenção Não Bloqueie Corretamente

**Cenário:** C-05  
**Probabilidade:** Baixa (regra temporal simples)  
**Impacto:** Médio (RN-04, RF-08 violados)  
**Mitigação Proposta:**
- Seção 7.4: Critério de sobreposição para manutenção
- Seção 12.2: Teste parametrizado para sobreposição (4 casos: antes, dentro, depois, contém)

**Status de Validação:** ⚠️ Lógica clara; implementação pendente

---

### RISCO-06: Pipeline Batch Falha Silenciosamente

**Cenário:** C-09, C-10  
**Probabilidade:** Média (scheduler pode falhar sem alerta)  
**Impacto:** Médio (Lag de analytics indefinido; RNF-05 não atendido)  
**Mitigação Proposta:**
- AD-11: Checkpoint transacional
- AD-13: Validação 3D (completeness, consistency, uniqueness)
- Seção 15A.5: Alertas críticos se checkpoint não progride > 48h

**Status de Validação:** ⚠️ Mecanismo proposto; implementação pendente

---

### RISCO-07: Rollback de Transformação Incorreto

**Cenário:** C-11  
**Probabilidade:** Baixa (se qualidade monitorada)  
**Impacto:** Médio (Dados errados em analytics; ML alimentado com lixo)  
**Mitigação Proposta:**
- AD-14: Versionamento de transformações
- Seção 15A.7: Aprovação de promoção com 7 dias em TEST
- Seção 15A.5: Monitoramento de quality_score com alerta se piora > 10%

**Status de Validação:** ⚠️ Processo proposto; tooling para monitorar quality_score não descrito

---

### RISCO-08: Perda de Dados H2 (Sem Backup)

**Cenário:** Arquivo H2 corrompido  
**Probabilidade:** Baixa (H2 robusto em operação normal)  
**Impacto:** Crítico (Perda total de dados OLTP e auditoria)  
**Mitigação Proposta:**
- Escopo acadêmico: Aceitável ter backup manual pré-apresentação
- Produção futura: Exigir backup automático (não no escopo atual)

**Status de Validação:** ✅ Não-risco para acadêmico; risco para produção

---

### RISCO-09: Anonimização LGPD Incompleta

**Cenário:** C-13  
**Probabilidade:** Média (campos hidden em JSONB ou cálculos)  
**Impacto:** Alto (Conformidade legal violada; Lei 13.709/2018)  
**Mitigação Proposta:**
- AD-15: Anonimização (pseudocódigo em 15A.6)
- Teste: Verificar todas as tabelas contendo user_id após ANONYMIZE_USER

**Status de Validação:** ⚠️ Procedimento descrito em pseudocódigo; teste não implementado

---

## 8. Não-Riscos (Decisões Bem Mitigadas)

| Não-Risco | Por Quê | Evidência |
|-----------|--------|----------|
| Executabilidade da aplicação | Tecnologias maduras (Java, Spring Boot, H2) | PRD §1.1; AD-01, AD-02 |
| Falha compilação | Pipeline CI incluiu compilação | Seção 12.4 (GitHub Actions) |
| Cobertura de testes baixa | Quality Gate exige 80% linhas, 70% branches | COV-01, COV-02; seção 12.4 |
| Vulnerabilidades críticas | SonarCloud integrado com 0 bugs críticos | Seção 12.4 (Quality Gate) |
| Falta de rastreabilidade | rtm.md vincula requisito-risco-teste-evidência | RTM-01 (PRD §6) |
| Documentação técnica ausente | README.md + arquitetura.md + diagramas Mermaid | RF-14, RNF-18; seção 18 |
| Testes unitários isolados | Pirâmide: unidade > integração > caixa-preta > carga | Seção 12.1 |

---

## 9. Análise de Atributos por Cenário

Matriz: **Cenário × Atributo × Avaliação**

```
Cenário       Reliability  Security  Performance  Maintainability  Testability  Auditability
C-01 (dupla)  ✅⚠️          ✅        ⚠️           ✅               ✅           ✅
C-02 (auth)   ✅           ✅⚠️       ✅           ✅               ✅           ✅
C-03 (audit)  ✅⚠️          ✅        ✅           ✅               ⚠️           ✅
C-04 (perf)   ✅           ✅        ⚠️⚠️          ✅               ✅           ✅
C-05 (maint)  ✅⚠️          ✅        ✅           ✅               ✅           ✅
C-06 (aprovação) ✅        ✅        ✅           ✅               ✅           ✅
C-07 (movto)  ✅           ✅        ✅           ✅               ✅           ✅
C-08 (relat)  ✅           ✅        ⚠️⚠️          ⚠️               ✅           ✅
C-09 (falha)  ⚠️           ✅        ✅           ⚠️               ⚠️           ✅
C-10 (qualid) ⚠️           ✅        ✅           ✅               ⚠️           ✅
C-11 (rollbk) ⚠️           ✅        ✅           ⚠️               ⚠️           ✅
C-12 (indispon) ⚠️         ✅        ✅           ⚠️               ⚠️           ✅
C-13 (LGPD)   ⚠️           ⚠️⚠️       ✅           ✅               ⚠️           ✅

Legenda:
✅ = Bem coberto
⚠️ = Hipótese / Validação pendente
❌ = Descoberto ou não aplicado
```

---

## 10. Resumo de Validações Pendentes

Hipóteses que **devem** ser verificadas antes de aceitar a arquitetura:

| ID | Hipótese | Verificação Necessária | Criticidade |
|----|----|----|----|
| **H1** | Bloqueio pessimista por recurso em H2 funciona | Teste concorrência C-01 com 10+ threads | 🔴 Crítico |
| **H2** | JWT RS256 com chave privada bem protegida | Audit de segurança; SonarCloud SECRETS | 🔴 Crítico |
| **H3** | Verificação de propriedade codificada em serviço | Code review; teste negativo C-02 | 🔴 Crítico |
| **H4** | Transação atômica para auditoria efetiva | Teste de rollback C-03 | 🔴 Crítico |
| **H5** | Consultas de sobreposição otimizadas < 1s | JMeter C-04 com 10K registros | 🟡 Alto |
| **H6** | Watermark + checkpoint sem duplicação | Teste idempotência C-09 com falha injected | 🟡 Alto |
| **H7** | Validação 3D detecta defeitos reais | Teste com dados malformados C-10 | 🟡 Alto |
| **H8** | Procedimento de anonimização LGPD completo | Audit de SQL; teste de cobertura C-13 | 🟡 Alto |
| **H9** | Manutenção bloqueia sobreposição | Teste parametrizado C-05 | 🟡 Médio |
| **H10** | Rollback automático se quality < threshold | Teste de monitoring C-11 | 🟡 Médio |

---

## 11. Temas de Risco Transversais

Identificam conceitos críticos que atravessam múltiplos cenários:

### Tema T1: Concorrência e Atomicidade

**Cenários afetados:** C-01, C-03, C-06, C-09  
**Premissa fundamental:** Operações concorrentes não violam invariantes (ACID)  
**Dependências críticas:**
- Bloqueio transacional (AD-04)
- Isolamento H2 (READ_COMMITTED ou SERIALIZABLE)
- Testes com threads reais (não stubs)

**Recomendação:** Testes de concorrência são **bloqueadores** para aceitar arquitetura.

---

### Tema T2: Autorização e Propriedade

**Cenários afetados:** C-02, C-06, C-07  
**Premissa fundamental:** Usuário nunca acessa dados de terceiros; Responsável verificado  
**Dependências críticas:**
- Spring Security (autenticação JWT)
- Lógica de domínio (verificação de propriedade)
- Testes negativos para cada operação

**Recomendação:** Testes de segurança são **bloqueadores** para aceitar arquitetura.

---

### Tema T3: Integridade de Dados

**Cenários afetados:** C-01, C-03, C-05, C-09, C-10  
**Premissa fundamental:** Não duplicação, não inconsistência, não órfãos  
**Dependências críticas:**
- Transação ACID
- Validação antes de persistência
- Teste com corrupção injected

**Recomendação:** Testes de integridade são **bloqueadores**.

---

### Tema T4: Observabilidade e Rastreabilidade

**Cenários afetados:** C-03, C-05, C-09, C-10, C-12, C-13  
**Premissa fundamental:** Auditoria completa, logs estruturados, alertas em tempo real  
**Dependências críticas:**
- Append-only audit (AD-05)
- Logs estruturados JSON (15A.5)
- Alertas críticos (checkpoint, qualidade, LGPD)

**Recomendação:** Logs e alertas são **essenciais** para operação.

---

### Tema T5: Desempenho sob Carga

**Cenários afetados:** C-04, C-08  
**Premissa fundamental:** P95 ≤ 1s (consultas/ações), ≤ 5s (relatórios)  
**Dependências críticas:**
- Índices em banco (não documentados)
- Separação OLTP/OLAP (AD-12)
- Teste JMeter com cenário acadêmico

**Recomendação:** Teste de carga é **bloqueador** para validar RNF-03, RNF-04, RNF-05.

---

## 12. Conclusão e Recomendações

### 12.1. Avaliação Global

A arquitetura proposta é **conceitualmente sólida** para escopo acadêmico, com:
- ✅ Decisões explícitas para drivers críticos (D1-D8)
- ✅ Modularização clara (AD-01)
- ✅ Segurança em camadas (JWT, autorização, validação)
- ✅ Auditoria imutável (AD-05)
- ✅ Extensão batch para futuro (AD-10 a AD-15)

Porém, **muitas premissas permanecem em hipóteses** que exigem validação prática:

| Status | Contagem | Categoria |
|--------|----------|-----------|
| ✅ Bem documentado | 6 | Decisões claras, menos implementação |
| ⚠️ Hipótese/Validação pendente | 10 | Conceito OK; teste/implementação necessária |
| ❌ Não documentado | 2 | Procedimentos operacionais (backup, recovery) |

---

### 12.2. Recomendações Imediatas (Antes de Implementação)

1. **Testes de Concorrência (H1, Tema T1)**
   - Implementar teste com 10+ threads competindo por mesmo recurso
   - Validar que bloqueio em H2 é efetivo
   - **Criticidade:** 🔴 Bloqueador

2. **Auditoria de Segurança (H2, H3, Tema T2)**
   - Verificar JWT (assinatura, claims, expiração)
   - Garantir verificação de propriedade em cada operação
   - Testes negativos para cada endpoint
   - **Criticidade:** 🔴 Bloqueador

3. **Teste de Integridade (H4, H6, Tema T3)**
   - Simular falha de transação; verificar rollback de auditoria
   - Injetar erros em pipeline; verificar idempotência
   - **Criticidade:** 🔴 Bloqueador

4. **Teste de Performance (H5, Tema T5)**
   - JMeter com 20 usuários, 50 salas, 10K reservas, duração 5 min
   - Medir P95; rejeitarr se > 1s (consultas/ações) ou > 5s (relatórios)
   - **Criticidade:** 🟡 Alto

5. **Documentação de Operação (Procedimentos, C-12)**
   - Backup manual antes de apresentação
   - Runbook de recovery (reset watermark)
   - Playbook LGPD (anonimização)
   - **Criticidade:** 🟡 Médio

---

### 12.3. Recomendações de Longo Prazo (Produção Futura)

1. **Escalabilidade Horizontal**
   - Revisão de AD-01 (monólito → microsserviços)
   - Migração de H2 para PostgreSQL/MySQL

2. **Backup e Disaster Recovery**
   - Backup automático do H2
   - Replicação para standby
   - RTO/RPO definidos

3. **Observabilidade Avançada**
   - ELK (Elasticsearch, Logstash, Kibana) para logs
   - Grafana para métricas
   - AlertManager para notificações críticas

4. **ML sobre Dados Históricos**
   - Modelos em AD-10 (pipeline consolidada dados)
   - Previsão de demanda por recurso
   - Detecção de anomalias

---

### 12.4. Matriz de Aceite Final

| Critério | Status | Evidência |
|----------|--------|----------|
| Arquitetura documentada | ✅ | arquitetura.md (seções 1-18B) |
| Drivers identificados | ✅ | Seção 2 (D1-D8) |
| Atributos de qualidade mapeados | ✅ | Seção 3.1 (Utility Tree) |
| Cenários críticos definidos | ✅ | Seção 4 (C-01 a C-13) |
| Decisões arquiteturais (ADRs) | ✅ | Seção 15, 15A.2 (AD-01 a AD-15) |
| Riscos identificados | ✅ | Seção 7 (RISCO-01 a RISCO-09) |
| Temas de risco | ✅ | Seção 11 (T1-T5) |
| Trade-offs documentados | ✅ | Seção 6 (T-01 a T-05) |
| Validações pendentes | ✅ | Seção 10 (H1-H10) |
| Recomendações | ✅ | Seção 12 |
| **Bloqueadores resolvidos?** | ⚠️ | Testes de concorrência, segurança, integridade **ainda não executados** |
| **Arquitetura aprovada para implementação?** | ⚠️ | **SIM, condicionado à execução de validações bloqueadores** |

---

## 13. Apêndice A — Referência Cruzada: Requisitos × Cenários

| RF | Cenário(s) Associado(s) | Avaliação |
|----|----|----|
| RF-01 | C-02, C-06 | Autorização coberta; implementação pendente |
| RF-02 | C-04, C-05, C-08 | Gestão recursos coberta; performance em validação |
| RF-03 | C-04 | Pesquisa parametrizada coberta; desempenho em hipótese |
| RF-04 | C-01, C-02, C-06, C-07 | Ciclo de vida coberto; transições em matriz |
| RF-05 | C-05 | Detecção sobreposição coberta; teste parametrizado |
| RF-06 | C-01 | Dupla reserva coberta; concorrência bloqueador |
| RF-07 | C-01 | Concorrência coberta; bloqueador |
| RF-08 | C-05, C-06 | Recurso restrito coberto; aprovação em matriz |
| RF-09 | C-05 | Bloqueio manutenção coberto; teste parametrizado |
| RF-10 | C-07 | Retirada/devolução coberta; auditoria atômica |
| RF-11 | C-03, C-07, C-13 | Auditoria coberta; imutabilidade em decisão |
| RF-12 | C-08, C-09 | Relatórios cobertos; desempenho + batch em hipótese |
| RF-13 | Fora do escopo ATAM | UX; não arquitetural |
| RF-14 | Fora do escopo ATAM | Documentação; não arquitetural |
| RF-15 | C-02 | Autorização por perfil coberta |
| RF-16 | C-02 | Propriedade coberta; verificação em serviço |
| RF-17 | C-06 | Aprovação coberta; workflow em matriz |
| RF-18 | C-02, C-04, C-05 | Admin coberto; privilégios em matriz |
| RF-19 | C-02 | Validação JWT coberta; teste negativo |
| RF-20 | C-01 a C-05 | Testes de borda cobertos em seção 12.2 |

---

## 14. Apêndice B — Referência Cruzada: RNF × Cenários

| RNF | Cenário(s) Associado(s) | Avaliação |
|----|----|----|
| RNF-01 | C-02, C-04, C-06 | Segurança JWT coberta; implementação pendente |
| RNF-02 | C-01, C-03 | Integridade coberta; testes bloqueadores |
| RNF-03 | C-04 | Desempenho consultas; JMeter bloqueador |
| RNF-04 | C-01, C-02, C-06, C-07 | Desempenho ações; JMeter bloqueador |
| RNF-05 | C-08 | Desempenho relatórios; AD-12 + JMeter |
| RNF-06 | C-01 a C-05 | Robustez carga; JMeter bloqueador |
| RNF-07 | C-02, C-13 | Segurança entrada; validação em seção 14 |
| RNF-08 | C-01 a C-13 | Ambiente realista; Testcontainers + H2 |
| RNF-09 | Fora do escopo ATAM | CI/CD; não arquitetural (mas obrigatório) |
| RNF-10 | Fora do escopo ATAM | TDD/BDD; não arquitetural (mas obrigatório) |
| RNF-11 | Fora do escopo ATAM | Cobertura; não arquitetural (mas obrigatório) |
| RNF-12 | C-03, C-07, C-09, C-13 | Auditoria coberta; append-only em decisão |
| RNF-13 | Fora do escopo ATAM | Evolução; arquitetura modular habilitador |
| RNF-14 | Fora do escopo ATAM | UX/responsividade; não arquitetural |
| RNF-15 | C-09, C-10, C-12 | Observabilidade coberta; logs JSON em decisão |
| RNF-16 | C-07, C-09 | Notificações simuladas coberta; logs em AD-06 |
| RNF-17 | C-01 a C-07 | Respostas consistentes; contrato HTTP em seção 13.1 |
| RNF-18 | Fora do escopo ATAM | Documentação; não arquitetural |

---

## 15. Apêndice C — Cronograma de Validação Sugerido

Ordem recomendada para executar validações bloqueadores:

1. **Semana 1:** Testes de concorrência (H1, C-01) + auditoria segurança JWT (H2)
2. **Semana 2:** Testes de integridade (H4, C-03) + testes negativos autorização (H3, C-02)
3. **Semana 3:** Teste de performance JMeter (H5, C-04, C-08)
4. **Semana 4:** Testes de pipeline batch (H6, C-09) + LGPD (H8, C-13)
5. **Semana 5:** Integração CI/CD + cobertura JaCoCo + SonarCloud
6. **Semana 6:** Ajustes; validação final; entrega

---

## Anexo — Histórico de Análise

**Versão 1.0 — 2026-11-03**
- Análise ATAM completa: drivers, atributos, utility tree, 13 cenários críticos
- Identificação de 9 riscos e 9 não-riscos
- 10 hipóteses pendentes de validação
- 5 temas de risco transversais
- Recomendações imediatas e de longo prazo

---

**Conclusão:** A arquitetura proposta é viável e bem fundamentada. A implementação poderá prosseguir com confiança **condicionada à execução rigorosa dos testes bloqueadores (concorrência, segurança, integridade)** descritos neste documento.

---

*Documento preparado por: Equipe Avaliadora ATAM Independente*  
*Próxima revisão: Após implementação e testes bloqueadores*
