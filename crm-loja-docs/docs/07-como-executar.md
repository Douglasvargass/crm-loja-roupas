## 9. Como executar

**Sem Docker (recomendado no laboratório):** na pasta do back-end, `./mvnw spring-boot:test-run` sobe um PostgreSQL 16 embutido (dados em `.data/`) já no perfil `dev`; na pasta do front, `npx pnpm install && npx pnpm dev`. Logins de demonstração: `gerente@loja.local`, `vendedor@loja.local`, `estoquista@loja.local`, senha `loja12345`. Detalhes no `README.md` do back-end.

**Com Docker:** com Java 21, Docker, Node 22 e pnpm instalados, o sistema sobe em 4 comandos. O Flyway aplica as migrations sozinho na subida do back-end, e o front abre em `http://localhost:5173`.

1. `docker compose up -d` na pasta do back-end (sobe o Postgres 16).
2. `cp .env.example .env` e preencher `JWT_SECRET`.
3. `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` (migrations + seeds; a senha do super admin aparece **uma vez** no console).
4. Na pasta do front: `pnpm install && pnpm dev`.

### docker-compose.yml

```yaml
services:
  postgres:
    image: postgres:16
    environment:
      POSTGRES_USER: ${DB_USER:-crm}
      POSTGRES_PASSWORD: ${DB_PASSWORD:-crm}
      POSTGRES_DB: ${DB_NAME:-crm_loja}
    ports: ["5432:5432"]
    volumes: [crm_pg:/var/lib/postgresql/data]
volumes:
  crm_pg:
```

### .env.example (cada variável explica o porquê)

```properties
# Banco local do docker-compose. Em servidor, nunca deixe credencial real preenchida aqui.
DB_URL=jdbc:postgresql://localhost:5432/crm_loja
DB_USER=crm
DB_PASSWORD=crm

# Segredo que assina os tokens. Sem ele a aplicação NÃO sobe (fail-fast).
# Gere com: openssl rand -hex 48
JWT_SECRET=

# Access curto limita o estrago de um token vazado; o refresh renova sem pedir senha.
ACCESS_TOKEN_TTL_SECONDS=600
REFRESH_TOKEN_TTL_HOURS=24

# Origens que podem chamar a API com cookie (só usado se o front não passar pelo proxy).
FRONTEND_ORIGINS=http://localhost:5173

# Login do super admin criado no primeiro boot.
SUPER_ADMIN_LOGIN=admin@loja.local
```

### application.yml

```yaml
spring:
  datasource:
    url: ${DB_URL}
    username: ${DB_USER}
    password: ${DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate      # schema só muda por Flyway
    open-in-view: false
    properties:
      hibernate.jdbc.time_zone: UTC
  flyway:
    enabled: true
    locations: classpath:db/migration
app:
  jwt-secret: ${JWT_SECRET}
  access-token-ttl-seconds: ${ACCESS_TOKEN_TTL_SECONDS:600}
  refresh-token-ttl-hours: ${REFRESH_TOKEN_TTL_HOURS:24}
  frontend-origins: ${FRONTEND_ORIGINS:http://localhost:5173}
  super-admin-login: ${SUPER_ADMIN_LOGIN}
```

`AppProperties` é um `record` com `@ConfigurationProperties("app")` e `@Validated` (`@NotBlank` no segredo): variável faltando derruba o boot com mensagem clara, em vez de falhar no primeiro login.

### Proxy do front (vite.config.ts)

```ts
server: {
  port: 5173,
  proxy: {
    '/api': {
      target: process.env.VITE_API_TARGET ?? 'http://localhost:8080',
      changeOrigin: true,
      rewrite: (p) => p.replace(/^\/api/, ''),
      cookiePathRewrite: { '/auth': '/api/auth' }, // sem isto o refresh não volta e a sessão cai
    },
  },
},
```

### Seeds (idempotentes, rodam em todo boot)

| Seed | Quando roda | O que faz |
| --- | --- | --- |
| `CoreSeeder` | Sempre | Cria o super admin se não existir; imprime a senha gerada (24 caracteres) uma única vez |
| `CrmModuleSeeder` | Sempre | Garante o módulo `crm` e os papéis `MANAGER` (admin), `SELLER`, `STOCK_KEEPER`; corrige nome ou flag divergente; nunca remove papel em uso |
| `DevDataSeeder` | Só no perfil `dev`, se o catálogo estiver vazio | 5 categorias, 6 clientes, 12 produtos e 3 usuários de teste, criados **pelos Services** para gerar as movimentações `ENTRY` corretas |

Os seeds rodam dentro de `try/catch`: falha de seed é logada e a aplicação continua no ar.

Dados do `DevDataSeeder`: os 6 clientes iniciais usam CPFs válidos de teste, não de pessoas reais
(529.982.247-25, 111.444.777-35, 390.533.447-05, 877.482.488-00, 714.287.938-60, 346.085.143-00). Os produtos
nascem com **estoque zero**: quem abre o estoque é o `HistoricoVendasSeeder`, com a carga retrodatada.

| `HistoricoVendasSeeder` | Só nos perfis `dev`/`demo`, se não houver venda anterior a hoje | 3 meses de vendas, mais 45 clientes da região de Tubarão, inseridos por SQL com data retroativa |

Esse seeder gera cerca de 2.000 vendas nos últimos 3 meses, respeitando o horário do shopping (10h–22h de
segunda a sábado, 14h–21h domingo e feriado), a sazonalidade e o calendário de feriados. O modelo completo está
em [14-dados-de-demonstracao.md](14-dados-de-demonstracao.md). Os alertas de estoque aparecem nas jaquetas, por
causa da saída de estação simulada.

### CI (.github/workflows/ci.yml do back-end)

```yaml
name: ci
on:
  pull_request:
    paths-ignore: ['**/*.md', 'docs/**']
concurrency: { group: ci-${{ github.ref }}, cancel-in-progress: true }
jobs:
  verify:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '21', cache: maven }
      - run: ./mvnw -B verify   # Testcontainers sobe o Postgres 16 dentro do runner
```

O front tem o mesmo workflow com `pnpm install --frozen-lockfile && pnpm lint && pnpm typecheck && pnpm build`.
