# language: pt
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
      | caso                                       | inicio              | fim                 | resultado                  |
      | termina exatamente no início da manutenção | 2026-11-20T11:00:00 | 2026-11-20T12:00:00 | aceita (estado SOLICITADA) |
      | começa exatamente no fim da manutenção     | 2026-11-20T13:00:00 | 2026-11-20T14:00:00 | aceita (estado SOLICITADA) |
      | coincide exatamente com a manutenção       | 2026-11-20T12:00:00 | 2026-11-20T13:00:00 | recusada com 409           |
      | cruza 1 segundo no início                  | 2026-11-20T11:00:00 | 2026-11-20T12:00:01 | recusada com 409           |
      | cruza 1 segundo no fim                     | 2026-11-20T12:59:59 | 2026-11-20T14:00:00 | recusada com 409           |
      | termina 1 segundo antes do início          | 2026-11-20T11:58:59 | 2026-11-20T12:00:00 | aceita (estado SOLICITADA) |
      | começa 1 segundo depois do fim             | 2026-11-20T13:00:00 | 2026-11-20T13:01:00 | aceita (estado SOLICITADA) |
