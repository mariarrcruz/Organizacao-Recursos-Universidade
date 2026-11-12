# DEF-009 — README descrevia tecnologias e endpoints que não existiam

**Status:** corrigido (branch `feat/front-login-carga-java21`)
**Data da detecção:** 2026-11-11, auditoria de `docs/status-requisitos.md` (RF-14)
**Severidade:** média — documentação enganosa para quem avalia o projeto (RF-14, RTM-01)
**Regras afetadas:** RF-14 (documentação técnica da API e fluxos)
**Testes que detectaram:** verificação manual comparando o README com o jar em execução (`docs/execucao-local.md`); não há teste automatizado para documentação

## Passos para reproduzir

1. Ler o README e tentar `POST /auth/login` no jar.
2. Comparar as tecnologias citadas com o `pom.xml`.

**Esperado:** o README descreve o que a aplicação faz hoje.
**Obtido:** citava login e rotas inexistentes e afirmava que não havia interface.

## Causa raiz

Documentação escrita antes da implementação e não revisada depois.

## Correção

README reescrito: seção de login real, tabela de API atualizada (catálogo, movimentações, notificações, reservas pessoais e pendentes), seção de interface e limitações revisadas. Contrato detalhado em `docs/api.md`.
