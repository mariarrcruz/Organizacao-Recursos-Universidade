# ADR-009: Cenário Acadêmico de Carga para Testes de Desempenho

**Status:** Aprovado (Bloqueador: Teste JMeter H5)

**Data:** 2026-11-03

---

## Contexto

RNF-06 exige robustez sob carga. Decisão: qual é o volume de teste, quantos usuários simultâneos, qual duração, qual percentil de aceite. Sem cenário documentado, testes são incomparáveis.

---

## Decisão

Definir **cenário acadêmico de referência** para JMeter:

### Volume de Dados

| Parâmetro | Valor |
|-----------|-------|
| Usuários cadastrados | 100 |
| Salas | 50 |
| Professores | 100 |
| Materiais e equipamentos | 200 |
| Reservas históricas | 10.000 |
| Registros de auditoria | 20.000 |

**Total BD:** ~50MB (H2 arquivo)

### Carga Simulada

| Parâmetro | Valor |
|-----------|-------|
| Usuários virtuais simultâneos | 20 |
| Taxa de requisições | 10 req/s |
| Duração medida | 5 minutos |
| Aquecimento (warmup) | 1 minuto |
| Repetições por cenário | 3 |

### Percentil de Aceite

**P95** (95º percentil): 95% das requisições devem estar abaixo do limite.

**Justificativa:** Aceita 5% de outliers; mais realista que P100 (max); mais rigoroso que média.

---

## Cenários de Teste

### 1. Consulta de Disponibilidade (RNF-03)
```
GET /resources?type=SALA&available=true&start=2026-11-10T08:00&end=2026-11-10T17:00
```
- **Limite:** 1s máximo
- **Alvo:** 500ms
- **Métrica:** P95 resposta

### 2. Criação de Reserva (RNF-04)
```
POST /reservations (payload com recurso, intervalo, usuário)
```
- **Limite:** 1s máximo
- **Alvo:** 500ms
- **Métrica:** P95 resposta
- **Validação:** Integridade (nenhuma dupla alocação)

### 3. Aprovação de Recurso Restrito
```
POST /reservations/{id}/approve
```
- **Limite:** 1s máximo
- **Métrica:** P95 resposta

### 4. Cancelamento de Reserva
```
DELETE /reservations/{id}
```
- **Limite:** 1s máximo
- **Métrica:** P95 resposta

### 5. Relatório de Utilização (RNF-05)
```
GET /reports/utilization?start=2026-10-01&end=2026-10-31
```
- **Limite:** 5s máximo
- **Alvo:** 2.5s
- **Métrica:** P95 resposta

### 6. Concorrência: Dupla Reserva (RNF-02 + H1)
```
10+ threads POST /reservations (mesmo recurso, intervalo sobreposto)
```
- **Limite:** Integridade 100% (nenhuma dupla)
- **Esperado:** P95 < 1s; 1 sucesso, 9+ conflicts
- **Validação:** Verificar banco final

---

## Documentação de Resultados

### Formato

```
JMeter Test Report — Cenário de Carga Acadêmico
Data: 2026-11-03
Executado em: Linux Ubuntu 22.04 | Java 11 | H2 em arquivo

Configuração:
- Usuários: 20 simultâneos
- Duração: 5 min (+ 1 min warmup)
- Taxa: 10 req/s
- Repetições: 3

Banco de Dados:
- Tamanho: 50MB
- Registros: 10K reservas, 20K auditoria
- Índices: definir conforme necessário

Resultado — Consulta Disponibilidade:
- Samples: 3000
- Mean: 250ms
- Median: 220ms
- P95: 450ms ✅ (limite: 1s)
- P99: 680ms
- Max: 1200ms

Conclusão: RNF-03 PASSOU (P95 < 1s)
```

---

## Forças Consideradas

### Ganhos
- **Reprodutibilidade:** Outros podem replicar com mesmos dados
- **Comparabilidade:** Baseline para melhorias futuras
- **Realismo:** 10K reservas é perto do real (50 salas × 200 dias)
- **Documentação:** Valores não são inventados

### Trade-offs
- **Número arbitrário:** 20 usuarios é escolha, não requisito
- **P95 vs. P100:** 5% de falhas aceitáveis; pode não satisfazer todos
- **Duração curta (5 min):** Não detecta memory leaks (horas)

---

## Alternativas Descartadas

1. **Sem cenário:** Cada desenvolvedor testa diferente; incomparáveis
2. **Testes infinitos:** Sem limite; pode falhar depois de horas
3. **Média em vez de P95:** Média mascara outliers; inadequado
4. **P100 (max):** Muito rigoroso; poucos sistemas passam

---

## Consequências

### Procedimento de Teste

1. **Preparar dados:** Script SQL para popular 10K reservas, 50 salas, 100 users
2. **Backup BD:** Snapshot antes do teste (garantir consistência)
3. **Executar JMeter:** Script parametrizado `load-test.jmx`
4. **Registrar resultados:** CSV com timestamps, response times
5. **Analisar:** Gerar gráficos (throughput, latência, distribuição)
6. **Validar integridade:** Verificar banco final (nenhuma dupla alocação)

### Integração CI/CD

- Testes de carga **NÃO executam em cada PR** (muito longo)
- Execução **manual** antes de release ou sob demanda
- Resultados **armazenados** em diretório `tests/load-test-results/`
- Comparação **histórica** entre execuções (detectar regressão)

### Manutenção

- Atualizar cenário se:
  - Volume esperado muda (mais usuários, mais salas)
  - SLA muda (limite de resposta)
  - Hardware muda (CPU, RAM)

---

## Riscos

| Risco | Probabilidade | Impacto | Mitigação |
|-------|---------------|--------|-----------|
| Cenário inadequado (muito fácil ou difícil) | Média | Médio | Ajustar dados conforme resultados |
| Hardware afeta resultado | Alta | Médio | Documentar HW; usar CI/CD padrão |
| Teste não detecta memory leak | Média | Médio | Executar testes de 24h separadamente |
| Integridade não é validada | Média | Alto | Verificar banco final em cada teste |

**Bloqueador:** H5 — P95 <1s em consultas e ações; P95 <5s em relatórios.

---

## Requisitos Relacionados

- RNF-03: Consultas <1s (alvo 500ms)
- RNF-04: Ações <1s (alvo 500ms)
- RNF-05: Relatórios <5s (alvo 2.5s)
- RNF-06: Robustez sob carga

---

## Condição de Revisão

Esta decisão será revisada se:
1. Produção exigir mais volume (atualizar para 100K reservas, 500 usuarios)
2. SLA mudar (atualizar limites)
3. Infraestrutura mudar (cloud vs. local)

---

## Referências

- [arquitetura.md § 11](../docs/arquitetura.md#seção-11-desempenho-e-volumes-de-referência)
- [ATAM § Cenário C-04, C-08](../docs/ATAM-analise.md#cenário-c-04-desempenho--consulta-disponibilidade--1s)
- [RFC - RNF-03, RNF-04, RNF-05, RNF-06](../rfc/requisitos-nao-funcionais.md)
