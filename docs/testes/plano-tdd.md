# Plano TDD — Sistema de Organização de Recursos

**Versão:** 1.0  
**Data:** 2026-11-03  
**Escopo:** Preparação de ciclo TDD baseada em requisitos funcionais, não funcionais e regras de negócio  
**Artefatos de origem:**
- `rfc/requisitos-funcionais.md` (RF-01 a RF-20)
- `rfc/requisitos-nao-funcionais.md` (RNF-01 a RNF-18)
- `docs/PRD.md` (Regras de Negócio RN-01 a RN-09)
- `docs/arquitetura.md` (Decisões Arquiteturais AD-01 a AD-15)
- `docs/personas/*.md` (Perfis de acesso: Solicitante, Responsável, Administrador)
- `adr/*.md` (Decisões técnicas ADR-001+)

---

## 1. Inventário de Regras de Negócio

### 1.1. Regras de Tempo e Concorrência

#### RN-01: Ordem Temporal
**Origem:** PRD § 4.1, RFC RF-04  
**Descrição:** O horário de término de uma reserva deve ser obrigatoriamente posterior ao horário de início.  
**Tipo:** Validação de entrada  
**Impacto:** Crítico (RN-01 é eliminatório se falhar)

**Casos de Teste:**

| Caso | Entrada | Condição | Resultado Esperado | Tipo |
|------|---------|----------|-------------------|------|
| RN-01.1 | início=10:00, fim=11:00 | Fim > Início | ✅ Aceito | Caminho feliz |
| RN-01.2 | início=10:00, fim=10:00 | Fim = Início | ❌ Rejeitado (duração zero) | Valor-limite |
| RN-01.3 | início=10:00, fim=09:59 | Fim < Início | ❌ Rejeitado | Entrada inválida |
| RN-01.4 | início=10:00, fim=10:00:00.001 | Duração < 1 minuto | ⚠️ Validar se permitido | Valor-limite |
| RN-01.5 | início=NULL, fim=11:00 | Início ausente | ❌ Rejeitado (obrigatório) | Entrada inválida |
| RN-01.6 | início=10:00, fim=NULL | Fim ausente | ❌ Rejeitado (obrigatório) | Entrada inválida |

---

#### RN-02: Exclusividade de Alocação
**Origem:** PRD § 4.1, RFC RF-06  
**Descrição:** Reservas para um mesmo recurso (sala, material, professor) não podem apresentar sobreposição de horário.  
**Tipo:** Validação de negócio (controle de conflitos)  
**Impacto:** Crítico (eliminatório se falhar)

**Regra Temporal Aplicada:** `inicio_existente < fim_novo AND fim_existente > inicio_novo`  
**Convenção:** Intervalos semiabertos `[início, fim)` — reserva de 10:00-11:00 não conflita com 11:00-12:00

**Casos de Teste:**

| Caso | Reserva Existente | Nova Reserva | Resultado | Tipo |
|------|------------------|--------------|-----------|------|
| RN-02.1 | [10:00, 11:00) | [14:00, 15:00) | ✅ Aceito | Sem conflito (distante) |
| RN-02.2 | [10:00, 11:00) | [11:00, 12:00) | ✅ Aceito | Sem conflito (adjacente, semiabertura) |
| RN-02.3 | [10:00, 11:00) | [10:30, 11:30) | ❌ Rejeitado | Conflito (sobreposição total) |
| RN-02.4 | [10:00, 11:00) | [09:30, 10:30) | ❌ Rejeitado | Conflito (sobreposição parcial início) |
| RN-02.5 | [10:00, 11:00) | [10:30, 11:00) | ❌ Rejeitado | Conflito (sobreposição parcial fim) |
| RN-02.6 | [10:00, 11:00) | [09:59, 10:01) | ❌ Rejeitado | Conflito (mínima sobreposição) |
| RN-02.7 | [10:00, 11:00) | [09:30, 12:00) | ❌ Rejeitado | Conflito (contém intervalo existente) |
| RN-02.8 | [10:00, 11:00) | [10:00, 11:00) | ❌ Rejeitado | Conflito (idêntico) |
| RN-02.9 | Múltiplos recursos no mesmo horário | [10:00, 11:00) para R1, R2, R3 | ✅ Aceito | Sem conflito (recursos diferentes) |

**Nota:** A regra se aplica por RECURSO. Se uma reserva envolve múltiplos recursos (sala+professor+material), a verificação deve iterar cada um.

---

#### RN-03: Atomicidade sob Concorrência
**Origem:** PRD § 4.1, RFC RF-07  
**Descrição:** Duas solicitações enviadas simultaneamente para o mesmo recurso e horário devem resultar em apenas uma reserva aceita, rejeitando a concorrente.  
**Tipo:** Validação transacional  
**Impacto:** Crítico (eliminatório se falhar — RISK-01)

**Estratégia de Controle:** Transação ACID + Serialização por recurso (bloqueio pessimista ou otimista com retry)

**Casos de Teste:**

| Caso | Requisição 1 | Requisição 2 | Timing | Resultado Esperado | Tipo |
|------|------------|------------|--------|-------------------|------|
| RN-03.1 | POST /reservations (R1, 10-11) | Aguardando | Sequential | ✅ R1 aceito; R2 aceito em [11,12) | Sem concorrência |
| RN-03.2 | POST /reservations (R1, 10-11) | POST /reservations (R1, 10-11) | Simultâneas (t=0) | ✅ Uma aceita; outra rejeitada (409) | Concorrência |
| RN-03.3 | POST /reservations (R1, 10-11) | POST /reservations (R1, 10:30-11:30) | Simultâneas | ✅ Uma aceita; outra rejeitada (409) | Conflito concorrente |
| RN-03.4 | 10 requisições simultâneas | Mesmo recurso, horário [10-11) | t=0 (stress) | ✅ 1 aceito; 9 rejeitados (409) | Stress test (RNF-06) |
| RN-03.5 | Req1: TX inicia; Req2: TX inicia | Req1 persiste; Req2 tenta | Isolamento | ❌ Req2 rejeita com 409 ou retry | Isolamento transacional |

**Validação:** Examinar estado final do banco — deve haver exatamente 1 reserva confirmada, não 2.

---

#### RN-04: Inoperância por Manutenção
**Origem:** PRD § 4.1, RFC RF-09  
**Descrição:** Nenhum recurso que esteja alocado em período de manutenção pode ser reservado.  
**Tipo:** Validação de negócio (bloqueio de período)  
**Impacto:** Alto

**Casos de Teste:**

| Caso | Manutenção | Nova Reserva | Resultado | Tipo |
|------|-----------|--------------|-----------|------|
| RN-04.1 | Nenhuma | [10:00, 11:00) | ✅ Aceito | Sem manutenção |
| RN-04.2 | [12:00, 13:00) | [10:00, 11:00) | ✅ Aceito | Sem sobreposição |
| RN-04.3 | [12:00, 13:00) | [11:00, 12:00) | ✅ Aceito | Adjacente (semiabertura) |
| RN-04.4 | [12:00, 13:00) | [12:00, 13:00) | ❌ Rejeitado | Durante manutenção (exato) |
| RN-04.5 | [12:00, 13:00) | [11:30, 12:30) | ❌ Rejeitado | Conflito com manutenção |
| RN-04.6 | [12:00, 13:00) | [11:00, 14:00) | ❌ Rejeitado | Envolve período de manutenção |
| RN-04.7 | Múltiplas manutenções | Intervalos entre elas | ✅ Aceito se sem conflito | Manutenção fragmentada |

---

### 1.2. Regras de Ciclo de Vida e Auditoria

#### RN-05: Fluxo Principal de Estados
**Origem:** PRD § 4.2, RFC RF-05  
**Descrição:** O ciclo de vida nominal da reserva segue: `SOLICITADA` → `APROVADA` → `EM_USO` → `CONCLUÍDA`  
**Tipo:** Máquina de estados  
**Impacto:** Crítico

**Casos de Teste:**

| Caso | Estado Atual | Estado Destino | Condição | Resultado | Tipo |
|------|-------------|----------------|----------|-----------|------|
| RN-05.1 | SOLICITADA | APROVADA | Aprovação por Responsável | ✅ Permitido | Caminho feliz |
| RN-05.2 | APROVADA | EM_USO | Início de utilização | ✅ Permitido | Caminho feliz |
| RN-05.3 | EM_USO | CONCLUÍDA | Conclusão de utilização | ✅ Permitido | Caminho feliz |
| RN-05.4 | SOLICITADA | EM_USO | Sem aprovação intermediária | ❌ Rejeitado | Transição proibida |
| RN-05.5 | EM_USO | SOLICITADA | Retrocesso inválido | ❌ Rejeitado | Transição proibida |
| RN-05.6 | CONCLUÍDA | SOLICITADA | Revogação de conclusão | ❌ Rejeitado | Transição proibida |
| RN-05.7 | CONCLUÍDA | CONCLUÍDA | Idempotência | ⚠️ Ignorar ou erro? | Valor-limite |

---

#### RN-06: Estados Alternativos
**Origem:** PRD § 4.2, RFC RF-05  
**Descrição:** O sistema admite estados alternativos: `REJEITADA`, `CANCELADA` e `NÃO COMPARECEU`  
**Tipo:** Máquina de estados  
**Impacto:** Crítico

**Casos de Teste:**

| Caso | Estado Atual | Estado Destino | Condição | Resultado | Tipo |
|------|-------------|----------------|----------|-----------|------|
| RN-06.1 | SOLICITADA | REJEITADA | Rejeição por Responsável | ✅ Permitido | Caminho feliz |
| RN-06.2 | SOLICITADA | CANCELADA | Cancelamento por Solicitante | ✅ Permitido | Caminho feliz |
| RN-06.3 | APROVADA | CANCELADA | Cancelamento antes do início | ✅ Permitido | Caminho feliz |
| RN-06.4 | APROVADA | NÃO COMPARECEU | Registro de ausência | ✅ Permitido | Caminho feliz |
| RN-06.5 | EM_USO | CANCELADA | Cancelamento durante uso | ❌ Rejeitado | Transição proibida (RN-08) |
| RN-06.6 | EM_USO | REJEITADA | Rejeição após início | ❌ Rejeitado | Transição proibida |
| RN-06.7 | REJEITADA | CANCELADA | Transição entre finais | ❌ Rejeitado | Terminal (sem saída) |
| RN-06.8 | CANCELADA | APROVADA | Retomada de cancelada | ❌ Rejeitado | Terminal |

---

#### RN-07: Alçada de Aprovação
**Origem:** PRD § 4.2, RFC RF-08, RF-17  
**Descrição:** Somente usuários com perfil Responsável possuem permissão para aprovar solicitações de recursos restritos.  
**Tipo:** Validação de autorização  
**Impacto:** Crítico

**Casos de Teste:**

| Caso | Recurso | Perfil do Usuário | Ação | Resultado | Tipo |
|------|---------|------------------|------|-----------|------|
| RN-07.1 | Recurso restrito=true | Responsável | Aprovação | ✅ Permitido | Caminho feliz |
| RN-07.2 | Recurso restrito=true | Solicitante | Aprovação | ❌ Negado (403) | Autorização insuficiente |
| RN-07.3 | Recurso restrito=true | Administrador | Aprovação | ⚠️ Esclarecer (escopo?) | Ambiguidade |
| RN-07.4 | Recurso restrito=false | Responsável | Aprovação | ✅ Permitido? | Valor-limite |
| RN-07.5 | Recurso restrito=false | Solicitante | Aprovação | ❌ Negado | Autorização insuficiente |
| RN-07.6 | Recurso restrito=true | Usuário anônimo | Aprovação | ❌ Negado (401) | Autenticação ausente |

---

#### RN-08: Imutabilidade de Reservas em Execução
**Origem:** PRD § 4.2, RFC RF-05  
**Descrição:** Reservas que já foram iniciadas (`EM_USO`) não podem ser excluídas do sistema.  
**Tipo:** Validação de estado  
**Impacto:** Alto

**Casos de Teste:**

| Caso | Estado | Ação | Resultado | Tipo |
|------|--------|------|-----------|------|
| RN-08.1 | SOLICITADA | Excluir (DELETE) | ❌ Rejeitado | Deletar após solicitação proibido? |
| RN-08.2 | APROVADA | Excluir (DELETE) | ❌ Rejeitado | Deletar após aprovação proibido? |
| RN-08.3 | EM_USO | Excluir (DELETE) | ❌ Rejeitado (crítico) | Deletar em execução |
| RN-08.4 | CONCLUÍDA | Excluir (DELETE) | ⚠️ Esclarecer | Deletar após conclusão? |
| RN-08.5 | REJEITADA | Excluir (DELETE) | ⚠️ Esclarecer | Deletar rejeitada? |
| RN-08.6 | EM_USO | PATCH/alterar | ❌ Rejeitado | Modificar durante uso |

**Nota:** RFC não especifica se DELETE é permitido em SOLICITADA/APROVADA ou se apenas a transição de estado é permitida.

---

#### RN-09: Rastreabilidade Obrigatória
**Origem:** PRD § 4.2, RFC RF-11  
**Descrição:** Toda mudança de estado em uma reserva deve gerar registro de auditoria imutável.  
**Tipo:** Validação de auditoria  
**Impacto:** Crítico (RNF-12)

**Casos de Teste:**

| Caso | Ação | Auditoria Esperada | Resultado | Tipo |
|------|------|-------------------|-----------|------|
| RN-09.1 | Criar reserva (SOLICITADA) | INSERT AuditEvent | ✅ Registro criado | Caminho feliz |
| RN-09.2 | Transição para APROVADA | INSERT AuditEvent | ✅ Registro criado | Caminho feliz |
| RN-09.3 | Transição para EM_USO | INSERT AuditEvent | ✅ Registro criado | Caminho feliz |
| RN-09.4 | Transição para CONCLUÍDA | INSERT AuditEvent | ✅ Registro criado | Caminho feliz |
| RN-09.5 | Transição para REJEITADA | INSERT AuditEvent + justificativa | ✅ Registro criado | Caminho feliz |
| RN-09.6 | Transição para CANCELADA | INSERT AuditEvent + justificativa | ✅ Registro criado | Caminho feliz |
| RN-09.7 | Registro contém: usuário, timestamp, ação, estado anterior, estado novo | Verificar integridade | ✅ Completo | Auditoria completa |
| RN-09.8 | Editar AuditEvent após inserção | ❌ Impossível (ou erro) | Append-only | Imutabilidade |
| RN-09.9 | Deletar AuditEvent | ❌ Impossível (ou erro) | Append-only | Imutabilidade |
| RN-09.10 | Transição inválida bloqueada | AuditEvent NÃO criado | ✅ Correto | Não registrar falha? |

---

### 1.3. Regras de Autorização e Controle de Acesso

#### RF-15: Controle de Acesso por Perfil
**Origem:** RFC RF-15, RF-01  
**Descrição:** Cada perfil tem acesso estritamente às ações permitidas.  
**Tipo:** Validação de autorização  
**Impacto:** Crítico

**Casos de Teste (por Perfil):**

##### Solicitante

| Caso | Ação | Recurso | Resultado | Tipo |
|------|------|---------|-----------|------|
| RF-15.S.1 | Criar reserva própria | Sua reserva | ✅ Permitido | Caminho feliz |
| RF-15.S.2 | Alterar reserva própria | Sua reserva (SOLICITADA) | ✅ Permitido | Caminho feliz |
| RF-15.S.3 | Cancelar reserva própria | Sua reserva | ✅ Permitido | Caminho feliz |
| RF-15.S.4 | Consultar disponibilidade | Recursos públicos | ✅ Permitido | Caminho feliz |
| RF-15.S.5 | Alterar reserva de terceiro | Reserva do Responsável | ❌ Negado (403) | Autorização insuficiente |
| RF-15.S.6 | Aprovar solicitação | Qualquer reserva | ❌ Negado (403) | Fora de escopo |
| RF-15.S.7 | Gerenciar usuários | Sistema global | ❌ Negado (403) | Fora de escopo |
| RF-15.S.8 | Ver relatórios gerenciais | Dashboard | ⚠️ Esclarecer | Ambiguidade |

##### Responsável

| Caso | Ação | Recurso | Resultado | Tipo |
|------|------|---------|-----------|------|
| RF-15.R.1 | Consultar disponibilidade | Recursos públicos | ✅ Permitido | Caminho feliz |
| RF-15.R.2 | Analisar solicitações | Solicitações sob responsabilidade | ✅ Permitido | Caminho feliz |
| RF-15.R.3 | Aprovar solicitação especial | Recurso restrito | ✅ Permitido | Caminho feliz |
| RF-15.R.4 | Rejeitar solicitação | Recurso restrito | ✅ Permitido | Caminho feliz |
| RF-15.R.5 | Registrar retirada | Material aprovado | ✅ Permitido | Caminho feliz |
| RF-15.R.6 | Registrar devolução | Material em posse | ✅ Permitido | Caminho feliz |
| RF-15.R.7 | Criar reserva própria | Sua reserva | ⚠️ Esclarecer | Ambiguidade (escopo?) |
| RF-15.R.8 | Gerenciar usuários | Sistema global | ❌ Negado (403) | Fora de escopo |
| RF-15.R.9 | Alterar bloqueios globais | Sem delegação | ❌ Negado (403) | Fora de escopo (salvo delegação) |

##### Administrador

| Caso | Ação | Recurso | Resultado | Tipo |
|------|------|---------|-----------|------|
| RF-15.A.1 | Cadastrar recurso | Sistema | ✅ Permitido | Caminho feliz |
| RF-15.A.2 | Editar recurso | Sistema | ✅ Permitido | Caminho feliz |
| RF-15.A.3 | Deletar recurso | Sistema | ✅ Permitido | Caminho feliz |
| RF-15.A.4 | Cadastrar usuário | Sistema | ✅ Permitido | Caminho feliz |
| RF-15.A.5 | Editar usuário | Sistema | ✅ Permitido | Caminho feliz |
| RF-15.A.6 | Deletar usuário | Sistema | ✅ Permitido | Caminho feliz |
| RF-15.A.7 | Criar bloqueios | Sistema | ✅ Permitido | Caminho feliz |
| RF-15.A.8 | Criar manutenção | Sistema | ✅ Permitido | Caminho feliz |
| RF-15.A.9 | Consultar auditoria | Sistema | ✅ Permitido | Caminho feliz |
| RF-15.A.10 | Gerar relatórios | Sistema | ✅ Permitido | Caminho feliz |
| RF-15.A.11 | Alterar parâmetros | Sistema | ✅ Permitido | Caminho feliz |
| RF-15.A.12 | Bypass de aprovação | Recurso restrito | ❌ Negado (ou escopo?) | Ambiguidade |

---

#### RF-16: Reserva do Próprio Usuário
**Origem:** RFC RF-16  
**Descrição:** Solicitante deve poder alterar/cancelar apenas suas próprias reservas.  
**Tipo:** Validação de propriedade  
**Impacto:** Crítico

**Casos de Teste:**

| Caso | Solicitante | Reserva | Ação | Resultado | Tipo |
|------|-----------|---------|------|-----------|------|
| RF-16.1 | Alice | Criada por Alice | PATCH | ✅ Permitido | Caminho feliz (propriedade) |
| RF-16.2 | Alice | Criada por Bob | PATCH | ❌ Negado (403) | Violação de propriedade |
| RF-16.3 | Alice | Criada por Alice | DELETE | ⚠️ Depende de estado | Valor-limite |
| RF-16.4 | Alice | Criada por Alice (EM_USO) | DELETE | ❌ Rejeitado (RN-08) | Proibido em execução |
| RF-16.5 | Alice | Criada por Alice | Mudar para estado CANCELADA | ✅ Permitido | Transição por proprietário |
| RF-16.6 | Responsável | Reserva de Alice | PATCH (alterar aprovação) | ✅ Permitido (não proprietário) | Permissão por perfil |
| RF-16.7 | Alice | Reserva de Alice | Ver auditoria | ✅ Permitido | Propriedade inclui auditoria |

---

### 1.4. Regras de Autenticação e JWT

#### RF-01: Autenticação e Autorização por Perfil
**Origem:** RFC RF-01, RFC RF-19  
**Descrição:** Autenticação com JWT; autorização por perfil; validação em operações protegidas.  
**Tipo:** Validação de segurança  
**Impacto:** Crítico

**Casos de Teste:**

| Caso | Token | Header | Resultado | Tipo |
|------|-------|--------|-----------|------|
| RF-01.1 | Válido (JWT válido, não expirado) | Authorization: Bearer <token> | ✅ Aceito | Caminho feliz |
| RF-01.2 | Ausente | Sem header Authorization | ❌ Rejeitado (401) | Entrada inválida |
| RF-01.3 | Inválido (JWT malformado) | Authorization: Bearer xyz | ❌ Rejeitado (401) | Entrada inválida |
| RF-01.4 | Expirado | Authorization: Bearer <expired> | ❌ Rejeitado (401) | Valor-limite (tempo) |
| RF-01.5 | Assinatura inválida | Authorization: Bearer <tampered> | ❌ Rejeitado (401) | Segurança (tampering) |
| RF-01.6 | Clainms ausentes (sub, roles, exp) | Authorization: Bearer <incomplete> | ❌ Rejeitado (401) | Entrada inválida |
| RF-01.7 | Perfil válido (SOLICITANTE) | Token com roles=[SOLICITANTE] | ✅ Aceito para ações SOLICITANTE | Caminho feliz |
| RF-01.8 | Perfil válido (RESPONSAVEL) | Token com roles=[RESPONSAVEL] | ✅ Aceito para ações RESPONSAVEL | Caminho feliz |
| RF-01.9 | Perfil válido (ADMINISTRADOR) | Token com roles=[ADMINISTRADOR] | ✅ Aceito para ações ADMINISTRADOR | Caminho feliz |
| RF-01.10 | Múltiplos perfis | Token com roles=[SOLICITANTE, RESPONSAVEL] | ✅ Aceito (aplica mais permissivo?) | Valor-limite |
| RF-01.11 | Sem perfil | Token sem claim 'roles' | ❌ Rejeitado (401) | Entrada inválida |
| RF-01.12 | Refresh token expirado | POST /auth/refresh com refresh_token expirado | ❌ Rejeitado (401) | Renovação falha |

---

### 1.5. Regras de Recursos e Gestão

#### RF-02: Gestão de Recursos Institucionais
**Origem:** RFC RF-02  
**Descrição:** Cadastro, consulta, atualização e exclusão de salas, materiais, equipamentos, professores e usuários.  
**Tipo:** CRUD + validação  
**Impacto:** Alto

**Casos de Teste:**

| Caso | Recurso | Operação | Dados | Resultado | Tipo |
|------|---------|----------|-------|-----------|------|
| RF-02.1 | Sala | CREATE | {nome, local, capacidade} | ✅ Criado | Caminho feliz |
| RF-02.2 | Sala | READ (id) | {id existente} | ✅ Retorno completo | Caminho feliz |
| RF-02.3 | Sala | UPDATE | {nome atualizado} | ✅ Atualizado | Caminho feliz |
| RF-02.4 | Sala | DELETE | {id sem reservas} | ✅ Deletado | Caminho feliz |
| RF-02.5 | Sala | DELETE | {id com reservas EM_USO} | ⚠️ Esclarecer | Valor-limite (referência ativa) |
| RF-02.6 | Sala | CREATE | {nome=""} | ❌ Rejeitado (validação) | Entrada inválida |
| RF-02.7 | Sala | UPDATE | {id inexistente} | ❌ 404 Not Found | Entrada inválida |
| RF-02.8 | Material | CREATE + status=INDISPONÍVEL | Disponível para reserva? | ❌ Não listado em buscas | Regra de negócio |
| RF-02.9 | Equipamento | CREATE + status=MANUTENÇÃO | Bloqueado automaticamente | ✅ Sem reservas aceitas | Estado padrão |
| RF-02.10 | Professor | CREATE + competência=[C1, C2] | Pode filtrar por competência? | ✅ Sim (RF-03) | Recurso complexo |

---

#### RF-03: Pesquisa Parametrizada de Recursos
**Origem:** RFC RF-03  
**Descrição:** Filtros de busca por tipo, localização, capacidade, competência, disponibilidade e período.  
**Tipo:** Query com múltiplas condições  
**Impacto:** Médio

**Casos de Teste:**

| Caso | Filtros | Resultado | Tipo |
|------|---------|-----------|------|
| RF-03.1 | tipo=SALA | Retorna todas as salas | Caminho feliz |
| RF-03.2 | tipo=SALA, localização=BLOCO_A | Retorna salas em Bloco A | Caminho feliz |
| RF-03.3 | tipo=SALA, capacidade_min=50 | Retorna salas com capacidade ≥ 50 | Caminho feliz |
| RF-03.4 | tipo=PROFESSOR, competência=[PYTHON, JAVA] | Retorna professores com uma das competências | Caminho feliz |
| RF-03.5 | disponibilidade=[2026-11-05, 10:00-12:00] | Retorna recursos disponíveis nesse intervalo | Caminho feliz |
| RF-03.6 | Nenhum filtro | Retorna todos os recursos (com paginação?) | Valor-limite |
| RF-03.7 | Filtro inválido (tipo=XYZ) | ❌ Rejeitado (400) ou ignorado | Entrada inválida |
| RF-03.8 | Disponibilidade=[11:00, 10:00] | Fim < Início | ❌ Rejeitado (400) | Entrada inválida |

---

### 1.6. Regras de Movimentações de Material

#### RF-10: Controle de Retirada e Devolução
**Origem:** RFC RF-10  
**Descrição:** Registro de retirada/devolução de materiais com identificação, usuário, data/hora.  
**Tipo:** Workflow + auditoria  
**Impacto:** Médio

**Casos de Teste:**

| Caso | Ação | Pré-requisitos | Resultado | Tipo |
|------|------|---|-----------|------|
| RF-10.1 | Registrar retirada | Reserva em APROVADA | ✅ MaterialMovement criado (retirada) | Caminho feliz |
| RF-10.2 | Registrar retirada | Reserva em SOLICITADA | ❌ Rejeitado (estado inválido) | Estado proibido |
| RF-10.3 | Registrar retirada | Perfil Solicitante | ❌ Negado (403) | Autorização insuficiente |
| RF-10.4 | Registrar devolução | Material em retirada (ativo) | ✅ MaterialMovement atualizado (devolução) | Caminho feliz |
| RF-10.5 | Registrar devolução | Material já devolvido | ❌ Rejeitado (estado inválido) | Valor-limite |
| RF-10.6 | Retirada com condição=OK, devolução com condição=DANIFICADO | Histórico completo | ✅ Registrado em auditoria | Auditoria |
| RF-10.7 | Período entre retirada e devolução | Cálculo de duração | ✅ Durações corretas | Lógica |
| RF-10.8 | Múltiplos materiais | Uma reserva com [M1, M2, M3] | ✅ Retiradas/devoluções independentes | Múltiplos itens |

---

### 1.7. Regras de Relatórios

#### RF-12: Relatórios Gerenciais
**Origem:** RFC RF-12  
**Descrição:** Relatórios de utilização, carga horária, volume de reservas, conflitos evitados, movimentações.  
**Tipo:** Query analítica + agregação  
**Impacto:** Médio

**Casos de Teste:**

| Caso | Relatório | Filtros | Resultado | Tipo |
|------|-----------|---------|-----------|------|
| RF-12.1 | Utilização por recurso | [data_início, data_fim] | Total horas reservadas por recurso | Caminho feliz |
| RF-12.2 | Carga horária por sala | [período, sala_id] | Somas de horas alocadas | Caminho feliz |
| RF-12.3 | Reservas por estado | [APROVADA, EM_USO, CONCLUÍDA] | Contagens por estado | Caminho feliz |
| RF-12.4 | Histórico de retiradas/devoluções | [material_id, período] | Lista de movimentações | Caminho feliz |
| RF-12.5 | Relatório vazio | [período sem dados] | Retorna estrutura vazia | Valor-limite |
| RF-12.6 | Autorização (Solicitante) | Relatório gerencial | ⚠️ Esclarecer (acesso?) | Ambiguidade (RF-15) |
| RF-12.7 | Relatório com filtro incompatível | [sala_id para professor_report] | ❌ Erro ou resultado vazio? | Valor-limite |
| RF-12.8 | Timeout (RNF-05) | Relatório com volume alto | Resposta em < 5s (p95) | Performance |

---

## 2. Ambiguidades e Lacunas Identificadas

### 2.1. Ambiguidades (Requerem Esclarecimento)

| ID | Questão | Impacto | Origem | Status |
|----|---------|--------|--------|--------|
| AMB-01 | DELETE é permitido em reservas SOLICITADA/APROVADA, ou apenas transições de estado são válidas? | Alto | RFC RF-05, RN-08 | ⏳ Esclarecer |
| AMB-02 | O Administrador pode contornar aprovação de Responsável em recursos restritos? | Alto | RFC RF-08, RN-07 | ⏳ Esclarecer |
| AMB-03 | Solicitante pode criar suas próprias reservas, ou apenas Responsável? Escopo do Solicitante na criação? | Médio | RFC RF-04, Persona Solicitante | ⏳ Esclarecer |
| AMB-04 | Qual a duração mínima de uma reserva? (1 minuto? 15 min? 1 hora?) | Médio | RFC RF-04, RN-01 | ⏳ Esclarecer |
| AMB-05 | Se DELETE em CONCLUÍDA/REJEITADA/CANCELADA é permitido, há necessidade de auditoria retroativa? | Médio | RFC RF-11, RN-09 | ⏳ Esclarecer |
| AMB-06 | Relatórios (RF-12) — Solicitante pode consultar dados de outros usuários? | Médio | RFC RF-12, RF-15 | ⏳ Esclarecer |
| AMB-07 | Transição APROVADA → NÃO COMPARECEU — quem pode registrar? Apenas Responsável? | Médio | RFC RF-05, RN-06 | ⏳ Esclarecer |
| AMB-08 | Reserva em REJEITADA — pode voltar a SOLICITADA (retentativa)? | Baixo | RFC RF-05, RN-05, RN-06 | ⏳ Esclarecer |
| AMB-09 | Material marcado como DANIFICADO na devolução — marcado como indisponível automaticamente? | Médio | RFC RF-10, RF-02 | ⏳ Esclarecer |
| AMB-10 | Refresh token — qual a política de revogação em caso de mudança de perfil de usuário? | Médio | RFC RF-01, AD-03 | ⏳ Esclarecer |

---

### 2.2. Lacunas (Requisitos Implícitos não Explícitos)

| ID | Lacuna | Impacto | Recomendação |
|----|--------|--------|--------------|
| GAP-01 | Permissão de READ em auditoria — quem pode consultar histórico? | Médio | Definir: Solicitante vê seu histórico? Responsável vê sob sua responsabilidade? Administrador vê tudo? |
| GAP-02 | Conflito de múltiplos recursos em uma reserva — qual prioridade se um estiver indisponível? (falha atômica ou parcial?) | Alto | Definir: Uma reserva envolve [Sala, Professor, 3 Materiais]; se Sala indisponível, tudo rejeitado? |
| GAP-03 | Notificações simuladas (RF-11, AD-06) — quem recebe? Apenas ator? Todos interessados? | Médio | Definir: Notificar Solicitante após aprovação? Notificar Responsável quando material danificado? |
| GAP-04 | Período de manutenção — pode ser criado sobre reserva existente? | Médio | Definir: Bloqueia retroativamente ou apenas futuras? |
| GAP-05 | Bloquei de agenda (não-manutenção) — qual a diferença? (uso administrativo vs manutenção?) | Médio | Definir: Quando usar bloqueio vs manutenção? Tratamento é idêntico? |
| GAP-06 | Capacidade de recurso (sala) — verifica ocupação dinâmica ou apenas alocação? | Médio | Definir: Sala com capacidade=50; é bloqueado se 50 pessoas alocadas em mesmo horário? |
| GAP-07 | Renovação de refresh token — número máximo de renovações? | Baixo | Definir: Ou pode renovar indefinidamente até expiração final (7 dias)? |
| GAP-08 | Estado NÃO COMPARECEU — cria MaterialMovement? (Material fica preso?) | Alto | Definir: Material não devolvido quando usuário não comparece — procedimento? |
| GAP-09 | Filtros em pesquisa (RF-03) — sem resultados retorna [] ou erro? | Baixo | Definir: GET /resources?tipo=SALA → 200 [] (sem resultados) ou 404? |
| GAP-10 | Identificador de usuário autenticado — sempre derivado do JWT (sub)? | Alto | Definir: Validar em cada operação protegida? Ou confiar no Spring Security? |

---

### 2.3. Validações Explicitamente Ausentes

| ID | Validação | Requisito? | Recomendação |
|----|-----------|-----------|--------------|
| VAL-01 | Email ou documento de usuário — formato válido? | Não especificado | Implementar validação de email if stored |
| VAL-02 | Telefone — formato obrigatório? | Não especificado | Executar apenas se campo existir e obrigatório |
| VAL-03 | Comprimento máximo de descrição/justificativa | Não especificado | Definir: 255? 1000? Sem limite? |
| VAL-04 | Timezone — reserva em UTC ou local? | Não especificado | Definir: Todos os timestamps em UTC (ISO 8601) |
| VAL-05 | Data de hoje — permite reserva retroativa? | Não especificado | Validar: início ≥ NOW()? Ou permitir? |
| VAL-06 | Página de paginação — tamanho padrão? | Não especificado | Definir: Default 20? Max 100? |

---

## 3. Sumário de Cobertura de Regras de Negócio

| Regra | Casos Definidos | Ambiguidades | Lacunas | Status |
|-------|-----------------|--------------|---------|--------|
| RN-01 (Ordem Temporal) | 6 | 1 (VAL-05 retroativa) | 1 (duração mínima AMB-04) | ⚠️ Parcial |
| RN-02 (Exclusividade) | 9 | 0 | 2 (múltiplos recursos GAP-02, referência ativa RF-02.5) | ✅ Completo |
| RN-03 (Concorrência) | 5 | 0 | 0 | ✅ Completo |
| RN-04 (Manutenção) | 7 | 0 | 1 (GAP-04 retroativa) | ✅ Completo |
| RN-05 (Fluxo Principal) | 7 | 2 (AMB-01 DELETE, AMB-08 retry) | 0 | ⚠️ Parcial |
| RN-06 (Estados Alternativos) | 8 | 1 (AMB-07 quem registra NÃO_COMPARECEU) | 1 (GAP-08 material preso) | ⚠️ Parcial |
| RN-07 (Alçada de Aprovação) | 6 | 1 (AMB-02 Administrador bypass?) | 0 | ⚠️ Parcial |
| RN-08 (Imutabilidade EM_USO) | 6 | 1 (AMB-01 DELETE em outros estados) | 0 | ⚠️ Parcial |
| RN-09 (Auditoria) | 10 | 0 | 1 (GAP-01 READ de auditoria) | ⚠️ Parcial |
| RF-01 (Autenticação JWT) | 12 | 0 | 2 (VAL-04 timezone, GAP-07 renovações) | ✅ Completo |
| RF-15 (Acesso por Perfil) | 26 (por perfil) | 3 (AMB-03, AMB-06, AMB-07) | 1 (GAP-01 READ audit) | ⚠️ Parcial |
| RF-16 (Propriedade) | 7 | 0 | 0 | ✅ Completo |
| RF-02 (Gestão de Recursos) | 10 | 0 | 1 (GAP-06 capacidade) | ⚠️ Parcial |
| RF-03 (Pesquisa) | 8 | 0 | 1 (GAP-09 sem resultado) | ✅ Completo |
| RF-10 (Movimentações) | 8 | 0 | 1 (GAP-08 NÃO_COMPARECEU) | ⚠️ Parcial |
| RF-12 (Relatórios) | 8 | 0 | 1 (GAP-01 READ auth) | ⚠️ Parcial |

**Resumo:** 143 casos de teste definidos; 10 ambiguidades; 16 lacunas de esclarecimento; 6 validações ausentes.

---

## 4. Matriz de Rastreabilidade Simplificada

| Requisito (Origem) | Regra de Negócio | Casos de Teste Críticos | Risco Associado | Status |
|----|--------|-------|--------|--------|
| RF-01 (RFC) | RN-01 a RN-09, RF-01 | 12 JWT + 26 autorização | RISK-01 (falha auth → acesso não autorizado) | ⏳ Esclarecer |
| RF-04 (RFC) | RN-01, RN-05, RN-06 | 7 + 8 (estados) | Inconsistência de estado | ⚠️ Parcial |
| RF-05 (RFC) | RN-02, RN-03 | 9 + 5 (concorrência) | RISK-01 (dupla reserva) | ✅ Completo |
| RF-06 (RFC) | RN-02, RN-04 | 9 + 7 (manutenção) | Conflito não detectado | ✅ Completo |
| RF-07 (RFC) | RN-03 | 5 | Condição de corrida | ✅ Completo |
| RF-08 (RFC) | RN-07 | 6 | Bypass de aprovação | ⚠️ Parcial |
| RF-11 (RFC) | RN-09 | 10 | Auditoria incompleta | ⚠️ Parcial |
| RF-15 (RFC) | RF-15 | 26 | Acesso não autorizado | ⚠️ Parcial |
| RF-16 (RFC) | RN-02 (propriedade) | 7 | Alteração indevida | ✅ Completo |
| RF-10 (RFC) | RN-09 | 8 | Histórico inconsistente | ⚠️ Parcial |
| RF-12 (RFC) | RN-09 | 8 | Relatório incorreto | ⚠️ Parcial |

---

## 5. Próximas Ações

1. **Esclarecer ambiguidades** com product owner (2-3 horas):
   - AMB-01, AMB-02, AMB-03, AMB-04
   - Impacto crítico em desenho de testes

2. **Validar lacunas** com stakeholders (1-2 horas):
   - GAP-01, GAP-02, GAP-08
   - Impacto em escopo de funcionalidade

3. **Definir políticas de validação** (1 hora):
   - VAL-04 (timezone), VAL-05 (retroativa), VAL-06 (paginação)

4. **Priorizar casos de teste** (1 hora):
   - Críticos: RN-02, RN-03, RN-05, RN-09, RF-01, RF-15, RF-16
   - Alto: RN-04, RN-07, RF-10, RF-12
   - Médio: RN-06, RN-08, RF-02, RF-03

5. **Estruturar suite TDD** baseada em priorização acima (dev phase).

---

## 6. Metadados do Documento

- **Preparado por:** Engenheiro de Qualidade (QA)
- **Método:** Análise estática de artefatos (sem execução de código)
- **Artefatos consultados:** 6 (requisitos, personas, arquitetura, PRD)
- **Tempo de preparação:** ~2 horas
- **Próxima revisão:** Pós-esclarecimento de ambiguidades

---

## 7. Matriz de Implementação (fase green)

**Build de verificação:** `./mvnw -B verify` — 146 testes executados, 0 falhas, 0 erros, 0 ignorados.

Legenda: ✅ verde · ⏸ `@Disabled` (bloqueado por contradição ou dado faltante, ver § 7.2) · ⚠️ verde com decisão provisória (ver § 7.2)

### 7.1. Regra → Caso → Teste → Status

| Regra | Caso | Teste (método) | Status |
|-------|------|----------------|--------|
| RN-01 Ordem temporal | RN-01.1 a RN-01.3, 01.5, 01.6 | `RN01OrdemTemporalTest` (rn01_1, rn01_2, rn01_3, rn01_5, rn01_6) | ✅ |
| RN-01 | RN-01.4 duração mínima (1 min) | rn01_4 | ✅ |
| RN-02 Exclusividade | RN-02.1 a RN-02.9 | `RN02ExclusividadeAlocacaoTest` (rn02_1 … rn02_9) | ✅ |
| RN-03 Concorrência | RN-03.1 a RN-03.5 | `RN03AtomicidadeConcorrenciaTest` (rn03_1 … rn03_5) | ✅ |
| RN-04 Manutenção | RN-04.1 a RN-04.7 | `RN04ManutencaoTest` (rn04_1 … rn04_7) | ✅ |
| RN-05 Fluxo principal | RN-05.1 a RN-05.6 | `RN05FluxoPrincipalTest` (rn05_1 … rn05_6) | ✅ |
| RN-05 | RN-05.7 idempotência (CONCLUÍDA → CONCLUÍDA é erro 409) | rn05_7 | ✅ |
| RN-06 Estados alternativos | RN-06.1 a RN-06.8 | `RN06EstadosAlternativosTest` (rn06_1 … rn06_8) | ✅ |
| RN-07 Alçada de aprovação | RN-07.1, 07.2, 07.5, 07.6 | `RN07AlcadaAprovacaoTest` | ✅ |
| RN-07 | RN-07.3 admin aprova restrito (403); RN-07.4 Responsável aprova não restrito | rn07_3, rn07_4 | ✅ |
| RN-08 Imutabilidade em execução | RN-08.3, RN-08.6 | `RN08ImutabilidadeExecucaoTest` (rn08_3, rn08_6) | ✅ |
| RN-08 | RN-08.2, 08.4, 08.5 (DELETE em APROVADA, CONCLUÍDA, REJEITADA é 409) | rn08_2, rn08_4, rn08_5 | ✅ |
| RN-08 | RN-08.1 DELETE em SOLICITADA pelo dono é permitido (expectativa alterada, § 7.2) | rn08_1 | ✅ |
| RN-09 Rastreabilidade | RN-09.1 a RN-09.10 | `RN09RastreabilidadeTest` (rn09_1 … rn09_10) | ✅ |
| RF-01 Autenticação JWT | RF-01.1 a RF-01.9, 01.11, 01.12 | `RF01AutenticacaoJwtTest` | ✅ |
| RF-01 | RF-01.10 múltiplos perfis (token com SOLICITANTE e RESPONSAVEL é aceito) | rf01_10 | ✅ |
| RF-02 Gestão de recursos | RF-02.1 a RF-02.4, 02.6 a 02.10 | `RF02GestaoRecursosTest` | ✅ |
| RF-02 | RF-02.5 excluir sala com reserva ativa é 409 | rf02_5 | ✅ |
| RF-03 Pesquisa | RF-03.1 a RF-03.8 | `RF03PesquisaRecursosTest` | ✅ |
| RF-10 Movimentações | RF-10.1 a RF-10.8 | `RF10MovimentacoesMaterialTest` | ✅ |
| RF-12 Relatórios | RF-12.1 a RF-12.5, 12.8 | `RF12RelatoriosTest` | ✅ |
| RF-12 | RF-12.6 acesso do Solicitante a relatório gerencial (403) | rf12_6 | ✅ |
| RF-12 | RF-12.7 id inexistente (sala_id = `PROF-1`) retorna 404 (expectativa alterada, § 7.2) | rf12_7 | ✅ |
| RF-15 Acesso por perfil | Solicitante S.1 a S.7 | `RF15ControleAcessoPerfilTest$Solicitante` | ✅ |
| RF-15 | Solicitante S.8 relatórios (403) | rf15_s8 | ✅ |
| RF-15 | Responsável R.1 a R.6, R.8, R.9 | `RF15ControleAcessoPerfilTest$Responsavel` | ✅ |
| RF-15 | Responsável R.7 criar reserva própria | rf15_r7 | ✅ |
| RF-15 | Administrador A.1 a A.11 | `RF15ControleAcessoPerfilTest$Administrador` | ✅ |
| RF-15 | Administrador A.12 bypass de aprovação (403) | rf15_a12 | ✅ |
| RF-16 Propriedade | RF-16.1, 16.2, 16.4 a 16.7 | `RF16PropriedadeReservaTest` | ✅ |
| RF-16 | RF-16.3 excluir SOLICITADA própria | rf16_3 | ✅ |

**Resumo:** 146 executados, 146 verdes, 0 `@Disabled`. Duas expectativas de teste foram alteradas por decisão do product owner (RN-08.1 e RF-12.7, ver § 7.2). A injeção de relógio fixo em `RN06EstadosAlternativosTest` não altera expectativa.

### 7.2. Decisões registradas (sem pendências)

1. **AMB-01 — exclusão de reserva `SOLICITADA`.** Decisão: vale RF-16.3. O dono pode excluir a própria reserva em `SOLICITADA`. **Expectativa alterada:** RN-08.1 passou de `assertRejeitado` para `assertDoesNotThrow`, com `@Disabled` removido. Demais estados seguem bloqueados (APROVADA, CONCLUÍDA, REJEITADA, EM_USO).
2. **RF-12.7 — id inexistente em relatório de sala.** Decisão: 404. **Expectativa alterada:** `assertErro(400)` passou a `assertErro(404)`, com `@Disabled` removido. Limitação registrada: sem o registro de recursos no relatório, "existe" é inferido das reservas. Uma sala sem nenhuma reserva também retorna 404 até o relatório receber o registro de recursos.
3. **AMB-04 — duração mínima de 1 minuto.** Confirmado. Ativa RN-01.4.
4. **AMB-03 — Responsável pode criar reserva própria.** Confirmado. Ativa RF-15.R.7.
5. **AMB-06 — Solicitante não acessa relatório gerencial (403).** Confirmado. Ativa RF-12.6 e RF-15.S.8.
6. **RN-06.3 — relógio injetado.** `ServicoReservas(Clock)`; `RN06EstadosAlternativosTest` usa relógio fixo em 2026-11-03 12:00. Sem alteração de expectativa.
