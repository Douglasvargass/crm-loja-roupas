# CLAUDE.md · crm-loja-frontend

## Camadas

`presentation → data → domain`, alias `@/` para `src/`.
- Mudou payload da API → `data/dto.ts` + `data/adapters.ts`.
- Mudou regra de exibição ou cálculo → `lib/` (função pura).
- Mudou só visual → `presentation/`.
- O RBAC do front só esconde e redireciona; quem barra é o back-end.

## Portões

`pnpm lint && pnpm typecheck && pnpm build` (typecheck = `tsc -b`).

## Regras

- Gerenciador é pnpm.
- Código em inglês; texto de tela em português simples (DESIGN_GUIDELINES.md).
- Toda seção com dados passa por `QueryState`.
- Mutação de venda, estoque ou produto invalida `dashboard`, `products` e `stockAlerts`.
