# Mutação manual e rastreabilidade dos requisitos críticos

> **Atenção (2026-11-12).** Este registro foi feito sobre o commit `b4c94b1`. As quebras não foram repetidas depois das mudanças desta entrega (login, catálogo, pool de conexões e remoção do monitor do serviço de reservas). Antes de usar como evidência atual, repita as sete quebras.

**Commit auditado:** `e5d0df2` (merge do PR #10 na `main`).
**CI do commit:** `Maven verify` — sucesso (run `43100055056`), verificado com `gh run list`.
**Data:** 2026-11-11.

Este documento registra duas coisas: (1) as regras críticas foram quebradas uma a uma no código de produção e o teste ligado a cada uma foi executado; (2) cada requisito crítico do RTM (`docs/rtm.md`, §1.2) foi conferido por risco, teste localizável por caminho e nome, e evidência de execução no commit atual.

Método: para cada regra, a quebra foi aplicada com uma alteração mínima no arquivo de produção; o teste ligado a ela foi executado com `mvn -o test -Dtest=...`; o arquivo foi restaurado com `git restore` e a árvore verificada limpa antes da próxima quebra. Em nenhum caso a quebra foi commitada.

---

## 1. Quebras de regra

| # | Regra crítica | Quebra feita (arquivo:linha) | Teste(s) executado(s) | Resultado | Restauração |
|---|---|---|---|---|---|
| M1 | Sobreposição de horários (RF-05, RN-02) | `ServicoReservas.sobrepoe`: `&&` trocado por `\|\|` (linha 335) | `RN02ExclusividadeAlocacaoTest`, `CoberturaRiscoReservasTest`, `RN03AtomicidadeConcorrenciaTest` | **Vermelho.** `rn02_1_reservaDistanteEAceita` e `rn02_2_reservaAdjacenteEAceita` (erro: reservas válidas recusadas); `rn03_1_requisicoesSequenciaisSaoAceitas` | `git restore` ✔ |
| M2 | Dupla reserva sob concorrência (RF-06, RNF-02) | `ArmazenamentoPostgres.inserirReserva`: removido `travarRecurso` (bloqueio por recurso) **e** removida a restrição `reserva_sem_sobreposicao` (EXCLUDE) de `schema.sql` — as duas camadas | `ConcorrenciaReservaPostgresTest`, `ConcorrenciaReservaHttpPostgresTest` | **Vermelho.** `criadas (201): expected 1 but was 20` (4 de 4 execuções) | `git restore` ✔ |
| M3 | Aprovação de recurso restrito só por Responsável (RF-07, RN-07, RF-15) | `ServicoReservas.exigirAutorizacaoTransicao`, `APROVADA`: `tem(RESPONSAVEL) && !dono && vinculado` trocado por `!dono` (linha 188) | `RN07AlcadaAprovacaoTest`, `VinculoResponsavelTest`, `MatrizAutorizacaoTest`, `RF15ControleAcessoPerfilTest` | **Vermelho.** 10 falhas: `rn07_3_administradorAprovaRecursoRestrito`, `rf15_a12_administradorNaoAprovaRecursoRestrito`, `rf15_s6_aprovarEhNegadoParaSolicitante`, `naoVinculadoNaoAprova` e 6 células da matriz | `git restore` ✔ |
| M4 | Bloqueio por manutenção (RF-08, RN-04) | `ServicoReservas.sobrepoeManutencao`: retorno forçado a `false` | `RN04ManutencaoTest`, `RevalidacaoAprovacaoTest`, `ArmazenamentoPostgresTest` | **Vermelho.** `rn04_4_reservaExataNaManutencaoEhRejeitada`, `rn04_5_reservaParcialSobreManutencaoEhRejeitada`, `rn04_6_reservaQueEnvolveManutencaoEhRejeitada`, `aprovacaoRecusadaQuandoManutencaoPosteriorSobrepoeReserva` | `git restore` ✔ |
| M5 | Autorização: só o dono cancela (RF-16, RF-15) | `ServicoReservas.exigirAutorizacaoTransicao`, `CANCELADA`: `ehDono` trocado por `true` (linha 191) | `MatrizAutorizacaoTest`, `RF15ControleAcessoPerfilTest`, `RF16PropriedadeReservaTest`, `CoberturaRiscoAutorizacaoTest`, `CoberturaRiscoReservasTest` | **Vermelho.** `solicitanteNaoCancelaReservaDeOutroPeloId`, `responsavelNaoCancelaReservaAlheia` e 6 células da matriz de cancelamento | `git restore` ✔ |
| M6 | Auditoria de toda mudança de estado (RF-10, RN-09) | `ArmazenamentoPostgres.atualizarReserva`: removida a inserção do evento (a transição é gravada sem registro) | `ArmazenamentoPostgresTest`, `RodarCenariosTest` (cenários BDD sobre PostgreSQL) | **Vermelho.** `mudancaDeEstadoEEventoSaoGravadosJuntos`, `falhaNoEventoDesfazMudancaDeEstadoSemAuditoriaOrfa`; BDD: "Aprovação gera registro de auditoria com o aprovador", "Cada transição gera exatamente um evento", "Fluxo nominal completo" | `git restore` ✔ |
| M7 | Auditoria somente inserção (RN-09.8, RN-09.9) | `schema.sql`: removido o trigger `evento_append_only` | `ArmazenamentoPostgresTest` | **Vermelho.** `auditoriaEhSomenteInsercao`: "Expected java.sql.SQLException to be thrown, but nothing was thrown" | `git restore` ✔ |

**Lacunas:** nenhuma quebra ficou verde. Não foi necessário escrever teste novo para completar a detecção.

**Observação sobre M1:** os dois testes de sobreposição falham por *erro* (exceção ao montar reservas sequenciais legítimas), não por asserção. Continuam sendo detecção válida da regra quebrada, mas a mensagem de erro não diz "sobreposição" diretamente. Vale uma asserção explícita sobre a recusa de sobreposição, se a equipe quiser a falha mais legível.

---

## 2. Rastreabilidade dos requisitos críticos

Requisitos críticos = os marcados como "Crítica" em `docs/rtm.md` §1.2, mais RNF-02 (Anexo A) e o driver D1 (§1.5), que o RTM trata como eliminatório. Oito itens.

Risco: quando o RTM registra o risco (Anexo A), a fonte é citada; quando não registra, o risco é derivado do texto do requisito e marcado como tal.

| Requisito | Risco | Teste (caminho :: método) | Execução | Mutação | Situação |
|---|---|---|---|---|---|
| RF-01 — autenticação e autorização por perfil com JWT | Falha de autorização (RTM Anexo A) | `src/test/java/com/organizacao/recursos/testes/RF01AutenticacaoJwtTest.java :: rf01_5_assinaturaAdulteradaEhRejeitada`; `…/testes/CoberturaRiscoAutenticacaoTest.java :: assinaturaNaoCanonicaRejeita401` | CI `e5d0df2` ✔ | Não quebrada nesta auditoria | **Coberto por execução** |
| RF-05 — sobreposição de horários | Conflito de alocação (derivado) | `…/testes/RN02ExclusividadeAlocacaoTest.java :: rn02_1_reservaDistanteEAceita`, `rn02_2_reservaAdjacenteEAceita` | CI `e5d0df2` ✔ | M1 — vermelho | **Coberto por mutação** |
| RF-06 — prevenção de dupla reserva concorrente | Dupla reserva (RTM Anexo A) | `src/test/java/com/organizacao/recursos/persistencia/ConcorrenciaReservaPostgresTest.java :: vinteSolicitacoesSimultaneasGeramUmaReserva`; `…/persistencia/ConcorrenciaReservaHttpPostgresTest.java :: vinteRequisicoesSimultaneasGeramUmaReserva` | CI `e5d0df2` ✔ | M2 — vermelho | **Coberto por mutação** |
| RF-15 — controle de acesso estrito por perfil | Acesso indevido por perfil (derivado) | `…/testes/RF15ControleAcessoPerfilTest.java :: rf15_s6_aprovarEhNegadoParaSolicitante` (e classes aninhadas); `…/api/MatrizAutorizacaoTest.java :: respostaConformeAMatriz` (98 células) | CI `e5d0df2` ✔ | M3, M5 — vermelho | **Coberto por mutação** |
| RF-16 — Solicitante manipula apenas reservas próprias | Manipulação de reserva alheia (derivado) | `…/testes/RF16PropriedadeReservaTest.java :: rf16_2_alterarReservaAlheiaEhNegado`; `…/api/MatrizAutorizacaoTest.java :: solicitanteNaoAlteraReservaDeOutroPeloId`, `solicitanteNaoCancelaReservaDeOutroPeloId` | CI `e5d0df2` ✔ | M5 — vermelho | **Coberto por mutação** |
| RF-19 — validação do JWT antes de operação protegida | Operação sem token válido (derivado) | `…/testes/CoberturaRiscoAutenticacaoTest.java :: assinaturaNaoCanonicaRejeita401`; `…/api/MatrizAutorizacaoTest.java :: respostaConformeAMatriz` (células 401) | CI `e5d0df2` ✔ | Não quebrada nesta auditoria | **Coberto por execução** |
| RNF-02 — integridade sob concorrência | Inconsistência transacional (RTM Anexo A) | Mesmos testes de RF-06 | CI `e5d0df2` ✔ | M2 — vermelho | **Coberto por mutação** |
| D1 — aplicação executável e demonstrável | Sistema não demonstrável (derivado) | `src/test/java/com/organizacao/recursos/AplicacaoContextoTest.java :: aplicacaoAtendeSolicitacaoDeReservaComBancoReal` | CI `e5d0df2` ✔ | Não quebrada nesta auditoria | **Coberto por execução** |

Além dos críticos, as regras de manutenção (RF-08) e de auditoria (RF-10, Alta) foram quebradas nas mutações M4, M6 e M7, pois o próprio enunciado da auditoria as incluía.

### Porcentagem

- **Requisitos críticos:** 8.
- **Com teste localizado por caminho e nome, executado no commit atual (CI verde):** 8 de 8 = **100%**.
- **Com evidência de detecção por mutação (teste vermelho com a regra quebrada):** 5 de 8 = **62,5%** (RF-05, RF-06, RF-15, RF-16, RNF-02).
- Sem mutação: RF-01, RF-19 e D1, cobertos por execução. Quebrar a validação de JWT e a inicialização da aplicação é o próximo passo para elevar a porcentagem de mutação.

---

## 3. Divergências encontradas no RTM

- **Caminhos genéricos.** As linhas de RF-01, RF-06, RF-10, RNF-02 do Anexo A citam "TG-xx" e tipos de evidência, sem caminho nem nome de teste. Esta auditoria localizou os testes por caminho e método (seção 2). Recomenda-se atualizar a coluna "Teste" do Anexo A com estes caminhos.
- **Ambiguidade de nome.** O RTM §1.2 lista RF-05 como "Crítica" e a Anexo A não tem linha própria para ele. Essa correspondência foi feita por texto.
- **Risco não registrado** para RF-05, RF-15, RF-16, RF-19 e D1 no RTM. Os riscos desses itens nesta auditoria são derivados e marcados como tal.
