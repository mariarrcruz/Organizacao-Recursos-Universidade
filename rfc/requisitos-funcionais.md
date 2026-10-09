# Requisitos Funcionais (RF)

Este documento detalha os requisitos funcionais do sistema de organização de recursos, com foco em autenticação, reservas, autorização por perfil, gestão dos recursos, relatórios e rastreabilidade. Cada requisito abaixo descreve o comportamento esperado e o critério de aceitação do sistema.

## RF-01 — Autenticação e autorização por perfil
O sistema deve implementar autenticação com JWT e autorização por perfil, permitindo que cada usuário acesse apenas as funcionalidades compatíveis com seu papel: Solicitante, Responsável ou Administrador.

Critérios de aceite:
- O usuário autenticado deve receber um JWT válido ao realizar login.
- Cada requisição protegida deve conter o JWT no cabeçalho de autorização.
- O sistema deve rejeitar acessos sem token ou com token inválido.
- As ações devem ser restringidas por perfil conforme as permissões definidas.

## RF-02 — Gestão de recursos institucionais
O sistema deve permitir o cadastro, consulta, atualização e exclusão de salas, materiais, equipamentos, professores e usuários, de acordo com o perfil autorizado.

Critérios de aceite:
- O Administrador deve poder cadastrar, editar e remover recursos.
- O sistema deve manter os dados básicos de cada recurso, como identificação, tipo, disponibilidade e status.
- Recursos com status indisponível ou em manutenção não devem aparecer como disponíveis para reserva.

## RF-03 — Pesquisa parametrizada de recursos
O sistema deve oferecer filtros para localizar recursos por tipo, localização, capacidade, competência, disponibilidade e período desejado.

Critérios de aceite:
- O usuário deve conseguir buscar salas, professores, materiais e equipamentos por critérios específicos.
- O sistema deve retornar apenas recursos compatíveis com os filtros informados.
- A busca deve considerar disponibilidade temporal e disponibilidade física do recurso.

## RF-04 — Cadastro de agenda e disponibilidade
O sistema deve registrar a disponibilidade dos recursos, considerando horários, folgas, manutenção, bloqueios e indisponibilidade pontual.

Critérios de aceite:
- O sistema deve distinguir entre disponibilidade normal e indisponibilidade por manutenção ou bloqueio.
- A agenda deve suportar intervalos de tempo e regras de exclusão entre reservas.
- A indisponibilidade deve ser considerada na validação de novas reservas.

## RF-05 — Ciclo de vida das reservas
O sistema deve permitir que um usuário autorizado solicite, consulte, altere e cancele reservas, seguindo o fluxo definido de estados e transições.

Critérios de aceite:
- A reserva deve ser criada no estado SOLICITADA.
- A reserva pode evoluir para APROVADA, EM_USO, CONCLUÍDA, REJEITADA, CANCELADA ou NÃO COMPARECEU.
- Transições inválidas devem ser bloqueadas pelo sistema.
- Uma reserva em uso não deve poder ser excluída do sistema.

## RF-06 — Detecção de sobreposição de horários
O sistema deve identificar qualquer conflito de agenda entre reservas que utilizem o mesmo recurso, professor ou espaço físico.

Critérios de aceite:
- O sistema deve impedir reservas simultâneas para o mesmo recurso.
- A sobreposição envolvendo professores, salas e materiais deve ser bloqueada.
- O sistema deve informar claramente o motivo do bloqueio ao usuário.

## RF-07 — Proteção contra dupla reserva concorrente
O sistema deve garantir integridade transacional em cenários de concorrência, evitando que duas solicitações simultâneas aceitem a mesma alocação.

Critérios de aceite:
- Duas requisições concorrentes para o mesmo recurso no mesmo horário não podem resultar em duas reservas aprovadas.
- O sistema deve aplicar controle de concorrência na criação da reserva.
- A segunda requisição deve receber resposta de rejeição ou conflito.

## RF-08 — Recursos restritos e aprovação obrigatória
O sistema deve exigir aprovação de um Responsável para recursos classificados como restritos ou sensíveis.

Critérios de aceite:
- Recursos restritos devem possuir flag de restrição ou classificação.
- Apenas usuários com perfil Responsável conseguem aprovar esse tipo de solicitação.
- Solicitações de recursos restritos sem aprovação não podem entrar em uso.

## RF-09 — Bloqueio por manutenção
O sistema deve impedir reservas em períodos nos quais o recurso estiver programado para manutenção.

Critérios de aceite:
- Períodos de manutenção devem ser cadastrados e vinculados ao recurso.
- Reservas que intersectem esse período devem ser rejeitadas automaticamente.
- O sistema deve exibir a razão do bloqueio na interface e na API.

## RF-10 — Controle de retirada e devolução de materiais
O sistema deve registrar formalmente a retirada e a devolução de materiais e equipamentos após a aprovação da reserva.

Critérios de aceite:
- O Responsável deve poder registrar a retirada com identificação do material, usuário e data/hora.
- A devolução deve ser registrada com validação do estado do item.
- O histórico da movimentação deve ser persistido para auditoria.

## RF-11 — Auditoria e rastreabilidade de alterações
O sistema deve manter registro imutável de mudanças de estado, alterações de dados e eventos relevantes nas reservas e nos recursos.

Critérios de aceite:
- Toda alteração em reservas deve gerar registro de auditoria.
- O sistema deve permitir consulta ao histórico de mudanças.
- O registro deve incluir usuário, data, ação e justificativa, quando relevante.

## RF-12 — Relatórios gerenciais
O sistema deve disponibilizar relatórios de desempenho e uso dos recursos, com base em filtros por período, usuário, sala, professor, material e status da reserva.

Tipos de relatório obrigatórios:
- utilização por recurso;
- carga horária alocada por sala/professor/material;
- total de reservas aprovadas, rejeitadas, canceladas e concluídas;
- volume de conflitos evitados;
- histórico de retiradas e devoluções de materiais;
- indicadores de manutenção e bloqueios.

Critérios de aceite:
- O Administrador e o Responsável devem conseguir gerar relatórios.
- O relatório deve aceitar filtros de data, unidade, recurso e status.
- O sistema deve exportar ou apresentar os dados de forma legível para tomada de decisão.
- Os limites de tempo de resposta aplicáveis a consultas, ações de disparo e relatórios estão definidos nos RNF-03, RNF-04 e RNF-05.

## RF-13 — Interface do usuário
O sistema deve possuir interface responsiva, clara e compreensível, com mensagens de erro objetivas e feedback de ação do usuário.

Critérios de aceite:
- A interface deve funcionar em diferentes tamanhos de tela.
- Mensagens de erro devem indicar a causa do problema e a ação esperada.
- As operações de reserva devem indicar se o processo foi realizado, rejeitado ou aguardando aprovação.

## RF-14 — Documentação técnica e de uso
O sistema deve disponibilizar documentação técnica da API e dos fluxos principais da aplicação para apoiar manutenção e integração.

Critérios de aceite:
- O projeto deve conter README com instruções de instalação e execução.
- Devem estar descritos os endpoints principais ou os fluxos públicos relevantes.
- A documentação deve ser suficiente para permitir entendimento técnico do sistema.

## RF-15 — Controle de acesso por perfil
Cada perfil deve ter acesso estritamente às ações permitidas no contexto do sistema.

Critérios de aceite:
- Solicitante: consulta e gestão apenas de suas próprias reservas.
- Responsável: validação, aprovação e acompanhamento das reservas sob sua responsabilidade.
- Administrador: gestão global de usuários, recursos, permissões e agendas.

## RF-16 — Reserva do próprio usuário
O Solicitante deve poder consultar, criar, alterar e cancelar apenas suas próprias reservas e não deve ter permissão para manipular reservas de terceiros.

Critérios de aceite:
- O sistema deve validar a identidade do usuário antes da modificação.
- A ação deve ser rejeitada se tentar alterar uma reserva que não pertença ao usuário autenticado.

## RF-17 — Aprovação e rejeição de solicitações especiais
O Responsável deve poder analisar solicitações especiais, avaliar a necessidade e decidir aprovação ou rejeição.

Critérios de aceite:
- O sistema deve listar solicitações pendentes para aprovação.
- A decisão deve alterar o estado da reserva e registrar o responsável que aprovou ou rejeitou.
- A notificação de resultado deve ser simulada pelo sistema.

## RF-18 — Administração global
O Administrador deve ter permissão para gerenciar usuários, recursos, bloqueios, manutenção e parâmetros do sistema.

Critérios de aceite:
- O Administrador pode cadastrar, editar e excluir salas, materiais, equipamentos e usuários.
- Deve poder criar e remover bloqueios de agenda.
- Deve ser capaz de configurar períodos de manutenção e parâmetros gerais do sistema.

## RF-19 — Validação de autenticação em operações protegidas
Antes de executar uma operação protegida e produzir sua resposta, o sistema deve validar o JWT da requisição e a autorização do usuário, sem repetir validações desnecessárias dentro do mesmo fluxo.

Critérios de aceite:
- O token deve ser validado antes da execução da operação protegida.
- Tokens expirados ou inválidos e usuários sem autorização devem ser rejeitados.
- A validação deve ocorrer no gateway, middleware ou camada de segurança do sistema.

## RF-20 — Testes de borda e cenários críticos
O sistema deve contemplar testes de borda e cenários específicos, cobrindo entradas extremas, conflitos, horários inválidos e condições de exceção.

Critérios de aceite:
- Devem existir casos para horários inválidos, sobreposição, manutenção, regras de perfil e concorrência.
- O sistema deve rejeitar entradas fora das regras de negócio.
- Os testes devem validar cenários específicos e desejados da aplicação.
