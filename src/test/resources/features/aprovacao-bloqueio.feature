# language: pt
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
