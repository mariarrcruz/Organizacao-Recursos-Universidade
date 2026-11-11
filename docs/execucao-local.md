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
     -d '{"sub":"prof1","perfis":["SOLICITANTE"]}'
```

O token de laboratório não emite `ADMINISTRADOR` (D4): a requisição responde `400`. Por isso as operações de Administrador (cadastro de recursos, manutenções e vínculos de Responsável) não são alcançáveis pela API. Prepare-as direto no banco, como no passo 3. Fora do laboratório, mantenha `app.dev.tokens=false`. O login real por credenciais ainda não existe (PEN-01b).

## 3. Fluxo mínimo

1. Cadastrar um recurso direto no banco (a API não emite token de Administrador):

```
docker compose exec postgres psql -U organizacao -d organizacao -c \
  "INSERT INTO recurso (id, tipo, nome, localizacao, capacidade, restrito, status, competencias) VALUES ('SALA-1','SALA','Sala 1','BLOCO_A',30,true,'DISPONIVEL','');"
```
2. Solicitar reserva (perfil SOLICITANTE): `POST /reservas`.
3. Vincular o Responsável ao recurso, também no banco (D3):

```
docker compose exec postgres psql -U organizacao -d organizacao -c \
  "INSERT INTO vinculo_responsavel (responsavel_id, recurso_id) VALUES ('resp1','SALA-1');"
```

4. Aprovar (perfil RESPONSAVEL vinculado ao recurso, só recurso restrito): `POST /reservas/{id}/aprovacao`.
4. Consultar auditoria: `GET /reservas/{id}/auditoria`.

## 4. Testes

```
./mvnw -B verify
```

Roda os testes unitários, os cenários BDD contra PostgreSQL (contêiner via Testcontainers), os testes de concorrência e os testes WireMock. Requer Docker.
