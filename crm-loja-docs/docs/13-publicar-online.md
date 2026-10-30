# Publicar online

O projeto tem duas partes publicáveis:

| O quê | Onde | Custo | Atualiza sozinho? |
| --- | --- | --- | --- |
| **Página de apresentação** (pasta `site/`) | GitHub Pages: https://douglasvargass.github.io/crm-loja-roupas/ | Grátis | Sim, a cada push que muda `site/` |
| **Sistema funcionando** (telas + API + banco) | Render: https://crm-loja.onrender.com (serviço `crm-loja`, banco `crm-loja-db`) | Grátis com limites (veja abaixo) | Sim, a cada push no `main` |

O GitHub Pages só hospeda arquivos estáticos (HTML, CSS, JS). Ele **não** roda Java nem banco, por isso o sistema em si vai para o Render.

## Como funcionam as atualizações

```
você altera o código → git push → GitHub Actions testa → Render/Pages publicam a nova versão
```

- **Não há cobrança por atualização** em nenhum dos dois. Pode publicar quantas vezes quiser.
- O GitHub Pages republica em 1 ou 2 minutos (workflow `pages.yml`).
- O Render reconstrói a imagem Docker e troca a versão em uns 5 a 10 minutos. Enquanto isso, a versão anterior continua no ar.
- Os dados do banco **não** são apagados nas atualizações. Mudanças de tabela entram por migration nova (`V6__…`), aplicada sozinha na subida.

## Acordar o sistema todo dia (agente de vendas)

O workflow `acordar-demo.yml` abre o site às 03:10 de São Paulo, de graça. O servidor acorda e o agente de vendas
da demonstração fecha o dia anterior (spec crm/08). Para testar na hora: **Actions → acordar-demo → Run workflow**.
Para a IA escolher o clima de cada dia, configure no Render `ASSISTANT_PROVIDER=groq` e `ASSISTANT_API_KEY` com uma
chave grátis de console.groq.com; sem isso, os dias saem no padrão.

## 1. Página de apresentação (GitHub Pages)

Já configurada: cada push que altera `site/` publica a página. Para editar, mude `site/index.html`, faça commit e push, e acompanhe em **Actions → pages**.

Se um dia a página sumir, confira em *Settings → Pages* que a origem (*Source*) é **GitHub Actions**.

## 2. Sistema funcionando (Render)

> **Já está publicado** em https://crm-loja.onrender.com, na conta Render do dono do repositório. As seções abaixo servem para publicar uma **nova cópia** (outra conta, outra loja) ou refazer do zero.

### 2.0 Armadilha mais comum: criar o serviço sem o Blueprint

Se o serviço for criado pelo botão **New → Web Service** (em vez do link do Blueprint), ele nasce **sem** as variáveis de ambiente e **sem** banco. O sintoma no log é:

```
No active profile set, falling back to 1 default profile: "default"
... Exited with status 1 while running your code.
```

Sem `JWT_SECRET` o sistema se recusa a subir (fail-fast). Solução: apagar esse serviço e criar pelo Blueprint (2.1), ou pelo CLI (2.6).

### 2.1 Criar (uma vez)

1. Crie uma conta em https://render.com. Dá para entrar com a conta do GitHub.
2. Abra o link de deploy do repositório:

   **https://render.com/deploy?repo=https://github.com/Douglasvargass/crm-loja-roupas**

3. O Render lê o `render.yaml` e mostra o que vai criar:
   - `crm-loja-db`: PostgreSQL 16 (plano free);
   - `crm-loja-roupas`: serviço web com a imagem do `Dockerfile` (plano free).
4. Ele pede `ASSISTANT_PROVIDER`, `ASSISTANT_API_KEY` e `ASSISTANT_MODEL`. Deixe **em branco** para o assistente ficar desligado, ou preencha provedor (`groq`, `gemini`, `anthropic`…) e chave para ligá-lo (modelo em branco = padrão do provedor).
5. Clique em **Apply** / **Deploy Blueprint**. O primeiro build leva de 5 a 10 minutos.
6. Quando o serviço ficar **Live**, abra a URL dele (algo como `https://crm-loja-roupas.onrender.com`) e entre com `douglas.vargas` / `loja12345`.

O perfil `demo` cria os dados de demonstração e os logins da equipe na primeira subida. O `JWT_SECRET` é gerado pelo próprio Render, e o `COOKIE_SECURE` já vem ligado, porque o Render usa HTTPS.

### 2.2 Limites do plano grátis do Render

- O serviço **dorme após uns 15 minutos sem acesso**. O próximo acesso demora de 30 a 60 segundos para acordar; depois fica normal.
- O PostgreSQL grátis **expira em 30 dias**. Depois disso é preciso criar outro (os dados se perdem) ou passar para um plano pago.
- Memória limitada (512 MB). O `Dockerfile` já ajusta o Java para caber.
- Planos pagos (sem dormir e com banco permanente) começam em torno de US$ 7 por mês para o serviço, mais o banco. Confira em https://render.com/pricing.

### 2.3 Segurança da demonstração pública

- Os logins de demonstração (`loja12345`) são **públicos**, porque estão na documentação. Use só dados fictícios.
- Para um uso real: crie usuários próprios (menu Usuários), desative as contas de demonstração e troque `SPRING_PROFILES_ACTIVE` para vazio no painel do Render, para não recriar dados de demonstração num banco vazio.
- Se ligar o assistente com a sua chave, **qualquer pessoa logada** gera custo na sua conta da Anthropic. Defina um limite de gasto em https://console.anthropic.com → *Limits*.

### 2.4 Configurar depois

No painel do Render: serviço `crm-loja-roupas` → **Environment**.

| Variável | Para quê |
| --- | --- |
| `ASSISTANT_PROVIDER` · `ASSISTANT_API_KEY` · `ASSISTANT_MODEL` | Ligam o assistente (chave vazia = desligado). `ANTHROPIC_API_KEY` sozinha também funciona (provedor anthropic) |
| `ASSISTANT_EFFORT` | `low` para respostas mais rápidas e baratas |
| `SPRING_PROFILES_ACTIVE` | `demo` (cria dados de exemplo num banco vazio) ou vazio |

Salvar uma variável reinicia o serviço.

### 2.5 Acompanhar e resolver problemas

- **Logs:** serviço → *Logs*. Mostra a subida do Spring, as migrations e os erros.
- **Build falhou:** veja o log de build. O mesmo `Dockerfile` é testado no GitHub a cada push (workflow `docker.yml`); se lá estiver verde, o problema costuma ser do Render (memória, tempo).
- **"Application failed to respond":** o serviço ainda está acordando, ou o banco não está pronto. Aguarde um minuto e recarregue.
- **Banco expirou:** crie um novo banco `crm-loja-db` no painel; o serviço se reconecta pelas variáveis `DB_*`.
- **Serviço criado pelo CLI:** as variáveis `DB_*` são fixas (não seguem o banco automaticamente como no Blueprint). Se trocar de banco, atualize `DB_HOST`, `DB_PASSWORD` etc. em *Environment*.

### 2.6 Pelo Render CLI (sem clicar no painel)

Foi assim que a demonstração atual foi publicada. Precisa do [Render CLI](https://github.com/render-oss/cli/releases) (arquivo `.zip`, sem instalador) e de um login autorizado no navegador.

```bash
render login                                   # abre o navegador; autorize o código mostrado
render workspace set "My Workspace" --confirm
render blueprints validate ./render.yaml       # confere o arquivo: deve responder "valid": true

# 1) banco PostgreSQL 16 grátis (expira em 30 dias)
render postgres create --confirm --name crm-loja-db --plan free --version 16 --region oregon \
  --database-name crm_loja --database-user crm

# 2) espere "status": "available"
render postgres get crm-loja-db -o json

# 3) serviço web com as variáveis. Pegue host, porta e senha em:
#    render postgres get crm-loja-db --include-sensitive-connection-info -o json  (campo internalConnectionString)
MSYS_NO_PATHCONV=1 render services create --confirm -o json --name crm-loja --type web_service \
  --repo https://github.com/Douglasvargass/crm-loja-roupas --branch main --runtime docker \
  --plan free --region oregon --health-check-path /login --auto-deploy \
  --env-var SPRING_PROFILES_ACTIVE=demo --env-var COOKIE_SECURE=true \
  --env-var "JWT_SECRET=$(openssl rand -hex 48)" \
  --env-var DB_HOST=<host interno dpg-…-a> --env-var DB_PORT=5432 --env-var DB_NAME=crm_loja \
  --env-var DB_USER=crm --env-var "DB_PASSWORD=<senha>"

# 4) acompanhar
render deploys list crm-loja
render logs -r crm-loja --limit 50
```

> No **Git Bash** do Windows, `/login` vira `C:/Program Files/Git/login` por causa da conversão de caminhos. O `MSYS_NO_PATHCONV=1` na frente do comando evita isso; se já aconteceu, corrija com `MSYS_NO_PATHCONV=1 render services update crm-loja --health-check-path /login --confirm`.

O CLI não edita variáveis de um serviço já criado; para isso use o painel (*Environment*).

## 3. Outras hospedagens

O `Dockerfile` da raiz funciona em qualquer serviço que rode contêiner (Railway, Fly.io, Google Cloud Run, Azure, um servidor próprio). O que a imagem precisa:

| Variável | Exemplo |
| --- | --- |
| `DB_URL` (ou `DB_HOST`, `DB_PORT`, `DB_NAME`) | `jdbc:postgresql://host:5432/crm_loja` |
| `DB_USER`, `DB_PASSWORD` | credenciais do PostgreSQL 16 |
| `JWT_SECRET` | 32+ caracteres aleatórios (`openssl rand -hex 48`) |
| `COOKIE_SECURE` | `true` com HTTPS |
| `PORT` | definido pela hospedagem (padrão 8080) |
| `SPRING_PROFILES_ACTIVE` | `demo` para dados de exemplo, ou vazio |

Testar a imagem localmente (com Docker):

```bash
docker build -t crm-loja .
docker compose -f crm-loja-backend/docker-compose.yml up -d        # PostgreSQL em localhost:5432
docker run --rm -p 8080:8080 --add-host=host.docker.internal:host-gateway \
  -e SPRING_PROFILES_ACTIVE=demo -e DB_HOST=host.docker.internal -e DB_USER=crm -e DB_PASSWORD=crm \
  -e JWT_SECRET=$(openssl rand -hex 48) crm-loja
# abra http://localhost:8080
```

## E-mail de "Esqueci minha senha" (opcional)

Sem estas variáveis o sistema funciona, mas o link de recuperação não chega ao cliente. Em render.com → serviço →
**Environment**, cadastre:

| Variável | Exemplo | Para quê |
| --- | --- | --- |
| `PUBLIC_URL` | `https://crm-loja-app.onrender.com` | Endereço usado no link do e-mail |
| `SMTP_HOST` | `smtp-relay.brevo.com` (grátis, 300 e-mails/dia) ou `smtp.gmail.com` | Servidor de envio |
| `SMTP_PORT` | `587` | Porta com STARTTLS |
| `SMTP_USER` | o login do serviço de e-mail | |
| `SMTP_PASSWORD` | a chave SMTP do serviço (no Gmail, uma "senha de app") | Nunca no Git |
| `MAIL_FROM` | `Orden <nao-responda@seudominio.com>` | Remetente |

No localhost, sem `SMTP_HOST`, o link aparece no terminal do back-end (só no perfil `dev`).
