# CLAUDE.md · crm-loja-backend

Instruções para quem (pessoa ou agente) mexer neste repositório.

## Regras de ouro

- Nunca inventar regra, rota, tabela ou coluna: confirmar no enunciado, na spec ou com a equipe.
- Nunca apagar entidade central: cancelar ou inativar. CASCADE só com justificativa escrita.
- Schema muda **só** por migration Flyway, nunca pelo Hibernate.
- Código (tabelas, colunas, classes, enums) em **inglês**; tela, mensagens, commits e documentação em **português**.
- Toda mudança: branch → PR para `develop` → CI verde → merge. `main` só na entrega.
- Antes de dar uma tarefa por concluída: `./mvnw verify` no back-end; `pnpm lint && pnpm typecheck && pnpm build` no front.
- Comentário explica o **porquê**, não o quê.

## Portões antes de dar uma tarefa por concluída

- `./mvnw verify` (compila, roda testes unitários e de integração com Testcontainers)

## Arquitetura

- `core/` é o núcleo e NUNCA importa nada de `modules/`.
- `modules/crm/` usa o núcleo por injeção e por FK; não redefine entidades do núcleo.
- Regra de negócio só no Service. Controller valida DTO e papel; Repository só consulta.
- Transação com trava (`@Lock(PESSIMISTIC_WRITE)`) quando a regra depende de contagem ou saldo.
- `audit_event` gravado na mesma transação da mudança.

## Migrations

- Uma por mudança coesa; núcleo e módulo nunca na mesma.
- Toda `V{n}` tem o reverso em `db/undo/U{n}`.
- Mudança destrutiva em dois passos (expand/contract) — ver MIGRATIONS_RUNBOOK.md.
- Módulo e papéis são dado de seed (`CrmModuleSeeder`), nunca de migration.

## Git

Branch `feat/`, `fix/`, `docs/` a partir da `develop` → Conventional Commits em português → PR para `develop` → CI verde → merge sem apagar a branch. `main` só na entrega. Nunca `push --force`.
