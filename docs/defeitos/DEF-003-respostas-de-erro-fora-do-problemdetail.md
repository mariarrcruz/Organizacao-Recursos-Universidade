# DEF-003 — Respostas de erro fora do contrato ProblemDetail (RFC 9457)

**Status:** corrigido (commit `dbe1fd4`)
**Data da detecção:** 2026-11-11
**Severidade:** média — contrato de erro inconsistente; o cliente não consegue tratar erros de forma uniforme
**Regras afetadas:** matriz de autorização (todas as células de erro), requisito de tratamento seguro de erros (NFR: "tratamento seguro de erros", especificação do projeto)
**Teste que detectou:** `src/test/java/com/organizacao/recursos/api/MatrizAutorizacaoTest.java`, método `respostaDeErroNaoVazaDetalhesInternos` (75 células de erro da matriz); `ValidacaoEntradaTest` (mesma verificação nos casos de validação)

## Passos para reproduzir

1. Subir a aplicação com a massa de `MundoAutorizacao`.
2. Chamar qualquer operação da matriz com status de erro (por exemplo `GET /reservas/RA` sem token).
3. Ler o `Content-Type` e o corpo da resposta.

**Esperado:** `application/problem+json` com `type`, `title`, `status` e `detail`, e sem detalhe interno.

## Evidência

Execução de `MatrizAutorizacaoTest` sobre o código atual (`main` em `b8dfbb8` com as correções de D1, D3, D4 e D5):

```
tipo de conteúdo do erro deve ser application/problem+json, veio: application/json · {"erro":"Token ausente"}
tipo de conteúdo do erro deve ser application/problem+json, veio: application/json · {"erro":"Perfil ADMINISTRADOR exigido"}
tipo de conteúdo do erro deve ser application/problem+json, veio: null ·
```

Resultado: 75 falhas, uma por célula de erro da matriz (todas as células com status 400 a 405). As 98 células de status (incluindo as de sucesso) passam.

Células afetadas (linhas da matriz, por operação): 2, 3, 3b, 5, 5b, 6, 7, 8, 9, 10, 11, 12, 13, 14, 16, 17, 18, 19. Os casos 405 (operações 18 e 19) e os 401 vêm com corpo `null`, sem nenhum conteúdo.

## Causa raiz

- `TratadorErros` trata apenas `ErroAplicacao` e devolve `{"erro": "..."}` com `Content-Type: application/json`. Não usa `ProblemDetail`.
- Erros do próprio framework (401 de `ServicoAutenticacao` já vem como `ErroAplicacao`, mas 405 e 404 de rota vêm do Spring) saem sem corpo, porque não há handler para eles.
