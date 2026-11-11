# ADR-016: Exclusão sobreposta no PostgreSQL (EXCLUDE USING gist sobre tstzrange)

**Status:** Aceito

**Data:** 2026-11-11

**Relaciona-se a:** ADR-002 (H2 relacional local), ADR-004 (lock de concorrência), DEF-001, DEF-002

---

## Contexto

RN-02 e RN-03 exigem que um recurso não tenha duas reservas ativas sobrepostas, mesmo com solicitações simultâneas. O teste `ConcorrenciaReservaPostgresTest` disparou 20 solicitações no mesmo horário e registrou reservas sobrepostas: a versão que checava sobreposição com `SELECT` e depois gravava com `INSERT` deixou passar 3 a 20 reservas, conforme a rodada (DEF-001).

O ADR-002 aprovou H2. Este ADR trata o caminho de reservas com PostgreSQL real, porque a garantia precisa ser testada contra um banco de produção, e o H2 não implementa restrições de exclusão com `tstzrange`. A decisão de H2 para os demais dados não muda.

## Decisão

1. **A garantia fica no banco.** Tabela `reserva` com `periodo tstzrange` e a restrição:
   `EXCLUDE USING gist (recurso_id WITH =, periodo WITH &&) WHERE (estado IN ('SOLICITADA','APROVADA','EM_USO'))`,
   com a extensão `btree_gist`. Intervalos são semiabertos `[início, fim)`, como em RN-02.
2. **O código não verifica sobreposição antes do `INSERT`.** A violação (SQLSTATE `23P01`) vira `ErroAplicacao(409)`.
3. **Lock pessimista por recurso como reforço, não como garantia.** Cada solicitação faz `SELECT ... FOR UPDATE` na linha do recurso antes do `INSERT`. Sozinha, a restrição deixava as transações esperarem umas pelas outras e o PostgreSQL abortava algumas com deadlock (`40P01`, DEF-002), o que devolvia erro 500 em vez de 409. Com o lock, as solicitações do mesmo recurso passam em fila e não formam ciclo. A restrição continua sendo a barreira final: vale para qualquer escritor que não use o lock.

## Alternativas consideradas

- **Verificar e depois inserir (versão ingênua):** rejeitada. É a causa do DEF-001.
- **Lock pessimista por recurso como única barreira:** depende de todo caminho de escrita adquirir o lock. Usado aqui só como reforço (item 3).
- **Isolamento SERIALIZABLE:** exigiria retentativas na aplicação e não diagnostica a causa.
- **H2:** não reproduz a restrição de exclusão com `tstzrange`, então o teste não provaria a garantia.

## Consequências

- Positivas: a garantia vale para qualquer escritor do banco, inclusive ferramentas ou futuros caminhos de código. O teste de concorrência verifica o próprio banco.
- Negativas: depende de PostgreSQL e da extensão `btree_gist`. O estado ativo fica codificado na restrição; se novos estados ocuparem o recurso, a restrição deve ser alterada.
- A regra de negócio completa (manutenção, status do recurso) continua no `ServicoReservas`; esta restrição cobre a exclusão de horário entre reservas ativas.

## Verificação

Teste `ConcorrenciaReservaPostgresTest` (Testcontainers, PostgreSQL 16): 20 solicitações simultâneas para SALA e para PROFESSOR, com um `CountDownLatch`. Resultado esperado e obtido: 1 reserva criada (201), 19 recusadas (409), 1 linha no banco para o recurso e intervalo, verificada por consulta SQL.

- Teste isolado: 5 execuções seguidas, todas verdes.
- Suíte completa (`./mvnw -B verify`): 3 execuções seguidas, 362 testes, todas verdes.
- Antes da correção: a versão sem restrição aceitou de 3 a 20 reservas (DEF-001); a restrição sem lock falhou com deadlock em 2 de 3 execuções da suíte completa (DEF-002).
