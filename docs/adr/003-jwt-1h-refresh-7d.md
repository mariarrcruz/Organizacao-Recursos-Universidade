# ADR-003: JWT com Access Token 1h + Refresh Token 7d

**Status:** Aprovado (Com Bloqueador: Auditoria JWT)

**Data:** 2026-11-03

---

## Contexto

RF-01 exige autenticação com JWT; decisão: validade do token, estrutura de claims, algoritmo de assinatura, proteção de chaves.

---

## Decisão

Implementar autenticação em **dois níveis**:

### Access Token (JWT, RS256)
- **Validade:** 1 hora (3.600 segundos)
- **Claims:** `sub` (UUID), `roles` (array), `iss`, `aud`, `iat`, `exp`, `jti`
- **Algoritmo:** RS256 (RSA 2048 bits)
- **Chave privada:** Fora do repositório; gerada localmente em `config/private.key`

### Refresh Token
- **Tipo:** Opaco, não JWT (256 bits de entropia, base64url)
- **Validade:** 7 dias (604.800 segundos)
- **Armazenamento:** Hash persistido em tabela `RefreshToken` (no banco)
- **Rotação:** Novo refresh token emitido a cada renovação; antigo invalidado
- **Proteção:** Detecção de reutilização; revogação de família se reapresentado

---

## Fluxo

```
1. POST /auth/login (credenciais)
   → Validar usuário, gerar access + refresh tokens
   → Persistir hash(refresh_token) no banco
   → Retornar ambos

2. Requisições com access token no cabeçalho Authorization: Bearer <access_token>

3. POST /auth/refresh (refresh_token)
   → Validar hash, expiração, revogação
   → Invalidar token antigo
   → Emitir novo par (access + refresh)
   → Retornar ambos

4. POST /auth/logout
   → Revogar refresh token (marcar como revogado no banco)
```

---

## Forças Consideradas

### Ganhos
- **Sessão renovável:** Access token curto (1h) reduz janela de exposição
- **Refresh token seguro:** Armazenado como hash; não transmitido a cada requisição
- **Proteção contra reutilização:** Detecção se antigo token reapresentado
- **Sem cookie:** Evita CSRF em browser (se usar localStorage)
- **Escalável:** Stateless (validação apenas por assinatura + banco para refresh)

### Trade-offs
- **Chaves privadas fora do repo:** Manual em cada ambiente
- **Armazenamento refresh:** Requer persistência (não pode ser apenas JWT)
- **Logout não é instantâneo:** Refresh token ainda válido até expiração (7 dias)

---

## Alternativas Descartadas

1. **Token único com validade longa (30 dias):** Risco aumentado; perda de token expõe por mês
2. **OAuth2 externo (Google, OIDC):** Fora do escopo acadêmico; sem provedor autorizado
3. **Session cookies:** CSRF risk; menos adequado para SPA
4. **Simetria (HS256):** Chave compartilhada entre servidor + clientes; risco de vazamento

---

## Consequências

### Implementação
- Spring Security com `JwtTokenProvider` customizado
- Configuração em `application.yml`: `jwt.secret`, `jwt.expiration`, `jwt.refresh-expiration`
- Testes de: expiração, assinatura inválida, claims ausentes, revogação

### Segurança
- **Validação obrigatória:** Cada endpoint protegido valida JWT antes de executar
- **Sem senhas em token:** Apenas `sub`, `roles`, `iss`, `aud`
- **Proteção contra XSS:** Se localStorage, usar HttpOnly cookies como alternativa

### Operacional
- Chave privada gerada com OpenSSL: `openssl genrsa -out private.key 2048`
- Chave pública extraída: `openssl rsa -in private.key -pubout -out public.key`
- Ciclo de rotação de chaves (futuro): versionamento + fallback

---

## Riscos

| Risco | Probabilidade | Impacto | Mitigação |
|-------|---------------|--------|-----------|
| Chave privada exposta no repo | Média | Crítico | .gitignore + SonarCloud SECRETS scan |
| JWT falsificado com chave compartilhada | Baixa | Crítico | Usar RS256 (assimétrico) |
| Logout não é instantâneo | Alta | Médio | Aceitar lag até 7 dias; documentar |
| Refresh token interceptado | Baixa | Alto | HTTPS obrigatório em produção |
| Claims inválidos aceitos | Média | Alto | Validação rigorosa em Spring Security |

**Bloqueador:** Auditoria de segurança JWT antes de aceitar arquitetura.

---

## Requisitos Relacionados

- RF-01: Autenticação obrigatória
- RF-19: Validação JWT antes de operações protegidas
- RNF-01: Segurança autenticação + claims validados
- RNF-07: Nenhuma senha armazenada em token

---

## Condição de Revisão

Esta decisão será revisada se:
1. Requisitos exigirem logout instantâneo (implementar token blacklist)
2. Escalabilidade exigir múltiplos servidores (adicionar endpoint de validação remota)
3. Conformidade exigir MFA ou 2FA

---

## Referências

- [arquitetura.md § 6.1-6.5](../docs/arquitetura.md#seção-6-autenticação-e-autorização)
- [ATAM § Cenário C-02](../docs/ATAM-analise.md#cenário-c-02-autorização-correta-por-perfil)
- [RFC - RNF-01](../rfc/requisitos-nao-funcionais.md#rnf-01--segurança-da-autenticação-e-autorização)
