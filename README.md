# 🏫 Sistema de Organização de Recursos Universitários

[![Java](https://img.shields.io/badge/Java-17%2B-blue)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen)](https://spring.io/projects/spring-boot)
[![H2 Database](https://img.shields.io/badge/H2%20Database-embedded-lightblue)](https://www.h2database.com/)
[![License](https://img.shields.io/badge/License-MIT-yellow)](LICENSE)

Sistema confiável para alocar salas, professores, materiais e equipamentos universitários **sem conflitos de horário**, com evidências objetivas de qualidade, segurança, rastreabilidade e desempenho.

## 📋 Índice

- [Visão Geral](#visão-geral)
- [Tecnologias](#tecnologias)
- [Pré-requisitos](#pré-requisitos)
- [Instalação](#instalação)
- [Como Executar](#como-executar)
- [Arquitetura](#arquitetura)
- [Estrutura do Projeto](#estrutura-do-projeto)
- [Fluxos e API](#fluxos-e-api)
- [Testes](#testes)
- [Documentação Complementar](#documentação-complementar)

---

## 🎯 Visão Geral

O **Sistema de Organização de Recursos** atende a três perfis de usuário:

| Perfil | Responsabilidades |
|--------|-------------------|
| **Solicitante** | Consultar disponibilidade, criar/alterar/cancelar suas próprias reservas |
| **Responsável** | Aprovar solicitações especiais, registrar retiradas/devoluções de material |
| **Administrador** | Gerenciar usuários, recursos, bloqueios, manutenção e parâmetros globais |

**Destaques técnicos:**
- ✅ Autenticação com JWT (access token 1h + refresh token 7 dias)
- ✅ Prevenção de dupla reserva sob concorrência com transações ACID
- ✅ Auditoria append-only de todas as alterações críticas
- ✅ Relatórios gerenciais com filtros avançados
- ✅ Testes automatizados com cobertura ≥80% (linhas) e ≥70% (branches)
- ✅ Pipeline CI/CD com GitHub Actions
- ✅ Conformidade LGPD (direito ao esquecimento)

---

## 🛠️ Tecnologias

| Componente | Versão | Função |
|------------|--------|--------|
| **Java** | 17+ | Linguagem principal |
| **Spring Boot** | 3.x | Framework backend |
| **Spring Security** | 6.x | Autenticação e autorização |
| **Spring Data JPA** | 3.x | Persistência (ORM) |
| **H2 Database** | 2.x | Banco relacional (local, embedded) |
| **JWT (io.jsonwebtoken)** | 0.12.x | Tokens de autenticação |
| **JUnit 5** | - | Testes automatizados |
| **JaCoCo** | - | Cobertura de código |
| **SonarCloud** | - | Análise estática |
| **Apache JMeter** | - | Testes de carga |
| **HTML 5 / CSS 3** | - | Interface responsiva (server-side) |

---

## 📦 Pré-requisitos

Antes de instalar, certifique-se de que você possui:

- **Java Development Kit (JDK) 17 ou superior**
  - Verifique: `java -version`
  - Download: [oracle.com/java](https://www.oracle.com/java/technologies/downloads/)

- **Maven: não é necessário instalar.** O projeto traz o Maven Wrapper (`./mvnw` em Linux/macOS, `mvnw.cmd` no Windows), que baixa automaticamente o Maven 3.9.9 na primeira execução. Se preferir o Maven instalado, use `mvn` (3.8.1 ou superior).
  - Verifique: `./mvnw -v`
  - Download (opcional): [maven.apache.org](https://maven.apache.org/download.cgi)

- **Git** (para clonar o repositório)
  - Download: [git-scm.com](https://git-scm.com/)

- **Editor ou IDE** (opcional, mas recomendado)
  - [IntelliJ IDEA](https://www.jetbrains.com/idea/)
  - [Eclipse](https://www.eclipse.org/)
  - [VS Code](https://code.visualstudio.com/) + Extension Pack for Java

---

## 📥 Instalação

### 1. Clonar o Repositório

```bash
git clone <URL-DO-REPOSITORIO>
cd Organizacao-Recursos-Universidade
```

### 2. Configurar Variáveis de Ambiente (Opcional)

Crie um arquivo `.env` na raiz do projeto para personalizações:

```env
# Banco de dados (H2)
SPRING_DATASOURCE_URL=jdbc:h2:file:./data/recursos-db
SPRING_DATASOURCE_USERNAME=sa
SPRING_DATASOURCE_PASSWORD=

# JWT
JWT_SECRET_KEY=sua-chave-secreta-rsa-aqui
JWT_ISSUER=organizacao-recursos
JWT_AUDIENCE=organizacao-recursos-api
JWT_ACCESS_TOKEN_EXPIRATION=3600
JWT_REFRESH_TOKEN_EXPIRATION=604800

# Servidor
SERVER_PORT=8080
SERVER_SERVLET_CONTEXT_PATH=/api

# Perfis ativos (dev, test, prod)
SPRING_PROFILES_ACTIVE=dev
```

### 3. Compilar o Projeto

```bash
./mvnw -B clean compile      # Windows: mvnw.cmd -B clean compile
```

Se tudo estiver correto, você verá:
```
[INFO] BUILD SUCCESS
```

### 4. Executar o Build Completo com Testes

```bash
./mvnw -B verify             # Windows: mvnw.cmd -B verify
```

Esperado atualmente: `Tests run: 146, Failures: 0, Errors: 0, Skipped: 0` e `BUILD SUCCESS`.

### 5. Cobertura de Código

O JaCoCo ainda não está configurado no `pom.xml`, então não há relatório de cobertura por enquanto.

---

## 🚀 Como Executar

> **Estado atual:** a implementação existente é a camada de serviços (regras de negócio) em Java puro, sem Spring e sem servidor HTTP. Não há ponto de entrada web ainda, então os comandos `spring-boot:run` e `/health` descritos abaixo são planejados, não funcionais hoje. Para verificar o sistema, use os testes (`./mvnw -B verify`).

### Opção 1: Maven (planejado)

```bash
./mvnw spring-boot:run       # ainda não disponível: requer Spring Boot
```

A aplicação iniciará em: **http://localhost:8080**

### Opção 2: JAR Executável

```bash
# Compilar e criar JAR
mvn clean package -DskipTests

# Executar o JAR
java -jar target/organizacao-recursos-<versao>.jar
```

### Opção 3: IDE

Se estiver usando IntelliJ IDEA ou Eclipse:

1. Abra o projeto
2. Localize a classe principal: `OrganizacaoRecursosApplication.java`
3. Clique em "Run" ou pressione `Shift + F10` (IntelliJ) / `Ctrl + F11` (Eclipse)

### Verificar Status da Aplicação

```bash
curl -X GET http://localhost:8080/health
```

Resposta esperada:
```json
{
  "status": "UP"
}
```

---

## 🏛️ Arquitetura

### Visão Geral

A aplicação segue um **padrão monolítico modular** com separação clara de responsabilidades:

```
┌─────────────────────────────────────────┐
│   Interface HTML / CSS (Server-side)    │
├─────────────────────────────────────────┤
│   Controladores REST + Spring Security  │
├─────────────────────────────────────────┤
│   Serviços de Aplicação (Business Logic)│
├─────────────────────────────────────────┤
│   Regras de Domínio + Entidades         │
├─────────────────────────────────────────┤
│   Repositórios (Spring Data JPA)        │
├─────────────────────────────────────────┤
│   H2 Database (Local, Embedded)         │
└─────────────────────────────────────────┘
```

### Módulos Principais

| Módulo | Responsabilidade |
|--------|------------------|
| **identity** | Autenticação (login/logout), JWT, renovação de sessão |
| **resources** | Gestão de salas, professores, materiais e equipamentos |
| **reservations** | Ciclo de vida das reservas (criar, alterar, cancelar, aprovar) |
| **availability** | Consulta de agendas, detecção de conflitos, verificação de manutenção |
| **movements** | Retirada, devolução e histórico de materiais |
| **audit** | Registro imutável de alterações e transições de estado |
| **reports** | Relatórios gerenciais com filtros e agregações |
| **notifications** | Notificações simuladas por logs estruturados |
| **shared** | Componentes comuns (tratamento de erros, identidade do usuário) |

---

## 📂 Estrutura do Projeto

```
src/
├── main/
│   ├── java/com/organizacaorecursos/
│   │   ├── OrganizacaoRecursosApplication.java (classe principal)
│   │   ├── identity/              (autenticação e JWT)
│   │   │   ├── controller/
│   │   │   ├── application/
│   │   │   ├── domain/
│   │   │   └── infrastructure/
│   │   ├── resources/             (gestão de recursos)
│   │   ├── reservations/          (ciclo de vida de reservas)
│   │   ├── availability/          (detecção de conflitos)
│   │   ├── movements/             (retirada/devolução)
│   │   ├── audit/                 (auditoria)
│   │   ├── reports/               (relatórios)
│   │   ├── notifications/         (eventos e logs)
│   │   ├── shared/                (componentes comuns)
│   │   └── config/                (configurações do Spring)
│   └── resources/
│       ├── application.yml        (configurações padrão)
│       ├── application-dev.yml    (perfil desenvolvimento)
│       ├── application-test.yml   (perfil testes)
│       ├── templates/             (Thymeleaf, HTML)
│       └── static/                (CSS, JS, imagens)
├── test/
│   ├── java/com/organizacaorecursos/
│   │   ├── unit/                  (testes unitários)
│   │   ├── integration/           (testes de integração)
│   │   ├── security/              (testes de segurança)
│   │   └── e2e/                   (testes end-to-end)
│   └── resources/
│       ├── application-test.yml
│       └── test-data.sql
├── pom.xml                        (dependências Maven)
├── README.md                      (este arquivo)
├── .github/workflows/
│   └── ci.yml                     (pipeline GitHub Actions)
└── docs/
    ├── arquitetura.md             (documentação arquitetural)
    ├── PRD.md                     (product requirement document)
    ├── ATAM-analise.md            (análise de arquitetura)
    └── personas/                  (personas do projeto)
```

---

## 🔗 Fluxos e API

### Fluxo de Autenticação

#### 1. Login

**Endpoint:** `POST /auth/login`

**Request:**
```json
{
  "username": "user@universidade.edu",
  "password": "senha123"
}
```

**Response (200 OK):**
```json
{
  "accessToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "opaque-token-256bits...",
  "expiresIn": 3600,
  "tokenType": "Bearer"
}
```

#### 2. Renovar Sessão

**Endpoint:** `POST /auth/refresh`

**Request:**
```json
{
  "refreshToken": "opaque-token-256bits..."
}
```

**Response (200 OK):**
```json
{
  "accessToken": "novo-token-jwt...",
  "refreshToken": "novo-refresh-token...",
  "expiresIn": 3600
}
```

#### 3. Logout

**Endpoint:** `POST /auth/logout`

**Headers:**
```
Authorization: Bearer <accessToken>
```

**Response (204 No Content)**

---

### Fluxo de Reservas

#### 1. Consultar Disponibilidade

**Endpoint:** `GET /availability/resources?type=SALA&date=2026-11-10`

**Response (200 OK):**
```json
[
  {
    "resourceId": "uuid-1",
    "resourceType": "SALA",
    "description": "Sala 101",
    "location": "Bloco A",
    "availableSlots": [
      {"start": "09:00", "end": "11:00"},
      {"start": "14:00", "end": "17:00"}
    ]
  }
]
```

#### 2. Criar Reserva

**Endpoint:** `POST /reservations`

**Headers:**
```
Authorization: Bearer <accessToken>
Content-Type: application/json
```

**Request:**
```json
{
  "resourceIds": ["uuid-1"],
  "startTime": "2026-11-10T09:00:00Z",
  "endTime": "2026-11-10T11:00:00Z",
  "reason": "Aula de Programação",
  "requestorId": "uuid-user"
}
```

**Response (201 Created):**
```json
{
  "id": "uuid-reservation",
  "status": "SOLICITADA",
  "createdAt": "2026-11-03T10:15:00Z",
  "message": "Reserva solicitada com sucesso. Aguardando aprovação."
}
```

#### 3. Listar Minhas Reservas

**Endpoint:** `GET /reservations/me`

**Response (200 OK):**
```json
[
  {
    "id": "uuid-reservation",
    "resources": ["SALA 101"],
    "startTime": "2026-11-10T09:00:00Z",
    "endTime": "2026-11-10T11:00:00Z",
    "status": "SOLICITADA",
    "reason": "Aula de Programação"
  }
]
```

#### 4. Cancelar Reserva

**Endpoint:** `DELETE /reservations/{id}`

**Response (204 No Content)**

---

### Fluxo de Aprovação (Responsável)

#### 1. Listar Solicitações Pendentes

**Endpoint:** `GET /reservations/pending`

**Response (200 OK):**
```json
[
  {
    "id": "uuid-reservation",
    "requestor": "professor@universidade.edu",
    "resources": ["SALA 101"],
    "startTime": "2026-11-10T09:00:00Z",
    "status": "SOLICITADA",
    "isRestricted": true
  }
]
```

#### 2. Aprovar Reserva

**Endpoint:** `POST /reservations/{id}/approve`

**Request:**
```json
{
  "approverNotes": "Aprovado. Recurso disponível."
}
```

**Response (200 OK):**
```json
{
  "id": "uuid-reservation",
  "status": "APROVADA",
  "approvedAt": "2026-11-03T11:00:00Z",
  "approvedBy": "responsavel@universidade.edu"
}
```

#### 3. Rejeitar Reserva

**Endpoint:** `POST /reservations/{id}/reject`

**Request:**
```json
{
  "reason": "Sala indisponível no período solicitado."
}
```

**Response (200 OK):**
```json
{
  "id": "uuid-reservation",
  "status": "REJEITADA",
  "reason": "Sala indisponível no período solicitado.",
  "rejectedAt": "2026-11-03T11:05:00Z"
}
```

---

### Fluxo de Relatórios

#### 1. Utilização por Recurso

**Endpoint:** `GET /reports/utilization?startDate=2026-11-01&endDate=2026-11-30&resourceType=SALA`

**Response (200 OK):**
```json
{
  "period": "2026-11-01 a 2026-11-30",
  "resources": [
    {
      "resourceId": "uuid-1",
      "name": "Sala 101",
      "utilizationRate": 0.78,
      "totalHours": 120,
      "utilizationHours": 94,
      "reservations": 32
    }
  ]
}
```

#### 2. Carga Horária por Professor

**Endpoint:** `GET /reports/workload?startDate=2026-11-01&endDate=2026-11-30&professorId=uuid-prof`

**Response (200 OK):**
```json
{
  "professor": "Prof. João Silva",
  "period": "2026-11-01 a 2026-11-30",
  "totalHours": 48,
  "allocatedHours": 42,
  "utilization": 0.875
}
```

---

### Fluxo de Movimentação de Materiais

#### 1. Registrar Retirada

**Endpoint:** `POST /movements/withdrawal`

**Request:**
```json
{
  "reservationId": "uuid-reservation",
  "materialId": "uuid-material",
  "condition": "BOM",
  "notes": "Material retirado sem problemas"
}
```

**Response (201 Created):**
```json
{
  "movementId": "uuid-movement",
  "type": "WITHDRAWAL",
  "timestamp": "2026-11-03T09:00:00Z"
}
```

#### 2. Registrar Devolução

**Endpoint:** `POST /movements/{movementId}/return`

**Request:**
```json
{
  "condition": "DANIFICADO",
  "notes": "Material com dano pequeno na lateral"
}
```

**Response (200 OK):**
```json
{
  "movementId": "uuid-movement",
  "returnedAt": "2026-11-03T11:30:00Z",
  "finalCondition": "DANIFICADO"
}
```

---

## 🧪 Testes

### Suíte atual (fase green do TDD)

A suíte está em `src/test/java/com/organizacao/recursos/testes/` e cobre os 146 casos definidos em [`docs/testes/plano-tdd.md`](docs/testes/plano-tdd.md) (RN-01 a RN-09, RF-01, RF-02, RF-03, RF-10, RF-12, RF-15 e RF-16). Todos passam: a implementação das regras está em `src/main/java/com/organizacao/recursos/servico/` e não há testes desabilitados. A matriz regra → caso → teste → status está na seção 7 do plano.

Pré-requisitos: JDK 17 ou superior. O `pom.xml` declara o `maven-surefire-plugin` 3.2.5 (necessário para executar testes JUnit 5) e fixa `source`/`target` 17.

```bash
# Build completo com testes (Linux/macOS; no Windows use mvnw.cmd)
./mvnw -B verify

# Rodar uma classe de teste (uma por regra ou requisito)
./mvnw -B -Dtest=RN02ExclusividadeAlocacaoTest test

# Rodar todas as classes de uma regra
./mvnw -B -Dtest='RN03*' test
```

Decisões de regra que ainda dependem de confirmação do product owner estão listadas na seção 7.2 do plano. Hoje não há casos ignorados.

### Testes por categoria (planejado)

As categorias `unit`, `integration`, `security` e `e2e` descritas na arquitetura ainda não existem no projeto; a suíte atual é uma única árvore de testes por regra. Quando existirem, o filtro será feito com tags do JUnit.

### Estrutura de Testes

```
test/
├── unit/                          # Testes isolados de lógica
│   ├── identity/
│   ├── reservations/
│   ├── availability/
│   └── ...
├── integration/                   # Testes com banco de dados
│   ├── ReservationIntegrationTest
│   ├── AuthenticationIntegrationTest
│   └── ...
├── security/                      # Testes de autorização
│   ├── JwtSecurityTest
│   ├── AuthorizationTest
│   └── ...
└── e2e/                          # Fluxos completos
    ├── ReservationFlowTest
    ├── ApprovalFlowTest
    └── ...
```

### Exemplos de Execução de Testes

#### Teste Unitário (Detecção de Conflito)

```java
@Test
void shouldDetectConflict_WhenReservationOverlaps() {
    // Given: duas reservas que se sobrepõem
    Reservation existing = new Reservation(resource, 9, 11);
    Reservation newReservation = new Reservation(resource, 10, 12);
    
    // When: verificar conflito
    boolean hasConflict = availability.hasConflict(newReservation);
    
    // Then: deve detectar conflito
    assertTrue(hasConflict);
}
```

#### Teste de Concorrência (Dupla Reserva)

```java
@Test
void shouldPreventDoubleReservation_WhenConcurrentRequests() {
    // Given: múltiplas threads tentando reservar o mesmo recurso
    ExecutorService executor = Executors.newFixedThreadPool(10);
    
    // When: executar concorrentemente
    List<Future<Reservation>> futures = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
        futures.add(executor.submit(() -> 
            reservationService.create(resource, timeSlot)));
    }
    
    // Then: apenas 1 deve ter sucesso
    long successCount = futures.stream()
        .filter(f -> f.get() != null)
        .count();
    assertEquals(1, successCount);
}
```

#### Teste de Segurança (Autorização)

```java
@Test
@WithoutUser
void shouldRejectUnauthorized_WhenAccessWithoutToken() {
    // Given: sem token JWT
    // When: fazer requisição protegida
    MockHttpServletResponse response = mockMvc.perform(
        get("/reservations/me")
    ).andReturn().getResponse();
    
    // Then: deve retornar 401 Unauthorized
    assertEquals(401, response.getStatus());
}
```

---

## 📊 Pipeline CI/CD

A aplicação usa **GitHub Actions** para automação contínua. Pipeline em `.github/workflows/ci.yml`:

```yaml
name: CI Pipeline

on: [push, pull_request]

jobs:
  build-and-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: 17
      
      - name: Compile
        run: mvn compile
      
      - name: Unit & Integration Tests
        run: mvn test
      
      - name: Code Coverage
        run: mvn jacoco:report
      
      - name: SonarCloud Analysis
        run: mvn sonar:sonar
      
      - name: Upload Artifacts
        uses: actions/upload-artifact@v3
        with:
          name: test-reports
          path: target/surefire-reports/
```

**Verificar status:** [GitHub Actions](../../actions)

---

## 📖 Documentação Complementar

### Arquivos Principais

| Documento | Descrição |
|-----------|-----------|
| [`docs/arquitetura.md`](docs/arquitetura.md) | Decisões arquiteturais, diagramas C4, sequências de fluxos |
| [`docs/PRD.md`](docs/PRD.md) | Requisitos funcionais, não-funcionais, personas e critérios de aceite |
| [`docs/ATAM-analise.md`](docs/ATAM-analise.md) | Análise de trade-offs arquiteturais |
| [`RTM.md`](RTM.md) | Matriz de rastreabilidade (requisito → risco → teste → evidência) |
| [`adr/`](adr/) | Architecture Decision Records (ADRs 001-015) |
| [`rfc/requisitos-funcionais.md`](rfc/requisitos-funcionais.md) | Detalhamento de todos os RF |
| [`rfc/requisitos-nao-funcionais.md`](rfc/requisitos-nao-funcionais.md) | Detalhamento de todos os RNF |

### Personas

- [`docs/personas/solicitante.md`](docs/personas/solicitante.md) — Professor ou coordenador (usuário final)
- [`docs/personas/responsavel.md`](docs/personas/responsavel.md) — Responsável por aprovações
- [`docs/personas/administrador.md`](docs/personas/administrador.md) — Gestor global

---

## 🔐 Segurança

### Principais Características

✅ **Autenticação por JWT** com acesso de 1 hora e refresh de 7 dias  
✅ **Autorização por perfil** (Solicitante, Responsável, Administrador)  
✅ **Transações ACID** para garantir integridade de dados  
✅ **Validação de entrada** com Bean Validation + regras de domínio  
✅ **Senhas com BCrypt** — nunca armazenadas em texto puro  
✅ **Auditoria imutável** de todas as alterações críticas  
✅ **Conformidade LGPD** — direito ao esquecimento implementado  

### Boas Práticas

```bash
# Nunca commit chaves privadas RSA:
echo "config/keys/" >> .gitignore

# Usar variáveis de ambiente para secrets:
export JWT_SECRET_KEY="sua-chave-rsa-aqui"
export SPRING_DATASOURCE_PASSWORD="sua-senha"

# Validar entradas em todas as camadas:
# 1. Controller (Bean Validation)
# 2. Service (regras de negócio)
# 3. Domain (invariantes)
```

---

## 🐛 Suporte e Resolução de Problemas

### Erro: "Port 8080 already in use"

```bash
# Encontrar processo usando porta 8080
lsof -i :8080

# Liberar porta (Linux/Mac)
kill -9 <PID>

# Ou usar porta diferente
java -Dserver.port=8081 -jar target/organizacao-recursos.jar
```

### Erro: "H2 database locked"

```bash
# Remover arquivo de banco corrompido
rm -rf ./data/recursos-db.mv.db
rm -rf ./data/recursos-db.trace.db

# Reiniciar aplicação (recriará banco)
mvn spring-boot:run
```

### Erro: "JWT signature validation failed"

```bash
# Verificar que chave RSA privada está configurada:
export JWT_SECRET_KEY=$(cat config/keys/private-key.pem)

# Confirmar JWT válido no http://jwt.io
```

### Testes falhando localmente?

```bash
# Limpar dependências Maven
mvn clean install

# Executar com logs detalhados
mvn test -X

# Executar teste específico
mvn test -Dtest=ReservationServiceTest
```

---

## 📈 Métricas de Qualidade

Métricas esperadas ao clonar e executar:

| Métrica | Mínimo | Target |
|---------|--------|--------|
| Cobertura de linhas (JaCoCo) | 80% | 85%+ |
| Cobertura de branches | 70% | 75%+ |
| Bugs críticos (SonarCloud) | 0 | 0 |
| Vulnerabilidades (SonarCloud) | 0 | 0 |
| Tempo resposta consultas | 1 seg | 500 ms |
| Tempo resposta ações | 1 seg | 500 ms |

Consulte [SonarCloud Project](../../) para dashboard completo.

---

## 🤝 Contribuindo

1. **Fork** o repositório
2. **Crie uma branch** para sua feature: `git checkout -b feature/minha-feature`
3. **Commit** suas mudanças: `git commit -m "Add minha feature"`
4. **Push** para a branch: `git push origin feature/minha-feature`
5. **Abra um Pull Request** e descreva suas alterações

Todos os PRs devem passar na suite de testes e ter cobertura ≥80%.

---

## 📄 Licença

Este projeto é licenciado sob a **MIT License** — veja [`LICENSE`](LICENSE) para detalhes.

---

## 👥 Autores

**Projeto Acadêmico** — Disciplina de Qualidade de Software e Arquitetura

- **Desenvolvedor:** Iago Souza
- **Instituição:** LWSA / Universidade
- **Data de Criação:** Nov 2026
- **Status:** Em desenvolvimento

---

## 📞 Contato

Para dúvidas ou sugestões:

- 📧 **Email:** iago.souza@lwsa.tech
- 🔗 **GitHub Issues:** [Abrir issue](../../issues)
- 💬 **Discussões:** [Forum](../../discussions)

---

**Última atualização:** Nov 2026  
**Versão:** 1.0.0 (em desenvolvimento)

## Integração contínua (CI)

Workflow: `.github/workflows/ci.yml` (executa `./mvnw -B verify` com Java 21 Temurin em `pull_request` e em `push` na `main`).

Runs de validação na branch `ci/github-actions-maven` (PR #1):

| Run ID | Commit | Resultado | Descrição |
|---|---|---|---|
| 41749229638 | `8be661d` | ✅ success | Workflow inicial, build verde |
| 41749369127 | `8d3e9a9` | ❌ failure | Asserção quebrada de propósito (`RN01OrdemTemporalTest`) |
| 41749482570 | `4d36099` | ✅ success | Reversão da quebra, build verde novamente |
