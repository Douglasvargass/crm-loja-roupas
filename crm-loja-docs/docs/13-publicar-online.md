# Publicar online

O projeto tem duas partes publicáveis:

| O quê | Onde | Custo | Atualiza sozinho? |
| --- | --- | --- | --- |
| **Página de apresentação** (pasta `site/`) | GitHub Pages: https://douglasvargass.github.io/crm-loja-roupas/ | Grátis | Sim, a cada push que muda `site/` |
| **Sistema funcionando** (telas + API + banco) | Render, a partir do `render.yaml` | Grátis com limites (veja abaixo) | Sim, a cada push no `main` |

O GitHub Pages só hospeda arquivos estáticos (HTML, CSS, JS). Ele **não** roda Java nem banco, por isso o sistema em si vai para o Render.

## Como funcionam as atualizações

```
você altera o código → git push → GitHub Actions testa → Render/Pages publicam a nova versão
```

- **Não há cobrança por atualização** em nenhum dos dois. Pode publicar quantas vezes quiser.
- O GitHub Pages republica em 1 ou 2 minutos (workflow `pages.yml`).
- O Render reconstrói a imagem Docker e troca a versão em uns 5 a 10 minutos. Enquanto isso, a versão anterior continua no ar.
- Os dados do banco **não** são apagados nas atualizações. Mudanças de tabela entram por migration nova (`V6__…`), aplicada sozinha na subida.

## 1. Página de apresentação (GitHub Pages)

Já configurada: cada push que altera `site/` publica a página. Para editar, mude `site/index.html`, faça commit e push, e acompanhe em **Actions → pages**.

Se um dia a página sumir, confira em *Settings → Pages* que a origem (*Source*) é **GitHub Actions**.

## 2. Sistema funcionando (Render)

### 2.1 Criar (uma vez)

1. Crie uma conta em https://render.com. Dá para entrar com a conta do GitHub.
2. Abra o link de deploy do repositório:

   **https://render.com/deploy?repo=https://github.com/Douglasvargass/crm-loja-roupas**

3. O Render lê o `render.yaml` e mostra o que vai criar:
   - `crm-loja-db`: PostgreSQL 16 (plano free);
   - `crm-loja-roupas`: serviço web com a imagem do `Dockerfile` (plano free).
4. Ele pede o valor de `ANTHROPIC_API_KEY`. Deixe **em branco** para o assistente ficar desligado, ou cole a sua chave para ligá-lo.
5. Clique em **Apply** / **Deploy Blueprint**. O primeiro build leva de 5 a 10 minutos.
6. Quando o serviço ficar **Live**, abra a URL dele (algo como `https://crm-loja-roupas.onrender.com`) e entre com `gerente@loja.local` / `loja12345`.

O perfil `demo` cria os dados de demonstração e os 3 logins na primeira subida. O `JWT_SECRET` é gerado pelo próprio Render, e o `COOKIE_SECURE` já vem ligado, porque o Render usa HTTPS.

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
| `ANTHROPIC_API_KEY` | Liga ou desliga o assistente (vazio = desligado) |
| `ASSISTANT_EFFORT` | `low` para respostas mais rápidas e baratas |
| `SPRING_PROFILES_ACTIVE` | `demo` (cria dados de exemplo num banco vazio) ou vazio |

Salvar uma variável reinicia o serviço.

### 2.5 Acompanhar e resolver problemas

- **Logs:** serviço → *Logs*. Mostra a subida do Spring, as migrations e os erros.
- **Build falhou:** veja o log de build. O mesmo `Dockerfile` é testado no GitHub a cada push (workflow `docker.yml`); se lá estiver verde, o problema costuma ser do Render (memória, tempo).
- **"Application failed to respond":** o serviço ainda está acordando, ou o banco não está pronto. Aguarde um minuto e recarregue.
- **Banco expirou:** crie um novo banco `crm-loja-db` no painel; o serviço se reconecta pelas variáveis `DB_*`.

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
