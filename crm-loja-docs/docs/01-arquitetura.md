## 2. Stack e arquitetura

O projeto usa Java/Spring Boot no back-end. É um **monólito modular**: um núcleo (`core`: acesso, permissões, auditoria) e um módulo de negócio (`crm`). O módulo depende do núcleo, nunca o contrário.

| Camada | Tecnologia | Papel |
| --- | --- | --- |
| Back-end | Java 21 + Spring Boot 3 (Web, Data JPA, Validation, Security) | API REST, regras, transações |
| Banco | PostgreSQL 16 | Persistência, CHECKs, triggers |
| Migrations | Flyway (SQL versionado) | Único caminho para mudar o schema; `ddl-auto=validate` |
| Autenticação | Spring Security + JWT (jjwt) em cookie httpOnly | Access 10 min, refresh rotativo 24 h |
| Testes | JUnit 5 + MockMvc + Postgres 16 embutido (zonky, sem Docker) | Integração contra banco de verdade |
| Front-end | React 19 + Vite + TypeScript estrito | SPA |
| Estado de servidor | TanStack Query 5 | Cache, invalidação após mutações |
| Estilo | Tailwind CSS 4 + tokens CSS | Tema claro/escuro por variáveis |
| Build | Maven Wrapper (`./mvnw`) · pnpm no front | Nada de npm/yarn |
| Local | Docker Compose com `postgres:16` | Mesmo banco do CI |
| CI | GitHub Actions com serviço Postgres | lint, testes e build em todo PR |

### Padrões de arquitetura aplicados em Java

| Padrão | No CRM em Spring Boot |
| --- | --- |
| Composition root em `main.ts` | `CrmApplication` + classes `@Configuration`; só elas ligam núcleo e módulo |
| Config fail-fast (`env.ts`) | `@ConfigurationProperties` + `@Validated`: sem `JWT_SECRET` a aplicação não sobe |
| Migrations TypeORM com `down()` | Flyway `V{n}__*.sql` + script de reversão `U{n}__*.sql` documentado; expand/contract |
| `synchronize: false` | `spring.jpa.hibernate.ddl-auto=validate` |
| PK `bigint identity`, `is_active` booleano | Igual: `GENERATED ALWAYS AS IDENTITY`, nada de soft-delete |
| Erros de domínio → 400/404/409 | Exceções de domínio + `@RestControllerAdvice` devolvendo `{error, code}` |
| `dataSource.transaction` + `pessimistic_write` | `@Transactional` + `@Lock(PESSIMISTIC_WRITE)` |
| Congelar valor da época | `sale_item` copia código, nome e preço do produto |
| Log imutável por trigger | `stock_movement` e `audit_event` recusam UPDATE/DELETE no banco |
| Seed reconciliador no boot | `ApplicationRunner` idempotente para módulo, papéis e super admin |
| Testes Vitest + Supertest em Postgres real | JUnit + MockMvc + Postgres embutido, rodando as migrations do zero |
| RBAC por módulo | Tabelas `module`, `module_role`, `user_module_access` + `@PreAuthorize` |
| Front: http.ts, hooks, adapters | Igual, em React (seção 7) |

### Regras de ouro (viram o CLAUDE.md de cada repositório)

- Nunca inventar regra, rota, tabela ou coluna: confirmar no enunciado, na spec ou com a equipe.
- Nunca apagar entidade central: cancelar ou inativar. CASCADE só com justificativa escrita.
- Schema muda **só** por migration Flyway, nunca pelo Hibernate.
- Código (tabelas, colunas, classes, enums) em **inglês**; tela, mensagens, commits e documentação em **português**.
- Toda mudança: branch → PR para `develop` → CI verde → merge. `main` só na entrega.
- Antes de dar uma tarefa por concluída: `./mvnw verify` no back-end; `pnpm lint && pnpm typecheck && pnpm build` no front.
- Comentário explica o **porquê**, não o quê.

### Camadas do back-end

&#91;embedded content: arquitetura em camadas · 5 níveis\]

Cada requisição passa por Controller → Service → Repository → banco. A regra de negócio fica **só no Service**; o front apenas exibe e envia dados, e o RBAC do front só esconde botões.

- **Controller** (`@RestController`): recebe HTTP, valida o DTO com `@Valid`, checa o papel com `@PreAuthorize`, devolve JSON. Nada de regra aqui.
- **Service**: regras de negócio, sem nada de HTTP. Confirmar e cancelar venda rodam em `@Transactional` com trava de linha. Grava `audit_event` na mesma transação.
- **Repository**: interfaces Spring Data JPA; consultas com parâmetros nomeados, nunca concatenando valor em SQL.
- **Entity**: classes JPA em inglês, `snake_case` no banco via `@Column(name = …)`.
- **DTO**: `record`s de entrada e saída; a entidade nunca sai pela API.
- **Exceptions**: `NotFoundException` (404), `InvalidInputException` (400), `BusinessRuleException` (409, com `code` estável), tratadas no `GlobalExceptionHandler`; o resto vira 500 com log e mensagem genérica.

### Três repositórios

| Repositório | Conteúdo |
| --- | --- |
| `crm-loja-backend` | API, migrations, seeds, testes, workflows de CI, `CLAUDE.md`, `README.md`, `MIGRATIONS_RUNBOOK.md` |
| `crm-loja-frontend` | SPA React, workflow de CI, `CLAUDE.md`, `DESIGN_GUIDELINES.md` |
| `crm-loja-docs` | SDD: visão, ADRs, `specs/crm/NN-*/{requirements,design,tasks}.md`, `api-contract.md` (sem código) |

Se a turma preferir um só repositório, as três pastas ficam na raiz (`backend/`, `frontend/`, `docs/`) com as mesmas regras.

### Estrutura do back-end

```
crm-loja-backend/
├── pom.xml · mvnw · docker-compose.yml · .env.example
├── CLAUDE.md · README.md · MIGRATIONS_RUNBOOK.md
├── (workflows de CI ficam na raiz do repositório: .github/workflows/backend.yml, frontend.yml, docker.yml, pages.yml)
└── src/
    ├── main/java/br/senai/crmloja/
    │   ├── CrmApplication.java          composition root
    │   ├── core/                        NÚCLEO (não importa nada de modules/)
    │   │   ├── config/                  AppProperties (fail-fast), SecurityConfig, CorsConfig
    │   │   ├── auth/                    AuthController, AuthService, JwtService,
    │   │   │                            AuthCookies, JwtCookieFilter
    │   │   ├── identity/                AppUser, RefreshToken, repositórios
    │   │   ├── rbac/                    Module, ModuleRole, UserModuleAccess, RbacService
    │   │   ├── audit/                   AuditEvent, AuditService
    │   │   ├── seed/                    CoreSeeder (super admin)
    │   │   └── http/                    GlobalExceptionHandler, ApiError, exceções base
    │   └── modules/crm/                 MÓDULO
    │       ├── customer/                Customer, CustomerRepository, CustomerService,
    │       │                            CustomerController, dto/
    │       ├── category/                (mesmo formato)
    │       ├── product/
    │       ├── stock/                   StockMovement, StockService, StockController
    │       ├── sale/                    Sale, SaleItem, SaleStatus, SaleService, SaleController
    │       ├── dashboard/               DashboardService, DashboardController
    │       └── seed/                    CrmModuleSeeder (módulo + papéis), DevDataSeeder
    ├── main/resources/
    │   ├── application.yml
    │   └── db/migration/                V1__core_identity_rbac.sql, V2__core_audit.sql,
    │                                    V3__crm_catalog.sql, V4__crm_sales_stock.sql
    └── test/java/br/senai/crmloja/
        ├── support/IntegrationTest.java base com Postgres embutido + login
        └── modules/crm/                 SaleIT, CatalogStockIT, CustomerIT, DashboardIT, AssistantIT (AuthIT em core/)
```

### Estrutura do front-end

Dependência em sentido único: `presentation → data → domain`, alias `@/` para `src/`.

```
crm-loja-frontend/src/
├── main.tsx
├── app/          router.tsx, providers.tsx (QueryClient), navigation.tsx,
│                 context/SessionContext.tsx, guards/RequireAuth, RequireActivePassword, RequireRole
├── data/         ÚNICA fronteira com a API
│   ├── http.ts       fetch com credentials, renovação de sessão deduplicada, ApiError
│   ├── auth-events.ts
│   ├── dto.ts        tipos 1:1 com a API (inglês)
│   ├── adapters.ts   DTO → domínio (DRAFT → "Aberta" etc.)
│   ├── queryKeys.ts
│   └── hooks/        useCustomers, useProducts, useSales, useDashboard…
├── domain/       tipos e regras de exibição, sem React
├── lib/          format.ts (moeda, CPF, datas), cn.ts, cálculos puros
└── presentation/
    ├── components/atoms | molecules | organisms | templates
    ├── pages/<NomePage>/<NomePage>.tsx
    └── styles/tokens.css, global.css
```
