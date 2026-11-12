# Changelog

Todas as mudanças notáveis deste projeto. Formato baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/).

## [v1.0.0-rc1] — 2026-11-12

Commit: `14d766a` · Tag anotada `v1.0.0-rc1` · CI verde: [run 43327161512](https://github.com/mariarrcruz/Organizacao-Recursos-Universidade/actions/runs/43327161512)

Candidata a release da avaliação. As pendências que continuam abertas estão listadas ao final.

### Adicionado
- **Login por senha** (`POST /auth/login`): PBKDF2-HMAC-SHA256, JWT de 1 hora. Usuário inexistente e senha errada recebem a mesma resposta 401.
- **Cadastro de usuários** (`POST /usuarios`), restrito ao administrador. O primeiro administrador vem de `APP_BOOTSTRAP_ADMIN_SENHA`.
- **Catálogo de recursos**: pesquisa com filtros (tipo, localização, capacidade, competência e disponibilidade no período), consulta, edição parcial e exclusão protegida por histórico (`/recursos`).
- **Reservas pessoais e pendências**: `GET /reservas/minhas` e `GET /reservas/pendentes`. Esta última lista só recursos vinculados ao Responsável.
- **Movimentações de material** por HTTP: retirada, devolução e histórico (`/movimentacoes`).
- **Notificações simuladas**: criação e mudanças de estado de reserva, consultáveis em `GET /notificacoes` por cada usuário.
- **Interface responsiva** em `/`, por perfil: login, pesquisa, solicitação, minhas reservas, aprovações, movimentações e administração.
- **Teste end-to-end** (`e2e/FluxoEndToEndTest`): aplicação em porta real, PostgreSQL em contêiner, quatro cenários.
- **Teste de concorrência** entre manutenção e reserva (RN-04).
- **Carga**: plano JMeter com login de administrador por `/auth/login`, script `scripts/analisar_jtl.py` com os critérios do `rnf.md`, workflow manual `carga.yml`.
- **Exportação de qualidade**: `docs/qualidade/` (JaCoCo e SonarCloud) e `scripts/exportar_sonarcloud.py`.
- **Documentação**: `docs/api.md`, `docs/rtm.md`, `docs/entrega/checklist.md`, `docs/evidencias/README.md`.

### Alterado
- **Java 21** (`release 21`) no build, na CI e na documentação.
- **Armazenamento PostgreSQL com pool HikariCP** compartilhado por URL e usuário.
- **Serviço de reservas sem monitor Java em volta de I/O**: a atomicidade fica com o armazenamento (transação, lock consultivo e restrição `EXCLUDE` no PostgreSQL).
- **Armazenamento em memória** recusa sobreposição em `inserirReserva`, cumprindo o contrato da porta.
- **Mocks de persistência** substituídos por decorador escrito à mão nos testes.
- README, status de requisitos e relatório de qualidade atualizados.

### Corrigido
- **DEF-007**: Responsável via solicitações de recursos não vinculados a ele.
- **DEF-008**: operações de reserva serializadas sob carga. Consulta de 870 ms para 3 ms (p95); criação de 906 ms para 38 ms (p95), nas rodadas locais 05 a 07.
- **DEF-009**: README descrevia tecnologias e endpoints inexistentes.
- **DEF-010**: três bugs `java:S8700` do SonarCloud (duração entre datas sem fuso).
- Bug `java:S2259` (possível NullPointerException) no login, encontrado pelo Sonar durante o PR #14.
- Workflow de carga: JMeter 5.6.3 oficial no lugar do pacote do Ubuntu, que recusava o plano com `ForbiddenClassException`.

### Pendências conhecidas nesta candidata
- **Revisão por pares**: os PRs #6 a #17 foram mergeados sem revisão formal de outro integrante.
- **Ambiente de referência**: a medição de desempenho foi feita em máquina de desenvolvimento e no runner do GitHub, não em ambiente fixo com três rodadas, como pede `docs/performance/rnf.md`.
- **Mutação**: as sete quebras de `docs/testes/mutacao-manual.md` foram feitas sobre o commit `b4c94b1` e não foram repetidas.
- **Persistência**: usuários, movimentações e notificações ficam em memória.
- **Auditoria**: só as reservas têm histórico auditável; recursos não.
- **Interface**: não foi aberta em navegador real para checar o layout responsivo.
- **Code smell** S8786 em `VinculoResponsavelTest.java:64`, aberto no SonarCloud.
