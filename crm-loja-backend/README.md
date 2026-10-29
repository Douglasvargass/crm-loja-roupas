# crm-loja-backend

API REST em Java 21 + Spring Boot 3.5 do MVP de CRM da loja de roupas. Banco PostgreSQL 16, schema só por migrations Flyway, autenticação por JWT em cookie httpOnly.

## Como rodar

Escolha **um** dos dois jeitos.

### A) Sem Docker (mais simples para estudar e apresentar)

Só precisa de Java 21 ou mais novo. O Maven vem junto (`mvnw`).

```bash
./mvnw spring-boot:test-run          # Windows: mvnw.cmd spring-boot:test-run
```

- Sobe um **PostgreSQL 16 de verdade embutido** (porta 54329), gravando em `.data/postgres`. Os dados continuam lá quando você fecha e abre de novo. Para zerar, apague a pasta `.data`.
- Roda no perfil `dev`: cria os dados de demonstração e os logins da equipe (senha `loja12345`). A equipe é conferida em todo boot: trocar login ou papel aqui no código vale na próxima subida, sem zerar o banco.

| Login | Pessoa | Papel | Vê |
| --- | --- | --- | --- |
| `guilherme.dower` | Guilherme Dower | Gerente | Dashboard, cadastros, vendas, estoque e Usuários |
| `douglas.vargas` | Douglas Vargas | Engenheiro(a) de Software (super admin) | Tudo |
| `julia.alcantara` | Julia Alcantara | Marketing e Desenvolvimento | Tudo (como o gerente) |
| `vitor.nunes` | Vitor Nunes | Vendedor(a) | Meu painel (vendas e meta), vendas e clientes |
| `vinicius.clemente` | Vinicius Clemente | Operações e Compras | Chamados de peça em falta (recebe e faz o pedido), produtos e estoque |
| `vinicius.muniz` | Vinicius Muniz | Vendedor(a) | Meu painel (vendas e meta), vendas e clientes |

Senha de todos: `loja12345` (só no ambiente de demonstração). O login é só `nome.sobrenome`, sem @.

- A API fica em `http://localhost:8080`. Se a porta estiver ocupada: `SERVER_PORT=8081 ./mvnw spring-boot:test-run` (e no front, `VITE_API_TARGET=http://localhost:8081 pnpm dev`).

### B) Com Docker (igual a produção)

```bash
docker compose up -d                                     # Postgres 16 em localhost:5432
cp .env.example .env                                     # preencha JWT_SECRET (openssl rand -hex 48)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev    # migrations + seeds
```

No perfil `dev` o `JWT_SECRET` tem um valor padrão de desenvolvimento; fora dele, sem `JWT_SECRET` a aplicação não sobe (fail-fast). A senha do super admin (`admin@loja.local`) aparece **uma vez** no console no primeiro boot, ou vem de `SUPER_ADMIN_PASSWORD`.

## Testes

```bash
./mvnw verify
```

116 testes: unitários (CPF, Talita, provedor de IA, ritmo da meta) e de integração (`*IT`) que sobem um Postgres 16 embutido, rodam **todas** as migrations do zero, fazem login de verdade e chamam a API com MockMvc. Cobrem os casos CT01–CT38 do plano de testes: venda tudo-ou-nada, duas confirmações simultâneas, reuso de refresh token, troca de senha obrigatória, virada de período do dashboard e os triggers que impedem alterar o histórico de estoque.

O Postgres embutido (zonky) substitui o Testcontainers: funciona sem Docker no Windows do laboratório e no runner do CI.

## Estrutura

```
src/main/java/br/senai/crmloja/
├── CrmApplication.java          composition root
├── core/                        núcleo, não conhece o módulo crm
│   ├── auth/                    login, refresh rotativo, cookies, filtro JWT
│   ├── audit/                   audit_event (append-only)
│   ├── config/                  AppProperties (fail-fast), SecurityConfig
│   ├── http/                    ApiError {error, code}, exceções → 400/401/404/409
│   ├── identity/ rbac/          usuários, papéis por módulo
│   ├── seed/                    CoreSeeder (super admin)
│   ├── text/                    busca sem acento
│   └── users/                   gestão de contas (HU35)
└── modules/crm/
    ├── customer/ category/ product/ stock/ sale/ dashboard/
    ├── assistant/               assistente de IA (Claude + consultas só de leitura)
    └── seed/                    CrmModuleSeeder (papéis), DevDataSeeder (perfil dev)
src/main/resources/db/migration  V1…V5 (Flyway)   ·   db/undo  U1…U5 (reversão manual)
```

Cada requisição passa por Controller → Service → Repository. Regra de negócio fica só no Service; confirmar e cancelar venda travam as linhas (`PESSIMISTIC_WRITE`) e gravam estoque, movimentação e auditoria na mesma transação.

Contrato completo da API: `crm-loja-docs/specs/api-contract.md`. Todos os comandos (testes isolados, banco, build, problemas comuns): `crm-loja-docs/docs/11-guia-de-comandos.md`. Assistente de IA: `crm-loja-docs/docs/10-assistente-ia.md`.
