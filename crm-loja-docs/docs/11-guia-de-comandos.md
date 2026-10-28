# Guia de comandos

Tudo o que é preciso para instalar, rodar, testar, inspecionar o banco, gerar a versão final e resolver problemas. Cada comando vem com o que ele faz e o que você deve ver se deu certo.

**Convenção dos blocos de comando:**

- `bash` = Git Bash (Windows), Terminal do macOS ou Linux.
- `powershell` = PowerShell do Windows.
- `cmd` = Prompt de Comando do Windows.

No Windows, use `mvnw.cmd` onde o texto disser `./mvnw`. Ele funciona no PowerShell e no cmd; no Git Bash, `./mvnw` também funciona.

## Sumário

1. [Instalar os pré-requisitos](#1-instalar-os-pré-requisitos)
2. [Baixar o projeto](#2-baixar-o-projeto)
3. [Rodar o back-end](#3-rodar-o-back-end)
4. [Rodar o front-end](#4-rodar-o-front-end)
5. [Testes automáticos](#5-testes-automáticos)
6. [Banco de dados](#6-banco-de-dados)
7. [Chamar a API direto (sem a tela)](#7-chamar-a-api-direto-sem-a-tela)
8. [Variáveis de ambiente](#8-variáveis-de-ambiente)
9. [Gerar a versão final (build)](#9-gerar-a-versão-final-build)
10. [Git: fluxo de trabalho da equipe](#10-git-fluxo-de-trabalho-da-equipe)
11. [Problemas comuns e soluções](#11-problemas-comuns-e-soluções)
12. [Cola rápida](#12-cola-rápida)

---

## 1. Instalar os pré-requisitos

| Programa | Versão | Para quê | Obrigatório? |
| --- | --- | --- | --- |
| Java (JDK) | 21 ou mais nova | Rodar e compilar o back-end | Sim |
| Node.js | 22 ou mais nova | Rodar e compilar o front-end | Sim |
| Git | qualquer recente | Baixar o código e enviar mudanças | Sim |
| pnpm | 10 | Gerenciador de pacotes do front | Não: `npx pnpm` baixa sozinho |
| Docker Desktop | qualquer recente | Rodar o PostgreSQL "de produção" | Não: o modo sem Docker já traz o banco |
| VS Code | qualquer | Editor recomendado | Não |

O Maven **não** precisa ser instalado: o projeto traz o `mvnw` (Maven Wrapper), que baixa a versão certa na primeira execução.

### 1.1 Conferir o que já está instalado

```bash
java -version      # esperado: "21" ou maior, ex.: java version "21.0.5" ou "25.0.1"
node -v            # esperado: v22.x ou maior
git --version      # qualquer versão
```

Se algum comando responder "não é reconhecido" / "command not found", instale como abaixo.

### 1.2 Instalar no Windows

```powershell
winget install --id Microsoft.OpenJDK.21 -e
winget install --id OpenJS.NodeJS.LTS -e
winget install --id Git.Git -e
```

Feche e abra o terminal de novo depois de instalar, para o `PATH` ser atualizado.

Sem permissão de administrador (máquina de laboratório): baixe as versões "zip"/portáteis:

- JDK: https://adoptium.net (escolha *JDK 21*, *Windows*, *.zip*); descompacte em `C:\Users\<você>\tools\jdk-21`.
- Node: https://nodejs.org/en/download (arquivo *.zip* para Windows).

Depois aponte o terminal para elas:

```powershell
$env:JAVA_HOME = "C:\Users\<você>\tools\jdk-21"
$env:Path = "$env:JAVA_HOME\bin;C:\Users\<você>\tools\node;" + $env:Path
```

Isso vale só para aquele terminal. Para deixar fixo: *Painel de Controle → Sistema → Configurações avançadas → Variáveis de Ambiente*.

### 1.3 Instalar no macOS

```bash
brew install openjdk@21 node@22 git
```

### 1.4 Instalar no Linux (Ubuntu/Debian)

```bash
sudo apt update
sudo apt install -y openjdk-21-jdk git curl
curl -fsSL https://deb.nodesource.com/setup_22.x | sudo -E bash -
sudo apt install -y nodejs
```

### 1.5 pnpm (opcional)

Todos os comandos do front funcionam com `npx pnpm ...`, que baixa o pnpm na hora. Para instalar de vez:

```bash
npm install -g pnpm@10
pnpm -v            # esperado: 10.x
```

> **Windows/PowerShell:** se aparecer *"a execução de scripts foi desabilitada neste sistema"*, use `npx.cmd pnpm ...` / `pnpm.cmd ...`, ou rode no cmd ou no Git Bash.

---

## 2. Baixar o projeto

```bash
git clone https://github.com/Douglasvargass/crm-loja-roupas.git
cd crm-loja-roupas
```

Abra no VS Code com todos os projetos juntos:

```bash
code crm-loja-roupas.code-workspace
```

Aceite instalar as extensões recomendadas (Java, Spring Boot, ESLint, Tailwind, SQLTools).

---

## 3. Rodar o back-end

O back-end é a API em Java na pasta `crm-loja-backend`. Escolha **um** dos dois modos.

### 3.A Modo sem Docker (recomendado para testar e estudar)

```bash
cd crm-loja-backend
./mvnw spring-boot:test-run
```

```cmd
cd crm-loja-backend
mvnw.cmd spring-boot:test-run
```

O que acontece, na ordem:

1. Na primeira vez, o `mvnw` baixa o Maven e as bibliotecas (pode levar alguns minutos).
2. Sobe um **PostgreSQL 16 de verdade**, embutido, na porta **54329**, gravando em `crm-loja-backend/.data/postgres`.
3. O Flyway cria as tabelas (migrations `V1` a `V5`).
4. Os *seeds* criam o módulo, os papéis, o super admin e, no perfil `dev`, os **dados de demonstração**: 5 categorias, 6 clientes, 12 produtos, 5 vendas e os 3 logins.
5. A API fica no ar em **http://localhost:8080**.

Deu certo quando aparecer no terminal:

```
Dados de demonstração criados. Logins (senha loja12345):
  gerente@loja.local · vendedor@loja.local · estoquista@loja.local
...
Tomcat started on port 8080
```

- **Parar:** `Ctrl + C` no terminal.
- **Rodar de novo:** o mesmo comando. Os dados continuam lá, porque ficam em `.data`.
- **Zerar o banco** e voltar aos dados de demonstração: pare o back-end e apague a pasta `.data`.

```bash
rm -rf crm-loja-backend/.data
```

```powershell
Remove-Item -Recurse -Force crm-loja-backend\.data
```

**Trocar portas** (quando a 8080 ou a 54329 já estão em uso por outro programa):

```bash
SERVER_PORT=8081 EMBEDDED_PG_PORT=54330 ./mvnw spring-boot:test-run
```

```powershell
$env:SERVER_PORT = "8081"; $env:EMBEDDED_PG_PORT = "54330"; .\mvnw.cmd spring-boot:test-run
```

```cmd
set SERVER_PORT=8081
set EMBEDDED_PG_PORT=54330
mvnw.cmd spring-boot:test-run
```

Se mudar a porta da API, avise o front: veja a [seção 4](#4-rodar-o-front-end).

### 3.B Modo com Docker (igual ao servidor de produção)

```bash
cd crm-loja-backend
docker compose up -d            # sobe o PostgreSQL 16 em localhost:5432 (usuário crm, senha crm, banco crm_loja)
docker compose ps               # deve mostrar o serviço "postgres" como "running"
cp .env.example .env            # Windows: copy .env.example .env
```

Abra o `.env` e preencha o `JWT_SECRET` com um texto aleatório de 32 caracteres ou mais:

```bash
openssl rand -hex 48            # gera um segredo; cole o resultado em JWT_SECRET=
```

O Spring **não** lê o arquivo `.env` sozinho: carregue as variáveis no terminal e rode.

```bash
set -a; source .env; set +a
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

```powershell
Get-Content .env | Where-Object { $_ -match '^\s*[^#].*=' } | ForEach-Object {
  $k, $v = $_ -split '=', 2; Set-Item "env:$($k.Trim())" $v.Trim() }
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

- Sem `-Dspring-boot.run.profiles=dev`, não há dados de demonstração. Nesse caso o único login é o super admin `admin@loja.local`, com a senha impressa **uma vez** no console no primeiro boot (ou definida em `SUPER_ADMIN_PASSWORD`).
- Para parar o banco: `docker compose down`. Para apagar também os dados: `docker compose down -v`.

### 3.C Conferir se a API está no ar

```bash
curl -i http://localhost:8080/auth/me
```

O esperado é `HTTP/1.1 401` com `{"error":"Sua sessão expirou. Entre novamente.","code":"unauthenticated"}`. Isso é **bom**: a API está no ar e pedindo login.

---

## 4. Rodar o front-end

```bash
cd crm-loja-frontend
npx pnpm install        # só na primeira vez ou quando o package.json mudar
npx pnpm dev
```

Deu certo quando aparecer `➜ Local: http://localhost:5173/`. Abra esse endereço no navegador.

| Comando | O que faz |
| --- | --- |
| `npx pnpm dev` | Servidor de desenvolvimento com recarga automática ao salvar |
| `npx pnpm build` | Checa os tipos e gera a versão final em `dist/` |
| `npx pnpm preview` | Serve a pasta `dist/` para conferir o build |
| `npx pnpm lint` | ESLint: aponta erros e más práticas |
| `npx pnpm typecheck` | TypeScript estrito, sem gerar arquivos |

**Como o front fala com a API:** o navegador chama `/api/...` no próprio endereço do front, e o Vite repassa para `http://localhost:8080`, tirando o `/api`. Se a API estiver em outra porta:

```bash
VITE_API_TARGET=http://localhost:8081 npx pnpm dev
```

```powershell
$env:VITE_API_TARGET = "http://localhost:8081"; npx pnpm dev
```

Se a porta 5173 estiver ocupada, o Vite usa a próxima livre (5174, 5175…) e mostra qual no terminal.

---

## 5. Testes automáticos

### 5.1 Back-end (60 testes)

```bash
cd crm-loja-backend
./mvnw verify
```

- Roda 12 testes unitários e 48 de integração. Os de integração (`*IT.java`) sobem um PostgreSQL embutido **novo e vazio**, aplicam todas as migrations, fazem login de verdade e chamam a API. Não usam nem estragam seus dados de `.data`.
- Deu certo quando terminar com `Tests run: 48, Failures: 0, Errors: 0` e `BUILD SUCCESS`.
- Leva de 1 a 2 minutos.

**Rodar só uma parte:**

```bash
./mvnw test -Dtest=CpfTest                          # só um teste unitário
./mvnw verify -Dtest=CpfTest -Dit.test=SaleIT       # só a classe de integração SaleIT
./mvnw verify -Dtest=CpfTest -Dit.test='SaleIT#vendaAcimaDoEstoqueERecusada'   # um único caso
```

O `-Dtest=CpfTest` evita rodar todos os unitários quando você só quer um IT.

| Classe | Cobre |
| --- | --- |
| `CustomerIT` | Cadastro, busca sem acento e por CPF, validações, exclusão × inativação |
| `CatalogStockIT` | Categorias, produtos, estoque, filtros, triggers do banco |
| `SaleIT` | Venda completa, tudo-ou-nada, confirmação dupla e simultânea, cancelamento, preço congelado |
| `DashboardIT` | Indicadores, cancelamentos, virada de período 23:59 / 00:00 |
| `AuthIT` | Login, refresh rotativo e reuso, troca de senha obrigatória, papéis, usuários |
| `AssistantIT` | Assistente desligado sem chave, ferramentas por papel, dados reais |
| `CpfTest` | Dígitos verificadores do CPF |

Os relatórios ficam em `target/surefire-reports/` (unitários) e `target/failsafe-reports/` (integração).

### 5.2 Front-end

```bash
cd crm-loja-frontend
npx pnpm lint && npx pnpm typecheck && npx pnpm build
```

Os três precisam passar sem erro. O lint mostra 2 *warnings* de fast-refresh nos arquivos de contexto, o que é esperado.

### 5.3 CI no GitHub

A cada push ou pull request, o GitHub Actions roda `.github/workflows/backend.yml` (`mvnw verify`) e `frontend.yml` (lint, typecheck e build). O resultado aparece na aba **Actions** e no próprio PR.

### 5.4 Testes manuais

Siga o [roteiro de testes manuais](12-roteiro-de-testes.md).

---

## 6. Banco de dados

### 6.1 Dados de conexão

| Modo | Host | Porta | Banco | Usuário | Senha |
| --- | --- | --- | --- | --- | --- |
| Sem Docker (`test-run`) | localhost | 54329 | postgres | postgres | postgres |
| Docker (`docker compose`) | localhost | 5432 | crm_loja | crm | crm |

O banco só aceita conexões enquanto estiver no ar: no modo sem Docker, enquanto o back-end estiver rodando.

### 6.2 Abrir o banco

- **VS Code + SQLTools:** *SQLTools → Add New Connection → PostgreSQL* e preencha com a tabela acima.
- **DBeaver ou pgAdmin:** nova conexão PostgreSQL com os mesmos dados.
- **psql pelo Docker** (não precisa instalar nada além do Docker):

```bash
docker compose exec postgres psql -U crm -d crm_loja      # modo Docker
docker run -it --rm postgres:16 psql -h host.docker.internal -p 54329 -U postgres   # modo sem Docker (senha: postgres)
```

### 6.3 Tabelas

| Tabela | Conteúdo |
| --- | --- |
| `app_user`, `refresh_token` | Contas e sessões (só o hash do token é guardado) |
| `module`, `module_role`, `user_module_access` | Papéis de cada usuário no módulo `crm` |
| `audit_event` | Auditoria: confirmações, cancelamentos, ajustes, mudanças de papel (não aceita UPDATE/DELETE) |
| `customer`, `category`, `product` | Cadastros |
| `sale`, `sale_item` | Vendas e itens (nome e preço copiados no momento da venda) |
| `stock_movement` | Histórico de estoque (não aceita UPDATE/DELETE) |
| `flyway_schema_history` | Quais migrations já rodaram |

### 6.4 Consultas úteis

```sql
-- Estoque baixo e zerado
SELECT code, name, stock_qty, min_stock FROM product
 WHERE is_active AND stock_qty <= min_stock ORDER BY stock_qty;

-- Vendas confirmadas por dia (fuso da loja)
SELECT (confirmed_at AT TIME ZONE 'America/Sao_Paulo')::date AS dia, count(*), sum(total)
  FROM sale WHERE status = 'CONFIRMED' GROUP BY dia ORDER BY dia DESC;

-- Histórico de um produto
SELECT m.created_at, m.type, m.quantity, m.balance_after, m.sale_id, m.reason
  FROM stock_movement m JOIN product p ON p.id = m.product_id
 WHERE p.code = 'CAM-001-M-BR' ORDER BY m.id;

-- O que foi auditado por último
SELECT created_at, action, entity_type, entity_id, payload FROM audit_event ORDER BY id DESC LIMIT 20;

-- Migrations aplicadas
SELECT version, description, installed_on, success FROM flyway_schema_history ORDER BY installed_rank;
```

> **Não altere dados direto pelo SQL** (principalmente `stock_qty`). O sistema garante que todo saldo tenha uma movimentação correspondente; mexer à mão quebra essa garantia. Para testar, use as telas ou a API.

### 6.5 Mudar o banco (nova migration)

1. Crie `crm-loja-backend/src/main/resources/db/migration/V6__descricao_curta.sql`, sempre com o próximo número.
2. Crie a reversão manual em `db/undo/U6__descricao_curta.sql`.
3. Rode o back-end: o Flyway aplica sozinho.
4. Se a mudança cria ou altera colunas usadas no Java, ajuste a entidade. O Hibernate valida: se a entidade e a tabela não baterem, a aplicação nem sobe e diz qual coluna está errada.

> **Nunca edite uma migration que já foi para o GitHub.** O Flyway guarda um checksum de cada arquivo e recusa subir se ele mudar (`Migration checksum mismatch`). Corrija sempre com uma migration nova. Detalhes em `crm-loja-backend/MIGRATIONS_RUNBOOK.md`.

---

## 7. Chamar a API direto (sem a tela)

A autenticação usa cookies, então o `curl` precisa de um "pote de cookies" (`-c` grava, `-b` envia). Todas as rotas estão no [contrato da API](../specs/api-contract.md).

### 7.1 bash / Git Bash

```bash
API=http://localhost:8080

# Entrar (grava os cookies em cookies.txt)
curl -s -c cookies.txt -X POST $API/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"login":"gerente@loja.local","password":"loja12345"}'

# Quem sou eu
curl -s -b cookies.txt $API/auth/me

# Dashboard de outubro
curl -s -b cookies.txt "$API/crm/dashboard?from=2026-10-01&to=2026-10-31"

# Buscar produtos disponíveis com "camiseta" no nome
curl -s -b cookies.txt "$API/crm/products?name=camiseta&availability=AVAILABLE"

# Abrir venda, incluir item e confirmar
VENDA=$(curl -s -b cookies.txt -X POST $API/crm/sales -H 'Content-Type: application/json' -d '{}' | sed -E 's/^\{"id":([0-9]+).*/\1/')
curl -s -b cookies.txt -X POST $API/crm/sales/$VENDA/items -H 'Content-Type: application/json' -d '{"productId":1,"quantity":2}'
curl -s -b cookies.txt -X POST $API/crm/sales/$VENDA/confirm

# Sair
curl -s -b cookies.txt -X POST $API/auth/logout
```

> **Acentos no Git Bash:** o terminal do Windows pode enviar "ó" em outra codificação, e a API responde `Dados em formato inválido`. Use o escape JSON (`"Florian\u00f3polis"`) ou envie um arquivo salvo em UTF-8 com `--data-binary @arquivo.json`.

### 7.2 PowerShell

```powershell
$API = "http://localhost:8080"
$s = New-Object Microsoft.PowerShell.Commands.WebRequestSession

Invoke-RestMethod -Method Post "$API/auth/login" -WebSession $s -ContentType "application/json; charset=utf-8" `
  -Body '{"login":"gerente@loja.local","password":"loja12345"}'

Invoke-RestMethod "$API/crm/dashboard" -WebSession $s
Invoke-RestMethod "$API/crm/customers?search=ana" -WebSession $s | Select-Object -ExpandProperty content
```

### 7.3 Formato de erro

Todo erro volta como `{"error": "frase em português", "code": "codigo_estavel"}`:

| HTTP | Quando | Exemplo de `code` |
| --- | --- | --- |
| 400 | Campo inválido | `invalid_input`, `invalid_cpf`, `reason_required` |
| 401 | Sem sessão | `unauthenticated`, `invalid_credentials` |
| 403 | Perfil sem permissão / troca de senha pendente | `forbidden`, `password_change_required` |
| 404 | Registro não existe | — |
| 409 | Regra de negócio | `insufficient_stock`, `sale_not_draft`, `cpf_taken`, `customer_has_sales` |

---

## 8. Variáveis de ambiente

### 8.1 Back-end

| Variável | Padrão | Para quê |
| --- | --- | --- |
| `SERVER_PORT` | `8080` | Porta da API |
| `EMBEDDED_PG_PORT` | `54329` | Porta do banco embutido (só no `test-run`) |
| `DB_URL` | `jdbc:postgresql://localhost:5432/crm_loja` | Banco (modo Docker/produção) |
| `DB_USER` / `DB_PASSWORD` | `crm` / `crm` | Credenciais do banco |
| `JWT_SECRET` | só no perfil `dev` | Assina os tokens. **Obrigatório** fora do `dev`, com 32+ caracteres; sem ele a aplicação não sobe |
| `ACCESS_TOKEN_TTL_SECONDS` | `600` | Validade do token de acesso (10 min) |
| `REFRESH_TOKEN_TTL_HOURS` | `24` | Sessão expira após 24 h sem uso |
| `FRONTEND_ORIGINS` | `http://localhost:5173` | Origens liberadas no CORS (só para chamadas que **não** passam pelo proxy) |
| `COOKIE_SECURE` | `false` | `true` em produção com HTTPS |
| `SUPER_ADMIN_LOGIN` | `admin@loja.local` | Login do super admin criado no primeiro boot |
| `SUPER_ADMIN_PASSWORD` | vazio | Senha fixa do super admin; vazio = senha aleatória impressa uma vez |
| `ANTHROPIC_API_KEY` | vazio | Liga o assistente de IA. Vazio = assistente desligado |
| `ASSISTANT_MODEL` | `claude-opus-5-5` | Modelo do assistente |
| `ASSISTANT_EFFORT` | `medium` | Profundidade do raciocínio: `low`, `medium`, `high`, `xhigh`, `max` |

Perfis do Spring: `dev` cria os dados de demonstração e tem um `JWT_SECRET` padrão. Sem perfil, o sistema sobe sem dados de demonstração.

### 8.2 Front-end

| Variável | Padrão | Para quê |
| --- | --- | --- |
| `VITE_API_TARGET` | `http://localhost:8080` | Para onde o Vite repassa as chamadas `/api` |

---

## 9. Gerar a versão final (build)

### 9.1 Back-end: um único `.jar`

```bash
cd crm-loja-backend
./mvnw -DskipTests package
# gera target/crm-loja-backend-0.1.0.jar
```

Para rodar o `.jar`, use um PostgreSQL real e as variáveis da [seção 8](#8-variáveis-de-ambiente):

```bash
export DB_URL=jdbc:postgresql://localhost:5432/crm_loja DB_USER=crm DB_PASSWORD=crm
export JWT_SECRET=$(openssl rand -hex 48) COOKIE_SECURE=true
java -jar target/crm-loja-backend-0.1.0.jar
```

### 9.2 Front-end: arquivos estáticos

```bash
cd crm-loja-frontend
npx pnpm build          # gera dist/
```

A pasta `dist/` é servida por qualquer servidor web (nginx, Apache). O servidor precisa:

1. Repassar `/api/*` para o back-end, **tirando** o prefixo `/api` e reescrevendo o caminho do cookie `/auth` para `/api/auth`.
2. Devolver `index.html` para rotas desconhecidas (SPA).

Exemplo de nginx:

```nginx
location /api/ {
    proxy_pass http://localhost:8080/;
    proxy_cookie_path /auth /api/auth;
    proxy_set_header Origin "";
}
location / {
    root /var/www/crm/dist;
    try_files $uri /index.html;
}
```

---

## 10. Git: fluxo de trabalho da equipe

Detalhes em [CONTRIBUTING.md](../../CONTRIBUTING.md).

```bash
git checkout main && git pull                 # começa sempre atualizado
git checkout -b feat/exportar-csv             # um branch por mudança
# ... edita, testa ...
git status                                    # o que mudou
git add caminho/do/arquivo                    # escolhe o que entra
git commit -m "Exporta lista de clientes em CSV (HU32)"
git push -u origin feat/exportar-csv          # envia o branch
# abra o Pull Request no GitHub (ou: gh pr create --fill)
```

Atualizar seu branch com o que entrou no `main`:

```bash
git fetch origin
git merge origin/main          # resolva conflitos, se houver, e faça commit
```

Prefixos de branch: `feat/` (funcionalidade), `fix/` (correção), `docs/` (documentação), `test/` (testes).

---

## 11. Problemas comuns e soluções

| Sintoma | Causa | Solução |
| --- | --- | --- |
| `Port 8080 was already in use` | Outro programa usa a 8080 | Descubra qual (abaixo) ou rode com `SERVER_PORT=8081` e `VITE_API_TARGET=http://localhost:8081` |
| `Gave up waiting for server to start` / `could not bind ... 54329` | Porta do banco embutido ocupada | `EMBEDDED_PG_PORT=54330` |
| `The server requested SCRAM-based authentication, but no password was provided` | Outro PostgreSQL já usa a porta escolhida | Troque `EMBEDDED_PG_PORT` |
| Tela de login mostra "O servidor recusou a requisição (HTTP 403)" | O front não está passando pelo proxy do Vite, ou é uma cópia antiga do `vite.config.ts` | Rode o front com `pnpm dev`, que já remove o `Origin`; para chamadas diretas, inclua a origem em `FRONTEND_ORIGINS` |
| Login funciona mas a sessão cai em 10 min | O cookie de refresh não volta | Confira o `cookiePathRewrite` no `vite.config.ts` (ou `proxy_cookie_path` no nginx) |
| `JWT_SECRET precisa de pelo menos 32 caracteres` / app não sobe | Rodando sem o perfil `dev` e sem segredo | Defina `JWT_SECRET` ou use `-Dspring-boot.run.profiles=dev` |
| `Migration checksum mismatch` | Uma migration já aplicada foi editada | Desfaça a edição e crie uma migration nova; no ambiente local, dá para apagar `.data` |
| `Schema-validation: missing column` / `wrong column type` | Entidade Java diferente da tabela | Ajuste a entidade ou crie a migration que falta |
| `release version 21 not supported` / `UnsupportedClassVersionError` | Java antigo no `PATH` | `java -version`; ajuste `JAVA_HOME` para o JDK 21+ |
| `'mvnw' não é reconhecido` | No PowerShell, precisa do caminho | `.\mvnw.cmd ...` |
| `./mvnw: Permission denied` (Linux/macOS) | Arquivo sem permissão de execução | `chmod +x mvnw` |
| `a execução de scripts foi desabilitada` | Política do PowerShell | Use `npx.cmd`/`pnpm.cmd` ou o cmd/Git Bash |
| `Ignored build scripts: esbuild` no `pnpm install` | Aviso do pnpm 10 | Pode ignorar; o `package.json` já libera o esbuild |
| `Dados em formato inválido` ao mandar JSON com acento pelo `curl` | Codificação do terminal | Escape `\u00f3` ou arquivo UTF-8 ([seção 7.1](#71-bash--git-bash)) |
| Página do assistente diz "assistente desligado" | Falta `ANTHROPIC_API_KEY` | Veja [10-assistente-ia.md](10-assistente-ia.md) |
| Dados de demonstração não aparecem | O banco já tinha categorias, ou rodou sem o perfil `dev` | Apague `.data` (modo sem Docker) ou rode com o perfil `dev` num banco vazio |

**Descobrir quem usa uma porta:**

```powershell
Get-NetTCPConnection -LocalPort 8080 -State Listen | ForEach-Object { Get-Process -Id $_.OwningProcess }
```

```bash
lsof -i :8080            # macOS/Linux
netstat -ano | findstr :8080   # cmd do Windows (o último número é o PID)
```

**Encerrar um processo** (só se for seu e você souber o que é): `Stop-Process -Id <PID>` no PowerShell, ou `kill <PID>` no macOS/Linux.

---

## 12. Cola rápida

```bash
# Back-end
cd crm-loja-backend
./mvnw spring-boot:test-run                 # rodar (banco embutido + dados de demo)
./mvnw verify                               # todos os testes
./mvnw verify -Dtest=CpfTest -Dit.test=SaleIT   # uma classe de teste
./mvnw -DskipTests package                  # gerar o .jar
rm -rf .data                                # zerar o banco local

# Front-end
cd crm-loja-frontend
npx pnpm install                            # dependências
npx pnpm dev                                # rodar (http://localhost:5173)
npx pnpm lint && npx pnpm typecheck && npx pnpm build   # checagens

# Logins: gerente@ / vendedor@ / estoquista@loja.local — senha loja12345
```
