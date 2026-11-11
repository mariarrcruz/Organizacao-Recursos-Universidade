# ADR-017: Persistência de reservas em PostgreSQL atrás de uma porta de armazenamento

**Status:** Aceito

**Data:** 2026-11-11

**Relaciona-se a:** ADR-002 (H2 relacional local), ADR-005 (auditoria append-only), ADR-016 (exclusão em PostgreSQL), DEF-001, DEF-002

---

## Contexto

O `ServicoReservas` guardava reservas, manutenções e auditoria em listas na memória. Isso impedia a garantia de exclusividade (RN-02, RN-03) de valer entre instâncias, e a auditoria não era persistente (RN-09). O ADR-016 já colocou a restrição de exclusão no PostgreSQL, mas a aplicação não a usava.

O ADR-002 aprovou H2. Para o caminho de reservas, a equipe escolheu PostgreSQL, porque a restrição `EXCLUDE` sobre `tstzrange` só existe nele e os testes precisam provar a garantia contra um banco real.

## Decisão

1. **Porta `Armazenamento`.** O `ServicoReservas` depende só da interface. Há duas implementações:
   - `ArmazenamentoMemoria`: usada pelos testes unitários, que continuam rápidos e sem contêiner.
   - `ArmazenamentoPostgres`: usada pela aplicação e pelos testes de integração e BDD.
2. **Uma unidade de trabalho por escrita de estado.** Reserva e evento de auditoria são gravados na mesma transação (AD-05, RN-09). Se o evento falhar, a mudança de estado também é desfeita.
3. **Trava por recurso.** Cada escrita toma `pg_advisory_xact_lock(hashtext(recurso))` antes de checar e gravar. Assim as solicitações do mesmo recurso passam em fila e não formam ciclo de espera (DEF-002). A trava vale mesmo quando o recurso ainda não tem linha.
4. **Manutenção checada no banco.** A criação de reserva e a aprovação (revalidação, RTM Q5) verificam manutenção sobreposta dentro da transação, sob a mesma trava. Isso fecha a corrida entre manutenção e reserva que existia na checagem só em Java.
5. **Restrição de exclusão** (ADR-016) como barreira final, valendo para qualquer instância do serviço.
6. **Auditoria somente inserção.** Um trigger `BEFORE UPDATE OR DELETE` na tabela `evento_auditoria` recusa alteração e exclusão (RN-09.8, RN-09.9), independentemente da aplicação.

## Consequências

- Positivas: duas instâncias do serviço não conseguem criar reservas sobrepostas, e a auditoria é imutável no banco. Os testes de concorrência usam instâncias separadas, então provam o banco e não o `synchronized` do Java.
- Negativas: a checagem em Java continua no serviço, para mensagens e para o modo memória; é redundante no PostgreSQL, por desenho. A trava por recurso serializa escritas do mesmo recurso, o que é aceitável para a escala acadêmica.
- Pendente: o endpoint de login e a aplicação Spring Boot (ADR-001) continuam fora desta decisão. Recursos e usuários ainda são cadastrados pelos testes, sem serviço de cadastro persistente.

## Verificação

- `ArmazenamentoPostgresTest`: ida e volta de reserva; manutenção sobreposta recusada pelo banco; alteração e exclusão de auditoria recusadas pelo trigger.
- `ConcorrenciaReservaHttpPostgresTest`: 20 requisições HTTP simultâneas, cada uma com sua instância de serviço, para salas e professores. Resultado esperado: 1 criada (201), 19 recusadas (409), 1 linha ativa no banco.
- Os 80 cenários BDD rodam contra o PostgreSQL (`BancoDeTestes`). Suíte completa com cobertura cumprida.
