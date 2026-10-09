# Requisitos Não Funcionais (RNF)

Este documento define os requisitos não funcionais do sistema, focando em segurança, desempenho, qualidade de software, confiabilidade, observabilidade e governança dos testes e da operação.

## RNF-01 — Segurança da autenticação e autorização
A autenticação do sistema deve ocorrer com JWT, com validação obrigatória em todos os endpoints protegidos e controle estrito de escopo por perfil.

Métrica e critério de aceite:
- Tokens inválidos, expirados ou ausentes devem ser rejeitados.
- A autorização deve validar o papel do usuário antes da execução da ação.
- A aplicação não deve expor dados sensíveis em mensagens de erro.

## RNF-02 — Integridade e proteção contra concorrência
O sistema deve garantir integridade de dados mesmo sob múltiplas solicitações simultâneas, evitando reservas duplicadas ou inconsistentes.

Métrica e critério de aceite:
- Não pode haver duas reservas aprovadas para o mesmo recurso no mesmo intervalo.
- A concorrência deve ser tratada com controle transacional ou mecanismo equivalente.
- Em caso de conflito, o sistema deve rejeitar a segunda tentativa de forma determinística.

## RNF-03 — Tempo de resposta da API em consultas
As consultas da API devem respeitar limites de tempo rigorosos para garantir experiência responsiva e operação estável.

Métrica e critério de aceite:
- Tempo máximo aceitável: 1 segundo.
- Tempo desejado: 500 ms.
- Consultas de disponibilidade, busca por recursos e leitura de dados relevantes devem responder em até 1 segundo, sendo o objetivo de desempenho 500 ms.
- O sistema deve ser validado com testes de carga e execução concorrente para consultas de disponibilidade, busca por recursos e leitura de dados relevantes.

## RNF-04 — Tempo de resposta da API em ações de disparo
As ações de disparo da API, como reserva, cancelamento e cadastro de manutenção, devem responder dentro do limite operacional definido para garantir velocidade e confiabilidade.

Métrica e critério de aceite:
- Tempo máximo aceitável: 1 segundo.
- Tempo desejado: 500 ms.
- Requisições de criação, alteração, cancelamento de reserva e cadastro de manutenção devem responder em até 1 segundo, sendo o objetivo de desempenho 500 ms.
- O sistema deve ser validado em cenários de criação, alteração, cancelamento e manutenção em situações concorrentes.

## RNF-05 — Tempo de resposta da API para relatórios
A geração e consulta de relatórios deve respeitar limites de tempo aceitáveis para uso em ambiente operacional.

Métrica e critério de aceite:
- Tempo máximo aceitável: 5 segundos.
- Tempo adequado: 2,5 segundos.
- Relatórios com filtros complexos, agregações e grandes volumes de dados devem manter resposta dentro desses limites.
- Os relatórios devem ser gerados com dados consistentes e sem perda de integridade.

## RNF-06 — Robustez sob carga e estabilidade operacional
O sistema deve manter estabilidade em cenários de uso simultâneo, com alta demanda de requisições e múltiplos usuários acessando recursos ao mesmo tempo.

Métrica e critério de aceite:
- O sistema deve ser validado com testes de carga usando ferramentas como JMeter.
- O tempo de resposta não deve degradar severamente sob carga incremental.
- A aplicação deve manter comportamento previsível mesmo com concorrência em operações críticas.

## RNF-07 — Segurança de entrada e tratamento de exceções
O sistema deve validar todas as entradas de dados e tratar erros de forma segura, sem exposição de detalhes internos ou sensíveis.

Métrica e critério de aceite:
- Entrada nula, vazia, malformada ou fora do domínio deve ser rejeitada.
- Erros devem ser registrados e tratados de forma segura.
- Mensagens para o cliente devem ser claras, sem vazar stack traces, credenciais ou dados internos.

## RNF-08 — Ambiente realista de testes
Testes de integração e comportamento devem ser executados em ambiente próximo ao real, com bancos e componentes isolados de forma confiável.

Métrica e critério de aceite:
- Persistência deve ser validada em ambiente realista usando containers.
- Integrações externas devem ser simuladas com WireMock, quando necessário.
- Mocks devem ser usados apenas para testes unitários isolados e não como substituição do ambiente realista de integração.

## RNF-09 — Pipeline de integração contínua
O repositório deve conter automação de integração contínua e validação de qualidade a cada pull request.

Métrica e critério de aceite:
- Deve existir pipeline via GitHub Actions.
- O pipeline deve executar compilação, testes e análise estática.
- Falhas críticas devem bloquear a entrega ou a revisão da alteração.

## RNF-10 — Práticas de engenharia de software
O projeto deve demonstrar aplicação de práticas modernas de engenharia de software, com evidência de desenvolvimento guiado por testes.

Métrica e critério de aceite:
- Pelo menos uma funcionalidade crítica deve ser desenvolvida com TDD ou BDD.
- A suíte de testes deve refletir comportamento real de negócio, não apenas cenários artificiais.
- Testes devem validar regras de negócio, autorização e conflito de agenda.

## RNF-11 — Cobertura mínima de testes
O sistema deve atingir níveis mínimos de cobertura e qualidade de testes para reduzir riscos de regressão e garantir robustez.

Métrica e critério de aceite:
- Cobertura de linhas: mínimo de 80%.
- Cobertura de branches: mínimo de 70%.
- Testes devem cobrir regras críticas de negócio, permissões e conflitos temporais.

## RNF-12 — Auditoria, rastreabilidade e histórico
O sistema deve registrar de forma persistente todas as alterações relevantes, principalmente transições de estado e ações administrativas.

Métrica e critério de aceite:
- Qualquer mudança de estado deve produzir registro de auditoria.
- O registro deve conter data, responsável, ação e efeito aplicado.
- O histórico deve permitir a rastreabilidade de decisões e a solução de incidentes.

## RNF-13 — Disponibilidade, escalabilidade e manutenção
O sistema deve ser construído para evoluir sem comprometer disponibilidade, performance ou clareza operacional.

Métrica e critério de aceite:
- A estrutura deve permitir evolução de novos módulos e recursos sem reescrita de base.
- O sistema deve manter operação estável mesmo com crescimento de usuários e registros.
- A manutenção deve ser viável por documentação e arquitetura bem definida.

## RNF-14 — Compatibilidade e responsividade
O sistema deve funcionar alinhado ao uso em diferentes dispositivos, mantendo usabilidade e clareza na interação.

Métrica e critério de aceite:
- A interface deve adaptar-se a telas menores e maiores.
- A experiência deve permanecer clara para usuários com diferentes níveis de exigência operacional.
- Nenhuma funcionalidade crítica deve depender exclusivamente de um tipo específico de dispositivo.

## RNF-15 — Observabilidade e diagnósticos
O sistema deve permitir acompanhamento do comportamento do sistema por meio de rastreio de eventos, logs e indicadores de operação.

Métrica e critério de aceite:
- O sistema deve registrar eventos relevantes de reservas, aprovação, rejeição, manutenção e auditoria.
- O processo de debugging deve ser apoiado por logs estruturados e rastreios claros.
- O acompanhamento deve servir para análise de falhas, conflitos e uso dos recursos.

## RNF-16 — Notificações simuladas para observabilidade
O sistema deve usar notificações simuladas como mecanismo de observabilidade, sem depender de integrações externas para o fluxo principal da aplicação.

Métrica e critério de aceite:
- A aplicação deve gerar notificações simuladas para eventos importantes.
- As notificações devem permitir acompanhamento do status das reservas e eventos críticos.
- Integrações externas não são exigidas para o funcionamento principal da aplicação.

## RNF-17 — Qualidade e confiabilidade das respostas
A aplicação deve entregar respostas consistentes, previsíveis e adequadas ao contexto do usuário, preservando integridade e clareza nas interações.

Métrica e critério de aceite:
- Respostas de sucesso, erro e conflito devem ser claras e consistentes.
- O sistema deve evitar ambiguidades em ações de reserva e aprovação.
- A confiabilidade deve ser validada em testes de integração e regressão.

## RNF-18 — Documentação e manutenção contínua
O sistema deve manter documentação suficiente para operação, manutenção e evolução técnica.

Métrica e critério de aceite:
- Deve haver documentação de instalação, execução e principais fluxos de uso.
- O projeto deve estar organizado para facilitar manutenção e revisão por pares.
- A documentação deve corresponder ao comportamento efetivo da aplicação.
