# CLAUDE.md · raiz do repositório (Orden)

Monorepo do Orden: `crm-loja-backend/` (Java 21, Spring Boot), `crm-loja-frontend/` (React 19, Vite, TypeScript),
`crm-loja-docs/` (documentação e specs), `site/` (página do projeto).

## Comece por aqui

1. **`PARA-CONTINUAR.md`**: o que mudou em 29/10/2026, como levar ao GitHub e ao Render, e o backlog.
2. `crm-loja-backend/CLAUDE.md` e `crm-loja-frontend/CLAUDE.md`: regras de cada lado.
3. `crm-loja-docs/specs/README.md`: o estado de cada spec (01 a 11 implementadas).
4. `crm-loja-docs/docs/16-processo-de-requisitos.md`: como transformar um pedido em spec e issue.

## Regras de ouro

- Nunca enviar ao Git com outra conta que não a do Douglas (`Douglasvargass`).
- Regra de negócio só no Service; o front esconde, quem barra é o back-end.
- Banco muda só por migration nova (próxima: **V13**), com o reverso em `db/undo`.
- Nada se apaga: venda se cancela, cadastro com histórico se inativa, histórico de estoque e auditoria são imutáveis.
- Tela e documentação em português simples; código em inglês.
- Mudou rota ou DTO: `crm-loja-docs/specs/api-contract.md` e `crm-loja-frontend/src/data/dto.ts` juntos.
- Mudou regra: `crm-loja-docs/docs/04-regras-de-negocio.md` e o prompt da Talita (`AssistantPrompt.java`).
- Portões: `./mvnw verify` no back-end; `pnpm lint && pnpm typecheck && pnpm build` no front-end.
- Toda tela nova tem o "?" de ajuda (`PageHeader help="..."`) e nenhum botão fica travado sem dizer por quê.
