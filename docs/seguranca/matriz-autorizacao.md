# Matriz de autorização da API

Análise de segurança da API HTTP (`src/main/java/com/organizacao/recursos/api/`). Versão atual: branch `fix/disponibilidade-solicitante` (commit `4ffaec0`), a partir de `main` em `b8dfbb8`.

**Fonte da resposta esperada:** a especificação do projeto (`afonsolelis.github.io/aulas_senac/pages/qualidade2/especificacao-projeto.html`), complementada pelo RFC RF-15/RF-16 e pelo plano de testes do repositório quando a especificação não decide. Onde nenhuma das duas decide, a célula está marcada como lacuna (❓), e o código não é tratado como divergente.

**Legenda**

| Símbolo | Significado |
|---|---|
| ✅ | Código conforme à matriz |
| ❌ | Código diverge da matriz (resposta diferente ou rota ausente) |
| ⚠️ | Conforme no comportamento, mas com ressalva de segurança ou de modelagem |
| ❓ | Regra não escrita ou pendente de decisão (P7, AMB-…); o código decide, e a matriz não confirma |

Códigos: `200` consulta/ação, `201` criação, `204` exclusão, `401` sem autenticação válida, `403` autenticado sem perfil ou posse, `404` rota inexistente, `405` método sem rota.

---

## 1. Como a autorização está implementada

**Não há Spring Security no projeto.** O `pom.xml` não tem `spring-boot-starter-security` e nenhuma classe usa `SecurityFilterChain`, `HttpSecurity` ou `@EnableWebSecurity`. Em consequência:

- Não existe regra declarativa por rota. Toda autorização é código.
- Cada controller chama `ServicoAutenticacao.autenticar(Authorization)`, que devolve `401` sem token válido.
- Perfil e posse são checados nos serviços (`ServicoReservas`, `ServicoRelatorios`, `ServicoMovimentacoes`), com `Autorizacao.exigirPerfil`, `exigirAlgumPerfil` e a checagem de dono (`ehDono`).
- Nas transições de reserva, a ordem é: estado permitido (`409`) antes de perfil e posse (`403`). Uma operação inválida para o estado responde `409` mesmo para quem não tem permissão.
- Corpo malformado ou ausente é resolvido pelo Spring antes da autenticação. Nesse caso a resposta é `400`, mesmo sem token. As tabelas abaixo assumem corpo válido.

Papéis usados nas colunas: **Anônimo** (sem header), **Solicitante** (dono da reserva quando a operação exige posse), **Responsável**, **Administrador**. Os casos de Solicitante que **não** é dono estão na seção 3.

---

## 2. Matriz por operação

| # | Operação | Anônimo | Solicitante | Responsável | Administrador | Divergência e observação |
|---|---|---|---|---|---|---|
| 1 | `POST /dev/tokens` | 404 em produção ⚠️ | — | — | — | Laboratório apenas (`app.dev.tokens=true`). Com o flag ligado, qualquer anônimo obtém token de **qualquer perfil**, inclusive `ADMINISTRADOR` (PEN-01a). Padrão do projeto: desligado. Ver seção 5. |
| 2 | `POST /recursos` | 401 ✅ | 403 ✅ | 403 ✅ | 201 ✅ | Conforme RF-15.A.1. |
| 3 | `POST /manutencoes` (bloqueio) | 401 ✅ | 403 ✅ | 403 ✅ | 201 ✅ | Conforme RF-15.A.7, A.8 e R.9. |
| 4 | `POST /reservas` | 401 ✅ | 201 ✅ | 201 ❓ | 201 ❓ | Solicitante cria a própria reserva (RF-15.S.1). Responsável: escopo a esclarecer (RF-15.R.7). Administrador: sem regra escrita. O código aceita qualquer autenticado, sem checagem de perfil (seção 4). |
| 5 | `GET /reservas?recursoId=` | 401 ✅ | 403 ✅ | 403 ✅ | 200 ✅ | Lista reservas com o solicitante, por isso é restrita ao Administrador. A consulta de disponibilidade do Solicitante é a linha 5b. |
| 5b | `GET /disponibilidade?recursoId=&inicio=&fim=` | 401 ✅ | 200 ✅ | 200 ✅ | 200 ✅ | Resposta só com `disponivel` true ou false, sem revelar quem reservou. Especificação: "O Solicitante consulta a disponibilidade" (corrigido, commit `4ffaec0`, antes ❌ D1). |
| 6 | `GET /reservas/{id}` | 401 ✅ | 200 se dono; 403 se terceiro ✅ | 200 ❓ | 200 ✅ | A especificação não diz quem lê reservas de terceiros. O Responsável lê qualquer reserva; o vínculo "sob sua responsabilidade" (RF-15.R.2) não está definido na especificação (lacuna, ver seção 4). |
| 7 | `PUT /reservas/{id}` (alterar período) | 401 ✅ | 200 se dono e `SOLICITADA`; 403 se terceiro ✅ | 403 ✅ | 403 ✅ | Alteração é só do dono (RF-16.1 e 16.2). Fora de `SOLICITADA`, `409`. |
| 8 | `DELETE /reservas/{id}` | 401 ✅ | 204 se dono e `SOLICITADA`; 403 se terceiro ✅ | 403 ✅ | 403 ✅ | Só `SOLICITADA` é excluível (RN-08). Dono em `APROVADA` recebe `409`; a regra da RF-16.3 é "depende de estado" (❓). |
| 9 | `POST /reservas/{id}/aprovacao` | 401 ✅ | 403 ✅ | 200 ✅ | 403 ✅ | Especificação: "Somente responsáveis aprovam recursos restritos". Responsável aprova; o próprio dono não (RN-07). Vínculo com recurso não definido na especificação (lacuna). |
| 10 | `POST /reservas/{id}/rejeicao` | 401 ✅ | 403 ✅ | 200 ❓ | 403 ✅ | A especificação não diz quem rejeita (lacuna). O código dá a rejeição ao Responsável, pela mesma regra da linha 9. Justificativa obrigatória (`400`), checada depois do perfil. |
| 11 | `POST /reservas/{id}/inicio-uso` | 401 ✅ | 200 ❓ | 200 ❓ | 403 ✅ | Ator de início de uso é pendente (P7, em RTM §3.14). O código permite dono ou Responsável. `409` antes do horário de início. |
| 12 | `POST /reservas/{id}/conclusao` | 401 ✅ | 200 ❓ | 200 ❓ | 403 ✅ | Mesma regra da linha 11. |
| 13 | `POST /reservas/{id}/cancelamento` | 401 ✅ | 200 se dono ✅ | 403 ✅ | 403 ✅ | Solicitante cancela só as próprias (RF-16.5). Justificativa obrigatória. Em `APROVADA`, só antes do início. |
| 14 | `POST /reservas/{id}/nao-comparecimento` | 401 ✅ | 403 ✅ | 200 ✅ | 403 ❓ | Responsável registra (RTM §3.14). O RTM lista "Responsável / Administrador (pendente)", então a resposta do Administrador é ❓ (P7). |
| 15 | `POST /reservas/{id}/transicoes` | 401 ✅ | conforme a transição ⚠️ | conforme a transição ⚠️ | conforme a transição ⚠️ | Rota genérica, sem regra própria. Delega às mesmas checagens das linhas 9 a 14. Ressalva: a rota serve para testes e está exposta em produção. Recomenda-se remover ou restringir. |
| 16 | `GET /reservas/{id}/auditoria` | 401 ✅ | 403 ✅ | 200 ❓ | 200 ❓ | A especificação não diz quem vê o histórico (lacuna). A decisão do projeto é Responsável e Administrador, **inclusive excluindo o dono** (RN-09, ADR-005). |
| 17 | `GET /relatorios/utilizacao` | 401 ✅ | 403 ✅ | 200 ❓ | 200 ❓ | A especificação não diz quem gera relatórios (lacuna). A decisão do projeto é Responsável e Administrador (RF-12). |
| 18 | `PUT /reservas/{id}/auditoria` | 405 ✅ | 405 ✅ | 405 ✅ | 405 ✅ | Sem operação de alteração (RN-09.8). |
| 19 | `DELETE /reservas/{id}/auditoria` | 405 ✅ | 405 ✅ | 405 ✅ | 405 ✅ | Sem operação de exclusão (RN-09.9). |

---

## 3. Regras de posse

| Regra | Teste de posse (resultado medido) | Situação |
|---|---|---|
| Solicitante **altera** só as próprias reservas | Dono (`alice`) em `PUT` → 200. Terceiro (`bob`) em `PUT` → 403. Responsável e Administrador → 403. | ✅ Conforme |
| Solicitante **cancela** só as próprias reservas | Dono em `POST /cancelamento` → 200. Terceiro → 403. Responsável e Administrador → 403. | ✅ Conforme |
| Só o **Responsável** aprova recurso restrito | `POST /aprovacao` de reserva restrita `SOLICITADA`: Responsável → 200. Dono (Solicitante) → 403. Terceiro → 403. Administrador → 403. | ⚠️ Conforme ao perfil; vínculo com o responsável não modelado (PEN-01c) |
| Solicitante não aprova a própria reserva | Dono em `POST /aprovacao` → 403. | ✅ Conforme |
| Solicitante não lê reserva de terceiro | Terceiro em `GET /reservas/{id}` → 403. | ✅ Conforme |
| Histórico não é do dono | Dono em `GET /auditoria` → 403. | ✅ Conforme |

---

## 4. Divergências e lacunas

**Corrigido nesta etapa:**

- **D1 — Consulta de disponibilidade do Solicitante.** Antes, Solicitante e Responsável recebiam 403 na única rota de reservas por recurso, e não havia consulta de disponibilidade. Agora existe `GET /disponibilidade` (linha 5b), com teste de caixa-preta em `DisponibilidadeControllerTest` (7 casos, incluindo a privacidade da resposta). Commits: `cc9baaf` (red), `4ffaec0` (green).

**Não corrigido, fora do escopo acordado:**

- **D2 — Operações da especificação sem rota** (respondem 404 em vez de 403). São as de gestão atribuídas ao Administrador: edição e exclusão de recurso, usuários, bloqueios e parâmetros. Também retirada e devolução de material, e pendentes para análise do Responsável. Já estão em PEN-05, PEN-06, PEN-07 e PEN-08.

**Lacunas da especificação — o código decide, e não há divergência:**

- **Vínculo do Responsável com recursos.** A especificação diz só "valida a alocação de docentes" e "somente responsáveis aprovam recursos restritos". O vínculo não existe, então qualquer Responsável aprova qualquer reserva restrita (PEN-01c).
- **Quem rejeita reservas.** Não definido. O código dá a rejeição ao Responsável.
- **Quem vê histórico e relatórios.** Não definido. O código dá os dois ao Responsável e ao Administrador, com decisão registrada em ADR-005 e RF-12.
- **Quem cria reservas.** A especificação diz que o Solicitante "cria, altera ou cancela suas reservas", sem dizer o que os outros perfis podem. O código deixa qualquer autenticado criar (linha 4).
- **Autenticação por JWT.** A especificação exige "autenticação e autorização por perfil", mas não cita JWT. A rota `POST /dev/tokens` continua desligada por padrão (linha 1). Com o flag ligado, qualquer anônimo obtém perfil à escolha (PEN-01a), e isso é risco operacional, não divergência da especificação.

## 5. Rotas sem regra de segurança

Como não há Spring Security, nenhuma rota tem regra declarativa. A pergunta é quais rotas não têm **nenhuma** checagem de perfil ou posse além da autenticação, ou nenhuma autenticação.

**Sem autenticação:**
- `POST /dev/tokens` (apenas com `app.dev.tokens=true`). Não exige nada além do corpo.

**Só autenticação, sem checagem de perfil ou posse:**
- `POST /reservas` (qualquer autenticado cria, linha 4).

**Checagem só dentro da transição, não na rota** (têm regra no serviço, mas não aparecem em nenhuma regra por rota):
- `POST /reservas/{id}/aprovacao`, `/rejeicao`, `/inicio-uso`, `/conclusao`, `/cancelamento`, `/nao-comparecimento` e `/transicoes`.

**Rotas sem operação** (`405`, sem regra porque não há operação): `PUT` e `DELETE` em `/reservas/{id}/auditoria`.

---

## 6. Método de verificação

- Leitura dos controllers, dos serviços e dos testes de autorização existentes (`RF15ControleAcessoPerfilTest`, `RF16PropriedadeReservaTest`, `RN09RastreabilidadeTest`, `CoberturaRiscoAutorizacaoTest`).
- Sonda temporária com MockMvc (`MatrizProbeTemporarioTest`, removida depois, não commitada): 19 operações × 5 papéis = 95 requisições, com massa fixa e relógio em `2026-11-05T10:30Z`.
  - Massa: recursos livres e um restrito; reservas da Alice em estados `SOLICITADA`, `APROVADA`, `EM_USO`, com a Alice como dona.
  - Papéis: `anon`, `alice` (Solicitante dono), `bob` (Solicitante terceiro), `resp`, `adm`.
- A sonda montou os controllers diretamente, sem o `@ConditionalOnProperty`. Por isso o `POST /dev/tokens` aparece como 200 na sonda. Na aplicação com o padrão `app.dev.tokens=false`, a rota não é registrada e responde 404.
- Não foi executado: teste de corpo ausente (`400` antes do `401`), nem a verificação de `POST /dev/tokens` com o flag ligado na aplicação.
