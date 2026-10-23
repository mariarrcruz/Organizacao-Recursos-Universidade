# ATAM — Análise Independente de Trade-offs

**Sistema:** Organização de Recursos — Universidade
**Equipe avaliadora:** independente (ATAM)
**Data:** 23/10/2026
**Base de análise:** `README.md`, `docs/PRD.md`, `docs/arquitetura.md`, `docs/RTM.md`, `docs/personas/solicitante.md`, `docs/personas/responsavel.md`, `docs/personas/administrador.md`, `rfc/requisitos-funcionais.md`, `rfc/requisitos-nao-funcionais.md`
**Execução desta análise:** exclusivamente documental. Não houve implementação de código, execução de testes, execução de build ou execução de comandos de sistema. Nenhum valor proposto nos documentos-fonte foi transformado em fato; valores marcados como proposta, premissa ou pendente permanecem como hipóteses.

---

## 1. Objetivo e postura do avaliador

1.1. **Objetivo:** aplicar a técnica ATAM (Architecture Tradeoff Analysis Method) de forma independente ao sistema de organização de recursos, com leitura das personas, requisitos e da documentação de arquitetura, produzindo utility tree, cenários e avaliação de trade-offs.

1.2. **Postura do avaliador:**
- Não inventar métricas: valores propostos (validade de token, refresh token, algoritmo JWT, H2, intervalos semiabertos, percentil, volumes de teste, limites de tempo) são **hipóteses pendentes** registradas nos documentos e não fatos.
- Não tratar hipótese como fato: decisões com status *Proposta*, *Pendente* ou marcadas como *premissa acadêmica* foram mantidas como tal.
- Não modificar documentos-fonte: esta análise **acrescenta** evidências e inspeções; nada foi apagado ou reescrito.
- Não implementar nem executar: não houve geração de código, testes, scripts de teste ou ações operacionais.

## 2. Evidências reconhecidas nos documentos-fonte

### 2.1. `docs/arquitetura.md`
- **Escopo:** aplicação local, sem infraestrutura de produção externa; tecnologias-base: Java, Spring Boot, Spring Security, H2, HTML e CSS.
- **AD-01 (Proposta):** monolito modular Spring Boot, único processo executável, módulos com responsabilidades explícitas; comunicação interna por chamadas a serviços.
- **AD-02 (Confirmada como tecnologia; modo pendente):** H2 relacional local; modo memória vs arquivo = decisão pendente.
- **AD-03 (Proposta):** JWT access token 1h, refresh token 7d; RS256; refresh opaco, 256 bits, hash, rotação, revogação, proteção contra reuso.
- **AD-04 (Proposta):** validação transacional de reservas com lock por recurso (ordem determinística de IDs); restrição única de BD não suficiente para intervalos arbitrários.
- **AD-05 (Proposta):** auditoria persistente append-only no mesmo banco, mesma transação da mudança de estado.
- **AD-06 (Proposta):** notificações simuladas por logs locais; falha de logging não reverte operação confirmada.
- **AD-07 (Proposta):** relatórios com leitura agregada sobre o mesmo banco.
- **AD-08 (Confirmada):** testes automatizados e CI.
- **AD-09 (Proposta):** cenário acadêmico de carga documentado (metas como premissas).
- **§11.1:** limites máximos (1s consultas, 1s disparo, 5s relatórios) e alvos (500ms, 500ms, 2,5s) — provenientes dos RFCs; nota: não definem volume/carga/percentil.
- **§11.2:** volumes de referência propostos (100 usuários, 50 salas, 100 professores, 200 materiais, 10.000 reservas, 20.000 auditorias, 20 usuários virtuais, 10 req/s, 5 min, aquecimento 1 min, 3 repetições, p95) — **premissas de teste**, não fatos.
- **§19:** premissas acadêmicas listadas; §20 conclusão: documentação ≠ implementação.

### 2.2. Requisitos (`docs/PRD.md`, `rfc/*`)
- **RF-01 a RF-20** (funcionais), **RN-01 a RN-09** (regras), **RNF-01 a RNF-18** (não funcionais), **COV-01/02, RTM-01, SEC-01, QUAL-01, RISK-01**.
- **RNF-03:** tempo máximo aceitável 1s; desejável 500ms (consultas).
- **RNF-04:** tempo máximo aceitável 1s; desejável 500ms (ações de disparo: reserva, cancelamento, manutenção).
- **RNF-05:** tempo máximo aceitável 5s; adequado 2,5s (relatórios).
- **RNF-08:** ambiente realista: Testcontainers para persistência, WireMock para integrações externas, mocks restritos a unitários.
- **RISK-01 / §10.3 do RTM:** limitadores de nota que inviabilizam aprovação máxima: aplicação que não executa; pipeline ausente; falha de autorização; possibilidade de dupla reserva.

### 2.3. Personas
- **Solicitante (USR-01):** professor/coordenador; consulta disponibilidade; cria/altera/cancela próprias reservas; restrições: não aprova, não administra.
- **Responsável (USR-02):** analisa/aprova/rejeita solicitações especiais; registra retiradas/devoluções; restrições: não configura globalmente.
- **Administrador (USR-03):** gerencia usuários, recursos, bloqueios, manutenção, permissões; restrições: preservar integridade/auditoria.

### 2.4. `docs/RTM.md`
- Convenções: **Confirmado** (fontes), **Proposta** (decisão sugerida, requer validação), **Pendente** (ausente nos fontes), **Não implementado** (nada executado).
- Drivers D1–D9 com importância/criticidade/fundamento.
- Suposições S1–S9; Pendências P1–P10; lacunas (P3 integração externa, P8 unidade, AD-16 WireMock).
- Checklist Anexo B: itens que devem permanecer não marcados enquanto não houver evidência.

### 2.5. Situação transversal observada nos artefatos
- O diretório `.github/workflows/` está **vazio**, o que é compatível com o diagnóstico de pipeline ausente (RNF-04/RNF-09, RISK-01).
- O `RTM.md` classifica a arquitetura, os ADRs e a estratégia de testes como *Proposta/Pendente*, com nota de que nenhum teste foi executado.

## 3. Drivers, atributos prioritários e abordagens

| Driver | Atributos de qualidade associados | Importância | Criticidade | Abordagem considerada (decisão) | Trade-off identificado |
|---|---|---|---|---|---|
| D1 | Executabilidade, disponibilidade | Crítica | Eliminatória | Monólito modular local (AD-01, Proposta) | Simplicidade operacional vs isolamento/escalabilidade futura |
| D2 | Segurança (autenticação/autorização) | Crítica | Eliminatória | JWT + RBAC (AD-03/AD-12, Propostas) | Stateless (JWT) vs revogação centralizada |
| D3 | Integridade de dados (concorrência) | Crítica | Eliminatória | Transação + lock + auditoria (AD-04/AD-05, Propostas) | Complexidade de lock/deadlock vs proteção contra dupla reserva |
| D4 | Auditoria/rastreabilidade | Alta | Obrigatória | Append-only, mesma transação (AD-05, Proposta) | Atomicidade (mesma transação) vs desacoplamento/eventos |
| D5 | Desempenho/latência | Alta | Obrigatória | Metas RNF-03/04/05; p95 (AD-09, Proposta) | Carga acadêmica vs demanda real |
| D6 | Testabilidade/evidências | Alta | Obrigatória | Pirâmide de testes (TG-01 a TG-11), CI (AD-08) | Complexidade de ambiente realista (P2) |
| D7 | Segurança de entrada/tratamento de erros | Alta | Obrigatória | Error contract 400/401/403/404/409/500; logs; validação (RNF-07) | ... |
| D8 | Simplicidade operacional | Média | Desejável | AD-01/AD-02 | Monólito vs microsserviços |
| D9 | Rastreabilidade requisito→risco→teste→evidência | Alta | Obrigatória | RTM-01; arquitetura §17 | Completude de mapeamento |

**Priorização de atributos para a análise ATAM:** autorização e integridade concorrente lideram (eliminatórios), seguidos por desempenho, auditoria e testabilidade (obrigatórios), com disponibilidade e simplicidade em segundo plano (D8).

## 4. Árvore de utilidade (utility tree)

```
QUALIDADE
└── Disponibilidade e integridade de dados
    ├── Disponibilidade do sistema (RNF-13) ── SC exec. diária/indisponibilidade
    └── Integridade sob concorrência (RNF-02) ── SC-01 (dupla reserva), SC-08 (rollback)
└── Segurança
    ├── Autenticação (RNF-01) ── SC-04
    ├── Autorização por perfil/propriedade (RF-01/RF-15/RF-16) ── SC-02, SC-09
    └── Validação de entrada/erro sem exposição (RNF-07) ── SC-09
└── Desempenho
    ├── Latência de consulta (RNF-03) ── SC-07
    ├── Latência de ação de disparo (RNF-04) ── SC-07
    └── Relatórios (RNF-05) ── SC-07
└── Auditoria e rastreabilidade (RF-10/RN-09/RNF-12) ── SC-03
└── Operação e observabilidade (RF-11/RNF-16/RNF-15) ── SC-06
└── Testes, cobertura e CI (COV/RNF-04/RNF-10/RNF-11) ── SC-01/SC-02/SC-07 (evidências)
```

Cada atributo da árvore está vinculado, no §8, aos cenários, decisões, requisitos e riscos.

## 5. Cenários ATAM no formato fonte – estímulo – ambiente – artefato – resposta – métrica

### 5.1 Catálogo de cenários (formato exigido)

| ID | Fonte | Estímulo | Ambiente | Artefato | Resposta | Métrica | Importância | Dificuldade |
|---|---|---|---|---|---|---|---|---|
| SC-01 | PRD RF-06; RFC RF-07; RN-03; RNF-02; arquit. AD-04; RTM §3.7 | Duas requisições simultâneas (POST /reservations) para mesmo recurso e intervalo incompatível | Monólito Spring Boot (AD-01) + H2 (AD-02); módulos reservations/availability | Serviço de aplicação + verificação de disponibilidade; lock transacional por recurso (ordem por ID) — AD-04 | No máximo uma reserva confirmada; segunda rejeitada com 409; auditoria na mesma transação — AD-05 | TG-07; no máximo 1 reserva aceita; estado final do banco; conflitos rejeitados; p95 registrada separadamente — arquit. §11.3 | Crítica/Eliminatória | Alta |
| SC-02 | PRD RF-01/RF-15/RF-16; RFC RF-01/RF-15/RF-16; RNF-01/RNF-07; arquit. §6/§14; RTM §3.1 | Operação protegida com token ausente/inválido/expirado, perfil insuficiente ou alvo de terceiro | Spring Security + JWT; matriz de autorização §6.6; SecurityContext | Filtros/autorização; verificação de propriedade — RF-16 | 401/403 corretos; operação bloqueada; sem ação sobre reservas de terceiros | TG-06; falha de autorização inviabiliza aprovação máxima — RISK-01; SEC-01 | Crítica/Eliminatória | Alta |
| SC-03 | PRD RN-05–RN-09; RFC RF-05/RF-11; arquit. §8/§9.1; RTM §3.11 | Transição de estado da reserva (ex.: SOLICITADA→APROVADA) e falha de persistência concomitante | Transações; módulos reservations/audit | Matriz de transições §8.2; append-only — AD-05 | Transição conforme matriz; auditoria mesma transação; rollback em falha | TG-01/TG-03; RN-09; sequência de falha §3 do RTM | Alta | Alta |
| SC-04 | PRD RF-01/RF-19; RFC RF-01; RNF-01; arquit. AD-03; RTM §3.1 | Login/refresh com credenciais válidas/inválidas; refresh expirado/revogado/reusado; token expirado | identity module; Spring Security | Emissão JWT access 1h/refresh 7d; RS256; rotação/hash/revogação — AD-03 | Emitir/renovar/rejeitar tokens; reuso detectado → revogação da família | RF-19/RNF-01; TG-06; premissa S1 (hipótese) | Alta | Média |
| SC-05 | PRD RF-08; RFC RF-09; RN-04; arquit. §7.4; RTM §3.9 | Reserva cujo intervalo intersecta período de manutenção/bloqueio | Módulo availability | Verificação de indisponibilidade na transação | Rejeição com motivo; recurso indisponível temporariamente | RF-08/RN-04; testes de integração/API | Alta | Média |
| SC-06 | PRD RF-11; RFC RF-17; RNF-16; arquit. AD-06; RTM §3.12, sequência falha 2 | Evento de negócio + falha na notificação simulada/log pós-commit | notifications (logs locais) | Notificações simuladas por logs — AD-06 | Negócio confirma; falha registrada; não reverte operação | RF-11/AD-06; sequência falha §2 do RTM; RNF-15 | Média | Baixa |
| SC-07 | PRD RNF-01/03/04/05; RFC RNF-03–06; arquit. §11/§12/§11.3; RTM §7.1 | Carga (cenário acadêmico proposto) | JMeter; H2; módulos | Arquitetura/layers; relatórios | Limites respeitados (1s/500ms; 5s/2,5s); p95 | RNF-03/RNF-04/RNF-05; S9; AD-09; §11.1 | Alta | Alta |
| SC-08 | PRD RN-01–RN-04; arquit. AD-04/AD-05; RTM sequência falha 3 | Falha de escrita/persistência no ciclo reserva+auditoria | Transação; H2 | Repositório + auditoria (mesma transação) | Rollback atômico; resposta 500 genérica sem detalhes | AD-05/RN-09; sequência falha §3; RNF-07 | Alta | Alta |
| SC-09 | PRD RNF-02/RNF-07; RFC RNF-07; arquit. §13/§14; RTM §3.1 | Token/senha/stack trace/erro interno exposto em resposta ou log | API; logs; Spring Security | Tratamento de erros, logs, Spring Security | Sem exposição de dados sensíveis/segredos | RNF-07/RF-19/SEC-01 | Alta | Média |

### 5.2 Priorização dos cenários (importância × dificuldade)

1. **SC-01** — Crítica/Eliminatória × Alta: prioridade máxima (dupla reserva é limitador de nota).
2. **SC-02** — Crítica/Eliminatória × Alta: prioridade máxima (falha de autorização é limitador de nota).
3. **SC-07** — Alta × Alta: prioridade alta (desempenho; limites propostos).
4. **SC-03** — Alta × Alta: prioridade alta (auditoria/ciclo).
5. **SC-08** — Alta × Alta: prioridade alta (falha/rollback/auditoria).
6. **SC-09** — Alta × Média: prioridade alta (segurança/erro).
7. **SC-04** — Alta × Média: prioridade alta (JWT/refresh).
8. **SC-05** — Alta × Média: prioridade alta (manutenção/bloqueio).
9. **SC-06** — Média × Baixa: prioridade média (notificação).

## 6. Avaliação dos cenários solicitados

| # | Tema | Cenário ATAM relacionado | Avaliação (hipóteses como hipóteses) | Lacunas observadas |
|---|---|---|---|---|
| 1 | Execução diária | SC implícito de executabilidade (D1) | Aplicação local/demonstração; critério RISK-01 "aplicação que não executa". Sem SLA de execução diária nos fontes. | Ausência de métrica/SLA para execução rotineira; RNF-13 não detalha recuperação. |
| 2 | Dados novos | SC-01/SC-05 (criação/alteração) | Cadastros/reservas novas devem preservar RN-01–RN-04; sem meta de volume de novos dados nos fontes. | Impacto de crescimento de novos dados sobre limites de desempenho (SC-07) não quantificado. |
| 3 | Falha parcial | SC-06/SC-08 | Falha isolada (ex.: notificação) não reverte negócio; rollback atômico. Estratégia de isolamento entre módulos não formalizada como decisão. | Falha parcial em módulo não coberta por decisão explícita (fora do escopo documentado). |
| 4 | Jobs sobrepostos | RF-04/RN-04 + RF-05/RN-02 | Não há scheduler/jobs documentados no escopo atual. Bloqueios/manutenção já modelam indisponibilidade temporal. | Se jobs forem introduzidos, sobreposição deve reutilizar verificação de conflito (RN-02/RF-05). Não inventar jobs. |
| 5 | Degradação de dados | SC-07 (relatórios sobre 10.000 reservas) | Consistência de agregações; limites RNF-05. Degradação sob volume real não quantificada (P5). | Relatórios sobre volume histórico proposto (10k reservas) sem medição. |
| 6 | Candidato pior | SC-01 (10 requisições simultâneas) | Pior caso documentado (§11.2); no máximo uma reserva aceita; p95 separada. | Intensidade do "pior caso" ainda hipótese (S7). |
| 7 | Rollback | SC-08 | Rollback transacional (reserva+auditoria) descartados juntos. Rollback de versão/de deploy não documentado. | Estratégia de rollback de releases ausente. |
| 8 | Indisponibilidade | SC-05 + RNF-13 | Recurso bloqueado por manutenção/bloqueio; indisponibilidade do serviço. | Sem DR/estratégia de recuperação documentada. |
| 9 | Vazamento | SC-09 | Error contract sem stack traces/segredos; logs sem tokens. | Log de falha crítica (RF-11/AD-06) sem política de sanitização definida. |

## 7. Pontos de sensibilidade, trade-offs, riscos, não-riscos e temas de risco

### 7.1 Pontos de sensibilidade
- **AD-04:** lock por recurso + ordem de IDs (evita deadlock); risco de escolha inadequada de estratégia.
- **AD-03:** rotação/reuso de refresh token (concorrência na renovação).
- **AD-05:** auditoria na mesma transação (estado sem auditoria não ocorre).
- **AD-06:** notificação pós-commit (falha não reverte negócio; idempotência não definida).
- **AD-02:** H2 memória × arquivo (P1) — durabilidade.
- **S9/AD-09:** percentil p95 como critério inicial.

### 7.2 Trade-offs
- Monólito (AD-01) × microsserviços: simplicidade × isolamento/escalabilidade.
- H2 (AD-02) × persistência externa/produção: local × durabilidade/operação.
- JWT (AD-03) × sessão server-side: stateless × revogação.
- Lock (AD-04) × SERIALIZABLE: custo × consistência.
- Auditoria no mesmo banco (AD-05) × outbox/desacoplamento.
- Logs (AD-06) × integração externa de notificação.
- Relatórios leitura agregada no mesmo banco (AD-07) × read model.

### 7.3 Riscos (registro documental, com origem)
- Dupla reserva sob concorrência (crítica/eliminatória) — SC-01 — AD-04 — TG-07 — RISK-01.
- Falha de autorização (crítica/eliminatória) — SC-02 — matriz §6.6 — TG-06 — RISK-01.
- Uso indevido de refresh token — alta — AD-03.
- Transições inconsistentes — alta — matriz §8.2.
- Auditoria incompleta — alta — AD-05.
- Metas de desempenho sem cenário comum — alta — S7/P5.
- Divergência PRD/RFCs — média — matriz correspondência.
- Cobertura artificial — média — QUAL-01.
- Falha no ambiente de integração — média — pipeline.
- Integração externa indisponível — média — condicional P3.

### 7.4 Não-riscos (aceitáveis no escopo atual, com ressalva documental)
- Ausência de microsserviços/mensageria/cache: fora de escopo; não configura risco imediato, mas limita arquitetura futura (trade-off D8/AD-01).
- Notificações simuladas (AD-06, Proposta): opção da RF-11; se aceita, desacopla o fluxo principal.

### 7.5 Temas de risco
- T1 Integridade de dados e concorrência.
- T2 Segurança/autorização.
- T3 Disponibilidade/operação.
- T4 Desempenho/escalabilidade.
- T5 Evidências/qualidade (testes, cobertura, CI).
- T6 Rastreabilidade/auditoria.

## 8. Rastreabilidade de apoio: cenário × decisão × requisito × risco × evidência esperada

Exemplos de cadeia:
- **Dupla reserva:** RF-06/RN-03/RNF-02 → SC-01 → AD-04/AD-05 → Risco T1 → Evidência esperada TG-07/estado final do BD.
- **Autorização:** RF-01/RF-15/RF-16/RF-19 → SC-02 → AD-03/AD-12 → Risco T2 → Evidência TG-06.
- **Performance:** RNF-03/RNF-04/RNF-05 → SC-07 → AD-07/AD-09 → Risco T4 → Evidência JMeter (planejada).

## 9. Pendências, hipóteses e conclusão do avaliador

### 9.1 Pendências (P1–P10) e suposições (S1–S9)
- Pendentes: modo H2 (P1), Testcontainers (P2), integração externa (P3), retenção/LGPD (P4), metas numéricas de desempenho (P5), critérios de cancelamento (P6), NÃO_COMPARECEU (P7), entidade unidade (P8), CSRF/CORS/token storage (P9), transições adicionais (P10).
- Suposições: S1–S9 (todas com marcador de premissa/hipótese).

### 9.2 Conclusão do avaliador independente
- A análise ATAM não substitui a execução: os cenários propostos permanecem com status *Proposta/Pendente*; nenhuma evidência de execução foi produzida por esta análise.
- Critérios de aprovação máxima (RISK-01): (1) aplicação executa — pendente de verificação; (2) pipeline presente — **ausente** observado; (3) autorização correta — proposta, a validar; (4) sem dupla reserva — proposta, a validar.
- Recomendação de priorização da investigação: SC-01 e SC-02 (eliminatórios), seguida de SC-07 (desempenho), SC-03/SC-08 (auditoria/rollback), SC-09 (segurança/erro).

---

*Esta análise foi registrada sem alterar os documentos-fonte. Valores propostos nos fontes permanecem como hipóteses pendentes; nenhuma hipótese foi apresentada como fato.*
