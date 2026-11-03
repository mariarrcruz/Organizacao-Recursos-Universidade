# ADR-001: Monólito Modular Spring Boot

**Status:** Aprovado

**Data:** 2026-11-03

---

## Contexto

O projeto acadêmico requer uma arquitetura executável localmente, com tecnologias definidas no PRD (Java, Spring Boot, H2). A decisão primária é escolher entre:
- Monólito com módulos bem definidos
- Microsserviços distribuídos

Escopo acadêmico favoreça simplicidade operacional, sem infraestrutura cloud.

---

## Decisão

Implementar um **monólito modular** com um único processo Spring Boot dividido em 8 módulos com responsabilidades explícitas:
- `identity` (autenticação + JWT)
- `resources` (gestão de recursos)
- `reservations` (ciclo de vida de reservas)
- `availability` (consulta + conflito)
- `movements` (retirada/devolução)
- `audit` (rastreabilidade)
- `reports` (relatórios)
- `notifications` (eventos + logs)

Comunicação entre módulos ocorre por chamadas internas a serviços de aplicação, não HTTP.

---

## Forças Consideradas

### Ganhos
- **Transações ACID locais:** Sem complexidade de coordenação distribuída
- **Deploy simples:** Um artefato; uma máquina
- **Sem versionamento cruzado:** APIs internas evolvem naturalmente
- **Facilidade operacional:** Adequado para escopo acadêmico

### Trade-offs
- **Escalabilidade horizontal limitada:** Uma instância; downtime em mudanças globais
- **SPOF (Single Point of Failure):** Sem réplicas automáticas
- **Modularização é responsabilidade do código:** Sem isolamento de processo

---

## Alternativas Descartadas

1. **Microsserviços:** Complexidade operacional, orquestração, versionamento. Fora de escopo para acadêmico.
2. **Serverless:** Sem suporte para H2 local; requer cloud. Fora de escopo.
3. **Monólito sem modularização:** Aumenta acoplamento; dificulta testes isolados.

---

## Consequências

### Imediatas
- Estrutura de pacotes em `src/main/java/organizacaorecursos/<modulo>/{controller,application,domain,infrastructure}`
- Testes unitários isolados por módulo
- Sem necessidade de API Gateway ou service discovery

### Futuras (Produção)
- Migração para microsserviços exigirá refatoração de transações distribuídas
- Escalabilidade horizontal requer isolamento de dados (base por serviço)
- Custo de reescrita provavelmente alto (2-3 semanas)

---

## Riscos

| Risco | Probabilidade | Impacto | Mitigação |
|-------|---------------|--------|-----------|
| Monólito cresce sem limites | Alta | Alto | Documentar limites por módulo; revisar dependências circulares |
| Deploy se torna lento | Média | Médio | CI/CD rápido; compilação incrementa |
| Falha em um módulo derruba tudo | Baixa | Crítico | Testes de isolamento; falha grácil |

---

## Requisitos Relacionados

- PRD §1.1: Tecnologias base (Java, Spring Boot)
- RF-01 a RF-20: Todos os RF executáveis em processo único
- RNF-09: CI/CD em GitHub Actions (um artefato)

---

## Condição de Revisão

Esta decisão será revisada se:
1. Escopo evoluir para ambiente de produção distribuído (múltiplas regiões, failover automático)
2. Requisitos de escalabilidade exigirem >1000 usuários simultâneos
3. Necessidade de deploy independente de módulos

---

## Referências

- [arquitetura.md § 4.1-4.4](../docs/arquitetura.md#seção-4-estilo-e-topologia-arquitetural)
- [ATAM § Trade-off T-01](../docs/ATAM-analise.md#trade-off-t-01-monólito-vs-microsserviços)
