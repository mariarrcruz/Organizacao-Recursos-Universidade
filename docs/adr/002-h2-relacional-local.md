# ADR-002: H2 Relacional Local

**Status:** Aprovado

**Data:** 2026-11-03

---

## Contexto

PRD define H2 como tecnologia de persistência. Decisão: H2 em memória vs. arquivo, com implicações para durabilidade e testes.

---

## Decisão

Usar **H2 em arquivo** como banco de dados oficial do escopo acadêmico.

**Configuração proposta:**
- Modo padrão: arquivo em `data/app.mv.db`
- Perfil de testes: H2 em memória (rápido, isolado)
- Configurável via `application.yml` ou variáveis de ambiente

**Justificativa:** Preserva dados entre reinicializações; simula ambiente mais realista sem infraestrutura externa.

---

## Forças Consideradas

### Ganhos
- **Zero dependências externas:** Não requer PostgreSQL, MySQL, RDS
- **Instalação instantânea:** `mvn spring-boot:run` funciona imediatamente
- **Testes reproduzíveis:** Dados de teste no repositório
- **Sem custos:** Adequado para acadêmico

### Trade-offs
- **Concorrência limitada:** Sem controle fino de isolamento (H2 é single-threaded para arquivo)
- **Sem backup automático:** Responsabilidade do usuário (admin)
- **Sem replicação:** Sem standby ou failover automático
- **Performance em escala:** 10K registros é limite realista

---

## Alternativas Descartadas

1. **PostgreSQL:** Requer instalação externa; custo (produção); fora do PRD
2. **MySQL:** Mesmo problema; além disso, menos robusto que PostgreSQL
3. **SQLite:** Não é relacional puro; sem suporte a constraints avançados
4. **H2 em memória permanentemente:** Perde dados ao desligar; inadequado para demo

---

## Consequências

### Modelo de Dados
- Entidades com `@Entity` + `@Table`; Hibernate como ORM
- Constraints check: NOT NULL, UNIQUE, FOREIGN KEY
- Índices otimizados manualmente (problema: queries de sobreposição podem ser lentas)

### Testes
- Integração: H2 em memória, recreado a cada teste
- Dados de teste: carregados via `schema.sql` + `data.sql` em classpath

### Performance
- Esperado: P95 < 1s para consultas com 50 salas, 10K reservas
- Se degradar: considerar índices compostos ou migração PostgreSQL

### Deployment
- Um arquivo: `data/app.mv.db` (~50MB para 10K reservas)
- Necessário backup manual antes de apresentação

---

## Riscos

| Risco | Probabilidade | Impacto | Mitigação |
|-------|---------------|--------|-----------|
| Arquivo corrompido | Baixa | Crítico | Backup manual; versionar com dados de teste |
| Performance degrada >1s | Média | Alto | JMeter validar; índices adicionar |
| Concorrência causa deadlock | Baixa | Alto | Teste com 10+ threads; ordem determinística de locks |
| Arquivo cresce demais | Baixa | Médio | Política de retention em auditoria |

---

## Requisitos Relacionados

- PRD §1.1: H2 como banco oficial
- RNF-02: Integridade transacional (H2 ACID é confiável)
- RNF-06: Robustez sob carga (validar com JMeter)
- RNF-08: Ambiente realista (H2 arquivo é proxy aceitável)

---

## Condição de Revisão

Esta decisão será revisada se:
1. Produção exigir replicação ou failover automático
2. Volume crescer >100K registros (H2 mostra limitações)
3. Concorrência >20 usuários simultâneos (H2 satura)

Para produção: migrar para PostgreSQL + replicação + backup automático.

---

## Referências

- [arquitetura.md § 5.1-5.3](../docs/arquitetura.md#seção-5-modelo-de-domínio-e-persistência)
- [ATAM § Trade-off T-02](../docs/ATAM-analise.md#trade-off-t-02-h2-local-vs-postgresqlmysql)
