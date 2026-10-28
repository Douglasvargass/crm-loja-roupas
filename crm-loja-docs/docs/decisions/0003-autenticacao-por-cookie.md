# ADR-0003 · Autenticação por cookie httpOnly

**Status:** 🟢 decidida — 2026-10-26

## Contexto

O sistema tem três papéis e precisa de login seguro sem guardar token no navegador.

## Decisão

JWT de acesso (10 min) e refresh token rotativo (24 h, salvo como SHA-256) em cookies httpOnly sameSite=strict. Reuso de refresh revoga a sessão inteira. Senha com scrypt.

## Consequências

O front nunca vê o token. Renovação de sessão é deduplicada no http.ts.
