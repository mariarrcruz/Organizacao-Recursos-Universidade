# Requisitos de desempenho mensuráveis — consulta de disponibilidade e criação de reserva

**Status:** MEDIDO. Os limites p95 ≤ 500 ms e erro < 1 % foram medidos nas rodadas `resultado-02.md` (não atende), `resultado-03.md` (atende, máquina local) e na carga do GitHub (`docs/performance/github-carga-43322490773/`, atende). A equipe ainda não fixou os limites como critério de aceite: a medição em ambiente de referência (três rodadas, seção 5) continua pendente.
**Relaciona-se a:** RNF-03 (consultas), RNF-04 (ações de disparo), RNF-06 (robustez sob carga), D5 em `docs/rtm.md`.

---

## 1. Contexto

Os RNF-03 e RNF-04 (`rfc/requisitos-nao-funcionais.md`) definem tempo máximo de 1 s e objetivo desejado de 500 ms, mas não fixam percentil nem taxa de erro nem carga. Esta proposta transforma esses limites em métricas medíveis com o JMeter, para a consulta de disponibilidade e para a criação de reserva.

## 2. Proposta de requisito

| ID | Operação | Métrica | Limite proposto | Base |
|----|----------|---------|-----------------|------|
| RNF-03-P1 | Consulta de disponibilidade (`GET /reservas?recursoId=`) | p95 do tempo de resposta | **≤ 500 ms** | RNF-03: máximo 1 s, objetivo desejado 500 ms |
| RNF-04-P1 | Criação de reserva (`POST /reservas`) | p95 do tempo de resposta | **≤ 500 ms** | RNF-04: máximo 1 s, objetivo desejado 500 ms |
| RNF-06-P1 | Ambas, na mistura de carga da seção 3 | Taxa de erro | **< 1 %** | RNF-06: estabilidade sob uso simultâneo |
| RNF-06-P2 | Criação de reserva | Respostas fora de {201, 409} | **0** (nenhum 5xx) | RN-03 / DEF-002: concorrência não pode virar erro interno |

**Definições que valem para a medição:**

- **Taxa de erro** = respostas que não são esperadas, ou seja, 5xx, timeouts, erros de conexão e falhas de asserção. A resposta **409 é esperada** na criação concorrente (é a recusa de horário ocupado, RN-02) e **não conta** como erro na taxa. Ela conta no tempo de resposta, porque é uma resposta real do servidor.
- **p95** é calculado sobre todas as amostras da operação durante a janela de medição (após o ramp-up).
- O critério só vale se os testes forem rodados contra um ambiente de referência fixo (ver seção 5).

## 3. Modelo de carga

| Parâmetro | Valor proposto |
|-----------|----------------|
| Usuários simultâneos (threads) | **50** |
| Ramp-up (subida até 50 usuários) | **60 s** |
| Duração em regime (medição) | **180 s** após o ramp-up |
| Duração total do teste | 240 s |
| Temporização entre operações | Constant Timer de **500 ms** por iteração (simula leitura da tela) |
| Mistura de operações por iteração | **75 %** consulta de disponibilidade, **25 %** criação de reserva |
| Recurso | **Um só** recurso (sala), criado no início da rodada com identificador único |
| Horário | **Um só** intervalo (`2026-11-20T10:00` a `11:00`) para todas as criações |
| Usuários fictícios | 50 linhas em `perf/usuarios.csv` (`usuario01` a `usuario50`), sem dados pessoais |

**Por que disputar o mesmo recurso e horário:** é o pior caso de contenção e o cenário que a RN-02 e o DEF-001 exigem. Depois da primeira criação aceita, quase todas as criações seguintes devem responder 409. A carga, portanto, mede principalmente o caminho de recusa sob concorrência, e não só o caminho feliz. Um cenário de uso típico, com recursos e horários distintos, deve ser medido à parte, se o grupo quiser.

## 4. Observações sobre a consulta de disponibilidade

- Hoje `GET /reservas?recursoId=` exige o perfil **ADMINISTRADOR** (`ServicoReservas.listarDoRecurso`). Um **Solicitante** não consegue consultar a disponibilidade pela API, embora o RTM e a persona USR-01 digam que ele deveria consultar. Isso é uma lacuna funcional: a medição usa um token de administrador para essa consulta e o defeito deve ser tratado à parte.
- Não existe endpoint dedicado de disponibilidade. A medição usa a listagem de reservas do recurso como aproximação.

## 5. Ambiente e condições da medição

- Aplicação e PostgreSQL 16 locais (`docker compose up -d` e `app.dev.tokens=true`), sem outros processos pesados na máquina.
- Execução **somente em modo CLI** (`jmeter -n`), contra `localhost:8080`. **Nunca contra ambiente compartilhado.** O plano tem `host` e `porta` parametrizados para evitar apontamento acidental, mas o uso fora de `localhost` está fora desta proposta.
- Uma rodada por execução: cada rodada cria um recurso novo, então rodadas não se contaminam.
- Pelo menos três rodadas para o grupo comparar a variação antes de fixar o limite.

## 6. Pontos para o grupo decidir

1. **Decidido: p95 ≤ 500 ms** para consulta e criação, alinhado ao objetivo desejado de RNF-03/RNF-04. Referência: na rodada-01 (`perf/resultados/rodada-01.jtl`), o p95 foi 326 ms na consulta e 335 ms na criação, dentro do limite.
2. A taxa de erro de 1 % inclui ou não os 409 como sucesso esperado? (Esta proposta: não inclui como erro.)
3. A consulta de disponibilidade deve ser liberada ao Solicitante (ver seção 4) antes de medir o critério oficial?
4. Aceitar a medição em um único recurso como cenário de referência, ou exigir também o cenário distribuído?

## 7. Como reproduzir

```
docker compose up -d
./mvnw -B -DskipTests package
java -jar target/organizacao-recursos-0.1.0-SNAPSHOT.jar --app.dev.tokens=true

jmeter -n -t perf/reservas.jmx -l perf/resultados/rodada-01.jtl -e -o perf/relatorio-01
```

O relatório HTML (`perf/relatorio-*`) e os resultados brutos (`perf/resultados/`) são gerados e **não são versionados**.
