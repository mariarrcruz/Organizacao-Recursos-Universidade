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
APP_BOOTSTRAP_ADMIN_ID=admin APP_BOOTSTRAP_ADMIN_SENHA='uma-senha-com-8-ou-mais' \
  java -jar target/organizacao-recursos-0.1.0-SNAPSHOT.jar
```

Na subida, o esquema é criado no banco (ADR-017) e, se ainda não houver administrador, o primeiro é semeado a partir das variáveis de ambiente. A senha nunca fica no repositório. A aplicação escuta em `http://localhost:8080`.

Para a demonstração e para a carga, pode-se ligar também o token de laboratório (`--app.dev.tokens=true`, `POST /dev/tokens`, sem senha e sem perfil `ADMINISTRADOR`). Fora do laboratório, mantenha desligado.

## 3. Fluxo mínimo (somente pela API)

Não é preciso mexer no banco. Substitua `$ADM` pelo token do administrador.

1. Entrar como administrador:

```
ADM=$(curl -s -X POST localhost:8080/auth/login -H 'Content-Type: application/json' \
  -d '{"id":"admin","senha":"uma-senha-com-8-ou-mais"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])')
```

2. Cadastrar um recurso restrito, um Solicitante e um Responsável:

```
curl -s -X POST localhost:8080/recursos -H "Authorization: Bearer $ADM" -H 'Content-Type: application/json' \
  -d '{"id":"SALA-1","tipo":"SALA","nome":"Sala 1","localizacao":"BLOCO_A","capacidade":30,"restrito":true,"status":"DISPONIVEL","competencias":[]}'
curl -s -X POST localhost:8080/usuarios -H "Authorization: Bearer $ADM" -H 'Content-Type: application/json' \
  -d '{"id":"prof1","senha":"senha-prof-2026","perfis":["SOLICITANTE"]}'
curl -s -X POST localhost:8080/usuarios -H "Authorization: Bearer $ADM" -H 'Content-Type: application/json' \
  -d '{"id":"resp1","senha":"senha-resp-2026","perfis":["RESPONSAVEL"]}'
```

3. Vincular o Responsável ao recurso (D3): `POST /vinculos` com `{"responsavelId":"resp1","recursoId":"SALA-1"}`.

4. O Solicitante entra, pesquisa e reserva: `POST /auth/login`, `GET /recursos?tipo=SALA`, `POST /reservas`. O recurso é restrito, então a reserva nasce `SOLICITADA`.

5. O Responsável entra, vê a fila e aprova: `GET /reservas/pendentes`, depois `POST /reservas/{id}/aprovacao`.

6. O Solicitante recebe a notificação: `GET /notificacoes`.

7. Auditoria da reserva (Responsável ou Administrador): `GET /reservas/{id}/auditoria`.

A página em `http://localhost:8080/` faz os mesmos passos pelo navegador. Contratos completos em [`docs/api.md`](api.md).

## 4. Testes

```
./mvnw -B verify
```

Roda os testes unitários, os cenários BDD contra PostgreSQL (contêiner via Testcontainers), os testes de concorrência, os testes WireMock e os testes end-to-end (`FluxoEndToEndTest`, aplicação em porta real). Requer Docker.

Testes de carga (JMeter) não fazem parte do `verify`. Ver `docs/performance/`.
