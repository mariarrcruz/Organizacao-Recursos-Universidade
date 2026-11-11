# language: pt
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

  Cenário: Cancelamento pelo próprio solicitante continua permitido
    Dado que "alice" tem uma reserva SOLICITADA em "SALA-02"
    Quando "alice" cancela a própria reserva com justificativa "mudança de planos"
    Então a reserva fica com estado CANCELADA
