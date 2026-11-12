# Resumo do projeto para a prova oral

**Estado de referência:** tag `v1.0.0-rc1`, commit `14d766a`. Os números vêm dos relatórios versionados em `docs/`.

---

## O que é o sistema?

Sistema que aloca salas, professores, materiais e equipamentos sem conflito de horário, com histórico das reservas. Monolito Spring Boot em Java 21, com PostgreSQL e uma página estática em `/`.

## Quem usa o sistema?

- **Solicitante:** consulta disponibilidade e cria, altera ou cancela as próprias reservas.
- **Responsável:** aprova ou rejeita reservas dos recursos a que está vinculado e registra retirada e devolução de material.
- **Administrador:** cadastra recursos, usuários, vínculos e manutenções. Não cria reservas.

## Quais são os estados de uma reserva?

- Fluxo principal: SOLICITADA → APROVADA → EM_USO → CONCLUIDA.
- Alternativos: REJEITADA, CANCELADA e NAO_COMPARECEU.
- Recurso restrito nasce SOLICITADA e precisa de aprovação. Recurso livre nasce APROVADA.

## Como se evita a dupla reserva?

Três barreiras, da mais fraca para a mais forte:
1. Verificação na aplicação, rápida, mas sujeita a corrida.
2. Transação com lock consultivo por recurso (`pg_advisory_xact_lock`).
3. Restrição `EXCLUDE` com `tstzrange` no PostgreSQL (ADR-016), que é a barreira final.

Testes: 20 solicitações simultâneas geram uma reserva (`ConcorrenciaReservaPostgresTest`). Reserva em período de manutenção é recusada sob concorrência.

## Como funciona a autenticação?

- `POST /auth/login` com usuário e senha; senha em PBKDF2-HMAC-SHA256.
- Token JWT RS256 válido por 1 hora. A validação verifica assinatura, expiração, emissor, audiência e perfis.
- O primeiro administrador vem da variável `APP_BOOTSTRAP_ADMIN_SENHA`. Não há senha padrão no código.
- `POST /dev/tokens` existe só com `app.dev.tokens=true`, para demonstração e carga.

## Como funciona a auditoria?

- Toda mudança de estado de reserva gera um evento na mesma transação.
- Um trigger impede `UPDATE` e `DELETE` na tabela de eventos (append-only).
- Limite: quem tem acesso direto ao banco pode alterar o histórico (ADR-005).
- Ações administrativas não são auditadas.

## Quais dados são persistidos?

- **No PostgreSQL:** reservas, recursos, manutenções, eventos de auditoria, conflitos e vínculos.
- **Em memória:** usuários, movimentações de material e notificações. Reiniciar a aplicação apaga esses dados.

## O que são as notificações?

Simuladas (ADR-006): cada criação e mudança de estado gera uma mensagem no log e em memória, consultável em `GET /notificacoes`. Não há envio real.

## Quais tipos de teste existem?

- Unitários e parametrizados (JUnit 5), API com MockMvc, integração com PostgreSQL real (Testcontainers), concorrência, WireMock para o calendário institucional.
- 80 cenários BDD executados pela API HTTP.
- 4 cenários end-to-end com a aplicação em porta real.
- Total: **643 testes**, 0 falhas. Não há Mockito em `src/test`.

## Quais são os números de qualidade?

- Cobertura: linhas 92,5 % (meta 80 %) e ramos 83,7 % (meta 70 %). Cobertura mede execução, não verificação.
- SonarCloud: quality gate OK, 0 bugs, 0 vulnerabilidades, 1 code smell aberto (S8786).
- Mutação: sete quebras de regra detectadas no commit `b4c94b1`. Não repetidas no código atual.

## Como foi o desempenho?

- Antes da correção do DEF-008: consulta e criação de 870 a 995 ms (p95).
- Depois: consulta de 3 ms e criação de 38 a 39 ms nas rodadas locais. No GitHub, consulta de 1 ms e criação de 2 ms.
- Ressalva: não foi medido em ambiente de referência, que o `rnf.md` pede com três rodadas.

## Quais defeitos foram encontrados?

Dez, em `docs/defeitos/`, todos corrigidos. Os mais importantes:
- DEF-001: dupla reserva sob solicitações simultâneas.
- DEF-002: deadlock.
- DEF-007: Responsável via solicitações de recursos não vinculados.
- DEF-008: serialização que deixava o sistema lento sob carga.
- DEF-010: bugs S8700 do SonarCloud.

## Como o projeto é executado e testado?

- Pipeline: `ci.yml` roda `./mvnw -B verify` e o SonarCloud em PR e na `main`. Run do commit da tag: 43327161512 (sucesso).
- Carga: `carga.yml` (manual) roda o JMeter no GitHub com os critérios de `scripts/analisar_jtl.py`.
- Reprodução: JDK 21 e Docker. Passos no README.

## Qual é a decisão de qualidade?

**Liberar com ressalvas**, como entrega acadêmica, não para produção (`docs/decisao-qualidade.md`).

## O que falta para a entrega final?

1. Revisão por pares de outro integrante. Nenhum dos PRs #1 a #19 tem revisão formal no GitHub.
2. Repetir as sete quebras de mutação no código atual.
3. Decisão da equipe sobre o limite de desempenho e medição em ambiente de referência.

## Quais são as limitações?

- Usuários, movimentações e notificações não são persistidos.
- Auditoria só cobre reservas.
- Interface não foi verificada em navegador real.
- Refresh token existe no serviço, sem rota HTTP.
- Chave JWT não é persistida: reiniciar invalida os tokens.

## Como o grupo trabalhou?

Pelo histórico do repositório, a maior parte dos commits em `main` é de Iago Souza. Maria Rita Ribeiro Cruz, ThalesConstruct e Thais Rodrigues têm commits de documentação e de arquitetura. A divisão e as declarações ficam em `docs/decisao-qualidade.md` §6, para cada integrante confirmar.

---

## Onde conferir cada coisa

| Assunto | Arquivo |
|---|---|
| Decisão de qualidade | `docs/decisao-qualidade.md` |
| Evidências da tag | `docs/evidencias/README.md` |
| Relatório de qualidade | `docs/relatorio-qualidade.md` |
| Matriz de rastreabilidade | `docs/rtm.md` |
| Arquitetura e ADRs | `docs/arquitetura.md`, `docs/adr/` |
| Autorização | `docs/seguranca/matriz-autorizacao.md` |
| Desempenho | `docs/performance/` |
| Defeitos | `docs/defeitos/` |
| Mutação | `docs/testes/mutacao-manual.md` |
| Perguntas da banca | `docs/prova-oral/perguntas.md` |
