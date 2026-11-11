# language: pt
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

  Esquema do Cenário: Transição permitida é aceita
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
      | de             | para           |
      | SOLICITADA     | EM_USO         |
      | SOLICITADA     | CONCLUIDA      |
      | SOLICITADA     | NAO_COMPARECEU |
      | SOLICITADA     | SOLICITADA     |
      | APROVADA       | SOLICITADA     |
      | APROVADA       | CONCLUIDA      |
      | APROVADA       | REJEITADA      |
      | APROVADA       | APROVADA       |
      | EM_USO         | SOLICITADA     |
      | EM_USO         | APROVADA       |
      | EM_USO         | REJEITADA      |
      | EM_USO         | CANCELADA      |
      | EM_USO         | NAO_COMPARECEU |
      | EM_USO         | EM_USO         |
      | CONCLUIDA      | SOLICITADA     |
      | CONCLUIDA      | APROVADA       |
      | CONCLUIDA      | EM_USO         |
      | CONCLUIDA      | REJEITADA      |
      | CONCLUIDA      | CANCELADA      |
      | CONCLUIDA      | NAO_COMPARECEU |
      | CONCLUIDA      | CONCLUIDA      |
      | REJEITADA      | SOLICITADA     |
      | REJEITADA      | APROVADA       |
      | REJEITADA      | EM_USO         |
      | REJEITADA      | CONCLUIDA      |
      | REJEITADA      | CANCELADA      |
      | REJEITADA      | NAO_COMPARECEU |
      | REJEITADA      | REJEITADA      |
      | CANCELADA      | SOLICITADA     |
      | CANCELADA      | APROVADA       |
      | CANCELADA      | EM_USO         |
      | CANCELADA      | CONCLUIDA      |
      | CANCELADA      | REJEITADA      |
      | CANCELADA      | NAO_COMPARECEU |
      | CANCELADA      | CANCELADA      |
      | NAO_COMPARECEU | SOLICITADA     |
      | NAO_COMPARECEU | APROVADA       |
      | NAO_COMPARECEU | EM_USO         |
      | NAO_COMPARECEU | CONCLUIDA      |
      | NAO_COMPARECEU | REJEITADA      |
      | NAO_COMPARECEU | CANCELADA      |
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
