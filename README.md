# Sistema de Organização de Recursos Universitários

Aloca salas, professores, materiais e equipamentos **sem conflito de horário**, com histórico auditável das reservas. Projeto acadêmico de Qualidade de Software: a entrega é o código, os testes e a documentação de rastreabilidade (`docs/RTM.md`).

Este README descreve **o que a aplicação faz hoje**. O que ainda não existe está listado em [Limitações conhecidas](#limitações-conhecidas) e em `docs/status-requisitos.md`.

---

## Perfis

| Perfil | Pode |
|---|---|
| **Solicitante** | Criar, alterar e excluir (somente em `SOLICITADA`) as próprias reservas; cancelar as próprias reservas; iniciar o uso e concluir as próprias reservas |
| **Responsável** | Aprovar ou rejeitar reservas de outros usuários; registrar não comparecimento; consultar o histórico de qualquer reserva |
| **Administrador** | Cadastrar recursos e manutenções; consultar o histórico de qualquer reserva; listar as reservas de um recurso |

Acesso sem token recebe `401`. Perfil sem permissão recebe `403`. Transição inválida para o estado atual recebe `409`.

---

## Tecnologias

| Componente | Versão | Função |
|---|---|---|
| Java | 17 (`pom.xml`); CI em JDK 21 | Linguagem |
| Spring Boot | 3.2.5 | Aplicação web (Spring MVC) |
| PostgreSQL | 16 | Persistência (`docker-compose.yml`) |
| Testcontainers | 1.20.x | Testes de integração com PostgreSQL real |
| Cucumber | — | Cenários BDD em `src/test/resources/features/` |
| WireMock | — | Simulação do calendário institucional |
| JaCoCo | — | Cobertura: gate de linhas ≥ 80% e ramos ≥ 70% no `verify` |

Autenticação: JWT assinado (`ServicoAutenticacao`), com validação de assinatura, expiração, emissor, audiência e perfis.

---

## Pré-requisitos

- Docker (para o PostgreSQL e para os testes de integração)
- JDK 17 ou superior
- Não é preciso instalar Maven: use o wrapper `./mvnw`

---

## Como executar

O roteiro completo está em [`docs/execucao-local.md`](docs/execucao-local.md). Resumo:

```bash
# 1. Banco PostgreSQL local (usuário, senha e banco: organizacao)
docker compose up -d

# 2. Build e execução
./mvnw -B -DskipTests package
java -jar target/organizacao-recursos-0.1.0-SNAPSHOT.jar --app.dev.tokens=true
```

A aplicação escuta em `http://localhost:8080`. O esquema é criado na subida (`src/main/resources/db/schema.sql`).

### Obter um token (somente laboratório)

Não há login por senha. Com `app.dev.tokens=true`, o endpoint `POST /dev/tokens` emite um JWT para o `sub` e os `perfis` informados:

```bash
curl -s -X POST http://localhost:8080/dev/tokens \
     -H 'Content-Type: application/json' \
     -d '{"sub":"admin1","perfis":["ADMINISTRADOR"]}'
```

Use o valor de `token` como `Authorization: Bearer <token>`. **Fora do laboratório, mantenha `app.dev.tokens=false`** (padrão em `application.properties`). O comando acima com `--app.dev.tokens=true` está documentado só para demonstração.

---

## API

| Método e caminho | Perfil | Descrição |
|---|---|---|
| `POST /dev/tokens` | (somente com `app.dev.tokens=true`) | Emite token de laboratório |
| `POST /recursos` | Administrador | Cadastra recurso (sala, professor, material) |
| `POST /manutencoes` | Administrador | Bloqueia um recurso em um período de manutenção |
| `POST /reservas` | Autenticado | Solicita reserva. Recurso restrito nasce `SOLICITADA`; livre nasce `APROVADA` |
| `GET /reservas?recursoId=` | Administrador | Lista as reservas de um recurso |
| `GET /disponibilidade?recursoId=&inicio=&fim=` | Autenticado | Diz se o recurso está livre no intervalo (`disponivel`: true ou false). Não revela quem reservou |
| `GET /reservas/{id}` | Dono, Responsável, Administrador | Consulta uma reserva |
| `PUT /reservas/{id}` | Dono | Altera o período (somente em `SOLICITADA`) |
| `DELETE /reservas/{id}` | Dono | Exclui (somente em `SOLICITADA`; reserva iniciada não pode ser apagada) |
| `POST /reservas/{id}/aprovacao` | Responsável (não dono) | Aprova |
| `POST /reservas/{id}/rejeicao` | Responsável (não dono) | Rejeita, com justificativa |
| `POST /reservas/{id}/inicio-uso` | Dono ou Responsável | Inicia o uso a partir do horário de início |
| `POST /reservas/{id}/conclusao` | Dono ou Responsável | Conclui o uso |
| `POST /reservas/{id}/cancelamento` | Dono | Cancela, com justificativa; em `APROVADA`, só antes do início |
| `POST /reservas/{id}/nao-comparecimento` | Responsável | Registra não comparecimento, após o início |
| `POST /reservas/{id}/transicoes` | Conforme a transição | Forma genérica de transição |
| `GET /reservas/{id}/auditoria` | Responsável, Administrador | Histórico da reserva: ação, ator, instante, estado anterior e novo, justificativa |
| `GET /relatorios/utilizacao?inicio=&fim=` | Responsável, Administrador | Horas por recurso, carga horária por professor e conflitos evitados no período (ver abaixo) |

### Fluxo mínimo

1. Administrador cadastra um recurso: `POST /recursos`.
2. Solicitante reserva: `POST /reservas`.
3. Se o recurso for restrito, o Responsável aprova: `POST /reservas/{id}/aprovacao`.
4. Qualquer participante autorizado consulta o histórico: `GET /reservas/{id}/auditoria`.

### Regras de integridade

- **Sem dupla reserva.** Uma reserva ativa (`SOLICITADA`, `APROVADA` ou `EM_USO`) não pode se sobrepor a outra do mesmo recurso. A garantia está no PostgreSQL (restrição `EXCLUDE` com `tstzrange`) e num lock por recurso. Intervalos são semiabertos `[início, fim)`.
- **Manutenção bloqueia reserva.** Período de manutenção sobreposto recusa a reserva com `409`.
- **Auditoria append-only.** Cada mudança de estado grava um registro na mesma transação da mudança. Um trigger no banco recusa `UPDATE` e `DELETE` em `evento_auditoria`. A API não tem rota para alterar nem apagar auditoria: `PUT` e `DELETE` em `/reservas/{id}/auditoria` retornam `405`.

---

### Relatório de utilização

`GET /relatorios/utilizacao?inicio=AAAA-MM-DD&fim=AAAA-MM-DD` (dias inclusivos; `fim` não pode ser anterior a `inicio`, ou a resposta é `400`).

Regras de contagem:

- **Horas** contam só reservas em `APROVADA`, `EM_USO` e `CONCLUIDA`. Canceladas, rejeitadas, pendentes (`SOLICITADA`) e não comparecimentos não contam como uso.
- **Recorte pelo período.** Cada reserva conta só a parte que cai entre `inicio 00:00` e `fim + 1 dia 00:00`. Reserva que cruza a borda entra com o trecho interno.
- **Carga horária por professor** é o total de horas dos recursos do tipo `PROFESSOR`.
- **Conflito evitado** é uma tentativa recusada (`409`) por sobreposição com reserva cujo intervalo cruza o período. Recusas por manutenção não entram.

Exemplo de resposta, com a massa do teste `massaSemeada_retornaHorasCargaEConflitos` (`RelatorioUtilizacaoContrato`):

```json
{
  "inicio": "2026-11-05",
  "fim": "2026-11-05",
  "recursos": [
    {"recursoId": "PROF-1", "tipo": "PROFESSOR", "horas": 2.0},
    {"recursoId": "SALA-A", "tipo": "SALA", "horas": 4.0}
  ],
  "cargaHorariaPorProfessor": [{"professorId": "PROF-1", "horas": 2.0}],
  "conflitosEvitados": {
    "total": 2,
    "porRecurso": [
      {"recursoId": "PROF-1", "quantidade": 1},
      {"recursoId": "SALA-A", "quantidade": 1}
    ]
  }
}
```

Códigos: `401` sem token, `403` para Solicitante, `400` para período inválido.

## Arquitetura

```
src/main/java/com/organizacao/recursos/
├── api/            Controladores HTTP (Spring MVC) e tratamento de erros
├── servico/        Regras de negócio: ServicoReservas, ServicoRecursos, ServicoAutenticacao, ...
├── dominio/        Modelo: Reserva, Recurso, EventoAuditoria, EstadoReserva, ...
├── persistencia/   Porta Armazenamento; implementações ArmazenamentoPostgres e ArmazenamentoMemoria
└── integracao/     Cliente do calendário institucional (WireMock nos testes)
```

Decisões arquiteturais estão em [`docs/adr/`](docs/adr/) e em [`docs/arquitetura.md`](docs/arquitetura.md). Pontos principais:

- **ADR-005:** auditoria append-only transacional.
- **ADR-016:** restrição `EXCLUDE` no PostgreSQL contra dupla reserva.
- **ADR-017:** persistência em PostgreSQL atrás de uma porta de armazenamento.

---

## Testes

```bash
./mvnw -B verify
```

Requer Docker, porque os testes de persistência e os cenários BDD sobem PostgreSQL com Testcontainers. O comando roda:

- testes unitários de regras (`testes/`, `servico/`);
- testes de API com MockMvc (`api/`);
- testes de persistência e concorrência contra PostgreSQL real (`persistencia/`);
- cenários BDD em `src/test/resources/features/`;
- gate de cobertura JaCoCo.

Os testes seguem o ciclo vermelho → verde → refatoração; os commits `test(red)` e `feat(green)` mostram a sequência. Rastreabilidade requisito → teste → commit em [`docs/RTM.md`](docs/RTM.md) §5.3.1.

---

## Integração contínua

`.github/workflows/ci.yml` roda `./mvnw -B verify` em cada pull request e em cada push para `main`, em JDK 21. A análise do SonarCloud roda após o `verify` quando o segredo está disponível. Os runs mais recentes estão verdes (verificado com `gh run list`).

---

## Documentação

| Documento | Conteúdo |
|---|---|
| [`docs/PRD.md`](docs/PRD.md) | Requisitos do produto e regras de negócio (RN-01 a RN-09) |
| [`rfc/`](rfc/) | Requisitos funcionais e não funcionais |
| [`docs/RTM.md`](docs/RTM.md) | Matriz de rastreabilidade requisito → teste → commit |
| [`docs/status-requisitos.md`](docs/status-requisitos.md) | Estado auditado de cada requisito e pendências |
| [`docs/execucao-local.md`](docs/execucao-local.md) | Roteiro de execução local |
| [`docs/adr/`](docs/adr/) | Decisões arquiteturais |
| [`docs/testes/`](docs/testes/) | Plano de testes, evidências de TDD e BDD |
| [`docs/performance/`](docs/performance/) | Requisitos de desempenho e resultados do JMeter |

---

## Limitações conhecidas

Detalhes e ações em `docs/status-requisitos.md`, seção 5.

- **Sem login real.** A identidade é o que o emissor de token declara. O endpoint de token de laboratório deve ficar desligado fora da demonstração (PEN-01).
- **Responsável não é vinculado a recursos.** Qualquer Responsável aprova qualquer reserva restrita (PEN-01c).
- **Sem gestão de recursos além do cadastro.** Não há `GET /recursos`, `PUT` nem `DELETE` (PEN-05).
- **Sem interface web.** A API é o único ponto de entrada (PEN-06).
- **Sem movimentações (retirada e devolução) e sem os demais relatórios HTTP** (carga por sala, contagem por estado, movimentações). O relatório de utilização já está disponível (PEN-07).
- **Sem notificações nem lista de pendentes** (PEN-08).
- **Recursos não são auditados.** A auditoria cobre só as reservas.

---

## Personas

Descrições em [`docs/personas/`](docs/personas/): `solicitante.md`, `responsavel.md`, `administrador.md`.
