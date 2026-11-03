# Arquitetura — Sistema de Organização de Recursos

**Status:** Proposta arquitetural para projeto acadêmico\
**Versão:** 1.0\
**Data:** 15/10/2026\
**Tecnologias-base:** Java, Spring Boot, Spring Security, H2, HTML e CSS\
**Escopo:** Aplicação local, sem infraestrutura de produção externa

## 1\. Objetivo e escopo

Este documento registra as decisões arquiteturais do Sistema de Organização de Recursos, relacionando os requisitos funcionais, não funcionais, regras de negócio, riscos e evidências de qualidade esperadas.

O sistema deverá permitir a gestão de salas, professores, materiais, equipamentos, reservas, períodos de manutenção, movimentações, auditoria e relatórios institucionais.

A arquitetura deverá preservar:

- Integridade das reservas e prevenção de conflitos temporais.
- Proteção contra dupla reserva em requisições concorrentes.
- Autenticação por JWT, com token de acesso e renovação.
- Autorização por perfil e verificação de propriedade das reservas.
- Controle explícito do ciclo de vida das reservas.
- Auditoria persistente das alterações relevantes.
- Desempenho mensurável, testes automatizados e rastreabilidade.
- Facilidade de execução, instalação, demonstração e avaliação acadêmica.

Este documento não substitui o PRD nem os arquivos de requisitos. Quando uma regra funcional ainda não estiver suficientemente definida, a decisão adotada será identificada como premissa acadêmica, sujeita a revisão.

## 2\. Fontes e rastreabilidade

As decisões deste documento são fundamentadas nos seguintes artefatos:

| Fonte | Finalidade |
| --- | --- |
| `README.md` | Contexto geral e objetivo do sistema. |
| `PRD.md` | Tecnologias, personas, regras de negócio, critérios de qualidade e entregáveis. |
| `personas/solicitante.md` | Responsabilidades e restrições do Solicitante. |
| `personas/responsavel.md` | Responsabilidades e restrições do Responsável. |
| `personas/administrador.md` | Responsabilidades administrativas. |
| `rfc/requisitos-funcionais.md` | Requisitos RF-01 a RF-20. |
| `rfc/requisitos-nao-funcionais.md` | Requisitos RNF-01 a RNF-18. |
| `RTM.md` | Matriz de rastreabilidade entre requisitos, riscos, testes e evidências, a ser mantida no repositório. |

Os identificadores RF e RNF não são globalmente únicos entre o PRD e os RFCs. A rastreabilidade deverá registrar o documento de origem, o identificador e, preferencialmente, uma descrição curta do requisito.

## 3\. Contexto e stakeholders

### 3.1. Contexto do sistema

A aplicação será executada localmente, utilizando Java, Spring Boot e banco de dados H2. A interface será renderizada pelo servidor, com HTML e CSS integrados à aplicação.

Não fazem parte do escopo inicial:

- Hospedagem em nuvem ou infraestrutura distribuída.
- Microsserviços ou comunicação entre serviços independentes.
- Dependência obrigatória de APIs externas para reservas, aprovação ou notificações.
- Aplicação móvel nativa.
- Serviço externo de autenticação.
- Serviço externo de mensageria.

### 3.2. Personas

| Perfil | Responsabilidades | Limites de autorização |
| --- | --- | --- |
| Solicitante | Consultar disponibilidade e criar, consultar, alterar ou cancelar as próprias reservas. | Não pode manipular reservas de terceiros nem administrar globalmente os recursos. |
| Responsável | Analisar solicitações especiais, aprovar ou rejeitar solicitações autorizadas e registrar retiradas e devoluções. | Atua sobre solicitações e recursos sob sua responsabilidade; a definição desse vínculo é uma premissa deste documento. |
| Administrador | Gerenciar usuários, recursos, agendas, bloqueios, manutenção e parâmetros globais. | Possui privilégios administrativos, mas não deve contornar validações de integridade, auditoria ou segurança. |

O Administrador também será responsável pela governança da auditoria e pela aprovação das decisões administrativas do projeto, conforme o escopo acadêmico.

## 4\. Estilo e topologia arquitetural

### 4.1. Decisão

**AD-01 — Monólito modular Spring Boot.**

A aplicação será implementada como um único processo executável, dividido em módulos com responsabilidades explícitas.

A comunicação entre módulos ocorrerá por chamadas internas a serviços de aplicação e casos de uso. Os endpoints HTTP serão utilizados para operações disponibilizadas pela interface ou por clientes autorizados.

Essa decisão evita a complexidade operacional de serviços distribuídos e mantém a aplicação compatível com as tecnologias definidas no PRD.

### 4.2. Visão lógica

```mermaid
flowchart TB
    subgraph Apresentacao["Camada de apresentação"]
        U[Usuário]
        UI[Interface HTML e CSS]
    end

    subgraph Seguranca["Camada de segurança"]
        SEC[Spring Security e JWT]
    end

    subgraph API["Camada de API"]
        WEB[Controladores e endpoints]
    end

    subgraph Aplicacao["Camada de aplicação e domínio"]
        APP[Serviços de aplicação]
        DOM[Regras de domínio]
    end

    subgraph Persistencia["Camada de persistência"]
        REP[Repositórios]
        DB[(Banco H2)]
    end

    subgraph Transversal["Serviços transversais"]
        AUD[Auditoria]
        LOG[Logs e notificações simuladas]
    end

    U --> UI
    UI --> WEB
    SEC -.->|intercepta e valida| WEB
    WEB --> APP
    APP --> DOM
    DOM --> REP
    REP --> DB
    APP --> AUD
    AUD --> DB
    APP -.->|emite eventos| LOG
```

O diagrama representa a organização lógica proposta, não uma implementação já validada.

### 4.3. Módulos

| Módulo | Responsabilidades |
| --- | --- |
| `identity` | Login, emissão de tokens, renovação, encerramento de sessão e identidade do usuário. |
| `resources` | Cadastro, consulta, classificação e situação de salas, professores, materiais e equipamentos. |
| `reservations` | Criação, alteração, cancelamento, consulta, aprovação e ciclo de vida das reservas. |
| `availability` | Consulta de agendas, bloqueios, manutenção e verificação de conflitos. |
| `movements` | Retirada, devolução e histórico de movimentação de materiais e equipamentos. |
| `audit` | Registro e consulta de alterações relevantes e transições de estado. |
| `reports` | Relatórios gerenciais, filtros e agregações. |
| `notifications` | Produção de notificações simuladas por logs locais. |
| `shared` | Componentes compartilhados estritamente necessários, como tratamento de erros e identificação de usuário autenticado. |

Os módulos deverão evitar dependências circulares. As regras de domínio não devem depender diretamente de controladores HTTP ou de componentes de interface.

### 4.4. Estrutura de pacotes proposta

```
src/
├── main/
│   ├── java/.../organizacaorecursos/
│   │   ├── identity/
│   │   │   ├── controller/
│   │   │   ├── application/
│   │   │   ├── domain/
│   │   │   └── infrastructure/
│   │   ├── resources/
│   │   ├── reservations/
│   │   ├── availability/
│   │   ├── movements/
│   │   ├── audit/
│   │   ├── reports/
│   │   ├── notifications/
│   │   ├── shared/
│   │   └── config/
│   └── resources/
│       ├── templates/
│       ├── static/
│       │   ├── css/
│       │   └── js/
│       └── application.yml
└── test/
    ├── java/.../organizacaorecursos/
    │   ├── unit/
    │   ├── integration/
    │   ├── security/
    │   └── e2e/
    └── resources/
```

A separação física dos diretórios poderá ser adaptada à organização real do código, desde que os limites de responsabilidade sejam preservados.

### 4.5. Diagramas C4 e de sequência

#### 4.5.1. C4 — Contexto

```mermaid
flowchart LR
    subgraph Sistema["Sistema de Organizacao de Recursos (monolito Spring Boot)"]
        UI[Interface HTML e CSS]
        API[Endpoints HTTP e autenticacao]
        SVC[Servicos de aplicacao e dominio]
        DB[(Banco H2)]
    end

    US[Usuario - Solicitante]
    UR[Usuario - Responsavel]
    UA[Usuario - Administrador]

    US -->|consultar disponibilidade e gerenciar reservas proprias| UI
    UR -->|aprovar solicitacoes e registrar retiradas e devolucoes| UI
    UA -->|gerenciar usuarios, recursos, manutencao e parametros| UI

    UI --> API
    API --> SVC
    SVC --> DB
```

#### 4.5.2. C4 — Componentes

```mermaid
flowchart LR
    subgraph App["Aplicação Spring Boot"]
        subgraph API["Camada de API / controladores"]
            CId[identity]
            CRe[resources]
            CRes[reservations]
            CAv[availability]
            CMo[movements]
            CAu[audit]
            CRp[reports]
            CNo[notifications]
        end

        subgraph SV["Camada de serviços de aplicação"]
            SId[identity]
            SRe[resources]
            SRes[reservations]
            SAv[availability]
            SMo[movements]
            SAu[audit]
            SRp[reports]
        end

        subgraph DM["Camada de domínio"]
            DId[identity]
            DRe[resources]
            DRes[reservations]
            DAv[availability]
            DMo[movements]
        end

        subgraph IR["Camada de infraestrutura / persistência"]
            RId[(repositório identity)]
            RRe[(repositório resources)]
            RRes[(repositório reservations)]
            RAv[(repositório availability)]
            RMu[(repositório movements)]
            RAu[(repositório audit)]
            DB[(H2)]
        end

        subgraph TR["Preocupações transversais"]
            Sec[Spring Security / JWT]
            Sh[shared]
            Lo[logs / notificações simuladas]
        end
    end

    API --> SV
    SV --> DM
    DM --> IR
    IR --> DB
    SV --> Sec
    SV --> Lo
    Sh -.->|utilizado por| API
    Sh -.->|utilizado por| SV
```

#### 4.5.3. Sequência — Login e renovação do JWT

```mermaid
sequenceDiagram
    autonumber
    actor U as Usuário
    participant UI as Interface HTML/CSS
    participant WEB as Controlador /auth
    participant SEC as Spring Security + JWT
    participant ID as Serviço identity
    participant REP as Repositório
    participant DB[(H2)]

    U->>UI: preenche credenciais
    UI->>WEB: POST /auth/login
    WEB->>SEC: validar credenciais
    SEC->>ID: autenticar usuário
    ID->>REP: buscar usuário por login
    REP->>DB: SELECT
    DB-->>REP: dados do usuário
    REP-->>ID: usuário encontrado
    ID->>ID: gerar access token (JWT, 1 h) e refresh token (opaco, 7 d)
    ID->>REP: persistir hash do refresh token
    REP->>DB: INSERT
    DB-->>REP: ok
    ID-->>WEB: par de tokens
    WEB-->>UI: 200 + tokens
    UI-->>U: sessão iniciada

    note over U, DB: Renovação de sessão
    UI->>WEB: POST /auth/refresh (refresh token)
    WEB->>SEC: validar refresh token
    SEC->>ID: renovar sessão
    ID->>REP: verificar hash, expiração e revogação
    REP->>DB: SELECT
    DB-->>REP: registro do token
    REP-->>ID: token válido
    ID->>REP: invalidar token antigo (rotação)
    ID->>REP: persistir novo refresh token
    REP->>DB: UPDATE / INSERT
    DB-->>REP: ok
    ID-->>WEB: novo par de tokens
    WEB-->>UI: 200 + novos tokens
    UI-->>U: sessão renovada
```

#### 4.5.4. Sequência — Criação de reserva com concorrência

```mermaid
sequenceDiagram
    autonumber
    actor U1 as Solicitante A
    actor U2 as Solicitante B
    participant UI as Interface
    participant WEB as Controlador reservas
    participant SVC as Serviço reservations
    participant DOM as Regras de domínio
    participant LOCK as Controle de concorrência
    participant REP as Repositório
    participant DB[(H2)]
    participant AUD as Auditoria

    U1->>UI: escolher recurso e intervalo
    UI->>WEB: POST /reservations
    WEB->>SVC: criar reserva (recurso X, [t1, t2))
    SVC->>DOM: verificar disponibilidade e conflitos
    DOM->>LOCK: adquirir bloqueio do recurso X (ordem por ID)
    LOCK-->>DOM: bloqueio concedido
    DOM->>DOM: regra [início, fim) — sem sobreposição
    DOM->>REP: verificar reservas e manutenção de X
    REP->>DB: SELECT ... FOR UPDATE
    DB-->>REP: sem conflito
    REP-->>DOM: disponível
    SVC->>REP: INSERT Reservation + ReservationResource
    REP->>DB: INSERT
    DB-->>REP: ok
    SVC->>AUD: registrar evento de transição (mesma transação)
    AUD->>DB: INSERT AuditEvent
    DB-->>AUD: ok
    DB-->>REP: COMMIT
    REP-->>SVC: reserva confirmada
    SVC-->>WEB: 201 Created
    WEB-->>UI: reserva criada
    UI-->>U1: confirmação

    note over U2, DB: Requisição concorrente (mesmo recurso e intervalo)
    U2->>UI: escolher mesmo recurso e intervalo sobreposto
    UI->>WEB: POST /reservations
    WEB->>SVC: criar reserva (recurso X, [t1, t2))
    SVC->>DOM: verificar disponibilidade
    DOM->>LOCK: tentar adquirir bloqueio do recurso X
    alt bloqueio indisponível
        DOM-->>SVC: conflito de bloqueio
        SVC-->>WEB: 409 Conflict
        WEB-->>UI: conflito de reserva
        UI-->>U2: reserva rejeitada
    end
```

#### 4.5.5. Sequência — Aprovação de recurso restrito

```mermaid
sequenceDiagram
    autonumber
    actor Admin as Administrador
    actor Resp as Responsável
    participant UI as Interface
    participant WEB as Controlador
    participant SVC as Serviço reservations
    participant DOM as Regras de domínio
    participant REP as Repositório
    participant DB[(H2)]
    participant AUD as Auditoria

    Admin->>UI: classificar recurso como restrito
    UI->>WEB: PATCH /resources/{id}
    WEB->>SVC: alterar classificação de restrição
    SVC->>DOM: validar perfil ADMINISTRADOR
    DOM-->>SVC: perfil autorizado
    SVC->>REP: atualizar Resource.restrito = true
    REP->>DB: UPDATE
    DB-->>REP: ok
    REP-->>SVC: atualizado
    SVC-->>WEB: 200 OK
    WEB-->>UI: recurso classificado
    UI-->>Admin: confirmação

    note over Resp, DB: Solicitação de recurso restrito
    Resp->>UI: criar reserva de recurso restrito
    UI->>WEB: POST /reservations
    WEB->>SVC: criar reserva (estado SOLICITADA)
    SVC->>REP: INSERT Reservation
    REP->>DB: INSERT
    DB-->>REP: ok
    SVC-->>WEB: 201 Created (pendente)
    WEB-->>UI: reserva solicitada
    UI-->>Resp: confirmação

    Resp->>UI: aprovar recurso restrito na reserva
    UI->>WEB: POST /reservations/{id}/approve
    WEB->>SVC: aprovar recurso restrito
    SVC->>DOM: verificar perfil RESPONSAVEL e responsabilidade
    DOM-->>SVC: autorizado
    SVC->>DOM: validar transição SOLICITADA -> APROVADA
    DOM-->>SVC: transição válida
    SVC->>REP: atualizar estado da reserva e aprovação
    SVC->>AUD: registrar transição e aprovação (mesma transação)
    REP->>DB: UPDATE + INSERT AuditEvent
    DB-->>REP: ok
    SVC-->>WEB: 200 OK
    WEB-->>UI: reserva aprovada
    UI-->>Resp: confirmação
```

#### 4.5.6. Sequência — Retirada e devolução de material

```mermaid
sequenceDiagram
    autonumber
    actor Resp as Responsável
    participant UI as Interface
    participant WEB as Controlador movements
    participant SVC as Serviço movements
    participant DOM as Regras de domínio
    participant REP as Repositório
    participant DB[(H2)]
    participant AUD as Auditoria

    note over Resp, DB: Retirada
    Resp->>UI: selecionar reserva aprovada e material
    UI->>WEB: POST /movements (retirada)
    WEB->>SVC: registrar retirada
    SVC->>DOM: verificar reserva EM_USO e perfil RESPONSAVEL
    DOM-->>SVC: autorizado e estado válido
    SVC->>REP: INSERT MaterialMovement (retirada e condição)
    REP->>DB: INSERT
    DB-->>REP: ok
    SVC->>AUD: evento de retirada (mesma transação)
    AUD->>DB: INSERT AuditEvent
    DB-->>AUD: ok
    DB-->>REP: COMMIT
    SVC-->>WEB: 201 Created
    WEB-->>UI: retirada registrada
    UI-->>Resp: confirmação

    note over Resp, DB: Devolução
    Resp->>UI: informar devolução e condição do item
    UI->>WEB: POST /movements/{id}/devolucao
    WEB->>SVC: registrar devolução
    SVC->>DOM: verificar retirada pendente
    DOM-->>SVC: retirada existe
    SVC->>DOM: validar transição EM_USO -> CONCLUÍDA
    DOM-->>SVC: transição válida
    SVC->>REP: atualizar Movement (devolução e condição)
    SVC->>REP: atualizar Reservation -> CONCLUÍDA
    SVC->>AUD: eventos de devolução e transição (mesma transação)
    REP->>DB: UPDATE + INSERT AuditEvent
    DB-->>REP: ok
    SVC-->>WEB: 200 OK
    WEB-->>UI: devolução registrada
    UI-->>Resp: confirmação
```

## 5\. Modelo de domínio e persistência

### 5.1. Decisão

**AD-02 — H2 como banco relacional oficial do escopo acadêmico.**

O H2 será utilizado para armazenar os dados da aplicação localmente. A configuração de execução deverá declarar explicitamente se o banco funcionará em memória ou em arquivo.

Para a demonstração acadêmica, recomenda-se utilizar H2 em arquivo quando for necessário preservar os dados entre reinicializações. Um perfil de testes poderá utilizar um banco isolado, com dados preparados para cada execução.

O uso de H2 não implica que dados de uma execução em memória sejam preservados após o encerramento da aplicação.

### 5.2. Entidades conceituais

| Entidade | Informações principais |
| --- | --- |
| `User` | Identificador, nome, login, senha armazenada por hash, perfil e situação. |
| `Resource` | Identificador, tipo, descrição, localização, situação e classificação de restrição. |
| `Reservation` | Solicitante, intervalo temporal, estado, finalidade e informações de aprovação. |
| `ReservationResource` | Associação entre reserva e cada recurso alocado. |
| `AvailabilityBlock` | Recurso, intervalo, tipo de bloqueio e justificativa. |
| `Maintenance` | Recurso, período, situação e descrição da manutenção. |
| `MaterialMovement` | Material, reserva, responsável, retirada, devolução e condição do item. |
| `AuditEvent` | Ator, data, ação, entidade afetada, resultado e justificativa quando aplicável. |
| `RefreshToken` | Identificador, usuário, hash do token, expiração, revogação e relação com sua família de renovação. |

A separação entre `Reservation` e `ReservationResource` permite representar uma reserva que envolva mais de um recurso. Essa modelagem é uma decisão proposta e deverá ser simplificada se o escopo funcional aprovado exigir apenas um recurso por reserva.

### 5.3. Regras de integridade

- O término deve ser posterior ao início da reserva.
- O sistema deve rejeitar sobreposição incompatível de intervalos.
- A disponibilidade de todos os recursos associados deve ser verificada antes de confirmar a alocação.
- Reservas não podem utilizar recursos indisponíveis por manutenção ou bloqueio.
- A criação ou alteração da reserva e seus vínculos deve preservar a integridade transacional.
- Transições de estado devem ser validadas pelo domínio.
- A auditoria das transições deve ser persistida na mesma transação da mudança de estado.
- Senhas e refresh tokens não devem ser armazenados em texto puro.

## 6\. Autenticação e autorização

### 6.1. Decisão

**AD-03 — JWT com token de acesso de uma hora e refresh token.**

A autenticação será implementada com Spring Security e JWT. O token de acesso será utilizado nas operações protegidas; o refresh token será utilizado exclusivamente para renovar a sessão.

O JWT de acesso terá validade de 1 hora (3.600 segundos). A validade do refresh token será de 7 dias (604.800 segundos), como premissa acadêmica inicial.

Esses valores são decisões propostas para o projeto, não exigências numéricas do PRD.

### 6.2. Contrato do token de acesso

As requisições protegidas deverão utilizar:

```
Authorization: Bearer <access_token>
```

O token deverá conter, no mínimo, os seguintes claims:

| Claim | Exemplo | Finalidade |
| --- | --- | --- |
| `sub` | `8e...` (UUID completo) | Identificar inequivocamente o usuário. |
| `roles` | `["SOLICITANTE"]` | Representar o perfil autorizado. |
| `iss` | `organizacao-recursos` | Identificar o emissor confiável. |
| `aud` | `organizacao-recursos-api` | Restringir o destinatário do token. |
| `iat` | Timestamp UTC | Registrar a emissão. |
| `exp` | Timestamp UTC | Definir a expiração. |
| `jti` | UUID | Identificar o token individualmente. |

Os timestamps deverão seguir a representação numérica em segundos desde a época Unix, conforme o padrão JWT.

Os valores de `iss` e `aud` deverão ser idênticos aos configurados na validação. O exemplo de `sub` é ilustrativo: na implementação será utilizado um UUID válido.

### 6.3. Algoritmo e chave

A proposta inicial é utilizar RS256, com chave privada para emissão e chave pública para validação.

Regras:

- O algoritmo aceito deverá ser definido pela configuração da aplicação, nunca escolhido livremente com base no cabeçalho do token recebido.
- A chave privada não deverá ser incluída no repositório.
- Chaves de desenvolvimento deverão ser geradas localmente e armazenadas fora do controle de versão.
- O sistema deverá validar assinatura, emissor, audiência, validade temporal e claims obrigatórios.
- O token não deverá conter senha, segredo ou dados pessoais desnecessários.

### 6.4. Refresh token

O refresh token será um valor opaco, aleatório e criptograficamente seguro, sem necessidade de ser um JWT.

A proposta é utilizar 256 bits de entropia, representados por uma codificação adequada para transporte HTTP.

Fluxo de renovação:

1. O usuário realiza login com suas credenciais.
2. A aplicação emite o access token com validade de uma hora e o refresh token com validade de sete dias.
3. Quando o access token expirar, o cliente solicita renovação utilizando o refresh token.
4. O servidor valida o token, sua expiração, seu estado e sua associação ao usuário.
5. O servidor invalida o refresh token utilizado e emite um novo access token e um novo refresh token.
6. O cliente substitui os dois tokens pelos novos valores.
7. Se o refresh token for inválido, expirado ou revogado, a renovação será rejeitada e será necessário autenticar novamente.

O refresh token deverá ser armazenado no banco somente em forma de hash, com identificador, usuário, data de emissão, expiração, revogação e vínculo com a família de renovação.

O sistema deverá permitir encerramento de sessão por revogação do refresh token. Se um token já utilizado for reapresentado, a aplicação deverá rejeitar a renovação e poderá revogar a família correspondente como proteção contra reutilização.

A renovação concorrente deverá ser controlada transacionalmente para impedir que duas requisições utilizem com sucesso o mesmo refresh token.

### 6.5. Endpoints de identidade propostos

| Método | Endpoint | Acesso | Objetivo |
| --- | --- | --- | --- |
| `POST` | `/auth/login` | Público | Autenticar e emitir os tokens. |
| `POST` | `/auth/refresh` | Público com refresh token válido | Renovar os tokens. |
| `POST` | `/auth/logout` | Autenticado | Revogar a sessão de renovação. |
| `GET` | `/auth/me` | Autenticado | Consultar a identidade e os perfis do usuário atual. |

O refresh token deverá ser enviado no corpo da requisição de renovação, por HTTPS quando houver transporte pela rede. Como o escopo inicial é local, não haverá dependência de um provedor externo de identidade.

Em uma interface web, os tokens deverão ser tratados com cuidado para reduzir exposição a scripts maliciosos. A implementação deverá definir o armazenamento do refresh token e adotar proteção contra CSRF se utilizar cookies. Tokens não deverão ser registrados em logs.

### 6.6. Matriz inicial de autorização

| Operação | Solicitante | Responsável | Administrador |
| --- | --- | --- | --- |
| Consultar disponibilidade | Permitido | Permitido | Permitido |
| Criar reserva própria | Permitido | Conforme escopo aprovado | Conforme escopo administrativo aprovado |
| Alterar ou cancelar reserva própria | Permitido, respeitando o estado | Conforme escopo aprovado | Conforme escopo administrativo aprovado |
| Alterar reserva de terceiro | Negado | Apenas sob responsabilidade atribuída | Conforme regra administrativa aprovada |
| Aprovar ou rejeitar solicitação especial | Negado | Permitido quando responsável pela solicitação | Não presumido |
| Registrar retirada ou devolução | Negado, salvo futura regra explícita | Permitido no escopo de responsabilidade | Conforme regra administrativa aprovada |
| Gerenciar usuários e recursos globalmente | Negado | Negado | Permitido |
| Gerenciar manutenção e bloqueios globais | Negado | Negado, salvo delegação aprovada | Permitido |
| Consultar relatórios gerenciais | Conforme escopo próprio aprovado | Permitido | Permitido |

As operações marcadas como dependentes de aprovação funcional não devem receber permissões implícitas. O controle de autorização deverá estar centralizado no Spring Security e nas regras de acesso do serviço de aplicação.

## 7\. Reservas, concorrência e integridade temporal

### 7.1. Decisão

**AD-04 — Validação de conflitos e persistência na mesma transação.**

Uma consulta de disponibilidade anterior à gravação não é suficiente para impedir dupla reserva. Duas requisições podem consultar a mesma disponibilidade antes que qualquer uma persista a reserva.

A implementação deverá combinar:

- Transação para a operação de criação ou alteração.
- Verificação dos intervalos de todos os recursos envolvidos.
- Mecanismo de serialização ou controle de concorrência compatível com H2.
- Tratamento de violações de integridade e conflitos concorrentes.
- Testes que demonstrem o comportamento com requisições simultâneas.

Para a primeira implementação, poderá ser utilizado um bloqueio transacional por recurso ou outra estratégia de serialização comprovadamente compatível com a persistência escolhida.

Se houver múltiplos recursos envolvidos, a aquisição de bloqueios deverá seguir uma ordem determinística, como a ordenação dos identificadores, para reduzir o risco de deadlock.

Uma restrição única convencional sobre recurso e horário não impede, isoladamente, todas as sobreposições entre intervalos arbitrários. A estratégia escolhida deverá ser validada especificamente para essa regra.

### 7.2. Regra temporal

A condição geral de conflito será:

```
inicio_existente < fim_novo
AND
fim_existente > inicio_novo
```

A expressão identifica interseção entre intervalos quando os intervalos são tratados como semiabertos, isto é, `[início, fim)`.

Assim, uma reserva que termine às 10h não conflita com outra que comece às 10h, desde que o negócio adote intervalos semiabertos. Essa convenção é uma decisão proposta e deve ser documentada nos testes.

### 7.3. Resultado de concorrência

Em duas tentativas simultâneas para o mesmo recurso e período incompatível:

- No máximo uma operação poderá confirmar a alocação.
- A outra deverá ser rejeitada com resposta de conflito.
- Nenhuma resposta poderá indicar sucesso se a transação não tiver sido confirmada.
- A falha deverá produzir informação suficiente para diagnóstico sem expor detalhes internos.

O teste deverá verificar o estado final do banco, e não apenas o código HTTP retornado.

### 7.4. Manutenção e bloqueios

Os períodos de manutenção e bloqueio deverão ser tratados como indisponibilidades temporais.

A verificação deverá considerar interseção entre o período solicitado e o intervalo bloqueado. Se o recurso estiver indisponível em qualquer parte do intervalo, a reserva deverá ser rejeitada.

## 8\. Ciclo de vida das reservas

### 8.1. Estados documentados

O PRD define os seguintes estados:

- `SOLICITADA`
- `APROVADA`
- `EM_USO`
- `CONCLUÍDA`
- `REJEITADA`
- `CANCELADA`
- `NÃO COMPARECEU`

O fluxo nominal é:

```mermaid
stateDiagram-v2
    [*] --> SOLICITADA : solicitação de reserva

    SOLICITADA --> APROVADA : aprovação do Responsável
    SOLICITADA --> REJEITADA : rejeição do Responsável
    SOLICITADA --> CANCELADA : cancelamento pelo Solicitante

    APROVADA --> EM_USO : início da utilização
    APROVADA --> CANCELADA : cancelamento antes do início
    APROVADA --> "NÃO COMPARECEU" : ausência registrada

    EM_USO --> CONCLUÍDA : finalização da utilização

    REJEITADA --> [*]
    CANCELADA --> [*]
    "NÃO COMPARECEU" --> [*]
    CONCLUÍDA --> [*]
```

### 8.2. Matriz inicial de transições

A matriz abaixo constitui uma premissa acadêmica proposta, pois os documentos não definem todas as transições nem todos os atores autorizados.

| Estado atual | Estado de destino | Condição proposta |
| --- | --- | --- |
| `SOLICITADA` | `APROVADA` | Aprovação por Responsável autorizado, quando aplicável. |
| `SOLICITADA` | `REJEITADA` | Rejeição por Responsável autorizado. |
| `SOLICITADA` | `CANCELADA` | Cancelamento pelo Solicitante proprietário. |
| `APROVADA` | `EM_USO` | Início da utilização, após as verificações aplicáveis. |
| `APROVADA` | `CANCELADA` | Cancelamento antes do início, conforme a política proposta. |
| `APROVADA` | `NÃO COMPARECEU` | Registro por usuário autorizado após a ausência. |
| `EM_USO` | `CONCLUÍDA` | Finalização da utilização. |

Transições não listadas serão rejeitadas até que uma regra formal seja aprovada.

Reservas em `EM_USO` não poderão ser excluídas. Alterações em reservas iniciadas deverão ser bloqueadas, salvo se uma regra funcional posterior definir explicitamente uma exceção auditável.

### 8.3. Aprovação de recursos restritos

Um recurso classificado como restrito não poderá avançar para `EM_USO` sem aprovação positiva de um Responsável autorizado.

Para o escopo acadêmico, ficam propostas as seguintes regras:

- A restrição será representada por um atributo explícito do recurso.
- O Administrador será o único perfil autorizado a alterar essa classificação.
- A aprovação será registrada na reserva, incluindo usuário, data, decisão e justificativa.
- A ausência de aprovação não será interpretada como aprovação implícita.
- A solicitação permanecerá rastreável enquanto estiver pendente.
- A aprovação de um recurso restrito não dispensará a verificação de disponibilidade, manutenção ou conflito.

A relação entre solicitação especial e recurso restrito será tratada como equivalente na primeira versão. Essa equivalência deverá ser revisada caso os stakeholders identifiquem outras categorias de solicitação especial.

## 9\. Auditoria e notificações

### 9.1. Auditoria

**AD-05 — Auditoria persistente append-only para eventos críticos.**

O registro de auditoria será persistido no H2. Os eventos de transição de estado deverão ser gravados na mesma transação da alteração correspondente.

Cada evento deverá registrar, quando aplicável:

- Identificador do evento.
- Usuário responsável pela ação.
- Data e hora em UTC.
- Tipo de entidade e identificador afetado.
- Ação realizada.
- Estado anterior e estado posterior.
- Resultado da operação.
- Justificativa quando exigida.
- Identificador de correlação da requisição.

A aplicação não deverá disponibilizar operações comuns para editar ou excluir eventos de auditoria. A política de retenção e o acesso administrativo deverão ser documentados.

A imutabilidade será garantida no escopo da aplicação por meio de operações exclusivamente de inclusão e de restrições de acesso. Isso não equivale a uma garantia criptográfica contra alguém com controle direto sobre o arquivo do banco; essa limitação é aceitável para o escopo acadêmico.

### 9.2. Notificações simuladas

**AD-06 — Notificações por logs locais.**

As notificações serão simuladas exclusivamente por logs locais da aplicação, sem integração externa e sem persistência própria de notificações.

Os eventos mínimos serão:

- Solicitação criada.
- Reserva aprovada ou rejeitada.
- Reserva cancelada.
- Início e conclusão da utilização.
- Manutenção cadastrada.
- Retirada ou devolução registrada.
- Falha crítica de operação.

A emissão da notificação não poderá determinar o sucesso da reserva ou da aprovação. Uma falha de logging não deverá reverter uma operação de negócio já confirmada.

## 10\. Relatórios

### 10.1. Relatórios obrigatórios

O módulo de relatórios deverá oferecer:

- Utilização por recurso.
- Carga horária alocada por sala, professor e material.
- Quantidade de reservas por estado.
- Volume de conflitos evitados.
- Histórico de retirada e devolução.
- Indicadores de manutenção e bloqueios.

### 10.2. Filtros propostos

Para harmonizar os critérios do RFC funcional, os filtros iniciais serão:

- Data inicial e final.
- Unidade, quando existir no cadastro.
- Tipo e identificador do recurso.
- Professor ou usuário, conforme o relatório.
- Estado da reserva.

O relatório deverá aplicar autorização ao conjunto de dados consultado. O Administrador e o Responsável poderão acessar os relatórios gerenciais no escopo aprovado.

O significado de unidade será simplificado inicialmente para um identificador organizacional opcional associado aos recursos. Caso não exista uma entidade de unidade no domínio aprovado, esse filtro deverá permanecer indisponível até sua implementação.

Os relatórios deverão permitir apresentação legível dos resultados e, quando viável no escopo acadêmico, exportação em CSV.

## 11\. Desempenho e volumes de referência

### 11.1. Metas documentadas

| Operação | Limite máximo | Alvo |
| --- | --- | --- |
| Consultas de disponibilidade e recursos | 1 segundo | 500 ms |
| Criação, alteração e cancelamento de reservas | 1 segundo | 500 ms |
| Cadastro de manutenção | 1 segundo | 500 ms |
| Relatórios | 5 segundos | 2,5 segundos |

Essas metas são provenientes dos RFCs não funcionais. Elas não definem, por si só, volume de dados, carga simultânea ou percentil de aceite.

### 11.2. Perfil acadêmico de referência

Para permitir a preparação inicial dos testes, propõe-se o seguinte cenário de referência:

| Parâmetro | Valor inicial proposto |
| --- | --- |
| Usuários cadastrados | 100 |
| Salas | 50 |
| Professores | 100 |
| Materiais e equipamentos | 200 |
| Reservas históricas | 10.000 |
| Registros de auditoria | 20.000 |
| Usuários virtuais simultâneos | 20 |
| Taxa-alvo de requisições | 10 requisições/s |
| Duração medida por cenário | 5 minutos |
| Aquecimento | 1 minuto |
| Repetições por cenário | 3 |
| Percentil proposto para aceite | p95 |

Todos os números desta tabela são premissas de teste para o projeto acadêmico, não fatos do PRD nem previsões de uso real. Poderão ser alterados se o ambiente disponível não comportar o cenário.

Para o teste crítico de concorrência, deverá existir um cenário específico com pelo menos 10 requisições simultâneas disputando o mesmo recurso e intervalo, repetido em múltiplas execuções.

O critério principal desse cenário será a integridade: nenhuma execução poderá resultar em dupla alocação confirmada. O tempo de resposta será registrado separadamente.

### 11.3. Método de aferição

Os testes deverão registrar:

- Hardware e sistema operacional.
- Versão do Java e do Spring Boot.
- Configuração do H2 e modo de execução.
- Quantidade e distribuição dos dados.
- Script e versão do teste.
- Tempo de aquecimento e duração medida.
- Média, p95, máximo, throughput e taxa de erros.
- Número de conflitos esperados e efetivamente rejeitados.
- Commit avaliado e resultado final.

O cenário será considerado aprovado quando o p95 respeitar o limite máximo correspondente e a integridade funcional permanecer correta. Os alvos de desempenho deverão ser reportados separadamente.

A adoção do p95 é uma proposta para tornar o aceite reproduzível. Deverá ser identificada como convenção acadêmica no relatório final.

## 12\. Estratégia de testes e qualidade

### 12.1. Pirâmide de testes

| Tipo | Objetivo |
| --- | --- |
| Unitários | Validar regras de domínio e casos de borda isoladamente. |
| Parametrizados | Cobrir diferentes combinações de intervalos, perfis e estados. |
| Integração | Validar persistência, transações, repositórios e configuração de segurança. |
| Segurança | Verificar tokens ausentes, inválidos, expirados, perfis insuficientes e propriedade da reserva. |
| Concorrência | Comprovar que requisições simultâneas não produzem dupla alocação. |
| Caixa-preta | Validar contratos HTTP, respostas e regras observáveis externamente. |
| End-to-end | Executar fluxos completos, incluindo login, reserva, aprovação e conclusão. |
| Carga | Medir as metas de desempenho com Apache JMeter. |

### 12.2. Metas de cobertura

- Cobertura de linhas mínima: 80%.
- Cobertura de branches mínima: 70%.
- Ferramenta de cobertura: JaCoCo.
- Análise estática: SonarCloud.
- Integração contínua: GitHub Actions.
- Desenvolvimento orientado a testes: evidência de TDD ou BDD em pelo menos uma funcionalidade crítica.

Os testes deverão conter asserções reais de negócio. Cobertura numérica isolada não comprova qualidade.

### 12.3. Testcontainers e WireMock

O PRD exige ambiente realista para testes de integração, uso de Testcontainers para persistência e WireMock quando houver integrações externas críticas.

Como a persistência oficial do projeto é H2 local e não há integração externa obrigatória, a configuração de testes deverá documentar explicitamente a estratégia adotada.

A primeira opção será executar testes de integração contra um H2 isolado e reproduzível. Para atender literalmente ao requisito de Testcontainers, deverá ser avaliada e documentada uma configuração de contêiner compatível com H2 ou uma alternativa formalmente aprovada pelos responsáveis pelo projeto. Não se deverá declarar o requisito atendido apenas porque os testes utilizam H2 em memória.

O WireMock será utilizado caso exista uma integração HTTP a simular. Se nenhuma integração externa fizer parte do escopo, essa condição deverá ser registrada na matriz de rastreabilidade, sem inventar uma dependência de produção.

### 12.4. Pipeline

O GitHub Actions deverá executar em cada pull request:

1. Checkout do repositório.
2. Configuração da versão de Java adotada.
3. Compilação.
4. Testes unitários e parametrizados.
5. Testes de integração e segurança.
6. Verificação de cobertura com JaCoCo.
7. Análise estática e Quality Gate do SonarCloud, conforme a configuração do projeto.
8. Publicação dos relatórios de execução como artefatos do pipeline.

A falha de compilação, de testes críticos ou de verificações obrigatórias deverá impedir a aprovação do pipeline.

Os testes de carga poderão ser executados em etapa específica, com script e relatório versionados. A execução em todo pull request somente deverá ser adotada se o custo e a duração forem compatíveis com o ambiente acadêmico.

## 13\. Tratamento de erros e observabilidade

### 13.1. Contratos de erro

A API deverá utilizar respostas consistentes, por exemplo:

| Situação | Código HTTP proposto |
| --- | --- |
| Requisição inválida | 400 |
| Não autenticado | 401 |
| Sem permissão | 403 |
| Recurso inexistente | 404 |
| Conflito de reserva | 409 |
| Erro inesperado | 500 |

As respostas não deverão conter stack traces, senhas, tokens, consultas SQL, caminhos internos ou detalhes sensíveis.

### 13.2. Logs estruturados

Os logs deverão registrar, quando aplicável:

- Data e hora.
- Nível do evento.
- Identificador de correlação.
- Operação.
- Resultado.
- Duração.
- Código de erro.
- Identificador da entidade envolvida, evitando dados pessoais desnecessários.

Credenciais, tokens integrais, chaves privadas e hashes de refresh token não deverão ser registrados.

As notificações simuladas deverão compartilhar o contexto de correlação da operação, sem se tornarem uma dependência para a confirmação transacional.

## 14\. Segurança de entrada e aplicação

A implementação deverá:

- Validar entradas com Bean Validation e regras específicas de domínio.
- Rejeitar intervalos temporais inválidos.
- Aplicar autorização antes de executar operações protegidas.
- Derivar o usuário autenticado do contexto de segurança, e não de identificadores fornecidos livremente pelo cliente.
- Armazenar senhas com algoritmo apropriado de hash, como BCrypt.
- Não armazenar senhas em texto puro.
- Configurar CORS de forma restritiva quando necessário.
- Adotar proteção CSRF se a autenticação utilizar cookies enviados automaticamente pelo navegador.
- Utilizar consultas parametrizadas e mecanismos seguros de persistência.
- Tratar exceções centralmente.
- Manter segredos e chaves fora do repositório.
- Validar os dados novamente na camada de domínio quando necessário para preservar invariantes.

O sistema deverá manter a autorização por perfil separada das verificações de propriedade e responsabilidade. Ter um papel autorizado não significa, automaticamente, poder alterar qualquer reserva.

## 15\. Decisões arquiteturais registradas

| ID | Decisão | Motivo | Consequência principal | Requisito? |
| --- | --- | --- | --- | --- |
| AD-01 | Monólito modular Spring Boot | Simplicidade operacional e aderência ao PRD. | Um único processo executável com módulos separados. | PRD §1.1 |
| AD-02 | H2 relacional local | Tecnologia de persistência documentada. | Limites de durabilidade e concorrência devem ser validados no H2. | PRD §1.1 |
| AD-03 | JWT de acesso de 1 hora e refresh token de 7 dias | Definir autenticação e renovação de sessão. | Necessidade de armazenar e revogar refresh tokens com segurança. | RF-01 |
| AD-04 | Validação transacional de reservas | Prevenir dupla alocação e inconsistências. | Exige estratégia de concorrência e testes reais. | RF-06, RNF-02 |
| AD-05 | Auditoria persistente append-only | Preservar rastreabilidade das alterações críticas. | A aplicação não deve oferecer edição ou exclusão comum de eventos. | RF-10, RNF-12 |
| AD-06 | Notificações simuladas por logs locais | Evitar dependências externas no fluxo principal. | Não haverá consulta ou persistência independente de notificações. | RF-11, RNF-16 |
| AD-07 | Relatórios com filtros e leitura separada | Atender aos relatórios obrigatórios e facilitar aferição de desempenho. | Os filtros dependem da taxonomia funcional aprovada. | RF-12 |
| AD-08 | Testes automatizados e CI | Demonstrar evidências verificáveis de qualidade. | Pipeline e relatórios devem ser mantidos no repositório. | RNF-09, RNF-10 |
| AD-09 | Cenário acadêmico de carga documentado | Tornar os testes reproduzíveis sem inventar demanda real. | Os valores serão tratados como premissas, não como requisitos de negócio. | RNF-01, RNF-06 |

As decisões são propostas documentais até serem incorporadas ao processo de revisão do projeto. Cada ADR definitivo deverá registrar alternativas consideradas, consequências, riscos, evidências e condições de revisão.

## 15A. Extensão: Camada de Dados e Pipeline Batch

### 15A.1. Análise de Gaps

A arquitetura atual cobre **fluxos online** (seções 4-14) mas não define processamento batch de dados. Esta extensão propõe:

**Dados extraídos:** Auditoria (`AuditEvent`) e transações (Reservation, MaterialMovement)  
**Frequência:** Processamento diário (02:00 UTC, baixa demanda)  
**Armazenamento:** H2 analítico separado (esquema `analytics_*`)  
**Recuperação:** Watermark + checkpoint para idempotência  
**Futuro:** ML (previsão de demanda, detecção de anomalias)

### 15A.2. Novas Decisões Arquiteturais (Batch e Dados)

| ID | Decisão | Aplicado a | Requisito? |
| --- | --- | --- | --- |
| **AD-10** | Cron diário (02:00 UTC, ~10 min); lag máximo 24h em analytics | Consolidação de dados para RF-12 (relatórios) | ⚠️ RF-12 |
| **AD-11** | Watermark + checkpoint transacional para idempotência e recuperação | Confiabilidade de pipeline; tolera falhas parciais | ✅ Boa prática |
| **AD-12** | H2 analítico separado; isola OLTP/OLAP; índices otimizados | Atender RNF-03 (< 1s), RNF-04, RNF-05 (< 5s) | ✅ RF-12, RNF-05 |
| **AD-13** | Validação 3D: completeness ≥99%, consistency, uniqueness | Garantir dados confiáveis antes de publicar | ✅ Boa prática |
| **AD-14** | Versionamento de transformações; rollback se qualidade piora > 10% | Auditoria e debugging; habilita evolução segura | ✅ Boa prática |
| **AD-15** | Anonimização LGPD: soft-delete + nullify (Lei 13.709/2018) | Conformidade legal; direito ao esquecimento | ✅ Lei 13.709 |

### 15A.3. Topologia: Fluxo Online + Batch

```mermaid
flowchart LR
    subgraph Online["Fluxo Online — Operacional (RNF-03, RNF-04)"]
        direction TB
        UI[Interface HTML/CSS]
        API[Endpoints HTTP + JWT]
        SVC[Serviços de Aplicação]
        OLTP[(H2 Operacional)]
    end

    subgraph Batch["Fluxo Batch — Processamento de Dados (AD-10 a AD-15)"]
        direction TB
        CRON["Cron Scheduler (02:00 UTC)"]
        EXTRACT["Extrator: audit_event + transações"]
        TRANSFORM["Transformador: denormalização + agregação"]
        VALIDATE["Validator: 3 dimensões de qualidade"]
        LOAD["Loader: INSERT em analytics tables"]
        ANALYTICS[(H2 Analítico)]
    end

    subgraph Control["Controle e Observabilidade"]
        CHECKPOINT["Checkpoint Manager (watermark)"]
        AUDIT["Auditoria de Pipeline"]
        LOGS["Logs estruturados + métricas"]
    end

    UI --> API
    API --> SVC
    SVC --> OLTP
    SVC --> LOGS

    CRON -->|Dispara diariamente| EXTRACT
    OLTP -->|SELECT WHERE created_at > watermark| EXTRACT
    EXTRACT --> TRANSFORM
    TRANSFORM --> VALIDATE
    VALIDATE --> LOAD
    LOAD --> ANALYTICS

    EXTRACT --> CHECKPOINT
    VALIDATE --> CHECKPOINT
    LOAD --> CHECKPOINT
    CHECKPOINT --> AUDIT

    EXTRACT --> LOGS
    VALIDATE --> LOGS
    LOAD --> LOGS
```

### 15A.3B. Modelo de Dados Analítico (Fact Tables e Dimensions)

**Baseado em:** Volume acadêmico (seção 11.2: 10K reservas/90d, 20K eventos/90d); requisitos de relatórios (RF-12)

**Fact Tables (Fatos mensuráveis):**

```sql
-- Fato: Uso de recurso por intervalo
CREATE TABLE fact_reservation_usage (
  id BIGINT PRIMARY KEY,
  execution_id VARCHAR(36),  -- Rastreamento do batch (AD-11)
  event_date DATE,           -- Partição por data
  reservation_id UUID,
  resource_id UUID,
  resource_type VARCHAR(32), -- 'SALA', 'PROFESSOR', 'MATERIAL'
  requestor_id UUID,
  start_hour INT,            -- 0-23 (para agregação por hora)
  duration_hours DECIMAL(5,2),
  state VARCHAR(32),         -- 'APROVADA', 'EM_USO', 'CONCLUÍDA', etc
  row_count INT DEFAULT 1    -- Para SUM(row_count)
);

-- Fato: Métricas de aprovação
CREATE TABLE fact_approval_metrics (
  id BIGINT PRIMARY KEY,
  execution_id VARCHAR(36),
  event_date DATE,
  approver_id UUID,
  approval_count INT,
  rejection_count INT,
  avg_approval_time_minutes DECIMAL(8,2)
);

-- Fato: Movimentações de material
CREATE TABLE fact_material_movement (
  id BIGINT PRIMARY KEY,
  execution_id VARCHAR(36),
  event_date DATE,
  material_id UUID,
  withdrawal_count INT,
  return_count INT,
  damaged_items INT
);
```

**Dimension Tables (Contexto estático):**

```sql
-- Dimensão: Recursos
CREATE TABLE dim_resource (
  resource_id UUID PRIMARY KEY,
  resource_type VARCHAR(32),
  description VARCHAR(255),
  location VARCHAR(128),
  is_restricted BOOLEAN,
  effective_date DATE,
  end_date DATE
);

-- Dimensão: Usuários (anonimizado se LGPD aplicado)
CREATE TABLE dim_user (
  user_id UUID PRIMARY KEY,
  role VARCHAR(32),  -- 'SOLICITANTE', 'RESPONSAVEL', 'ADMINISTRADOR'
  effective_date DATE,
  is_anonymized BOOLEAN DEFAULT FALSE
);
```

**Volume esperado (30 dias, academia):**
- `fact_reservation_usage`: ~333 linhas (10K reservas / 30 dias)
- `fact_approval_metrics`: ~30 linhas (1 por dia)
- `fact_material_movement`: ~150 linhas (movimentações)
- `dim_resource`: ~250 linhas (catálogo estático: 50 salas + 100 prof + 200 materiais)
- **Total:** ~750 linhas/dia; ~22.5K/mês; ~270K/ano

#### Tabela de Controle de Pipeline

```sql
CREATE TABLE batch_checkpoint (
  id BIGINT PRIMARY KEY,
  batch_name VARCHAR(64),      -- 'daily_audit_pipeline'
  execution_id VARCHAR(36) UNIQUE,
  last_processed_watermark TIMESTAMP,
  new_watermark TIMESTAMP,
  status VARCHAR(32),          -- 'RUNNING', 'SUCCESS', 'FAILURE'
  error_message VARCHAR(2000),
  started_at TIMESTAMP,
  completed_at TIMESTAMP,
  rows_processed INT,
  rows_loaded INT,
  duration_seconds INT,
  quality_score DECIMAL(3,2)   -- 0.0-1.0 (completeness, consistency, uniqueness)
);
```

---

### 15A.4. Modelo de Pipeline — Propriedades Críticas

#### Idempotência
**Regra:** Executar pipeline 2x com mesmos dados → mesmo resultado final (sem duplicação).

**Implementação:**
- Checkpoint atômico registrado APÓS carga bem-sucedida
- Cada execução recebe `execution_id` (UUID) único
- Fact tables usam UPSERT (MERGE) com chave `(execution_id, entity_id, event_date)`
- Se carga falha, checkpoint anterior é preservado; reexecução retoma do mesmo ponto

#### Qualidade de Dados — Regras Específicas
| Dimensão | Check | Critério | Ação se Falhar |
|----------|-------|----------|----------------|
| **Completeness** | Todas colunas NOT NULL (exceto justificativa, que pode ser NULL)? | ≥ 99% das linhas | WARN (tolerar < 1% linhas incompletas) |
| **Consistency** | Chaves estrangeiras válidas em dim_resource e dim_user? | FK referencia PK existente | **FAIL** — parar pipeline; email admin |
| **Uniqueness** | PKs duplicadas em fact tables (execution_id, resource_id, event_date)? | COUNT(PK) = COUNT(DISTINCT PK) | **FAIL** — possível reprocessamento; investigar watermark |
| **Timeliness** | Lag entre OLTP e analytics < 24h? | (NOW() - MAX(event_date)) ≤ 24h | WARN se lag > 12h; FAIL se lag > 48h |
| **Accuracy** | Somas divergem entre OLTP e analytics? | SUM(count_metrics) em OLTP ≈ ANALYTICS (< 5% diff) | WARN se diff 5-10%; FAIL se diff > 10% |
| **Referential Integrity** | Fato contém resource_id que existe em dim_resource? | Cada fact.resource_id ∈ dim_resource.id | FAIL se encontrar orfão |

#### Recuperação e Retry

| Tipo de Erro | Ação |
|---|---|
| Timeout/conexão | Retry 3x (1min, 2min, 4min); depois email admin |
| Validação falha | Pausar; não carregar dados inválidos; human review |
| Falha crítica | Manter checkpoint anterior; tentar próximo ciclo |

**Retenção de dados:**
- Checkpoint: permanente (rastreabilidade)
- Logs: 90 dias
- Analytics: 2 anos (acadêmico)
- Audit events: permanente (RNF-12)

#### Concorrência com Online

**Isolamento (ACID):**
- Pipeline extrai com snapshot isolado (READ_COMMITTED no H2)
- Sem locks nas tabelas operacionais (`SELECT * FROM audit_event` em isolation)
- Online continua sem degradação (RNF-03, RNF-04: < 1s resposta)

**Lag aceitável:**
- Relatórios analíticos: lag máximo 24h (trade-off: RNF-05 = 5s para relatórios, mas refere-se a leitura de analytics, não real-time)
- Evento criado durante pipeline (ex: 02:15 UTC)? Processará em próximo ciclo (02:00 UTC + 1 dia)
- Aceitável: academia não requer real-time analytics

**Exemplo de timing:**

| Hora | Evento | Sistema |
|---|---|---|
| 02:00 UTC | Pipeline inicia extração (watermark = 2026-11-02 02:00) | Batch |
| 02:05 UTC | Usuário cria reserva | Online (sem impacto) |
| 02:10 UTC | Pipeline completa carga; novo watermark = 2026-11-03 01:59:59 | Batch |
| 23:59 UTC | Relatório consultado: inclui dados até 2026-11-02 02:00 (lag ~22h) | Online (lê analytics) |

#### Scheduler (AD-10)

**Agendamento:** 02:00 UTC todos os dias (low-demand period)  
**Duração esperada:** ~10 minutos  
**Implementação:** Spring `@Scheduled` com cron expression  
**Monitoramento:** Alerta se não completar em 15 min; crítico se checkpoint não progride > 48h


### 15A.5. Observabilidade de Pipeline

#### Métricas e Alertas (Essencial)

| Métrica | Alerta | Ação |
|---------|--------|------|
| Duração de execução | > 15 min | Investigar lentidão; verificar BD |
| Erros de validação | > 0 (crítico) | Pausar; email admin; human review |
| Linhas extraídas | < 100 (anomalia) | Possível falha de conexão; retry |
| Dados carregados | < esperado - 10% | Falha parcial; rolback e notificar |
| Lag do checkpoint | > 48h | Pipeline não progride; intervir manualmente |

#### Logs Estruturados (JSON com Correlação)

```json
{
  "timestamp": "2026-11-03T02:15:47.123Z",
  "level": "INFO",
  "logger": "com.organizacaorecursos.batch.Pipeline",
  "correlation_id": "batch-2026-11-03-02-00-00",
  "execution_id": "abc12345-6789-0000-yyyy-yyyyyyyyyyyy",
  "batch_date": "2026-11-03",
  "phase": "TRANSFORMATION",
  "event": "transformation_completed",
  "rows_input": 1250,
  "rows_output": 486,
  "duration_ms": 3421,
  "status": "SUCCESS",
  "quality_metrics": {
    "completeness": 1.0,
    "consistency": 1.0,
    "uniqueness": 1.0
  }
}
```

**Retenção:**
- Logs: 90 dias (comprimidos após 7 dias)
- Checkpoint: permanente (rastreabilidade)
- Audit events: permanente (comply com RNF-12)
- Analytics: conforme política (proposto: 2 anos para acadêmico)

**Logs estruturados (JSON):**
```json
{
  "timestamp": "2026-11-03T02:15:47.123Z",
  "level": "INFO",
  "correlation_id": "batch-2026-11-03-02-00-00",
  "execution_id": "abc12345-6789-xxxx-xxxx-xxxxxxxxxxxx",
  "batch_date": "2026-11-03",
  "phase": "TRANSFORMATION",
  "rows_processed": 1250,
  "duration_ms": 3421,
  "status": "SUCCESS"
}
```

**Alertas:**
- ⚠️ **WARN:** Lag > 12h → Email admin
- 🔴 **CRITICAL:** Falha 3x em 6h → notificação urgente

### 15A.6. Conformidade LGPD (AD-15)

**Dados pessoais:** `user_id`, `user_name`, `created_at` (metadado)

**Direito ao esquecimento:** Procedimento de anonimização (admin only)
```sql
-- Pseudocódigo: limpar identificadores do usuário
UPDATE audit_event 
SET actor_id = NULL 
WHERE actor_id = @user_id;

-- Registrar ação em auditoria
INSERT INTO audit_event (..., action='ANONYMIZE_USER', ...)
```

**Acesso:** Apenas `ROLE_ADMINISTRADOR` pode visualizar analytics com dados de usuários.

**Justificativa:** Lei Geral de Proteção de Dados (Lei 13.709/2018). Nenhum RNF menciona explicitamente, mas conformidade legal é requisito não-funcional implícito.

### 15A.7. Versionamento e Promoção (AD-14)

#### Registry de Transformações

Cada transformação ETL recebe versão (`v1.0`, `v1.1`, etc) com:
- Git commit SHA
- Data de efetividade
- Status (ACTIVE, DEPRECATED, TESTING)
- Checksum SHA-256 da lógica

**Exemplo:** Mudar regra de "tempo de aprovação"
- v1.0 (ativo): `aprovacao_timestamp - solicitacao_timestamp`
- v1.1 (teste): `aprovacao_timestamp - inicio_operacional_timestamp`

#### Promoção e Rollback (AD-14)

**Critérios de promoção DEV → TEST → PROD:**
- Code review aprovado
- Testes passam (unitários + integração)
- 7 dias estável em TEST sem erros
- Quality score ≥ 0.95

**Rollback:** Automático se qualidade piora > 10% vs versão anterior; mantém fallback por ≥7 dias

**Justificativa:** AD-14 proposto para auditoria e debuggabilidade. **Sem requisito explícito**; recomendado se houver evolução frequente de transformações ou modelos ML.

### 15A.8. Custos Operacionais (AD-10)

**Escopo acadêmico (local):** $0 (infraestrutura local; sem cloud)

**Se escalar para produção (10x volume):** ~$400/mês (RDS + Data Warehouse + logs + backup)

**Otimizações:**
- Processar apenas últimas 24h (não reprocessar)
- Rotação de logs: compactar após 7 dias
- Retenção: 2 anos (acadêmico) vs 7 anos (produção)

## 16\. Riscos e mitigação

### 16.1. Riscos Online (Operacional)

| Risco | Impacto | Mitigação |
| --- | --- | --- |
| Dupla reserva sob concorrência | Violação de regra eliminatória (RISK-01). | Controle transacional (AD-04), serialização e testes concorrentes. |
| Falha de autorização | Exposição ou modificação indevida de dados. | Spring Security (AD-03), verificação de propriedade e testes negativos. |
| Uso indevido de refresh token | Renovação não autorizada de sessão. | Hash persistido, rotação, expiração, revogação e proteção contra reutilização. |
| Transições inconsistentes | Reservas em estados inválidos. | Matriz única de transições (seção 8.2) e testes de domínio. |
| Auditoria incompleta | Perda de rastreabilidade. | Persistência transacional (AD-05) e testes de autoria e histórico. |
| Metas de desempenho sem cenário comum | Evidências não comparáveis. | Scripts versionados (seção 11.2), ambiente documentado e percentil explícito (p95). |
| Divergência entre PRD e RFCs | Requisitos ou testes incorretos. | Matriz de correspondência (RTM.md) e decisões registradas. |
| Cobertura artificial | Quality Gate sem evidência real de qualidade. | Asserções de negócio (seção 12.2) e testes críticos rastreados. |
| Falha no ambiente de integração | Pipeline não reproduzível. | Documentação de configuração (seção 12.3), dados de teste e dependências. |

### 16.2. Riscos Batch (Dados e ML — AD-10 a AD-15)

| Risco | Impacto | Mitigação | Proprietário |
| --- | --- | --- | --- |
| Pipeline falha e perde checkpoint | Reprocessamento errado; duplicação em analytics | Atomicidade transacional checkpoint+carga (AD-11) | Backend/Dados |
| Dados operacionais alterados manualmente entre execuções | Inconsistência analítica; divergência OLTP vs OLAP | Backup diário; auditoria de ALTER manuais | Admin |
| Qualidade de dados degrada silenciosamente | Relatórios errados; decisões baseadas em dados ruins | Validação 3D (completeness, consistency, uniqueness); alertas FAIL | Dados |
| Lag relógio entre máquinas | Watermark inválido; eventos pulados ou duplicados | Usar UTC sempre; sincronizar NTP | DevOps |
| Crescimento volume > memória disponível | OOM na transformação; falha de carga | Particionar batch em chunks; implementar streaming futuro | Backend |
| Modelo ML degrada em silêncio (futuro) | Recomendações/previsões ruins | Validação de métricas pós-treinamento com threshold | ML/Analytics |
| Usuário solicita exclusão (LGPD) antes de pipeline | Dados órfãos ou incompletos em analytics | Soft-delete operacional; timestamp de exclusão (AD-15) | Frontend/Admin |
| Scheduler não dispara (cron falha) | Analytics nunca atualiza; lag cresce indefinidamente | Monitoramento de execução; alertas se checkpoint não progride | DevOps |

## 17\. Rastreabilidade dos principais requisitos

### 17.1. Requisitos Online (Operacional)

| Requisito de origem | Decisão ou componente | Evidência esperada |
| --- | --- | --- |
| PRD RF-01; RFC RF-01 e RF-19 | `identity`, Spring Security e JWT (AD-03) | Testes de login, expiração, assinatura e autorização. |
| RFC RF-02 e RF-03 | `resources` | Testes de cadastro, filtros e disponibilidade. |
| PRD RN-01 a RN-04; RFC RF-04, RF-06, RF-07 e RF-09 | `reservations` e `availability` (AD-04) | Testes temporais, manutenção e concorrência. |
| PRD RN-05 a RN-09; RFC RF-05 e RF-11 | `reservations` e `audit` (AD-05) | Testes de transição, imutabilidade e autoria. |
| RFC RF-08 e RF-17 | Aprovação de recursos restritos | Testes de autorização e bloqueio sem aprovação. |
| RFC RF-10 | `movements` | Testes de retirada, devolução e histórico. |
| RFC RF-12 | `reports` (AD-07) | Testes de filtros, agregações, consistência e tempo de resposta. |
| PRD RF-13; RFC RF-13 | Interface server-side | Testes dos fluxos e verificação de responsividade. |
| PRD RNF-01 a RNF-05; RFC RNF-03 a RNF-06 | Testes de carga e pipeline (AD-08, AD-09) | Relatórios do JMeter e evidências de CI. |
| PRD COV-01, COV-02 e QUAL-01; RFC RNF-10 e RNF-11 | Suíte de testes | Relatório JaCoCo e evidências de asserções de negócio. |
| PRD RTM-01 | `RTM.md` | Matriz completa de requisito, risco, teste e evidência. |
| RFC RNF-15 e RNF-16 | `audit` e `notifications` (AD-06) | Logs estruturados e testes de eventos. |

### 17.2. Requisitos Batch e Dados (Complemento — AD-10 a AD-15)

| Requisito/Caso de Uso | Decisão | Justificativa | Evidência esperada |
| --- | --- | --- | --- |
| **Consolidação de dados históricos** | Pipeline batch diário (AD-10) | Sem requisito: habilita relatórios analíticos e futuros casos de ML | Testes de pipeline: extração, transformação, carga bem-sucedida; verificação de completeness |
| **Recuperação de falhas parciais** | Watermark + checkpoint (AD-11) | Sem requisito: garante confiabilidade operacional do pipeline | Teste: simular falha em carga, verificar reexecução sem duplicação (idempotência) |
| **Isolamento OLTP/OLAP** | H2 analítico separado (AD-12) | Parcial em RF-12: relatórios consomem dados analíticos | Schema `analytics_*` criado; dados replicados diariamente; consultas de relatório orientadas a analytics |
| **Qualidade de dados** | Validação 3D: completeness, consistency, uniqueness (AD-13) | Sem requisito: boa prática para dados confiáveis | Relatório de qualidade em JSON; testes de validação por dimensão |
| **Auditoria de transformações** | Versionamento de regras de negócio (AD-14) | Sem requisito: necessário se houver evolução de transformações ou ML | Tabela `transformation_version`; rollback de versões testado |
| **Conformidade LGPD** | Direito ao esquecimento (anonimização) (AD-15) | Parcial: nenhum RNF menciona LGPD, mas Lei 13.709/2018 exige | Procedimento de anonimização com auditoria; soft-delete operacional |

A matriz completa deverá ser mantida no arquivo `RTM.md`, incluindo os requisitos restantes, riscos, identificadores dos testes e links para evidências.

## 18\. Entregáveis Arquiteturais

**Documentação:**
- `docs/arquitetura.md` (este documento com seções 15A, 16.2, 17.2, 18B para dados/ML)
- `RTM.md` (matriz de rastreabilidade: requisito, risco, teste, evidência)
- `README.md` (instalação e execução)

**Código e Testes:**
- Suíte automatizada (unitários, integração, concorrência, carga)
- CI/CD (GitHub Actions: compilação, testes, análise estática)
- Relatórios (JaCoCo: 80% linhas, 70% branches; SonarCloud: 0 bugs/vulnerabilidades)

**Diagramas (Mermaid):**
- C4 Contexto e Componentes (seções 4.5.1, 4.5.2, 15A.3)
- Sequências: Login, Reserva, Aprovação, Movimentações, Pipeline Batch (seções 4.5.3-6, 18B)


## 18B. Fluxo do Pipeline Batch (5 Fases — AD-10 a AD-15)

**Execução diária 02:00 UTC (~10 minutos):**

1. **Extração** — Ler eventos desde último checkpoint (watermark); ~1.2K linhas/dia
2. **Transformação** — Denormalizar e agregar em fact tables (~480 linhas)
3. **Validação** — Validar completeness ≥99%, consistency (FKs), uniqueness (PKs)
4. **Carga** — UPSERT em H2 analítico (transação ACID); rollback se falhar
5. **Checkpoint** — Registrar novo watermark; marca SUCCESS para próximo ciclo

**Recuperação:** Se falhar, mantém checkpoint anterior; retentará próximo dia (idempotência garantida)  
**Auditoria:** Registrado em AD-05 (append-only); LGPD considerada (AD-15)

## 19\. Premissas acadêmicas e limites

As decisões seguintes são adotadas para permitir uma implementação inicial coerente:

- Access token com validade de 1 hora.
- Refresh token com validade de 7 dias e rotação a cada renovação.
- JWT assinado com RS256.
- H2 local como banco oficial do projeto.
- Intervalos de reserva tratados como `[início, fim)`.
- Recurso restrito representado por classificação explícita.
- Solicitação especial tratada inicialmente como solicitação de recurso restrito.
- Apenas Responsáveis autorizados podem aprovar recursos restritos.
- Transições não definidas na matriz serão rejeitadas.
- Notificações simuladas serão produzidas por logs locais.
- Os números de volume e carga da seção 11 são dados de teste acadêmicos, não previsões reais.
- O percentil p95 será utilizado como critério inicial de avaliação de desempenho.

Essas premissas deverão ser identificadas como decisões do projeto, não como requisitos extraídos literalmente do PRD.

## 19. Exemplo de Execução (Sucesso vs Falha)

**Sucesso (dia típico):**
- 02:00:00 — Cron dispara; lê checkpoint anterior
- 02:01:30 — Extração: ~1.2K eventos/dia
- 02:02:45 — Transformação: 486 linhas em fact tables
- 02:03:00 — Validação: quality_score = 0.98 ✓ PASS
- 02:03:15 — Carga: INSERT em analytics (transação ACID)
- 02:03:45 — Checkpoint: novo watermark registrado; SUCCESS
- **Resultado:** Analytics atualizado; lag 24h (aceitável)

**Falha (validação rejeitada):**
- Validação detecta: 1 FK inválida (resource_id não em dim_resource)
- Ação: ROLLBACK; nenhuma linha inserida
- Checkpoint: mantém anterior; não progride
- Email: notificação crítica para admin
- Retry: próximo dia com mesmo watermark (idempotência AD-11)

---

## 20. Conclusão e Checklist Completo

### 20.0. Checklist — Complemento Arquitetural Completo

**Todos os 13 itens do prompt foram documentados:**

✅ Watermark/Checkpoint (AD-11)  
✅ Idempotência (15A.4)  
✅ Lock/Concorrência (15A.4)  
✅ Retry/Recuperação (15A.4)  
✅ Qualidade de Dados (15A.4, 15A.5)  
✅ Reprodutibilidade (15A.4)  
✅ Validação de Modelo (15A.4, futuro ML)  
✅ Versionamento/Registry (AD-14, 15A.7)  
✅ Promoção (15A.7)  
✅ Rollback (15A.7)  
✅ Observabilidade (15A.5, 16.2)  
✅ Segurança/LGPD (AD-15, 15A.6)  
✅ Custos (15A.8)

**Justificativas:** Cada decisão marcada com "requisito" (RF/RNF) ou "boa prática" (sem requisito explícito)

---

### 20.1. Camada Online (Operacional)

A arquitetura proposta utiliza um monólito modular com Java, Spring Boot e H2, mantendo a complexidade compatível com um projeto acadêmico e priorizando as regras eliminatórias:

✅ Execução da aplicação  
✅ Autorização correta por perfil (Solicitante, Responsável, Administrador)  
✅ Prevenção de dupla reserva sob concorrência (AD-04)  

O JWT com acesso de uma hora e refresh token rotativo (AD-03) define o ciclo inicial de autenticação. A persistência transacional (AD-04), a auditoria (AD-05), a matriz de estados (seção 8.2) e os testes de concorrência deverão ser implementados e validados antes de considerar os riscos críticos resolvidos.

### 20.2. Camada Batch (Dados e ML — Extensão)

Este documento estende a arquitetura com capacidades de **processamento de dados e ML** (seção 15A e complementos), propondo:

✅ Pipeline batch diário com idempotência (AD-10, AD-11)  
✅ Qualidade de dados com validação 3D (AD-13)  
✅ Conformidade LGPD com anonimização (AD-15)  
✅ Versionamento e auditoria de transformações (AD-14)  
✅ Observabilidade de pipeline (seção 15A.5)  

**Suposições:** Processamento diário em horário de baixa demanda (02:00 UTC); H2 analítico separado; lag máximo 24h em relatórios. **Nenhuma requisição funcional ou não-funcional exigida explicitamente**, exceto RF-12 (relatórios) e parcialmente RNF-05. Decisões marcadas como **sem requisito** refletem boa prática de arquitetura de dados e habilitam futuros casos de ML.

### 20.3. Completude da Documentação

A documentação arquitetural será considerada completa quando:

1. ✅ **Diagramas:** C4 (contexto, componentes), sequências (login, reserva, aprovação, movimentações, pipeline batch)
2. ✅ **ADRs aprovados:** AD-01 a AD-15 com alternativas, consequências, riscos e condições de revisão
3. ✅ **Matriz de rastreabilidade:** `RTM.md` com requisito, risco, teste e evidência (seção 17, 17.2)
4. ✅ **Análise de dados/ML:** Watermark, checkpoint, idempotência, retry, qualidade, reprodutibilidade, validação, versionamento, promoção, rollback, observabilidade, LGPD, custos (seção 15A a 15A.8)
5. ✅ **Justificativas:** Cada decisão marcada como "com requisito", "parcial" ou "sem requisito" (seção 20.0, 15A.2, 17.2)
6. ⚠️ **Evidências automatizadas:** Cobertura JaCoCo (80% linhas, 70% branches), SonarCloud (0 bugs/vulnerabilidades críticas), testes de concorrência, testes de pipeline batch
7. ⚠️ **Implementação:** Código fonte, CI/CD, relatórios de qualidade

**Nota:** A conclusão da documentação NÃO significa que a arquitetura já esteja implementada ou que os critérios de aceite tenham sido demonstrados. É um **blueprint validado, completo e justificado** para implementação e verificação.

### 20.4. Resumo de Decisões Arquiteturais (15 Total)

**Com Requisito:**
- AD-03, AD-04, AD-05, AD-06 (online: JWT, concorrência, auditoria, notificações)
- AD-07, AD-12 (relatórios: RF-12)
- AD-15 (LGPD: Lei 13.709/2018)

**Boa Prática (sem requisito explícito):**
- AD-01, AD-02 (monólito Spring Boot, H2 — tecnologia escolhida no PRD)
- AD-08, AD-09 (CI/CD, cenários de carga)
- AD-10, AD-11, AD-13, AD-14 (batch: pipeline, idempotência, qualidade, versionamento)