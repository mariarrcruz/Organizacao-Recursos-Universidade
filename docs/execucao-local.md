# Execução local (aplicação + PostgreSQL em contêiner)

Pré-requisitos: Docker, Java 17+ (testado com 21), Apache JMeter 5.6 (para o laboratório de carga).

## 1. Banco PostgreSQL

```
docker compose up -d
```

Sobe `postgres:16-alpine` em `localhost:5432` (banco, usuário e senha `organizacao`). Os valores batem com `src/main/resources/application.properties`.

## 2. Aplicação

```
./mvnw -B -DskipTests package
java -jar target/organizacao-recursos-0.1.0-SNAPSHOT.jar --app.dev.tokens=true
```

Na subida, o esquema é criado no banco (ADR-017). A aplicação escuta em `http://localhost:8080`.

`--app.dev.tokens=true` habilita `POST /dev/tokens`, só para laboratório. Ele emite um JWT para o `sub` e os `perfis` informados:

```
curl -s -X POST http://localhost:8080/dev/tokens -H 'Content-Type: application/json' \
     -d '{"sub":"admin1","perfis":["ADMINISTRADOR"]}'
```

Fora do laboratório, mantenha `app.dev.tokens=false`. O login real por credenciais ainda não existe (pendência em `docs/RTM.md`).

## 3. Fluxo mínimo

1. Cadastrar um recurso (perfil ADMINISTRADOR): `POST /recursos`.
2. Solicitar reserva (perfil SOLICITANTE): `POST /reservas`.
3. Aprovar (perfil RESPONSAVEL, só recurso restrito): `POST /reservas/{id}/aprovacao`.
4. Consultar auditoria: `GET /reservas/{id}/auditoria`.

## 4. Testes

```
./mvnw -B verify
```

Roda os testes unitários, os cenários BDD contra PostgreSQL (contêiner via Testcontainers), os testes de concorrência e os testes WireMock. Requer Docker.
