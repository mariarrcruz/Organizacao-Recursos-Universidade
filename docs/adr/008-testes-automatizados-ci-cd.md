# ADR-008: Testes Automatizados + CI/CD com GitHub Actions

**Status:** Aprovado

**Data:** 2026-11-03

---

## Contexto

RNF-09 a RNF-11 exigem qualidade verificável: testes automatizados, cobertura mínima, análise estática, integração contínua. Decisão: qual framework, qual coverage, qual quality gate.

---

## Decisão

Implementar **pipeline CI/CD completo** com GitHub Actions:

### Stack
- **Framework:** JUnit 5 + Mockito (unitários) + Testcontainers (integração)
- **Cobertura:** JaCoCo
- **Análise Estática:** SonarCloud
- **Quality Gate:** SonarCloud rules
- **Build:** Maven
- **CI:** GitHub Actions (workflow.yml)

### Pipeline por PR

1. **Checkout** do repositório
2. **Setup** Java (versão do PRD)
3. **Compilação:** `mvn clean compile`
4. **Testes Unitários:** `mvn test` (isolados, sem BD)
5. **Testes Integração:** `mvn verify` (com H2 em memória)
6. **Cobertura:** JaCoCo gera `target/site/jacoco/index.html`
7. **Análise Estática:** SonarCloud `mvn sonar:sonar`
8. **Publicar Artefatos:** Relatórios (JaCoCo, Surefire, SonarCloud)

### Requisitos de Aceite

| Métrica | Mínimo | Status | Bloqueador? |
|---------|--------|--------|------------|
| Linhas (JaCoCo) | 80% | FAIL se <80% | Sim |
| Branches (JaCoCo) | 70% | FAIL se <70% | Sim |
| Bugs Críticos (SonarCloud) | 0 | FAIL se >0 | Sim |
| Vulnerabilidades | 0 | FAIL se >0 | Sim |
| Compilação | OK | FAIL se erro | Sim |
| Testes | Pass | FAIL se qualquer teste fail | Sim |

---

## Configuração

### pom.xml
```xml
<properties>
  <maven.compiler.source>11</maven.compiler.source>
  <maven.compiler.target>11</maven.compiler.target>
  <jacoco.maven.plugin.version>0.8.8</jacoco.maven.plugin.version>
  <sonar.projectKey>organizacaorecursos</sonar.projectKey>
  <sonar.organization>seu-org</sonar.organization>
</properties>

<plugin>
  <groupId>org.jacoco</groupId>
  <artifactId>jacoco-maven-plugin</artifactId>
  <version>${jacoco.maven.plugin.version}</version>
  <executions>
    <execution>
      <goals><goal>prepare-agent</goal></goals>
    </execution>
    <execution>
      <id>report</id>
      <phase>test</phase>
      <goals><goal>report</goal></goals>
    </execution>
  </executions>
</plugin>
```

### .github/workflows/ci.yml
```yaml
name: CI

on:
  pull_request:
    branches: [ main ]
  push:
    branches: [ main ]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '11'
          distribution: 'adopt'
      
      - name: Build with Maven
        run: mvn clean verify
      
      - name: SonarCloud Analysis
        run: mvn sonar:sonar
        env:
          SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}
      
      - name: Upload JaCoCo Report
        uses: actions/upload-artifact@v3
        with:
          name: jacoco-report
          path: target/site/jacoco/
```

---

## Pirâmide de Testes

| Tipo | Quantidade | Tempo | Isolamento |
|------|-----------|-------|-----------|
| **Unitários** | 60% | <1ms cada | Sem BD, sem HTTP |
| **Parametrizados** | 20% | <10ms cada | Combinações de entrada |
| **Integração** | 15% | 10-100ms | H2 em memória |
| **E2E** | 5% | 100-500ms | Browser + app (raro) |

---

## Cobertura Esperada

### Código Coberto
- Regras de domínio (temporalidade, conflito, estados)
- Autenticação + autorização (JWT, permissões)
- Serviços de aplicação (casos de uso)
- Repositórios (queries críticas)
- Controllers (contratos HTTP)

### Código Não Coberto (Aceitável)
- Configuração Spring (beans)
- Template HTML/CSS (UX, não arquitetura)
- Logs/notificações (observabilidade)
- Admin procedures (raros)

---

## Forças Consideradas

### Ganhos
- **Qualidade verificável:** Números explícitos (80%, 70%, 0 bugs)
- **Regressão detectada:** Cada PR validado
- **Documentação executável:** Testes documentam comportamento
- **CI/CD padrão:** GitHub Actions é gratuito para público

### Trade-offs
- **Tempo de build:** ~5-10 min por PR (aceitável)
- **Falsos positivos SonarCloud:** Code smells vs. regras reais
- **Cobertura artificial:** 80% não garante qualidade (precisa de asserções reais)

---

## Alternativas Descartadas

1. **Sem CI/CD:** Inadequado; sem garantias de qualidade
2. **Jenkins local:** Complexidade; requer manutenção
3. **Travis CI:** Integração terceira; menos controle
4. **Cobertura <80%:** Inadequado; insuficiente

---

## Consequências

### Implementação
- Toda PR bloqueada se JaCoCo <80% ou SonarCloud falhar
- Desenvolvedor deve:
  1. Escrever testes
  2. Garantir cobertura
  3. Aguardar SonarCloud (5-10 min)
  4. Corrigir issues críticas
  5. Aproveitar revisão de código

### Manutenção
- Atualizar SonarCloud rules conforme crescimento
- Monitorar cobertura ao longo do tempo
- Revisar Quality Gate se muito restritivo

### Testes Críticos
- **Concorrência (H1):** Teste com 10 threads
- **Segurança (H3):** Teste negativo de autorização
- **Integridade (H4):** Teste de rollback
- **Desempenho (H5):** Teste JMeter (fora de CI; executado manualmente)

---

## Riscos

| Risco | Probabilidade | Impacto | Mitigação |
|-------|---------------|--------|-----------|
| CI pipeline lento (>10 min) | Baixa | Médio | Paralelizar jobs |
| SonarCloud muitos falsos positivos | Média | Médio | Tunar rules; usar exclusões |
| Cobertura artificial (asserts ruins) | Média | Alto | Code review validar asserções |
| Developers pulam testes | Baixa | Crítico | PR bloqueada até passar |

---

## Requisitos Relacionados

- RNF-09: Pipeline CI/CD obrigatório
- RNF-10: TDD/BDD em funcionalidade crítica
- RNF-11: Cobertura 80% linhas, 70% branches

---

## Condição de Revisão

Esta decisão será revisada se:
1. Tempo de build >15 min (otimizar, paralelizar)
2. SonarCloud inadequado (migrar para outro analisador)
3. Coverage requirement inadequado (aumentar ou diminuir conforme demanda)

---

## Referências

- [arquitetura.md § 12](../docs/arquitetura.md#seção-12-estratégia-de-testes-e-qualidade)
- [RFC - RNF-09, RNF-10, RNF-11](../rfc/requisitos-nao-funcionais.md)
