# ADR-0003 · Autenticação por cookie httpOnly

**Status:** 🟢 decidida — 2026-10-26

## Contexto

O sistema tem papéis por módulo e precisa de login seguro sem guardar token no navegador.

## Decisão

JWT de acesso (10 min) e refresh token rotativo (24 h, salvo como SHA-256) em cookies httpOnly SameSite=Lax (Strict impediria abrir um link do sistema vindo de fora já logado; Lax ainda bloqueia POST de outros sites). Reuso de refresh revoga a sessão inteira. Senha com scrypt.

## Consequências

O front nunca vê o token. Renovação de sessão é deduplicada no http.ts.
