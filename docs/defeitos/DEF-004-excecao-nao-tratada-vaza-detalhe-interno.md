# DEF-004 — Exceção não tratada vaza nome de enum, NullPointerException ou SQL

**Status:** aberto (correção no commit que referencia este defeito)
**Data da detecção:** 2026-11-11
**Severidade:** alta — vazamento de detalhe interno (nome de classe, mensagem de SQL) e erro de servidor onde deveria haver 400 ou 500 genérico
**Regras afetadas:** tratamento seguro de erros; validação de entrada (ver DEF-005 e DEF-006)
**Testes que detectaram:** `ValidacaoEntradaTest.tipoInvalido`, `ValidacaoEntradaTest.recursoSemIdEhTipo`, `ValidacaoEntradaTest.falhaInternaNaoVazaDetalhes`

## Passos para reproduzir

1. `POST /recursos` com `"tipo":"NAVE"` (administrador).
2. `POST /recursos` sem `id` e sem `tipo`.
3. Forçar uma falha de armazenamento com mensagem contendo SQL (teste com Mockito) e chamar `POST /reservas`.

## Evidência

```
tipoInvalido: jakarta.servlet.ServletException: Request processing failed:
  java.lang.IllegalArgumentException: No enum constant com.organizacao.recursos.dominio.TipoRecurso.NAVE
recursoSemIdEhTipo: jakarta.servlet.ServletException: Request processing failed:
  java.lang.NullPointerException: Name is null
falhaInternaNaoVazaDetalhes: jakarta.servlet.ServletException: Request processing failed:
  java.lang.IllegalStateException: Falha de banco: SELECT * FROM reserva WHERE recurso_id = 'SALA-V' (org.postgresql.util.PSQLException)
```

Em produção a exceção não tratada chega ao cliente como erro de servidor, com a mensagem original, que contém nome de classe e SQL.

## Causa raiz

- `TratadorErros` não tem handler para exceções que não sejam `ErroAplicacao`.
- `RecursosController` chama `TipoRecurso.valueOf(corpo.tipo())` e `StatusRecurso.valueOf(...)` sem checar o valor: um nome desconhecido lança `IllegalArgumentException` com o nome da classe.
- `new Recurso(...)` aceita `id` nulo e falha com NPE ao montar o nome.
- `ArmazenamentoPostgres` lança `IllegalStateException` com a mensagem SQL, e nada a converte em erro genérico.
