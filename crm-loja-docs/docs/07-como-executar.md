## 9. Como executar

**Sem Docker (recomendado no laboratório):** na pasta do back-end, `./mvnw spring-boot:test-run` sobe um PostgreSQL 16 embutido (dados em `.data/`) já no perfil `dev`; na pasta do front, `npx pnpm install && npx pnpm dev`. Logins de demonstração: `douglas.vargas`, `guilherme.dower`, `vitor.nunes` (sem @), senha `loja12345`. Detalhes no `README.md` do back-end.

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
| `CrmModuleSeeder` | Sempre | Garante o módulo `crm` e os papéis `MANAGER` (admin), `SELLER`, `STOCK_KEEPER`, `DEVELOPER`, `MARKETING_DEV`, `PURCHASING`; corrige nome ou flag divergente; nunca remove papel em uso |
| `DevDataSeeder` | Perfis `dev`/`demo`. A equipe (6 contas) é conferida em **todo** boot: login, nome, papel e senha de demonstração; o catálogo (5 categorias, 6 clientes, 12 produtos) só se estiver vazio | Catálogo criado **pelos Services**; produtos nascem com estoque zero (ver abaixo) |

Os seeds rodam dentro de `try/catch`: falha de seed é logada e a aplicação continua no ar.

Dados do `DevDataSeeder`: os 6 clientes iniciais usam CPFs válidos de teste, não de pessoas reais
(529.982.247-25, 111.444.777-35, 390.533.447-05, 877.482.488-00, 714.287.938-60, 346.085.143-00). Os produtos
nascem com **estoque zero**: quem abre o estoque é o `HistoricoVendasSeeder`, com a carga retrodatada.

| `HistoricoVendasSeeder` | Só nos perfis `dev`/`demo`, se não houver venda anterior a hoje | 3 meses de vendas, mais 45 clientes da região de Tubarão, inseridos por SQL com data retroativa |
| `LojaModuleSeeder` | Sempre | Garante o módulo `loja` (espaço do cliente) e o papel `CUSTOMER` |
| `ClientesDemoSeeder` | Perfis `dev`/`demo`, em todo boot | Contas dos professores `vitor.freitas` e `matheus.carvalho` (senha `cliente12345`, troca obrigatória); senha já trocada nunca é mexida |
| `MetasSeeder` | Perfis `dev`/`demo`, só a meta que faltar | Meta de cada vendedor nos últimos 3 meses e no mês atual, a partir do próprio histórico |
| `PedidosCompraDemoSeeder` | Perfis `dev`/`demo`, só com a tabela vazia | 3 pedidos de compra do Vinicius Clemente com as peças que acabam primeiro (um atrasado) |
| `AgenteVendasDemo` | Perfis `dev`/`demo`, ao subir e todo dia | Fecha cada dia que passou com vendas de demonstração (spec crm/08) |

Esse seeder gera cerca de 2.000 vendas nos últimos 3 meses, respeitando o horário do shopping (10h–22h de
segunda a sábado, 14h–21h domingo e feriado), a sazonalidade e o calendário de feriados. O modelo completo está
em [14-dados-de-demonstracao.md](14-dados-de-demonstracao.md). Os alertas de estoque aparecem nas jaquetas, por
causa da saída de estação simulada.

### CI (.github/workflows/backend.yml, na raiz do repositório)

```yaml
name: backend
on:
  push:
    branches: [main, develop]
    paths: ['crm-loja-backend/**', '.github/workflows/backend.yml']
  pull_request:
    paths: ['crm-loja-backend/**', '.github/workflows/backend.yml']
jobs:
  verify:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '21', cache: maven }
      - run: ./mvnw -B verify   # os testes sobem um Postgres 16 embutido (zonky), sem Docker
```

O front tem o mesmo workflow (`frontend.yml`) com `pnpm install --frozen-lockfile && pnpm lint && pnpm typecheck && pnpm build`. Há ainda `docker.yml` (constrói e sobe a imagem como no Render) e `pages.yml` (publica a pasta `site/`).
