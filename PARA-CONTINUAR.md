# Para continuar (leia primeiro)

> Pacote preparado em 29/10/2026 fora do computador do curso. Este arquivo explica **o que mudou**, **como levar ao
> GitHub e ao Render** e **de onde continuar**. Se você é o Claude abrindo este projeto agora: leia este arquivo
> inteiro, depois o `CLAUDE.md` da raiz, antes de mexer em qualquer coisa.

## 1. O que é este pacote

É o projeto **Orden** (Mini Projeto SENAI Tubarão, Técnico em Desenvolvimento de Sistemas, 2º semestre de 2026) com
**um dia inteiro de mudanças** feitas fora do repositório. O ponto de partida foi uma cópia do repositório
`Douglasvargass/crm-loja-roupas` exportada em 29/10/2026 às 09:00 (sem a pasta `.git`).

**Nada disto está no GitHub ainda.** O envio é feito pelo Douglas, com a conta pessoal dele, no computador do curso.
Nunca use outra conta para enviar.

## 1.1 Passo a passo para o Claude do curso (faça nesta ordem)

1. Leia este arquivo inteiro e o `CLAUDE.md` da raiz. Não mude código antes de rodar os passos 2 e 3.
2. Back-end: na pasta `crm-loja-backend`, rode `./mvnw verify` (Windows: `mvnw.cmd verify`). Precisa de Java 21. Esperado: 106 testes unitários e 97 de integração passando. Se der "Found more than one migration", apague a pasta `target` e rode de novo.
3. Front-end: na pasta `crm-loja-frontend`, rode `npx pnpm install`, depois `npx pnpm lint`, `npx pnpm typecheck` e `npx pnpm build`.
4. Suba local (`mvnw spring-boot:test-run` + `npx pnpm dev`) e passe o roteiro da seção 6. Se o banco embutido não subir com "Permission denied", o Windows reservou a porta: use `EMBEDDED_PG_PORT=55432` (detalhes em `crm-loja-docs/docs/11-guia-de-comandos.md`).
5. Só então siga as seções 4 (GitHub) e 5 (Render), sempre com a conta pessoal do Douglas.
6. Pedido novo do Douglas: siga o processo do doc 16 (spec em `crm-loja-docs/specs/crm/12-nome/`), migration **V13**, teste, roteiro e este arquivo atualizados.

## 2. O que mudou, em ordem

| # | Mudança | Spec / doc | Banco |
| --- | --- | --- | --- |
| 1 | Documentação alinhada ao código (Postgres embutido, cookie Lax, CI real, seeds, contagens) | doc 15 (conferência do enunciado) | — |
| 2 | Login `nome.sobrenome` (sem @), senha de demonstração continua `loja12345` | README | — |
| 3 | Papéis novos: **Engenheiro(a) de Software** (`DEVELOPER`) e **Marketing e Desenvolvimento** (`MARKETING_DEV`), que valem por gerente; **Operações e Compras** (`PURCHASING`), que vale por estoquista; **Cliente** (`CUSTOMER`, módulo `loja`) | doc 04 (RN24) | seed |
| 4 | Equipe: Guilherme gerente; Douglas engenheiro (super admin); Julia marketing e desenvolvimento; Vinicius Clemente operações e compras; Vitor e Vinicius Muniz vendedores | doc 17 | seed |
| 5 | Dashboard: filtros **Vendedor(a)** e **Canal** (loja física × online) | crm/05, crm/07 | — |
| 6 | **Meu painel** do vendedor e **Metas** do gerente | specs/crm/06 | **V6** |
| 7 | Processo de requisitos da equipe e modelos de issue do GitHub | doc 16, `.github/ISSUE_TEMPLATE/` | — |
| 8 | **Agente de vendas da demonstração**: fecha cada dia que passou; a IA grátis só escolhe o clima do dia | specs/crm/08 | **V7** |
| 9 | **Chamados de peça em falta** com sino de avisos; a entrada de estoque fecha o chamado sozinha | specs/crm/09 | **V8** |
| 10 | **Thalita**: abre chamado (com confirmação), conta a história da Orden, recusa assunto fora da loja, sabe dos pedidos online | doc 10, doc 17 | — |
| 11 | **"Ver como"** só leitura (gerente vê como outra pessoa) | specs/crm/10 | — |
| 12 | **Exportar planilha** (CSV) de clientes, produtos e vendas (HU32) | api-contract | — |
| 13 | **"?" de ajuda** em toda tela, aviso de **pendências ao entrar**, botão travado sempre com o motivo | roteiro seção 14 | — |
| 14 | **Espaço do cliente**: os professores entram como clientes, vitrine, carrinho, Pix ou cartão simulados, meus pedidos, meus dados | specs/crm/07 | **V9** |
| 15 | **Reposição** (o que vai faltar, em quantos dias acaba, quanto pedir, dinheiro parado) e **Pedidos de compra** (registrar, receber com um clique, mudar data, cancelar). Tela inicial do Vinicius Clemente | specs/crm/11 | **V10** |
| 16 | **Cores**: o site fica sempre na paleta clara da marca (Creme, Areia, Caramelo, Café), mesmo com o computador no modo escuro; menu e barra de cima em creme | `tokens.css` | — |
| 17 | **Pix com QR code de mentira** (faixa "DEMONSTRAÇÃO") na tela de pagamento e **"Ver como"** visível também em tela estreita (só o ícone) | `FakePixQr.tsx`, `ViewAs.tsx` | — |
| 18 | **Prévia da loja** ("Ver como" → Loja do cliente: o site como qualquer cliente vê, nada é gravado) e **Thalita online em verde** (cinza quando desligada). Celular e tablet conferidos: menu, navegação e Thalita | specs/crm/10 (REQ-06), roteiro seções 13 e 17 | — |
| 19 | **Estoque no Dashboard** (gerente vê vendas + estoque; Clemente e estoquista só estoque, e o Clemente entra por ele) e **etapas do pedido de compra** (pedido feito → em trânsito → em conferência → entrada com a nota fiscal) | specs/crm/11 (REQ-06, REQ-07) | **V11** |
| 20 | **Thalita do cliente** (só vitrine, pedidos dele e pedir peça) e botão **"Não achou? Peça à loja"**; o cliente acompanha em Meus pedidos, o gerente vê no sino | specs/crm/07 (REQ-12) | — |
| 21 | **Conferência do pedido** (conta peça por peça: a menos → devolver a nota; a mais → nota de bonificação), produto novo não é "parado", **Resetar senha** pela tela (equipe e clientes) e cliente com login não se exclui | specs/crm/11 (REQ-08), RN52–RN54, roteiro seção 19 | **V12** |
| 22 | **Sugestão de pedido no Dashboard** (lista com quantidades e botão que monta o pedido de compra) | roteiro seção 18 | — |
| 23 | **Previsão de vendas** com sazonalidade (dia da semana, salário, feriados, datas comerciais, mês do ano) e **metas sugeridas pela previsão** na tela Metas; Thalita com `previsao_vendas` e `sugestao_metas` | specs/crm/12, RN55–RN60, roteiro seção 20 | — |

O detalhe de cada passo está em `HISTORICO-DE-COMMITS.txt` (os commits feitos neste pacote) e no fim de
`DOCUMENTACAO_COMPLETA.md` ("Histórico de revisões").

## 3. O que já foi testado aqui

| Verificação | Resultado |
| --- | --- |
| `./mvnw verify` (back-end inteiro) | **Passou**: veja a última linha de `HISTORICO-DE-COMMITS.txt` para os números da rodada final |
| `pnpm lint && pnpm typecheck && pnpm build` (front-end) | **Passou** (2 avisos antigos do `react-refresh` e o aviso de tamanho do pacote, sem erro) |
| Teste pela tela no navegador | Login, cores e as telas de Reposição e Pedidos de compra conferidos no localhost em 29/10/2026; o roteiro completo (seção 6 abaixo) fica para o computador do curso |

## 4. Como levar ao GitHub (no computador do curso)

```bash
# 1) Clonar o repositório oficial (com a conta pessoal do Douglas)
git clone https://github.com/Douglasvargass/crm-loja-roupas.git
cd crm-loja-roupas
git checkout -b feat/atualizacao-29-10

# 2) Copiar por cima os arquivos deste pacote (descompactado ao lado, em ../orden-crm-loja)
#    Windows (PowerShell):  robocopy ..\orden-crm-loja . /E /XD .git node_modules target dist .data
#    Linux/macOS:           rsync -a --exclude .git --exclude node_modules --exclude target --exclude dist ../orden-crm-loja/ .

# 3) Conferir e testar
git status
cd crm-loja-backend && ./mvnw verify && cd ..           # Windows: mvnw.cmd verify  (precisa de Java 21)
cd crm-loja-frontend && npx pnpm install && npx pnpm lint && npx pnpm typecheck && npx pnpm build && cd ..

# 4) Enviar e abrir o PR
git add -A
git commit -m "Atualização de 29/10: metas, chamados, agente da demo, Ver como, espaço do cliente, ajuda e docs"
git push -u origin feat/atualizacao-29-10
# Abrir o PR no GitHub, esperar o CI (backend, frontend e docker) ficar verde e fazer o merge na main.
```

Os arquivos que **não** devem ir ao Git já estão no `.gitignore` (`node_modules`, `target`, `dist`, `.data`, `.env`).

## 5. Como publicar no Render

1. O merge na `main` publica sozinho (`autoDeploy: true` no `render.yaml`). Acompanhe em render.com → serviço `crm-loja-roupas` → **Events**. Leva de 5 a 10 minutos.
2. Na subida, o Flyway aplica **V6 a V12** sozinho. Nada é apagado; as vendas antigas ficam com canal "Loja física".
3. Os seeds de demonstração **acertam tudo sozinhos** no banco que já existe: logins `nome.sobrenome`, papéis (Vinicius Clemente vira Operações e Compras), histórico redistribuído entre os vendedores, metas, 4 pedidos de compra de exemplo (em trânsito com nota fiscal, pedido feito, um atrasado e um em conferência), diário do agente e as contas dos professores (`vitor.freitas` e `matheus.carvalho`, senha `cliente12345`, troca obrigatória).
4. **Ligar a IA grátis** (Thalita e agente): Render → **Environment** → `ASSISTANT_PROVIDER=groq` e `ASSISTANT_API_KEY=<chave grátis de console.groq.com>`. Salvar reinicia o serviço.
5. **Acordar todo dia:** GitHub → **Actions → acordar-demo → Run workflow** uma vez para testar. Depois ele roda sozinho às 03:10.
6. **Atenção:** o PostgreSQL grátis do Render expira 30 dias depois de criado (perto de **25/11/2026**). Quando expirar, recrie pelo Blueprint (`crm-loja-docs/docs/13-publicar-online.md`); o histórico de demonstração volta sozinho.

## 6. Testar pela tela

Roteiro: `crm-loja-docs/docs/12-roteiro-de-testes.md`. As seções novas são a 10 (Meu painel e Metas), 11 (agente),
12 (chamados e Thalita), 13 ("Ver como"), 14 (ajuda, pendências, exportar), 15 (espaço do cliente), 16 (reposição e pedidos de compra), 17 (celular, tablet e Thalita), 18 (estoque no Dashboard, etapas do pedido, Thalita do cliente e sugestão de pedido) e 19 (conferência, reset de senha e cliente com login).

## 7. De onde continuar (backlog)

Tudo o que foi combinado está feito. Sobrou só o que depende de você ou é melhoria pequena:

1. **Publicação:** passos 4 e 5 acima (GitHub, Render, chave do Groq, workflow) e recriar o banco perto de 25/11.
2. **Cofre de versões:** decidido em 29/10/2026 que **não** será feito. Não retomar.
3. **Limites conhecidos**: o agente da demonstração não cria cancelamentos nos dias novos e não fecha chamados; no "Ver como" a Thalita não responde (é só leitura); o cliente não cancela a própria solicitação de peça; a Thalita não tem limite de perguntas por pessoa (decisão de 29/10: fica assim).
4. **Ideias que ficaram fora do escopo** (spec 11, "Não inclui"): cadastro de fornecedores, preço de custo, recebimento parcial, envio do pedido ao fornecedor, sazonalidade na previsão.
5. **Ideias que ficaram fora do escopo** (spec 07, "Não inclui"): frete, cupom, vários endereços, auto-cadastro, cancelamento pelo próprio cliente, aviso ao cliente por WhatsApp ou e-mail quando a peça do chamado chega.

## 8. Regras do projeto que valem sempre

- Leia o `CLAUDE.md` da raiz, do back-end e do front-end. Resumo: regra de negócio só no Service; banco muda só por migration nova (a próxima é **V13**); nada se apaga (cancela ou inativa); código em inglês, tela e documentos em português simples; todo comportamento novo tem teste.
- Antes de dar algo por pronto: `./mvnw verify` e `pnpm lint && pnpm typecheck && pnpm build`.
- Pedido novo segue o processo do doc 16 (história, DADO/QUANDO/ENTÃO com o negativo, regras R1..Rn, spec em `specs/crm/NN-nome/`).
- A história da Orden (doc 17) foi revisada com a equipe: **não** citar empresa nem pessoa como origem da dor do estoque; a origem do nome "Orden" não foi registrada.
- Se o `./mvnw verify` reclamar de "Found more than one migration with version N", apague `crm-loja-backend/target` (sobra de compilação antiga) e rode de novo.

## 9. Logins de demonstração

Equipe (senha `loja12345`):

| Login | Papel | Tela inicial |
| --- | --- | --- |
| `guilherme.dower` | Gerente | Dashboard |
| `douglas.vargas` | Engenheiro(a) de Software (super admin) | Dashboard |
| `julia.alcantara` | Marketing e Desenvolvimento | Dashboard |
| `vinicius.clemente` | Operações e Compras | Dashboard (só estoque) |
| `vitor.nunes` | Vendedor(a) | Meu painel |
| `vinicius.muniz` | Vendedor(a) | Meu painel |

Clientes (senha inicial `cliente12345`, troca obrigatória no primeiro acesso):

| Login | Pessoa | Tela inicial |
| --- | --- | --- |
| `vitor.freitas` | Vitor Freitas (professor) | Vitrine |
| `matheus.carvalho` | Matheus Carvalho (professor) | Vitrine |
