# Contrato da API — Sistema de Organização de Recursos

Este documento descreve os payloads e códigos de resposta de cada rota. A lista resumida de rotas e perfis está no `README.md`. Tudo o que está aqui foi conferido contra os controladores em `src/main/java/com/organizacao/recursos/api/`.

## Convenções

- **Autenticação:** `Authorization: Bearer <token>`. O token vem de `POST /auth/login` e vale 1 hora.
- **Datas:** ISO-8601 local, `AAAA-MM-DDTHH:MM` (ex.: `2026-11-20T10:00`). Intervalos são semiabertos `[inicio, fim)`.
- **Erros:** RFC 9457 (`application/problem+json`), com `type`, `title`, `status` e `detail`. O `detail` traz a mensagem de negócio ou o campo que falhou. Erros inesperados respondem `500` com mensagem genérica; o detalhe técnico fica só no log.
- **Códigos:** `200` consulta/ação, `201` criação, `204` exclusão, `400` entrada inválida, `401` sem token ou credencial inválida, `403` perfil sem permissão, `404` não encontrado, `409` conflito de estado ou horário.

## Autenticação e usuários

| Método e caminho | Perfil | Corpo | Resposta |
|---|---|---|---|
| `POST /auth/login` | público | `{"id": "...", "senha": "..."}` | `200` `{"token", "expiresIn": 3600, "perfis": [...]}`; `401` para usuário ou senha inválidos (mesma resposta nos dois casos) |
| `POST /usuarios` | Administrador | `{"id", "senha" (mín. 8), "perfis": ["SOLICITANTE" \| "RESPONSAVEL" \| "ADMINISTRADOR"]}` | `201`; `400` para senha curta ou perfil inválido |
| `POST /dev/tokens` | somente com `app.dev.tokens=true` | `{"sub", "perfis": ["SOLICITANTE" \| "RESPONSAVEL"]}` | `200` `{"token"}`; `400` se pedir `ADMINISTRADOR` |

## Recursos

| Método e caminho | Perfil | Corpo | Resposta |
|---|---|---|---|
| `POST /recursos` | Administrador | `{"id", "tipo": "SALA" \| "PROFESSOR" \| "MATERIAL" \| "EQUIPAMENTO", "nome", "localizacao", "capacidade" (≥ 0), "restrito": bool, "status": "DISPONIVEL" \| "INDISPONIVEL" \| "MANUTENCAO", "competencias": [...]}` | `201` |
| `GET /recursos?tipo=&localizacao=&capacidade_min=&competencia=&inicio=&fim=` | autenticado | — (parâmetros de consulta; `competencia` pode repetir) | `200` lista de recursos DISPONIVEIS. Com `inicio` **e** `fim`, exclui os que têm reserva ativa ou manutenção no período |
| `GET /recursos/{id}` | autenticado | — | `200` recurso; `404` se não existir |
| `PUT /recursos/{id}` | Administrador | `{"nome?", "localizacao?", "capacidade?", "restrito?", "status?", "competencias?"}` (campos ausentes mantêm o valor) | `200` recurso atualizado; `400` para nome vazio ou capacidade negativa |
| `DELETE /recursos/{id}` | Administrador | — | `204`; `409` se houver qualquer reserva ligada ao recurso (use `status: INDISPONIVEL`) |
| `POST /vinculos` | Administrador | `{"responsavelId", "recursoId"}` | `201` |
| `POST /manutencoes` | Administrador | `{"recursoId", "inicio", "fim"}` | `201`; `400` se `fim` não for posterior a `inicio` |

**Forma do recurso na resposta:** `{"id", "tipo", "nome", "localizacao", "capacidade", "restrito", "status", "competencias": [...]}`.

## Reservas

| Método e caminho | Perfil | Corpo | Resposta |
|---|---|---|---|
| `POST /reservas` | Solicitante ou Responsável | `{"recursoId", "inicio", "fim"}` | `201`; estado `SOLICITADA` se o recurso for restrito, `APROVADA` se não for; `409` com conflito de horário ou manutenção |
| `GET /reservas/minhas` | autenticado | — | `200` lista das reservas em que o usuário é o solicitante |
| `GET /reservas/pendentes` | Responsável | — | `200` solicitações `SOLICITADA` apenas de recursos vinculados ao Responsável |
| `GET /reservas?recursoId=` | Administrador | — | `200` reservas do recurso |
| `GET /reservas/{id}` | dono, Responsável vinculado ou Administrador | — | `200`; `403` para os demais |
| `PUT /reservas/{id}` | dono | `{"inicio", "fim"}` | `200` (somente em `SOLICITADA`); `409` caso contrário |
| `DELETE /reservas/{id}` | dono | — | `204` (somente em `SOLICITADA`); `409` caso contrário |
| `POST /reservas/{id}/aprovacao` | Responsável vinculado (não dono) | — | `200` estado `APROVADA` |
| `POST /reservas/{id}/rejeicao` | Responsável vinculado (não dono) | `{"justificativa"}` obrigatória | `200` estado `REJEITADA` |
| `POST /reservas/{id}/inicio-uso` | dono ou Responsável | — | `200` estado `EM_USO`; `409` antes do horário de início |
| `POST /reservas/{id}/conclusao` | dono ou Responsável | — | `200` estado `CONCLUIDA` |
| `POST /reservas/{id}/cancelamento` | dono | `{"justificativa"}` obrigatória | `200` estado `CANCELADA`; em `APROVADA`, só antes do início |
| `POST /reservas/{id}/nao-comparecimento` | Responsável | — | `200` estado `NAO_COMPARECEU`; `409` antes do início |
| `POST /reservas/{id}/transicoes` | conforme a transição | `{"destino", "justificativa?"}` | forma genérica das transições acima |
| `GET /reservas/{id}/auditoria` | Responsável ou Administrador | — | `200` lista de `{"acao", "atorId", "instante", "estadoAnterior", "estadoNovo", "justificativa"}`; `403` para o solicitante, inclusive o dono |

**Forma da reserva na resposta:** `{"id", "recursoId", "solicitanteId", "inicio", "fim", "estado"}`. Estados: `SOLICITADA`, `APROVADA`, `EM_USO`, `CONCLUIDA`, `REJEITADA`, `CANCELADA`, `NAO_COMPARECEU`.

## Disponibilidade

| Método e caminho | Perfil | Resposta |
|---|---|---|
| `GET /disponibilidade?recursoId=&inicio=&fim=` | autenticado | `200` `{"recursoId", "inicio", "fim", "disponivel": bool}`. Não revela quem reservou |

## Movimentações de material (RF-09)

| Método e caminho | Perfil | Corpo | Resposta |
|---|---|---|---|
| `POST /movimentacoes/retirada` | Responsável vinculado ao recurso da reserva | `{"reservaId", "materialId", "condicao"}` | `200` movimentação; `409` se a reserva não estiver `APROVADA` ou `EM_USO`, ou se o material já estiver retirado |
| `POST /movimentacoes/devolucao` | Responsável vinculado | mesmo corpo | `200` movimentação; `409` se o material não estiver retirado nesta reserva |
| `GET /movimentacoes/{materialId}` | Responsável ou Administrador | — | `200` histórico em ordem cronológica |

**Forma da movimentação:** `{"materialId", "reservaId", "tipo": "RETIRADA" \| "DEVOLUCAO", "condicao", "instante", "atorId"}`.

## Notificações (RF-11)

| Método e caminho | Perfil | Resposta |
|---|---|---|
| `GET /notificacoes` | autenticado | `200` lista de `{"destinatario", "mensagem", "instante"}` apenas do próprio usuário |

As notificações são geradas na criação de reserva e em cada mudança de estado. São simuladas: registradas no log e em memória (ADR-006).

## Relatórios (RF-12)

| Método e caminho | Perfil | Resposta |
|---|---|---|
| `GET /relatorios/utilizacao?inicio=AAAA-MM-DD&fim=AAAA-MM-DD` | Responsável ou Administrador | `200` com `recursos` (horas por recurso), `cargaHorariaPorProfessor` e `conflitosEvitados`. Regras e exemplo no `README.md` |

## Interface web

`GET /` serve a página responsiva (`src/main/resources/static/index.html`), que consome esta mesma API.
