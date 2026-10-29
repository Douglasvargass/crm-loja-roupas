# ADR-0004 · Prefixo único /api

**Status:** 🟢 decidida — 2026-10-26

## Contexto

Tela e API precisam estar na mesma origem para o cookie httpOnly (SameSite=Lax) funcionar sem CORS.

## Decisão

O back-end expõe rotas sem prefixo. O front chama /api/...; o proxy do Vite (e o nginx, se houver servidor) tira o /api e reescreve o path do cookie /auth → /api/auth.

## Consequências

Módulo novo não exige mudar proxy. Sem o cookiePathRewrite a sessão cai no primeiro refresh.
