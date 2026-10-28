# ADR-0002 · PostgreSQL 16 e Flyway

**Status:** 🟢 decidida — 2026-10-26

## Contexto

O schema precisa mudar de forma rastreável e igual em todas as máquinas da equipe e no CI.

## Decisão

PostgreSQL 16 em Docker Compose; schema só por migrations Flyway (V{n}__*.sql) com reverso documentado (U{n}__*.sql); Hibernate com ddl-auto=validate.

## Consequências

Ninguém altera tabela pelo Hibernate. Mudança destrutiva segue expand/contract em duas entregas.
