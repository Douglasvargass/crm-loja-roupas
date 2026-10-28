# ADR-0001 · Arquitetura modular em Java

**Status:** 🟢 decidida — 2026-10-26

## Contexto

O enunciado do SENAI exige Java. A equipe quer um monólito modular, com migrations versionadas e testes contra banco real.

## Decisão

Monólito modular em Spring Boot 3: pacote core (acesso, permissões, auditoria) e pacote modules/crm. O módulo depende do núcleo, nunca o contrário. Só CrmApplication e classes @Configuration conhecem os dois lados.

## Consequências

Os padrões estão na tabela da seção 2 da documentação. Front em React 19.
