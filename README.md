# Orden · sistema de gestão para loja de moda

[![backend](https://github.com/Douglasvargass/crm-loja-roupas/actions/workflows/backend.yml/badge.svg)](https://github.com/Douglasvargass/crm-loja-roupas/actions/workflows/backend.yml)
[![frontend](https://github.com/Douglasvargass/crm-loja-roupas/actions/workflows/frontend.yml/badge.svg)](https://github.com/Douglasvargass/crm-loja-roupas/actions/workflows/frontend.yml)
[![docker](https://github.com/Douglasvargass/crm-loja-roupas/actions/workflows/docker.yml/badge.svg)](https://github.com/Douglasvargass/crm-loja-roupas/actions/workflows/docker.yml)

**Sistema online (demonstração):** https://crm-loja.onrender.com · login `douglas.vargas` / `loja12345` (plano grátis: o primeiro acesso pode levar até 1 minuto para "acordar")
**Página do projeto:** https://douglasvargass.github.io/crm-loja-roupas/ · **Publicar sua própria cópia:** [![Deploy to Render](https://render.com/images/deploy-to-render-button.svg)](https://render.com/deploy?repo=https://github.com/Douglasvargass/crm-loja-roupas) ([passo a passo](crm-loja-docs/docs/13-publicar-online.md))

Sistema web para uma loja de roupas organizar clientes, produtos, estoque e vendas, com painel de indicadores e um **assistente de IA** que responde dúvidas consultando os dados reais da loja. MVP feito para o Mini Projeto SENAI.

| Camada | Tecnologia |
| --- | --- |
| Back-end | **Java 21** + Spring Boot 3.5 (API REST) |
| Banco de dados | **PostgreSQL 16**, tabelas criadas por migrations Flyway |
| Front-end | React 19 + TypeScript + Vite + Tailwind CSS |
| Assistente de IA | Claude (Anthropic) ou provedores com plano grátis: Groq, Google Gemini, OpenRouter, Ollama |
| Testes | JUnit 5 + MockMvc contra um PostgreSQL 16 real embutido, sem Docker (116 testes) |

## O que o sistema faz

- **Clientes:** cadastro com CPF validado e endereço pelo CEP, busca sem acento, histórico de compras.
- **Categorias e produtos:** tamanho, cor, código, preço; etiquetas *Disponível*, *Sem estoque*, *Estoque baixo* e *Inativo*.
- **Estoque:** entradas, ajustes com motivo, alertas de reposição e histórico permanente de cada movimento.
- **Vendas:** carrinho com total calculado no servidor, confirmação "tudo ou nada" que baixa o estoque, cancelamento com motivo que devolve as peças.
- **Dashboard:** vendas e faturamento por período, comparação com o período anterior, mais vendidos, vendas por categoria, estoque baixo e zerado; filtro por vendedor(a) para ver as vendas de cada um.
- **Acesso:** login `nome.sobrenome` com 5 papéis (Gerente, Vendedor, Estoquista e os técnicos Engenheiro de Software e Marketing e Desenvolvimento), troca de senha obrigatória no primeiro acesso, gestão de usuários.
- **Meu painel e metas:** cada vendedor entra no próprio painel, com a meta do mês, quanto falta por dia útil e a projeção; o gerente define as metas e vê a equipe na tela Metas.
- **Espaço do cliente:** o cliente entra com a própria conta, vê a vitrine, monta o carrinho, paga (Pix ou cartão simulados) e acompanha os pedidos; a loja vê os pedidos online junto das vendas.
- **"Ver como":** o gerente vê o sistema exatamente como um vendedor ou o estoque vê, só para olhar, com tudo registrado.
- **Chamados de peça em falta:** o cliente quis uma peça que acabou; o vendedor (ou a Thalita) abre o chamado, o estoque e o gerente veem no sino, e quando a peça chega o chamado se resolve e o vendedor é avisado.
- **Agente de vendas da demonstração:** todo dia que passa, o sistema cria vendas de demonstração parecidas com as reais; a IA grátis só escolhe o clima do dia, e o gerente vê tudo num diário.
- **Thalita, a assistente virtual:** botão flutuante em todas as telas; perguntas como "quanto vendemos este mês?" ou "o que preciso repor?", respondidas com dados do banco e respeitando o perfil de quem pergunta.

## Rodar em 5 minutos (sem Docker)

Pré-requisitos: **Java 21+**, **Node.js 22+** e **Git**. O passo a passo da instalação de cada um está no [guia de comandos](crm-loja-docs/docs/11-guia-de-comandos.md#1-instalar-os-pré-requisitos).

```bash
git clone https://github.com/Douglasvargass/crm-loja-roupas.git
cd crm-loja-roupas

# Terminal 1 - back-end (API + banco embutido). Windows: mvnw.cmd no lugar de ./mvnw
cd crm-loja-backend
./mvnw spring-boot:test-run

# Terminal 2 - front-end
cd crm-loja-frontend
npx pnpm install
npx pnpm dev
```

Abra **http://localhost:5173** e entre com:

| Login | Pessoa | Papel | Vê |
| --- | --- | --- | --- |
| `guilherme.dower` | Guilherme Dower | Gerente | Dashboard, cadastros, vendas, estoque e Usuários |
| `douglas.vargas` | Douglas Vargas | Engenheiro(a) de Software (super admin) | Tudo |
| `julia.alcantara` | Julia Alcantara | Marketing e Desenvolvimento | Tudo (como o gerente) |
| `vitor.nunes` | Vitor Nunes | Vendedor(a) | Meu painel (vendas e meta), vendas e clientes |
| `vinicius.clemente` | Vinicius Clemente | Operações e Compras | Reposição (o que vai faltar), pedidos de compra, chamados de peça em falta, produtos e estoque |
| `vinicius.muniz` | Vinicius Muniz | Vendedor(a) | Meu painel (vendas e meta), vendas e clientes |
| `vitor.freitas` | Vitor Freitas (professor) | Cliente | Espaço do cliente: vitrine, carrinho, pedidos (senha inicial `cliente12345`, troca obrigatória) |
| `matheus.carvalho` | Matheus Carvalho (professor) | Cliente | Espaço do cliente (senha inicial `cliente12345`, troca obrigatória) |

Senha de todos: `loja12345` (só no ambiente de demonstração). O login é só `nome.sobrenome`, sem @.

O assistente de IA precisa da chave de um provedor de IA; há opções grátis (Groq, Google Gemini): veja [10-assistente-ia.md](crm-loja-docs/docs/10-assistente-ia.md).

## Documentação

| Documento | Para quê |
| --- | --- |
| [Guia de comandos](crm-loja-docs/docs/11-guia-de-comandos.md) | **Todos os comandos**: instalar, rodar, testar, banco, build, Docker, problemas comuns |
| [Roteiro de testes manuais](crm-loja-docs/docs/12-roteiro-de-testes.md) | Checklist para testar cada funcionalidade pela tela |
| [Dados de demonstração](crm-loja-docs/docs/14-dados-de-demonstracao.md) | Como são gerados os 3 meses de vendas: horário do shopping, feriados, sazonalidade e calibragem |
| [Como contribuir](CONTRIBUTING.md) | Fluxo de Git, onde mexer em cada tipo de mudança, receitas passo a passo |
| [Assistente de IA](crm-loja-docs/docs/10-assistente-ia.md) | Como ligar, como funciona, custo, como adicionar consultas |
| [Publicar online](crm-loja-docs/docs/13-publicar-online.md) | GitHub Pages, Render, custos, como funcionam as atualizações |
| [Rastreabilidade](crm-loja-docs/docs/09-rastreabilidade.md) | Cada história de usuário → tela → API → teste |
| [Histórias de usuário](crm-loja-docs/docs/03-historias-de-usuario.md) | As 36 histórias com critérios de aceitação |
| [Conferência do enunciado](crm-loja-docs/docs/15-conferencia-enunciado.md) | Cada critério do enunciado do SENAI → onde está atendido |
| [**Para continuar**](PARA-CONTINUAR.md) | O que mudou em 29/10/2026, como publicar e o que falta |
| [Nossa história](crm-loja-docs/docs/17-nossa-historia.md) | Como a Orden nasceu no SENAI Tubarão, em 2026, e quem fez o quê |
| [Processo de requisitos](crm-loja-docs/docs/16-processo-de-requisitos.md) | Como um pedido vira spec e issue: história, DADO/QUANDO/ENTÃO, regras, etiquetas, revisão |
| [Regras de negócio](crm-loja-docs/docs/04-regras-de-negocio.md) | RN01–RN25 e o ciclo de vida da venda |
| [Modelo de dados](crm-loja-docs/docs/02-modelo-de-dados.md) · [ERD](crm-loja-docs/specs/erd.md) | Tabelas, colunas e relacionamentos |
| [Contrato da API](crm-loja-docs/specs/api-contract.md) | Todas as rotas, papéis e formato de erro |
| [Arquitetura](crm-loja-docs/docs/01-arquitetura.md) | Camadas, pastas e decisões ([ADRs](crm-loja-docs/docs/decisions/)) |
| [Plano de testes](crm-loja-docs/docs/06-plano-de-testes.md) | Os 38 casos de teste (CT01–CT38) |

## Estrutura do repositório

```
crm-loja-roupas/
├── crm-loja-backend/     API Java (Spring Boot), migrations do banco, testes
├── crm-loja-frontend/    Telas em React + TypeScript
├── crm-loja-docs/        Documentação: visão, histórias, regras, testes, ADRs, specs
├── site/                 Página de apresentação (GitHub Pages)
├── Dockerfile            Imagem única (front + back) para publicar online
├── render.yaml           Configuração do Render (banco + serviço)
├── .github/workflows/    CI: testes, build, imagem Docker e publicação da página
└── crm-loja-roupas.code-workspace   abre os três projetos juntos no VS Code
```
