# Prova oral — Qualidade de Software: Sistema de Organização de Recursos

**Regra de distribuição.** Cada integrante responde só sobre partes que não escreveu:

- **Código de produção e testes** foram escritos no histórico git pelo Iago. Maria, Thais e Thales respondem sobre o código.
- **Documentos de arquitetura, requisitos e personas** foram escritos por Maria (personas, PRD, RFC, `arquitetura.md`) e Thales (ATAM). Thais atualizou o RTM. Iago responde sobre esses documentos.
- **Limite da atribuição:** todos os PRs mesclados (#1 a #19) foram abertos pela mesma conta do GitHub. A autoria por arquivo vem do histórico de commits, e não é confirmada pelos PRs. A divisão é a documentada em `docs/status-requisitos.md`, §6.

**Formato da resposta esperada:** oral, com referência ao arquivo indicado. "Resposta fraca" descreve o que a banca não deve aceitar.

---

## Iago Souza — perguntas sobre documentos de outros autores (4)

**I1. A persona do Responsável não lista "criar reserva própria". Qual decisão registrada resolve esse conflito, quem a confirmou, e o que a matriz de autorização muda por causa dela?**
- *Onde está a resposta:* `docs/personas/responsavel.md`; `docs/testes/plano-tdd.md`, §7.2 (AMB-03); `docs/seguranca/matriz-autorizacao.md`, linha 4 e seção 4.
- *Resposta fraca:* "A persona diz que o Responsável não cria reserva, então o código deveria bloquear." (Ignora a decisão registrada e não cita quem a confirmou.)

**I2. Qual é o cenário C-03 do ATAM, qual atributo de qualidade ele protege e qual teste do repositório o verifica? O que o teste prova e o que não prova?**
- *Onde está a resposta:* `docs/ATAM-analise.md`, seção "Cenário C-03" (linha 124) e a linha C-03 da matriz de sensibilidade (linha 570); `src/test/java/com/organizacao/recursos/persistencia/ArmazenamentoPostgresTest.java :: auditoriaEhSomenteInsercao`.
- *Resposta fraca:* "É o cenário de auditoria imutável; temos testes para isso." (Não identifica o teste nem o limite: o trigger impede UPDATE e DELETE, mas não impede acesso direto ao banco por um administrador de infraestrutura, como o ADR-005 registra.)

**I3. O RNF-12 exige rastreabilidade completa e imutável. Quais partes do requisito o repositório comprova com teste e quais ficam sem prova? Dê um exemplo de cada.**
- *Onde está a resposta:* `rfc/requisitos-nao-funcionais.md`, seção RNF-12 (linha 96); `docs/adr/005-auditoria-append-only-transacional.md`, seção "Trade-offs"; `docs/rtm.md`, §3.11.
- *Resposta fraca:* "RNF-12 está atendido pela tabela de auditoria." (Não distingue o que é comprovado do que é só afirmado, por exemplo a retenção de longo prazo, que o ADR marca como responsabilidade de produção.)

**I4. Quais são os limitadores de nota do PRD (RISK-01)? Para cada um, diga o estado atual e a evidência que o sustenta.**
- *Onde está a resposta:* `docs/PRD.md`, RISK-01 (linha 80); `docs/status-requisitos.md`, §1 e §5 (PEN-01 a PEN-04); `docs/rtm.md`, §1.5 (D1 a D3).
- *Resposta fraca:* "Todos estão atendidos, por isso o sistema pode ser aprovado." (Não cita a evidência de cada um e ignora a ressalva de autenticação sem login real, PEN-01b.)

---

## Maria Rita Ribeiro Cruz — perguntas sobre código escrito pelo Iago (4)

**M1. Qual mecanismo impede dupla reserva quando duas instâncias do serviço gravam ao mesmo tempo? Qual teste prova isso e o que ele mede?**
- *Onde está a resposta:* `docs/adr/016-exclusao-postgres-tstzrange.md`; `src/main/resources/db/schema.sql` (restrição `reserva_sem_sobreposicao`); `src/main/java/com/organizacao/recursos/persistencia/ArmazenamentoPostgres.java`, método `inserirReserva` e `travarRecurso`; `src/test/java/com/organizacao/recursos/persistencia/ConcorrenciaReservaPostgresTest.java :: vinteSolicitacoesSimultaneasGeramUmaReserva`.
- *Resposta fraca:* "Usamos transações e verificamos a disponibilidade antes de gravar." (Não distingue a verificação de aplicação, que tem corrida, da restrição do banco, que é a barreira final.)

**M2. Se a regra de aprovação de recurso restrito for removida (qualquer usuário pode aprovar), qual teste falha, e por quê? Quais testes ficam verdes nesse caso?**
- *Onde está a resposta:* `src/main/java/com/organizacao/recursos/servico/ServicoReservas.java`, método `exigirAutorizacaoTransicao`; `docs/testes/mutacao-manual.md`, M3; `src/test/java/com/organizacao/recursos/testes/RN07AlcadaAprovacaoTest.java :: rn07_1_responsavelAprovaRecursoRestrito`; `src/test/java/com/organizacao/recursos/api/VinculoResponsavelTest.java :: naoVinculadoNaoAprova`.
- *Resposta fraca:* "Os testes de aprovação falham." (Não diz qual teste nem por quê. Também não sabe responder que um teste de API, `MatrizAutorizacaoTest`, cobre a mesma regra por outra via.)

**M3. Como o token JWT é validado antes de uma operação protegida, e qual teste mostra que uma assinatura adulterada é rejeitada?**
- *Onde está a resposta:* `src/main/java/com/organizacao/recursos/servico/ServicoAutenticacao.java`, método `autenticar`; `src/test/java/com/organizacao/recursos/testes/RF01AutenticacaoJwtTest.java :: rf01_5_assinaturaAdulteradaEhRejeitada`.
- *Resposta fraca:* "Usamos JWT com assinatura e testamos." (Não cita o que é validado: assinatura, expiração, emissor, audiência e perfis. Também não diz que o emissor de tokens é o endpoint de laboratório, que existe apenas com o flag ligado.)

**M4. Uma falha de banco no meio de uma requisição pode vazar SQL ou nome de classe para o cliente? Mostre onde isso é impedido e o teste que o verifica.**
- *Onde está a resposta:* `src/main/java/com/organizacao/recursos/api/TratadorErros.java`, método `falhaInesperada`; `src/test/java/com/organizacao/recursos/api/ValidacaoEntradaTest.java :: falhaInternaNaoVazaDetalhes`; `src/test/java/com/organizacao/recursos/api/ValidacaoEntradaTest.java`, constante `VAZAMENTO` (os padrões que o teste procura no corpo da resposta).
- *Resposta fraca:* "O tratador de erros devolve mensagem genérica." (Não explica que a mensagem técnica vai para o log do servidor, nem qual padrão o teste usa para detectar vazamento.)

---

## Thais Rodrigues — perguntas sobre código escrito pelo Iago (4)

**T1. Há duas estratégias de bloqueio por recurso no PostgreSQL: `SELECT ... FOR UPDATE` e `pg_advisory_xact_lock`. Qual delas o caminho de produção usa, por quê, e qual é a outra para?**
- *Onde está a resposta:* `src/main/java/com/organizacao/recursos/persistencia/ArmazenamentoPostgres.java`, método `travarRecurso` (advisory lock); `src/main/java/com/organizacao/recursos/persistencia/RepositorioReservasPostgres.java`, método `solicitar` (`FOR UPDATE`); `src/main/java/com/organizacao/recursos/ConfiguracaoAplicacao.java` (qual armazenamento é injetado em `ServicoReservas`).
- *Resposta fraca:* "As duas evitam deadlock, então tanto faz." (Não percebe que uma das classes é usada apenas por testes, e que a aplicação usa outra.)

**T2. Num ponto dos testes usamos um armazenamento de teste escrito à mão, em vez de mock; em outros usamos a implementação em memória; e em outros usamos PostgreSQL real (Testcontainers). Por que essa escolha, e o que o teste sem banco não consegue provar?**
- *Onde está a resposta:* `src/test/java/com/organizacao/recursos/api/ValidacaoEntradaTest.java :: falhaInternaNaoVazaDetalhes` (classe `ArmazenamentoComFalhaEmListagem`, escrita à mão, que delega ao armazenamento em memória e falha só na listagem de reservas; não há Mockito em `src/test`); `src/test/java/com/organizacao/recursos/persistencia/ArmazenamentoPostgresTest.java` (Testcontainers); `src/test/java/com/organizacao/recursos/testes/RN02ExclusividadeAlocacaoTest.java` (implementação em memória, `ArmazenamentoMemoria`).
- *Resposta fraca:* "Mock é mais rápido que o banco." (Confunde dublê de teste com implementação em memória, e não diz o que só o banco real prova, como a restrição EXCLUDE, o trigger de auditoria e os locks.)

**T3. Por que os conflitos evitados ficam numa tabela própria (`conflito_reserva`), e não no `evento_auditoria`? O que acontece com essa contagem se a recusa estiver numa transação que sofre rollback?**
- *Onde está a resposta:* `src/main/java/com/organizacao/recursos/servico/ServicoReservas.java`, método `verificarDisponibilidade` (chamada a `registrarConflito`); `src/main/resources/db/schema.sql` (tabela `conflito_reserva`); `src/main/java/com/organizacao/recursos/persistencia/ArmazenamentoPostgres.java`, método `registrarConflitoAposRecusa`; `docs/rtm.md`, §3.13.
- *Resposta fraca:* "Porque é outra métrica." (Não cita a regra de auditoria do PRD (RN-09): recusa não muda o estado da reserva e, portanto, não gera evento. Também não explica por que o conflito é gravado em conexão própria, para sobreviver ao rollback.)

**T4. Os três bugs `S8700` do SonarCloud estavam na `main` até a correção do DEF-010. O que a regra alerta, por que o quality gate não reprovou o projeto, e qual é o risco real?**
- *Onde está a resposta:* `docs/relatorio-qualidade.md`, seção 4.4; `src/main/java/com/organizacao/recursos/servico/ServicoMovimentacoes.java`, linha 64 (conversão para UTC, DEF-010); `src/main/java/com/organizacao/recursos/servico/ServicoRelatorios.java`, método `duracaoNoPeriodo` (correção aplicada).
- *Resposta fraca:* "São bugs, e o projeto deveria ter zero bugs." (Não distingue o alerta de fuso do erro de cálculo, não diz que o gate avalia código novo, e não sabe a severidade.)

---

## ThalesConstruct — perguntas sobre desempenho, testes BDD e rastreabilidade (3)

**Th1. O p95 medido de 326 ms na consulta e 335 ms na criação permite afirmar que o sistema atende a meta de desempenho? Diga o que permite, o que não permite e por quê.**
- *Onde está a resposta:* `docs/performance/resultado-01.md`, seções 1, 3 e 4; `docs/performance/rnf.md`, seção 5 (critério de três rodadas); `perf/reservas.jmx`.
- *Resposta fraca:* "Sim, está abaixo de 500 ms, então a meta foi cumprida." (Ignora que a criação mede o caminho de recusa, 3.986 de 3.987 POSTs; que foi uma rodada; que o ambiente não é de referência; e que o commit medido não é o atual.)

**Th2. Os 80 cenários BDD passam pela API HTTP, e não chamam o domínio direto. Por que isso, e o que essa escolha torna possível verificar que um teste de domínio não verificaria?**
- *Onde está a resposta:* `src/test/java/com/organizacao/recursos/bdd/PassosReservas.java`, cabeçalho da classe (padrões do harness); `src/test/java/com/organizacao/recursos/bdd/RodarCenariosTest.java`; `src/test/resources/features/aprovacao-bloqueio.feature`.
- *Resposta fraca:* "BDD é para o cliente entender." (Não percebe que a camada HTTP e o PostgreSQL real fazem parte do que é verificado, e que o relógio é a única parte preparada diretamente.)

**Th3. Qual é a diferença entre "requisito crítico com teste localizado" e "requisito crítico com mutação detectada"? Dê um exemplo do relatório em que as duas medidas dão resultados diferentes e explique a diferença.**
- *Onde está a resposta:* `docs/testes/mutacao-manual.md`, seção 2 (coluna "Mutação"); `docs/relatorio-qualidade.md`, seção 4.5 (100% contra 62,5%).
- *Resposta fraca:* "Uma é o teste existir, a outra é o teste funcionar." (Não identifica os requisitos que ficaram sem mutação, RF-01, RF-19 e D1, nem o motivo.)

---

## Resumo da distribuição

| Integrante | Perguntas | Área |
|---|---|---|
| Iago Souza | I1–I4 | Personas, ATAM, RFC, PRD (escritos por Maria e Thales) |
| Maria Rita Ribeiro Cruz | M1–M4 | Código de reservas, JWT, tratamento de erros (escritos pelo Iago) |
| Thais Rodrigues | T1–T4 | Persistência, testes e análise estática (escritos pelo Iago) |
| ThalesConstruct | Th1–Th3 | Desempenho, BDD e rastreabilidade (escritos pelo Iago e pela equipe) |
