# Cenários BDD — Aprovação, Bloqueio por Manutenção e Estados da Reserva

**Sistema:** Organização de Recursos (Java 21, Spring Boot 3)
**Escopo:** quatro regras de negócio de reservas
**Status:** especificação de QA. Nenhum código foi alterado. Os cenários descrevem o comportamento esperado segundo o PRD; quando o PRD não decide, o ponto está em **Perguntas em aberto**, e não há política inventada.

**Fontes lidas:**
- `docs/PRD.md`: RF-07, RF-08, RN-04, RN-05, RN-06, RN-07, RN-08, RN-09, RISK-01
- `docs/RTM.md`: §1.1 (personas), §1.6 (suposições S4–S6 e pendências), §3.8, §3.9, tabela de transições (linhas ~370–383), e a seção de aprovação com revalidação (~linha 608)
- `docs/testes/plano-tdd.md`: RN-02 a RN-06 e a convenção de intervalos semiabertos
- `src/main/java/com/organizacao/recursos/servico/ServicoReservas.java`
- `src/main/java/com/organizacao/recursos/servico/Autorizacao.java`
- `src/main/java/com/organizacao/recursos/dominio/{Reserva, EstadoReserva, Recurso, Manutencao, StatusRecurso, Usuario, Perfil}.java`
- Testes existentes: `RN04ManutencaoTest`, `RN05FluxoPrincipalTest`, `RN06EstadosAlternativosTest`, `RN07AlcadaAprovacaoTest`

**Convenções:**
- Intervalos são semiabertos `[início, fim)`. Esta é a convenção da implementação e de `plano-tdd.md` (RN-02, RN-04.3), mas o PRD não a fixa. Ver Q4.
- Duração mínima de reserva: 1 minuto (`DURACAO_MINIMA`, AMB-04, provisória).
- Códigos HTTP de referência, conforme o serviço: 401 (anônimo), 403 (perfil ou dono errado), 409 (transição proibida, conflito, indisponível), 400 (dados inválidos).
- Datas de exemplo: `2026-11-20`. Um recurso `SALA-01` é usado em todos os exemplos.

---

## Funcionalidade 1 — Aprovação obrigatória de recurso restrito

**Fonte:** PRD RF-07 ("exigir aprovação obrigatória de um Responsável para a alocação de recursos restritos") e RN-07 ("somente usuários com perfil de Responsável possuem permissão para aprovar solicitações de recursos restritos").

```gherkin
Funcionalidade: Aprovação obrigatória de recurso restrito
  Como sistema de organização de recursos
  Quero que reservas de recursos restritos só avancem com aprovação de um Responsável
  Para que a alocação de recursos sensíveis seja controlada

  Contexto:
    Dado que o recurso "SALA-01" está com status DISPONIVEL
    E que "SALA-01" está marcado como restrito
    E que "alice" tem o perfil SOLICITANTE
    E que "resp" tem o perfil RESPONSAVEL
    E que "adm" tem o perfil ADMINISTRADOR

  Cenário: Reserva de recurso restrito nasce aguardando aprovação
    Quando "alice" solicita "SALA-01" de "2026-11-20T10:00" a "2026-11-20T11:00"
    Então a reserva fica com estado SOLICITADA
    E a reserva não fica com estado APROVADA nem EM_USO

  Cenário: Início de uso sem aprovação é bloqueado
    Dado que existe uma reserva SOLICITADA de "alice" em "SALA-01"
    Quando "alice" tenta iniciar o uso da reserva
    Então a operação é recusada com 409
    E o estado da reserva continua SOLICITADA

  Cenário: Responsável aprova recurso restrito e o uso passa a ser possível
    Dado que existe uma reserva SOLICITADA de "alice" em "SALA-01"
    Quando "resp" aprova a reserva
    Então a reserva fica com estado APROVADA
    Quando "alice" inicia o uso da reserva
    Então a reserva fica com estado EM_USO

  Cenário: Solicitante não consegue aprovar recurso restrito
    Dado que existe uma reserva SOLICITADA de "bruno" em "SALA-01"
    Quando "alice" tenta aprovar a reserva
    Então a operação é recusada com 403
    E o estado da reserva continua SOLICITADA

  Cenário: Administrador não consegue aprovar recurso restrito
    Dado que existe uma reserva SOLICITADA de "alice" em "SALA-01"
    Quando "adm" tenta aprovar a reserva
    Então a operação é recusada com 403
    E o estado da reserva continua SOLICITADA

  Cenário: Usuário anônimo não consegue aprovar
    Dado que existe uma reserva SOLICITADA de "alice" em "SALA-01"
    Quando um ator anônimo tenta aprovar a reserva
    Então a operação é recusada com 401

  Cenário: Reserva rejeitada por Responsável não pode ser aprovada depois
    Dado que existe uma reserva SOLICITADA de "alice" em "SALA-01"
    Quando "resp" rejeita a reserva com justificativa "recurso indisponível para o evento"
    Então a reserva fica com estado REJEITADA
    Quando "resp" tenta aprovar a reserva
    Então a operação é recusada com 409

  Cenário: Aprovação gera registro de auditoria com o aprovador
    Dado que existe uma reserva SOLICITADA de "alice" em "SALA-01"
    Quando "resp" aprova a reserva
    Então existe um evento de auditoria com ator "resp", estado anterior SOLICITADA e estado novo APROVADA
```

**Observação de cobertura:** o serviço não lê `Recurso.restrito` em nenhum ponto. Hoje a aprovação é exigida por causa da máquina de estados, para todos os recursos. Os cenários acima passam com o flag ligado ou desligado, então não provam que o flag funciona. O cenário de recurso **não** restrito está em Q1.

---

## Funcionalidade 2 — Solicitante não aprova a própria reserva

**Fonte:** regra informada pela equipe. O PRD não diz isso de forma literal. O único texto relacionado é RTM §1.1, que fala em "não aprovar terceiros" para o Solicitante. Ver Q2.

```gherkin
Funcionalidade: Solicitante não aprova a própria reserva
  Como sistema de organização de recursos
  Quero impedir que quem solicita aprove a própria reserva
  Para que a aprovação seja sempre uma decisão independente

  Cenário: Solicitante tenta aprovar a própria reserva
    Dado que "alice" tem apenas o perfil SOLICITANTE
    E que "alice" tem uma reserva SOLICITADA em "SALA-02"
    Quando "alice" tenta aprovar a própria reserva
    Então a operação é recusada com 403
    E o estado da reserva continua SOLICITADA

  Cenário: Responsável aprova reserva de outro solicitante
    Dado que "bruno" tem uma reserva SOLICITADA em "SALA-02"
    Quando "resp" aprova a reserva de "bruno"
    Então a reserva fica com estado APROVADA

  Cenário: Usuário que tem os perfis SOLICITANTE e RESPONSAVEL não aprova a própria reserva
    Dado que "carla" tem os perfis SOLICITANTE e RESPONSAVEL
    E que "carla" tem uma reserva SOLICITADA em "SALA-02"
    Quando "carla" tenta aprovar a própria reserva
    Então a operação é recusada com 403
    E o estado da reserva continua SOLICITADA
    # Hoje o serviço permite esta aprovação. Ver Q2 e a seção "Lacunas encontradas".

  Cenário: Cancelamento pelo próprio solicitante continua permitido
    Dado que "alice" tem uma reserva SOLICITADA em "SALA-02"
    Quando "alice" cancela a própria reserva com justificativa "mudança de planos"
    Então a reserva fica com estado CANCELADA
```

---

## Funcionalidade 3 — Recurso em manutenção não pode ser reservado

**Fonte:** PRD RF-08 e RN-04 ("nenhum recurso que esteja alocado em período de manutenção pode ser reservado"). Existem dois mecanismos: o status `StatusRecurso.MANUTENCAO` (checado em `solicitar`) e os períodos `Manutencao` (checados em `conflita`). Ver Q6.

```gherkin
Funcionalidade: Bloqueio de recurso em manutenção
  Como sistema de organização de recursos
  Quero impedir reservas que caiam em período de manutenção
  Para que nenhum recurso indisponível seja alocado

  Cenário: Recurso com status MANUTENCAO não aceita nova reserva
    Dado que o recurso "LAB-03" está com status MANUTENCAO
    Quando "alice" solicita "LAB-03" de "2026-11-20T10:00" a "2026-11-20T11:00"
    Então a operação é recusada com 409
    E nenhuma reserva é criada

  Cenário: Reserva que intersecta período de manutenção é rejeitada
    Dado que "SALA-01" tem manutenção de "2026-11-20T12:00" a "2026-11-20T13:00"
    Quando "alice" solicita "SALA-01" de "2026-11-20T11:30" a "2026-11-20T12:30"
    Então a operação é recusada com 409
    E a mensagem informa conflito com o período bloqueado

  Cenário: Manutenção de outro recurso não bloqueia
    Dado que "SALA-02" tem manutenção de "2026-11-20T12:00" a "2026-11-20T13:00"
    Quando "alice" solicita "SALA-01" de "2026-11-20T12:00" a "2026-11-20T13:00"
    Então a reserva fica com estado SOLICITADA

  Cenário: Alteração de reserva para período de manutenção é rejeitada
    Dado que "alice" tem uma reserva SOLICITADA em "SALA-01" de "2026-11-20T09:00" a "2026-11-20T10:00"
    E que "SALA-01" tem manutenção de "2026-11-20T12:00" a "2026-11-20T13:00"
    Quando "alice" altera a reserva para "2026-11-20T12:00" a "2026-11-20T13:00"
    Então a operação é recusada com 409
    E a reserva mantém o período original

  Esquema do Cenário: Fronteiras do período de manutenção
    Dado que "SALA-01" tem manutenção de "2026-11-20T12:00" a "2026-11-20T13:00"
    Quando "alice" solicita "SALA-01" de <inicio> a <fim>
    Então o resultado é <resultado>

    Exemplos:
      | caso                                      | inicio              | fim                 | resultado                 |
      | termina exatamente no início da manutenção | 2026-11-20T11:00:00 | 2026-11-20T12:00:00 | aceita (estado SOLICITADA) |
      | começa exatamente no fim da manutenção     | 2026-11-20T13:00:00 | 2026-11-20T14:00:00 | aceita (estado SOLICITADA) |
      | coincide exatamente com a manutenção       | 2026-11-20T12:00:00 | 2026-11-20T13:00:00 | recusada com 409           |
      | cruza 1 segundo no início                  | 2026-11-20T11:00:00 | 2026-11-20T12:00:01 | recusada com 409           |
      | cruza 1 segundo no fim                     | 2026-11-20T12:59:59 | 2026-11-20T14:00:00 | recusada com 409           |
      | termina 1 segundo antes do início          | 2026-11-20T11:58:59 | 2026-11-20T12:00:00 | aceita (estado SOLICITADA) |
      | começa 1 segundo depois do fim             | 2026-11-20T13:00:00 | 2026-11-20T13:01:00 | aceita (estado SOLICITADA) |
```

**Nota sobre a tabela de fronteiras:** a reserva de "1 segundo antes" foi escrita com 61 segundos (`11:58:59` a `12:00:00`) de propósito. Uma reserva de 1 segundo é recusada com 400 pela duração mínima de 1 minuto (`validarPeriodo`) antes de a manutenção ser checada. Se a equipe usar 1 segundo literal, o resultado esperado vira 400, não "aceita". Isso depende de AMB-04.

**Fronteiras que o PRD não decide:** o PRD não diz se a manutenção é um intervalo fechado ou semiaberto. Os exemplos "termina exatamente no início" e "começa exatamente no fim" dependem disso. Ver Q4.

---

## Funcionalidade 4 — Transições de estado da reserva

**Fonte:** PRD RN-05 ("ordem estrita SOLICITADA → APROVADA → EM_USO → CONCLUÍDA"), RN-06 (estados alternativos REJEITADA, CANCELADA, NÃO COMPARECEU) e RN-09 (auditoria de cada mudança). A matriz abaixo é a que o código implementa em `transicaoPermitida`.

**Matriz de transições permitidas (7):**

| De | Para | Quem pode (código) |
|---|---|---|
| SOLICITADA | APROVADA | Responsável |
| SOLICITADA | REJEITADA | Responsável (justificativa obrigatória) |
| SOLICITADA | CANCELADA | Dono da reserva (justificativa obrigatória) |
| APROVADA | EM_USO | Dono ou Responsável |
| APROVADA | CANCELADA | Dono, antes do início (justificativa obrigatória) |
| APROVADA | NAO_COMPARECEU | Responsável |
| EM_USO | CONCLUIDA | Dono ou Responsável |

Estados sem saída: REJEITADA, CANCELADA, NAO_COMPARECEU, CONCLUIDA.
Pares proibidos: 49 pares possíveis, menos 7 permitidos = 42 proibidos (incluindo as auto-transições).

```gherkin
Funcionalidade: Transições de estado da reserva
  Como sistema de organização de recursos
  Quero que a reserva só mude de estado pelas transições previstas
  Para que o ciclo de vida seja rastreável e íntegro

  Cenário: Fluxo nominal completo
    Dado que "alice" tem uma reserva SOLICITADA em "SALA-02"
    Quando "resp" aprova a reserva
    E "alice" inicia o uso
    E "alice" conclui a reserva
    Então a reserva fica com estado CONCLUIDA
    E existem quatro eventos de auditoria: SOLICITAR, e uma transição para cada estado seguinte

  Cenário: Transição permitida é aceita
    Dado que a reserva está em <de>
    Quando o ator autorizado executa a transição para <para>
    Então a reserva fica com estado <para>

    Exemplos:
      | de         | para           |
      | SOLICITADA | APROVADA       |
      | SOLICITADA | REJEITADA      |
      | SOLICITADA | CANCELADA      |
      | APROVADA   | EM_USO         |
      | APROVADA   | CANCELADA      |
      | APROVADA   | NAO_COMPARECEU |
      | EM_USO     | CONCLUIDA      |

  Esquema do Cenário: Transição fora da matriz é recusada
    Dado que a reserva está em <de>
    Quando um ator tenta a transição para <para>
    Então a operação é recusada com 409
    E o estado da reserva continua <de>

    Exemplos:
      | de         | para           |
      | SOLICITADA | EM_USO         |
      | SOLICITADA | CONCLUIDA      |
      | SOLICITADA | NAO_COMPARECEU |
      | SOLICITADA | SOLICITADA     |
      | APROVADA   | SOLICITADA     |
      | APROVADA   | CONCLUIDA      |
      | APROVADA   | REJEITADA      |
      | APROVADA   | APROVADA       |
      | EM_USO     | SOLICITADA     |
      | EM_USO     | APROVADA       |
      | EM_USO     | REJEITADA      |
      | EM_USO     | CANCELADA      |
      | EM_USO     | NAO_COMPARECEU |
      | EM_USO     | EM_USO         |
      | CONCLUIDA  | SOLICITADA     |
      | CONCLUIDA  | APROVADA       |
      | CONCLUIDA  | EM_USO         |
      | CONCLUIDA  | REJEITADA      |
      | CONCLUIDA  | CANCELADA      |
      | CONCLUIDA  | NAO_COMPARECEU |
      | CONCLUIDA  | CONCLUIDA      |
      | REJEITADA  | SOLICITADA     |
      | REJEITADA  | APROVADA       |
      | REJEITADA  | EM_USO         |
      | REJEITADA  | CONCLUIDA      |
      | REJEITADA  | CANCELADA      |
      | REJEITADA  | NAO_COMPARECEU |
      | REJEITADA  | REJEITADA      |
      | CANCELADA  | SOLICITADA     |
      | CANCELADA  | APROVADA       |
      | CANCELADA  | EM_USO         |
      | CANCELADA  | CONCLUIDA      |
      | CANCELADA  | REJEITADA      |
      | CANCELADA  | NAO_COMPARECEU |
      | CANCELADA  | CANCELADA      |
      | NAO_COMPARECEU | SOLICITADA |
      | NAO_COMPARECEU | APROVADA   |
      | NAO_COMPARECEU | EM_USO     |
      | NAO_COMPARECEU | CONCLUIDA  |
      | NAO_COMPARECEU | REJEITADA  |
      | NAO_COMPARECEU | CANCELADA  |
      | NAO_COMPARECEU | NAO_COMPARECEU |

  Cenário: Estados terminais não têm saída
    Dado que a reserva está em REJEITADA
    Quando o Responsável tenta aprovar a reserva
    Então a operação é recusada com 409

  Cenário: Cancelamento de reserva em uso é recusado
    Dado que a reserva está em EM_USO
    Quando "alice" tenta cancelar a reserva com justificativa "imprevisto"
    Então a operação é recusada com 409
    E a reserva continua EM_USO

  Cenário: Exclusão de reserva em uso é recusada
    Dado que a reserva está em EM_USO
    Quando "alice" tenta excluir a reserva
    Então a operação é recusada com 409

  Cenário: Rejeição e cancelamento exigem justificativa
    Dado que existe uma reserva SOLICITADA de "alice" em "SALA-02"
    Quando "resp" rejeita a reserva sem justificativa
    Então a operação é recusada com 400
    E a reserva continua SOLICITADA

  Cenário: Não comparecimento só se registra a partir de APROVADA
    Dado que existe uma reserva SOLICITADA de "alice" em "SALA-02"
    Quando "resp" registra não comparecimento
    Então a operação é recusada com 409

  Cenário: Cada transição gera exatamente um evento de auditoria
    Dado que existe uma reserva SOLICITADA de "alice" em "SALA-02"
    Quando "resp" aprova a reserva
    Então existe exatamente um novo evento de auditoria com estado anterior SOLICITADA e estado novo APROVADA

  Cenário: Transição recusada não gera evento de auditoria
    Dado que existe uma reserva SOLICITADA de "alice" em "SALA-02"
    Quando "alice" tenta iniciar o uso
    Então a operação é recusada com 409
    E nenhum evento de auditoria novo é registrado
```

**Ordem de verificação (importante para as asserções de API):** o serviço valida primeiro a transição (409) e só depois o perfil do ator (403). Por isso "Solicitante tenta APROVADA numa reserva SOLICITADA" dá 403, mas "Solicitante tenta EM_USO numa reserva SOLICITADA" dá 409.

---

## Tabela consolidada de fronteiras (manutenção)

Manutenção de referência: `[2026-11-20T12:00, 2026-11-20T13:00)`.

| # | Reserva `[início, fim)` | Resultado | Fronteira testada | Fonte |
|---|---|---|---|---|
| B1 | `[11:00:00, 12:00:00)` | Aceita | termina exatamente no início | Convenção semiaberta (Q4) |
| B2 | `[13:00:00, 14:00:00)` | Aceita | começa exatamente no fim | Convenção semiaberta (Q4) |
| B3 | `[12:00:00, 13:00:00)` | Rejeitada | coincide com a manutenção | PRD RN-04 |
| B4 | `[11:00:00, 12:00:01)` | Rejeitada | cruza 1 s no início | Convenção semiaberta (Q4) |
| B5 | `[12:59:59, 14:00:00)` | Rejeitada | cruza 1 s no fim | Convenção semiaberta (Q4) |
| B6 | `[11:58:59, 12:00:00)` | Aceita | 1 s antes do início, com duração de 61 s | Convenção semiaberta, AMB-04 |
| B7 | `[13:00:00, 13:01:00)` | Aceita | 1 s depois do fim | Convenção semiaberta (Q4) |

Quando a mesma fronteira aparece em `RN04ManutencaoTest` (B1 ≈ RN-04.3, B3 ≈ RN-04.4), o teste existente cobre o caso. B4, B5 e B6 não têm teste equivalente.

---

## Perguntas em aberto

O PRD não decide estes pontos. Nenhum foi resolvido por suposição nos cenários acima.

- **Q1 — Recurso não restrito precisa de aprovação?** O código exige `APROVADA` para todos os recursos, e o flag `restrito` não é consultado. RF-07 fala só em restritos. RTM §3.8 escreve "quando recurso restrito ou regra aplicável". Decisão necessária antes de escrever o cenário "recurso livre vai direto para APROVADA" (que hoje não existe) ou "recurso livre é aprovado pelo Responsável" (que `RN07AlcadaAprovacaoTest.rn07_4` marca com "confirmar").
- **Q2 — Quem tem os perfis SOLICITANTE e RESPONSAVEL pode aprovar a própria reserva?** A regra da equipe diz que não. O PRD só diz que Responsável aprova. O serviço permite a autoaprovação porque só checa `tem(ator, RESPONSAVEL)`. A RTM diz "não aprovar terceiros", o que sugere outra leitura. Decidir se a regra vale para qualquer perfil.
- **Q3 — Administrador aprova?** RN-07 e `RN07AlcadaAprovacaoTest.rn07_3` dizem que não. RTM §1.1 pede "esclarecer escopo". Confirmar.
- **Q4 — Manutenção é intervalo fechado ou semiaberto?** A convenção semiaberta está em `plano-tdd.md` e em `ServicoReservas`, mas RTM marca AD-11 como "Proposta" e o PRD não a cita. Os casos B1, B2, B4, B5 e B7 dependem disso. Confirmar para manutenção, não só para reservas entre si.
- **Q5 — Manutenção criada depois de uma reserva já aprovada que se sobrepõe?** O código não revalida disponibilidade na aprovação nem em `EM_USO`. A RTM (~linha 608) propõe revalidar na aprovação. Opções possíveis: recusar a aprovação, cancelar a reserva ou apenas sinalizar. O PRD não escolhe. Um cenário de risco fica sem resultado esperado até a decisão.
- **Q6 — Qual a relação entre `StatusRecurso.MANUTENCAO`/`INDISPONIVEL` e os períodos `Manutencao`?** `solicitar` bloqueia pelo status, mas `alterar` só bloqueia pelo período. Não está definido se um status sem período bloqueia, nem se `INDISPONIVEL` deve ser tratado como manutenção.
- **Q7 — Quem inicia o uso (EM_USO) e quando?** RTM P7 está pendente. O código aceita Dono ou Responsável em qualquer momento depois da aprovação. Não há regra de horário de início.
- **Q8 — Quem registra NAO_COMPARECEU e após quanto tempo?** RTM P7 está pendente. O código aceita só o Responsável, a partir de APROVADA, sem checar o horário.
- **Q9 — Cancelamento exatamente no início.** O código recusa o cancelamento de APROVADA quando `agora >= início` (409). O RTM diz "antes do início" (P6, pendente). Confirmar se o limite é inclusivo.
- **Q10 — Idempotência de transições (RN-05.7).** O código recusa CONCLUIDA → CONCLUIDA com 409, mas `plano-tdd.md` deixa a decisão em aberto ("ignorar ou erro?"). Os exemplos desta especificação seguem o código.

---

## Lacunas encontradas no código (para registro, sem alteração nesta etapa)

1. **Autoaprovação possível** (Q2): `ServicoReservas.exigirAutorizacaoTransicao` não compara `ator` com o dono para `APROVADA`. Hoje um usuário com os dois perfis aprova a própria reserva.
2. **Flag `restrito` ignorado** (Q1): o campo existe em `Recurso`, mas nenhuma regra do serviço o lê.
3. **Aprovação sem revalidação de manutenção** (Q5): `transicionar` não chama `conflita`. Uma reserva solicitada antes de uma manutenção pode ser aprovada mesmo sobreposta a ela.
4. **Método público que contorna a máquina de estados:** `registrarExistente` (linha ~219) é `public` e insere reserva em qualquer estado, sem passar por `transicionar` nem gerar auditoria. Está marcado como "usado para preparar cenários de teste", mas fica disponível em produção. Recomendo restringir a visibilidade ou mover para o pacote de testes.
5. **Ausência de camada de API:** `src/main` não tem pacote de controladores. Os endpoints de RTM §3 (ex.: `DELETE /reservas/{id}`, `GET /reservas/pendentes`) estão como "Proposta". Os cenários de API abaixo só podem ser executados depois que o controlador existir.

---

## Classificação: teste de unidade × teste de API

**Teste de unidade** (chama `ServicoReservas` diretamente, com `Clock` fixo; sem HTTP, sem banco):

| Cenários | Motivo |
|---|---|
| F1: reserva nasce SOLICITADA; início de uso sem aprovação é recusado; aprovação por Responsável; recusa por Solicitante, Administrador e anônimo (ainda sem o mapeamento para HTTP); rejeitada não volta a ser aprovada | Regra de domínio e autorização em uma chamada de serviço |
| F2: todos os cenários de autoaprovação | Regra de autorização do serviço |
| F3: status MANUTENCAO bloqueia; intersecção bloqueia; manutenção de outro recurso não bloqueia; alteração para período bloqueado; **toda a tabela de fronteiras B1–B7** | Lógica de intervalo pura e determinística |
| F4: fluxo nominal; **todas as 7 transições permitidas**; **todas as 42 proibidas**; estados terminais; cancelamento em EM_USO; exclusão em EM_USO; justificativa obrigatória; auditoria por transição | Máquina de estados é lógica de domínio; a matriz inteira cabe em testes parametrizados rápidos |

**Teste de API** (HTTP, com mapeamento de status, corpo de erro e persistência; depende do controlador — ver lacuna 5):

| Cenários | Motivo |
|---|---|
| Códigos 401, 403, 409 e 400 retornados pelos endpoints de aprovação, rejeição, início de uso, conclusão e cancelamento | Mapeamento de exceção para HTTP é responsabilidade da camada web |
| Fluxo ponta a ponta com JWT: Solicitante cria, Responsável aprova, Solicitante inicia uso | Autenticação real (RF-01, RF-19) |
| Mensagem de bloqueio por manutenção visível na resposta (RTM §3.9) | Contrato de resposta |
| Autoaprovação recusada com 403 pelo endpoint, com JWT de usuário duplo perfil | Depende de Q2 e do controlador |
| Concorrência de duas solicitações no mesmo horário (RN-03) | Fora deste escopo; cenário já coberto em outra etapa do plano |

**Pendente de decisão antes de virar teste:** Q1 a Q5 bloqueiam os cenários marcados com "Q" acima. Q6 a Q10 afetam apenas as asserções de borda e podem ser escritas hoje com o comportamento atual, marcadas como "comportamento atual, não confirmado pelo PRD".

---

## Resolução das perguntas em aberto

Decisões tomadas pela equipe de QA com base no PRD e no RTM quando o documento não decidia. Cada uma tem commit de teste ou está registrada como comportamento já existente.

- **Q1 — Recurso livre precisa de aprovação?** Não. Só recurso restrito exige aprovação de Responsável (PRD RF-07, RN-07). Recurso livre entra já `APROVADA`. Commits `f4f87a1` / `ac22ade`.
- **Q2 — Quem tem SOLICITANTE e RESPONSAVEL pode aprovar a própria reserva?** Não. A regra vale para qualquer usuário, nas decisões de aprovação: `APROVADA` e `REJEITADA`. `NAO_COMPARECEU` continua só para Responsável, porque é registro de execução, não decisão sobre a aprovação. Commits `c5b34a7` / `18e4e51` (aprovação) e `6fd1a0c` / `f270594` (rejeição).
- **Q3 — Administrador aprova?** Não. Já é o comportamento e já está coberto por `RN07AlcadaAprovacaoTest.rn07_3`. Sem alteração.
- **Q4 — Manutenção é intervalo fechado ou semiaberto?** Semiaberto `[início, fim)`, como nas reservas. A adjacência já está coberta por `RN04.3`. As fronteiras B4, B5 e B7 já passam com o código atual e não foram escritas como teste novo, pela regra de não pular o vermelho.
- **Q5 — Manutenção posterior sobrepõe reserva já solicitada?** Aprovação revalida disponibilidade e manutenção e recusa com 409, sem aprovar. Commits `592fb3d` / `d796aae`.
- **Q6 — Status do recurso bloqueia alteração?** Sim. Qualquer status diferente de `DISPONIVEL` (inclusive `INDISPONIVEL`) recusa a alteração com 409, como já recusa a criação. Commits `9b7bbe9` / `e6642f5`.
- **Q7 — Quem inicia o uso e quando?** Dono ou Responsável, e só a partir do horário de início da reserva (RTM propõe "início do horário"). Commits `7506a4d` / `d3fb235`. Uso de materiais por retirada fica fora deste escopo.
- **Q8 — Quem registra não comparecimento e quando?** Só Responsável, e só a partir do horário de início. Commits `51b18d3` / `c229abe`.
- **Q9 — Cancelamento exatamente no início?** Recusado. Só é permitido antes do início, como diz o RTM. Já é o comportamento; sem teste novo.
- **Q10 — Transição repetida (CONCLUIDA → CONCLUIDA)?** Recusada com 409, não ignorada. Já é o comportamento; sem teste novo.

**Observação sobre o F2 do cenário:** a nota "Hoje o serviço permite esta aprovação" deixou de valer após o commit `18e4e51`.
