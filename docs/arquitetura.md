# Arquitetura — seção inicial

> Esta seção consolida o contexto e os drivers arquiteturais que podem ser
> extraídos dos artefatos atualmente disponíveis. Não define uma arquitetura
> de implementação nem substitui os requisitos. Fatos estão associados às
> fontes citadas; prioridades e riscos são análises preliminares, identificadas
> como tais.

## Fontes consultadas e rastreabilidade

- [README.md](../README.md): objetivo resumido do sistema.
- [PRD.md](./PRD.md): personas, requisitos, regras de negócio, critérios de
  qualidade e entregáveis.
- [solicitante.md](./personas/solicitante.md),
  [responsavel.md](./personas/responsavel.md) e
  [administrador.md](./personas/administrador.md): perfil, objetivos,
  permissões, restrições e necessidades das personas.
- [requisitos-funcionais.md](../rfc/requisitos-funcionais.md): requisitos
  funcionais detalhados RF-01 a RF-20.
- [requisitos-nao-funcionais.md](../rfc/requisitos-nao-funcionais.md):
  requisitos não funcionais detalhados RNF-01 a RNF-18.

O PRD e os RFCs usam numerações RF/RNF diferentes para assuntos relacionados.
As referências abaixo identificam sempre o arquivo junto do identificador;
um identificador isolado não deve ser tratado como chave global.

## 1. Contexto e objetivo

### Fatos

O sistema deve organizar a alocação institucional de salas, professores,
materiais e recursos, evitando conflitos de horário e apoiando reservas,
manutenção, movimentação de materiais, auditoria e relatórios. Esse objetivo
está declarado no [README.md](../README.md) e em [PRD.md](./PRD.md), seção 1.

Os fluxos conhecidos envolvem três perfis: Solicitante, Responsável e
Administrador ([PRD.md](./PRD.md), seção 2; RF-01 e RF-15 em
[requisitos-funcionais.md](../rfc/requisitos-funcionais.md)). O sistema deve
validar disponibilidade e conflitos, tratar reservas concorrentes e bloquear
alocações durante manutenção (RF-03 a RF-09 no RFC funcional; RN-01 a RN-04
em [PRD.md](./PRD.md), seção 4).

Os documentos não determinam uma topologia, um estilo arquitetural, protocolos
de comunicação ou uma plataforma de execução; exigem autenticação com JWT.
O PRD prevê diagramas de contexto, componentes e sequências críticas como
entregáveis ([PRD.md](./PRD.md), seção 7), mas eles ainda não estão presentes
entre as fontes consultadas.

### Objetivo arquitetural

Orientar decisões futuras para que os fluxos de reserva e administração de
recursos satisfaçam os requisitos de segurança, integridade temporal,
desempenho, rastreabilidade, observabilidade e qualidade de testes
documentados. Esta orientação não presume uma solução técnica específica.

## 2. Stakeholders e personas

Os artefatos nomeiam as três personas abaixo. Não identificam nominalmente
outros stakeholders, como patrocinadores, operadores de infraestrutura ou
equipes de suporte; sua existência e responsabilidades permanecem por
confirmar.

| Persona | Fatos documentados | Fontes |
|---|---|---|
| **Solicitante (USR-01)** | Professor ou coordenador. Consulta disponibilidade e cria, altera ou cancela as próprias reservas. Não aprova reservas de terceiros nem gerencia cadastros ou configurações globais. | [PRD.md](./PRD.md), seção 2; [solicitante.md](./personas/solicitante.md); RF-15 e RF-16 em [requisitos-funcionais.md](../rfc/requisitos-funcionais.md) |
| **Responsável (USR-02)** | Gestor ou coordenador. Analisa solicitações especiais, aprova ou rejeita, valida alocação de docentes e acompanha retirada/devolução de materiais sob sua responsabilidade. Não administra globalmente cadastros e parâmetros. | [PRD.md](./PRD.md), seção 2; [responsavel.md](./personas/responsavel.md); RF-08, RF-10, RF-15 e RF-17 em [requisitos-funcionais.md](../rfc/requisitos-funcionais.md) |
| **Administrador (USR-03)** | Gestor global. Gerencia salas, materiais, equipamentos, usuários/professores, bloqueios, manutenção, permissões e parâmetros do sistema. | [PRD.md](./PRD.md), seção 2; [administrador.md](./personas/administrador.md); RF-02, RF-15 e RF-18 em [requisitos-funcionais.md](../rfc/requisitos-funcionais.md) |

## 3. Requisitos arquiteturalmente significativos

### Fatos

1. **Autenticação e autorização:** autenticar com JWT; validar tokens em
   operações protegidas; rejeitar token ausente, inválido ou expirado e
   restringir ações por perfil ([PRD.md](./PRD.md), RF-01; RF-01, RF-15,
   RF-16 e RF-19 em [requisitos-funcionais.md](../rfc/requisitos-funcionais.md);
   RNF-01 em [requisitos-nao-funcionais.md](../rfc/requisitos-nao-funcionais.md)).
2. **Integridade da agenda:** impedir sobreposição de reservas para o mesmo
   recurso, professor ou espaço e impedir dupla alocação sob concorrência;
   recursos em manutenção não podem ser reservados (RF-04, RF-06, RF-07 e
   RF-09 no RFC funcional; RN-01 a RN-04 em [PRD.md](./PRD.md), seção 4;
   RNF-02 no RFC não funcional).
3. **Ciclo de vida e aprovação:** registrar estados de reserva e bloquear
   transições inválidas; exigir aprovação de Responsável para recursos
   restritos (RF-05 e RF-08 no RFC funcional; RN-05 a RN-08 em
   [PRD.md](./PRD.md), seção 4).
4. **Auditoria:** manter histórico de alterações e de transições de estado
   com autoria, data e ação; o PRD exige registro imutável de mudanças de
   estado (RF-11 no RFC funcional; RNF-12 no RFC não funcional; RN-09 em
   [PRD.md](./PRD.md), seção 4).
5. **Desempenho mensurável:** consultas têm máximo aceitável de 1 s e alvo de
   500 ms; ações de reserva, cancelamento, alteração e cadastro de manutenção
   têm máximo de 1 s e alvo de 500 ms; relatórios têm máximo de 5 s e alvo de
   2,5 s (RNF-03 a RNF-05 no RFC não funcional).
6. **Relatórios:** apresentar utilização, carga horária, estados de reservas,
   conflitos evitados, movimentação de materiais e indicadores de manutenção;
   filtros incluem período e recursos, com escopo de usuário e unidade também
   mencionado nos critérios (RF-12 no RFC funcional).
7. **Notificações e observabilidade:** registrar eventos operacionais e gerar
   notificações simuladas para eventos importantes, sem exigir integração
   externa para o fluxo principal (RF-17 no RFC funcional; RNF-15 e RNF-16 no
   RFC não funcional).
8. **Qualidade e testes:** cobrir regras de negócio, autorização, conflito,
   concorrência e casos de borda; metas de cobertura de linha e branch são
   80% e 70%, respectivamente. O PRD também exige evidência de TDD/BDD e
   validação de qualidade no pipeline (RF-20 no RFC funcional e RNF-08 a
   RNF-11 no RFC não funcional; RNF-03 a RNF-05, COV-01, COV-02 e QUAL-01 em
   [PRD.md](./PRD.md), seções 5 e 6).

### Atributos de qualidade e critérios documentados

| Atributo | Critério ou evidência existente | Origem |
|---|---|---|
| Segurança | JWT em operações protegidas; autorização por perfil; rejeição de credenciais inválidas; erros sem dados sensíveis. | RF-01, RF-15, RF-16 e RF-19; RNF-01 e RNF-07 no RFC; SEC-01 e RISK-01 no PRD, seção 6 |
| Integridade e confiabilidade | Sem dupla reserva; controle concorrente determinístico; conflitos e respostas previsíveis; integridade nos relatórios. | RF-06 e RF-07; RNF-02 e RNF-17 no RFC; RN-02 e RN-03 no PRD |
| Desempenho | Consultas e ações: máximo 1 s, desejado 500 ms; relatórios: máximo 5 s, desejado 2,5 s. | RNF-03 a RNF-05 no RFC |
| Disponibilidade e escalabilidade | Evoluir módulos e manter estabilidade com crescimento, sem métrica quantitativa definida. | RNF-13 no RFC; objetivo de qualidade em [PRD.md](./PRD.md), seção 1 |
| Auditabilidade e rastreabilidade | Registrar alterações, transições, data, responsável, ação e efeito; PRD requer imutabilidade para transições. | RF-11 e RNF-12 no RFC; RN-09 e RTM-01 no PRD |
| Observabilidade | Eventos de reserva, aprovação, rejeição, manutenção e auditoria; logs estruturados, rastreio e notificações simuladas. | RNF-15 e RNF-16 no RFC; RF-11 no PRD |
| Testabilidade e qualidade | Testes de carga, integração em ambiente realista, TDD/BDD, cobertura mínima, pipeline automatizado e matriz de rastreabilidade. | RF-20 em [requisitos-funcionais.md](../rfc/requisitos-funcionais.md); RNF-06 e RNF-08 a RNF-11 em [requisitos-nao-funcionais.md](../rfc/requisitos-nao-funcionais.md); RNF-01 a RNF-05, COV-01, COV-02, RTM-01 e QUAL-01 em [PRD.md](./PRD.md) |
| Usabilidade e compatibilidade | Interface responsiva, mensagens compreensíveis e funcionalidades críticas acessíveis em diferentes tamanhos de tela. | RF-13 no RFC; RNF-14 no RFC não funcional; RF-13 no PRD |
| Manutenibilidade | Documentação e estrutura que permitam evolução e manutenção. | RF-14 no RFC; RNF-18 no RFC não funcional; entregáveis 3 e 5 do PRD |

## 4. Drivers arquiteturais priorizados

Esta ordem é uma **priorização analítica inicial**, não uma decisão já aprovada
nos documentos. Ela considera critérios explicitamente eliminatórios no PRD,
regras de integridade e limites mensuráveis. Deve ser revisada com os
stakeholders quando as perguntas abertas forem respondidas.

| Prioridade | Driver | Implicação arquitetural a investigar | Origem |
|---|---|---|---|
| **P1 — Integridade da agenda** | Reservas sobrepostas e requisições simultâneas não podem ocupar o mesmo recurso/horário. | A validação de disponibilidade e a persistência da reserva precisam preservar atomicidade e produzir conflito determinístico sob concorrência. | RN-01 a RN-04 no [PRD.md](./PRD.md), seção 4; RF-04, RF-06, RF-07 e RF-09 e RNF-02 nos RFCs; RISK-01 no PRD, seção 6 |
| **P1 — Segurança de identidade e escopo** | Operações protegidas exigem JWT e autorização por papel; solicitantes só manipulam as próprias reservas. | Autenticação, autorização e verificação de propriedade devem ocorrer antes da operação e ser verificáveis nos testes. | USR-01 a USR-03 e RF-01 no PRD; RF-01, RF-15, RF-16 e RF-19 e RNF-01 no RFC; RISK-01 no PRD, seção 6 |
| **P1 — Limites de resposta** | Consultas e ações têm limite de 1 s; relatórios, 5 s, com alvos mais baixos. | Consultas, comandos e relatórios requerem medições separadas sob condições de teste ainda a definir. | RNF-03 a RNF-06 no [requisitos-nao-funcionais.md](../rfc/requisitos-nao-funcionais.md); RF-12 no [requisitos-funcionais.md](../rfc/requisitos-funcionais.md) |
| **P2 — Ciclo de vida auditável** | Mudanças de estado devem obedecer ao fluxo e gerar histórico rastreável. | Transições, autorização de aprovação e auditoria precisam manter consistência entre si. | RF-05, RF-08 e RF-11 em [requisitos-funcionais.md](../rfc/requisitos-funcionais.md); RNF-12 em [requisitos-nao-funcionais.md](../rfc/requisitos-nao-funcionais.md); RN-05 a RN-09 no [PRD.md](./PRD.md), seção 4 |
| **P2 — Evidência verificável de qualidade** | Cobertura, testes de negócio, testes de carga, CI e rastreabilidade são entregáveis/metas. | As decisões devem ser testáveis e produzir evidências ligadas aos requisitos e riscos. | RF-20 em [requisitos-funcionais.md](../rfc/requisitos-funcionais.md); RNF-06 e RNF-08 a RNF-11 em [requisitos-nao-funcionais.md](../rfc/requisitos-nao-funcionais.md); RNF-01 a RNF-05, COV-01, COV-02, RTM-01, QUAL-01 e entregáveis 4, 6 a 9 em [PRD.md](./PRD.md) |
| **P2 — Operação observável** | Eventos e notificações simuladas devem apoiar diagnóstico e acompanhamento sem dependência externa obrigatória. | Definir eventos, dados de auditoria e destino observável das notificações sem pressupor serviço externo. | RNF-15 e RNF-16 em [requisitos-nao-funcionais.md](../rfc/requisitos-nao-funcionais.md); RF-11 em [PRD.md](./PRD.md); RF-17 em [requisitos-funcionais.md](../rfc/requisitos-funcionais.md) |
| **P3 — Evolução e experiência multiplataforma** | A estrutura deve permitir evolução e a interface deve ser responsiva. | Evitar decisões que acoplem fluxos críticos a uma única forma de uso ou dificultem manutenção. | RNF-13, RNF-14 e RNF-18 no RFC não funcional; RF-13 e RF-14 no RFC funcional |

## 5. Glossário

| Termo | Significado nos artefatos |
|---|---|
| **Solicitante** | Professor ou coordenador que consulta recursos e gere suas próprias solicitações de reserva. |
| **Responsável** | Gestor ou coordenador que analisa solicitações especiais, valida alocação e acompanha materiais sob sua responsabilidade. |
| **Administrador** | Perfil de gestão global de cadastros, permissões, bloqueios, manutenção e parâmetros. |
| **Recurso** | Elemento alocável mencionado nos documentos: sala/espaço, professor/docente, material ou equipamento. A equivalência e taxonomia completa não estão especificadas. |
| **Reserva / solicitação de reserva** | Pedido de alocação de um ou mais recursos em um intervalo de tempo, sujeito a disponibilidade, autorização e estados. Os artefatos usam ambos os termos sem formalizar distinção. |
| **Recurso restrito / solicitação especial** | Recurso que exige análise/aprovação de Responsável. Os critérios de classificação e a relação exata entre “restrito” e “especial” não estão definidos. |
| **Bloqueio / manutenção** | Indisponibilidade de agenda vinculada a um recurso; reservas que intersectem período de manutenção devem ser impedidas. |
| **Conflito / sobreposição** | Coincidência total ou parcial de intervalos para o mesmo recurso, professor ou espaço. A regra temporal geral está em RN-01 e RN-02 do PRD. |
| **Notificação simulada** | Evento de notificação produzido pela aplicação para observabilidade, sem exigir integração externa no fluxo principal. |
| **JWT** | Token definido como mecanismo de autenticação para requisições protegidas; formato de claims, emissão e ciclo de vida não especificados. |

## 6. Fatos, lacunas, conflitos e suposições

### Fatos documentados

- Os três perfis e seus escopos gerais são USR-01 a USR-03 do PRD e detalhados
  nos arquivos de personas.
- A ordem temporal de início e término está declarada em RN-01 do PRD; estados
  nominais e alternativos, ordem do fluxo principal e imutabilidade de reservas
  iniciadas estão em RN-05 a RN-09. Os RFCs detalham comportamentos
  relacionados em RF-05, RF-06 e RF-11.
- JWT, concorrência, limites de resposta, cobertura, testes, notificações
  simuladas, auditoria e responsividade estão documentados nos requisitos
  funcionais e não funcionais citados acima.
- O README resume o objetivo do sistema, mas não define tecnologia ou
  arquitetura.

### Lacunas documentais

- Não foi localizado registro independente de decisões arquiteturais, catálogo
  de restrições ou documento separado de regras de negócio. As regras
  identificadas estão na seção 4 do PRD e os requisitos detalhados estão nos
  RFCs.
- O PRD menciona `RTM.md` como entregável e define RTM-01, mas esse arquivo não
  está entre os artefatos consultados.
- Não estão definidos volumes de usuários, recursos, reservas, concorrência ou
  dados; cenário, carga e ambiente para aferir as metas de tempo; nem
  percentis, janela ou condições de medição.
- Não estão especificados provedor/emissão/renovação/revogação de JWT,
  autorização granular além dos perfis e propriedade, retenção/acesso à
  auditoria, nem política de backup/recuperação.
- Não está definido o grafo completo de transições entre estados, incluindo
  em quais condições e por qual perfil uma reserva pode ser alterada,
  cancelada, rejeitada ou marcada como não comparecimento.
- A classificação de recurso restrito, o significado operacional de
  “solicitação especial”, o modelo de unidades, a taxonomia dos recursos e a
  semântica dos filtros de relatório não estão fechados.
- Não estão definidos formato, persistência, consulta e destinatário das
  notificações simuladas, nem se haverá integração externa opcional.
- Não há decisão de tecnologia de implementação, persistência, hospedagem,
  topologia ou limites entre componentes. Ferramentas e tecnologias citadas
  para testes/entrega (por exemplo, JMeter, Testcontainers, WireMock,
  GitHub Actions, JaCoCo e SonarCloud) não constituem, por si, uma decisão
  sobre a arquitetura de produção.
- Os documentos não indicam stakeholders além das três personas, nem
  responsabilidades por operação, suporte ou aprovação das decisões.

### Conflitos e inconsistências entre fontes

- **Identificadores RF/RNF não correspondem entre documentos.** No PRD,
  RF-04 é ciclo de reservas e RF-05 é detecção de sobreposição; no RFC
  funcional, RF-04 é agenda/disponibilidade e RF-05 é ciclo de vida. No PRD,
  RNF-01 é robustez/desempenho; no RFC não funcional, RNF-01 é segurança.
  Portanto, a rastreabilidade não pode usar apenas os identificadores.
- **Escopo de notificação não está alinhado.** RF-11 do PRD admite notificação
  simulada ou integração direta com API externa. RF-17 do RFC funcional
  especifica notificação simulada para o resultado de aprovação/rejeição, e
  RNF-16 do RFC não funcional diz que integração externa não é exigida para o
  fluxo principal. Isso não esclarece se integração opcional é permitida ou
  desejada.
- **Filtros de relatório têm escopos diferentes.** RF-12 no RFC funcional
  menciona filtros por período, usuário, sala, professor, material e status;
  seus critérios de aceite mencionam data, unidade, recurso e status. A
  equivalência e obrigatoriedade dos filtros não estão claras.
- **Imutabilidade da auditoria tem escopos diferentes.** RF-11 do RFC
  funcional pede registro imutável de alterações de estado, dados e eventos;
  RNF-12 especifica persistência das alterações relevantes; RN-09 do PRD
  exige imutabilidade para mudanças de estado. A imutabilidade para alterações
  que não sejam transições de estado precisa ser reconciliada.
- **Perfil do Administrador é amplo, sem regra granular equivalente.** O
  arquivo da persona diz que não há restrição operacional relevante dentro do
  escopo administrativo; RF-15 do RFC, por outro lado, exige acesso estrito
  por perfil. Os documentos não definem limites adicionais para ações
  administrativas.

### Suposições analíticas desta seção

- A ordenação P1/P2/P3 é uma proposta de priorização fundamentada nos
  critérios do PRD e nos requisitos explícitos, não uma prioridade acordada
  pelos stakeholders.
- “Recurso” é usado como termo agregador para os tipos citados, sem afirmar
  que possuem o mesmo modelo de dados ou ciclo operacional.
- Uma meta de tempo só poderá ser considerada demonstrada após definição do
  cenário e método de medição; esta seção não presume condições de carga.

## 7. Perguntas abertas

1. Qual documento e versão prevalecem quando os identificadores RF/RNF ou os
   critérios diferem entre o PRD e os RFCs? Deve ser criada uma matriz de
   correspondência estável?
2. A integração externa de notificações é permitida como extensão opcional,
   ou o escopo deve permanecer exclusivamente simulado?
3. Quais filtros de relatório são obrigatórios e como se relacionam unidade,
   recurso, professor, material e usuário?
4. Qual é o grafo completo de transições de reserva, incluindo atores
   autorizados, condições, alteração e cancelamento em cada estado?
5. Como são definidos recursos restritos e solicitações especiais? A aprovação
   é exigida apenas para recursos restritos ou para outras situações?
6. Quais volume, concorrência, ambiente, percentil e método devem caracterizar
   cada meta de desempenho?
7. Qual é a política de retenção, consulta e proteção dos registros de
   auditoria, e quais alterações além de transições precisam ser imutáveis?
8. Quais são o fluxo de emissão, expiração e revogação do JWT e as permissões
   granulares esperadas para cada perfil?
9. Qual é a taxonomia oficial de recursos e o significado de unidade nos
   filtros e na administração?
10. Quem são os stakeholders responsáveis por validar regras, prioridades,
    operação e aceite arquitetural? Onde será mantido o registro de decisões
    e a matriz RTM?

## 8. Riscos iniciais

Os itens abaixo são riscos preliminares derivados dos requisitos e lacunas;
não representam avaliação quantitativa de probabilidade.

| Risco | Impacto potencial | Base documental |
|---|---|---|
| Corrida entre solicitações permitir dupla alocação. | Conflito real de agenda e falha em critério explicitamente eliminatório. | RN-02 e RN-03 do PRD; RF-07 e RNF-02 dos RFCs; RISK-01 no PRD |
| Autorização ou verificação de propriedade incompleta. | Acesso indevido a reservas ou ações; falha em critério eliminatório. | RF-01, RF-15, RF-16 e RF-19; RNF-01; RISK-01 no PRD |
| Metas de desempenho sem cenário de medição comum. | Resultados não reproduzíveis ou não comparáveis; aceite de desempenho inconclusivo. | RNF-03 a RNF-06; volumes e condições não definidos |
| Divergência de estados e transições ser resolvida de forma diferente entre componentes e testes. | Reservas presas em estados ou ações inválidas aceitas/rejeitadas inconsistentemente. | RF-05, RN-05 a RN-08 e lacuna do grafo de transições |
| Auditoria não atender ao significado de “imutável” ou não preservar autoria e efeito. | Perda de rastreabilidade e evidência insuficiente para análise de incidentes. | RF-11, RNF-12 e RN-09 |
| Ambiguidade sobre integrações de notificação levar a escopo ou dependência externa inesperados. | Retrabalho ou dependência de serviço não requerida para o fluxo principal. | RF-11 do PRD; RF-17 e RNF-16 dos RFCs |
| Diferenças de identificadores e critérios dificultarem rastreabilidade requisito-teste-risco. | Requisitos críticos podem não estar ligados às evidências exigidas. | RNF-01 a RNF-05 e RTM-01 no PRD; numeração RF/RNF nos RFCs |
| Filtros e volumes de relatório não definidos impedirem validar o limite de resposta ou completude dos dados. | Relatórios inconsistentes ou aceitação ambígua. | RF-12 e RNF-05; critérios de filtro distintos no RFC funcional |
