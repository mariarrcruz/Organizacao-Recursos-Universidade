# Product Requirement Document (PRD) — Sistema de Organização de Recursos

## 1. Contexto e Objetivos

* **Contexto Metodológico:** As técnicas demonstradas em aula no projeto de referência *Foot Fanatics* devem ser aplicadas incrementalmente pelas equipes neste sistema de organização de recursos.
* **Objetivo:** Desenvolver e validar uma aplicação confiável para alocar salas, professores e materiais sem conflitos de horário, fornecendo evidências objetivas de qualidade, segurança, rastreabilidade e desempenho.

---

## 2. Personas e Perfis de Acesso

| ID Perfil | Nome do Perfil | Descrição e Responsabilidades |
| :--- | :--- | :--- |
| **USR-01** | **Solicitante** | Professor ou coordenador responsável por consultar a disponibilidade e criar, alterar ou cancelar suas próprias reservas. |
| **USR-02** | **Responsável** | Papel encarregado de aprovar solicitações especiais, validar a alocação de docentes e acompanhar retiradas e devoluções de material. |
| **USR-03** | **Administrador** | Gestor global responsável pelo gerenciamento de salas, professores, materiais, usuários, bloqueios e períodos de manutenção. |

---

## 3. Requisitos Funcionais (RF)

* **RF-01 (Autenticação e Autorização):** O sistema deve implementar autenticação e autorização estritas baseadas nos perfis Solicitante, Responsável e Administrador.
* **RF-02 (Gestão de Recursos):** O sistema deve fornecer cadastro e consulta completa de salas, professores e materiais.
* **RF-03 (Pesquisa Parametrizada):** O sistema deve permitir pesquisa de recursos filtrando por tipo, capacidade, localização, competência e disponibilidade.
* **RF-04 (Ciclo de Reservas):** O sistema deve permitir a criação, alteração e cancelamento de reservas pelos usuários autorizados.
* **RF-05 (Detecção de Sobreposição):** O sistema deve identificar e impedir qualquer sobreposição de horários, cobrindo inclusive a agenda de disponibilidade do professor.
* **RF-06 (Prevenção de Dupla Reserva Concorrente):** O sistema deve garantir integridade e proteção contra dupla reserva em caso de solicitações concorrentes ou simultâneas.
* **RF-07 (Controle de Recursos Restritos):** O sistema deve exigir aprovação obrigatória de um Responsável para a alocação de recursos classificados como restritos.
* **RF-08 (Bloqueio por Manutenção):** O sistema deve bloquear a reserva de recursos cadastrados em período de manutenção.
* **RF-09 (Controle de Retirada e Devolução):** O sistema deve registrar formalmente o fluxo de retirada e devolução de materiais e equipamentos.
* **RF-10 (Auditoria de Modificações):** O sistema deve manter histórico auditável de todas as alterações e transições de estado.
* **RF-11 (Comunicação e Notificação):** O sistema deve conter notificação simulada ou integração direta com API externa.
* **RF-12 (Relatórios Gerenciais):** O sistema deve emitir relatórios de utilização por recurso, total de carga horária alocada e volume de conflitos evitados.
* **RF-13 (Interface do Usuário):** O sistema deve disponibilizar interface responsiva e exibir mensagens de erro compreensíveis ao usuário.
* **RF-14 (Documentação Técnica):** O sistema deve disponibilizar documentação técnica da API ou dos fluxos públicos da aplicação.

---

## 4. Regras de Negócio (RN)

### 4.1. Tempo e Concorrência
* **RN-01 (Ordem Temporal):** O horário de término de uma reserva deve ser obrigatoriamente posterior ao horário de início.
* **RN-02 (Exclusividade de Alocação):** Reservas para um mesmo recurso (sala, material ou professor) não podem apresentar nenhuma sobreposição de horário.
* **RN-03 (Atomicidade sob Concorrência):** Duas solicitações enviadas simultaneamente para o mesmo recurso e horário devem resultar em apenas uma reserva aceita, rejeitando a concorrente.
* **RN-04 (Inoperância por Manutenção):** Nenhum recurso que esteja alocado em período de manutenção pode ser reservado.

### 4.2. Ciclo de Vida e Auditoria
* **RN-05 (Fluxo Principal de Estados):** O ciclo de vida nominal da reserva segue estritamente a ordem: `SOLICITADA` $\rightarrow$ `APROVADA` $\rightarrow$ `EM_USO` $\rightarrow$ `CONCLUÍDA`.
* **RN-06 (Estados Alternativos):** O sistema admite os estados alternativos `REJEITADA`, `CANCELADA` e `NÃO COMPARECEU`.
* **RN-07 (Alçada de Aprovação):** Somente usuários com perfil de Responsável possuem permissão para aprovar solicitações de recursos restritos.
* **RN-08 (Imutabilidade de Reservas em Execução):** Reservas que já foram iniciadas não podem ser excluídas do sistema.
* **RN-09 (Rastreabilidade Obrigatória):** Toda e qualquer mudança de estado em uma reserva deve gerar registro de auditoria imutável.

---

## 5. Requisitos Não Funcionais (RNF)

* **RNF-01 (Robustez sob Carga e Desempenho):** O sistema deve ser validado via testes de carga com Apache JMeter, demonstrando estabilidade em cenários de requisições simultâneas.
* **RNF-02 (Segurança de Entrada e Tratamento de Exceções):** O sistema deve realizar validação rigorosa de entradas de dados e garantir tratamento seguro de erros sem exposição de dados sensíveis.
* **RNF-03 (Ambiente e Fidelidade de Testes):** Persistência e integrações externas críticas devem ser validadas utilizando ambiente realista com banco de dados em container via Testcontainers e isolamento via WireMock. O uso de mocks é restrito a testes unitários isolados.
* **RNF-04 (Automação de Pipelines CI/CD):** O repositório deve conter pipeline automatizado via GitHub Actions executado a cada pull request, integrando compilação, testes e análise estática.
* **RNF-05 (Práticas de Engenharia de Software):** Pelo menos uma nova funcionalidade do sistema deve ser desenvolvida comprovadamente com abordagem TDD (*Test-Driven Development*) ou BDD (*Behavior-Driven Development*).

---

## 6. Metas Mensuráveis e Critérios de Aceite (Quality Gates)

* **COV-01 (Cobertura de Linhas):** Obter no mínimo 80% de cobertura de linhas aferida via JaCoCo.
* **COV-02 (Cobertura de Branches):** Obter no mínimo 70% de cobertura de ramos (*branches*) aferida via JaCoCo.
* **RTM-01 (Completude da Matriz):** Manter 100% dos requisitos críticos mapeados na Matriz de Rastreabilidade (`RTM.md`) ligando requisito, risco, teste e evidência.
* **SEC-01 (Vulnerabilidades e Defeitos):** Atingir a marca de 0 bugs e 0 vulnerabilidades críticas na inspeção do SonarCloud.
* **QUAL-01 (Relevância dos Testes):** A cobertura de código não pode ser atingida por testes triviais desprovidos de asserções reais de negócio.
* **RISK-01 (Limitadores de Nota / Critérios Eliminatórios):** A ocorrência de qualquer um dos seguintes cenários inviabiliza a aprovação máxima do projeto: aplicação que não executa, pipeline ausente, falha de autorização ou permissão de dupla reserva.

---

## 7. Entregáveis Obrigatórios

1. **Repositório de Código:** Histórico de versionamento ativo contendo evidências de trabalho contínuo e revisão por pares.
2. **Pacote da Aplicação:** Aplicação executável acompanhada de instruções detalhadas de instalação e execução.
3. **Documentação Operacional:** Arquivo `README.md` estruturado e documentação dos fluxos ou das APIs.
4. **Matriz de Rastreabilidade:** Arquivo `RTM.md` conectando cada requisito ao seu risco associado, teste correspondente e evidência objetiva.
5. **Modelagem Arquitetural:** Diagramas de contexto, componentes e sequências críticas do sistema.
6. **Suíte e Relatório de Testes:** Plano de testes, suíte automatizada (JUnit 5 com unitários, parametrizados, integração, caixa-preta e end-to-end) e relatório de execução.
7. **Pipeline de CI:** Configuração operacional de integração contínua (GitHub Actions).
8. **Relatórios de Qualidade de Código:** Relatórios consolidados de análise estática do SonarCloud e de cobertura de testes do JaCoCo.
9. **Evidências de Performance:** Plano de testes de carga e relatório com resultados consolidados via JMeter.
10. **Gestão de Defeitos:** Registro formal de defeitos identificados, triados, priorizados e corrigidos durante o ciclo de desenvolvimento.
11. **Relatório Final de Qualidade:** Documento executivo consolidando as decisões, medições e evidências de qualidade do projeto.
12. **Demonstração Prática:** Apresentação técnica e demonstração funcional do sistema em execução.