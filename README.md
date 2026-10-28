# CRM Loja de Roupas

[![backend](https://github.com/Douglasvargass/crm-loja-roupas/actions/workflows/backend.yml/badge.svg)](https://github.com/Douglasvargass/crm-loja-roupas/actions/workflows/backend.yml)
[![frontend](https://github.com/Douglasvargass/crm-loja-roupas/actions/workflows/frontend.yml/badge.svg)](https://github.com/Douglasvargass/crm-loja-roupas/actions/workflows/frontend.yml)
[![docker](https://github.com/Douglasvargass/crm-loja-roupas/actions/workflows/docker.yml/badge.svg)](https://github.com/Douglasvargass/crm-loja-roupas/actions/workflows/docker.yml)

**Sistema online (demonstração):** https://crm-loja.onrender.com · login `gerente@loja.local` / `loja12345` (plano grátis: o primeiro acesso pode levar até 1 minuto para "acordar")
**Página do projeto:** https://douglasvargass.github.io/crm-loja-roupas/ · **Publicar sua própria cópia:** [![Deploy to Render](https://render.com/images/deploy-to-render-button.svg)](https://render.com/deploy?repo=https://github.com/Douglasvargass/crm-loja-roupas) ([passo a passo](crm-loja-docs/docs/13-publicar-online.md))

Sistema web para uma loja de roupas organizar clientes, produtos, estoque e vendas, com painel de indicadores e um **assistente de IA** que responde dúvidas consultando os dados reais da loja. MVP feito para o Mini Projeto SENAI.

| Camada | Tecnologia |
| --- | --- |
| Back-end | **Java 21** + Spring Boot 3.5 (API REST) |
| Banco de dados | **PostgreSQL 16**, tabelas criadas por migrations Flyway |
| Front-end | React 19 + TypeScript + Vite + Tailwind CSS |
| Assistente de IA | Claude (Anthropic), via SDK oficial em Java |
| Testes | JUnit 5 + MockMvc contra um PostgreSQL real (60 testes) |

## O que o sistema faz

- **Clientes:** cadastro com CPF validado e endereço pelo CEP, busca sem acento, histórico de compras.
- **Categorias e produtos:** tamanho, cor, código, preço; etiquetas *Disponível*, *Sem estoque*, *Estoque baixo* e *Inativo*.
- **Estoque:** entradas, ajustes com motivo, alertas de reposição e histórico permanente de cada movimento.
- **Vendas:** carrinho com total calculado no servidor, confirmação "tudo ou nada" que baixa o estoque, cancelamento com motivo que devolve as peças.
- **Dashboard:** vendas e faturamento por período, comparação com o período anterior, mais vendidos, vendas por categoria, estoque baixo e zerado.
- **Acesso:** login com 3 perfis (Gerente, Vendedor, Estoquista), troca de senha obrigatória no primeiro acesso, gestão de usuários.
- **Assistente IA:** perguntas como "quanto vendemos este mês?" ou "o que preciso repor?", respondidas com dados do banco e respeitando o perfil de quem pergunta.

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

| Login | Senha | Perfil |
| --- | --- | --- |
| `gerente@loja.local` | `loja12345` | Gerente: vê tudo |
| `vendedor@loja.local` | `loja12345` | Vendedor(a): clientes e vendas |
| `estoquista@loja.local` | `loja12345` | Estoquista: produtos e estoque |

O assistente de IA precisa de uma chave da Anthropic: veja [10-assistente-ia.md](crm-loja-docs/docs/10-assistente-ia.md).

## Documentação

| Documento | Para quê |
| --- | --- |
| [Guia de comandos](crm-loja-docs/docs/11-guia-de-comandos.md) | **Todos os comandos**: instalar, rodar, testar, banco, build, Docker, problemas comuns |
| [Roteiro de testes manuais](crm-loja-docs/docs/12-roteiro-de-testes.md) | Checklist para testar cada funcionalidade pela tela |
| [Como contribuir](CONTRIBUTING.md) | Fluxo de Git, onde mexer em cada tipo de mudança, receitas passo a passo |
| [Assistente de IA](crm-loja-docs/docs/10-assistente-ia.md) | Como ligar, como funciona, custo, como adicionar consultas |
| [Publicar online](crm-loja-docs/docs/13-publicar-online.md) | GitHub Pages, Render, custos, como funcionam as atualizações |
| [Rastreabilidade](crm-loja-docs/docs/09-rastreabilidade.md) | Cada história de usuário → tela → API → teste |
| [Histórias de usuário](crm-loja-docs/docs/03-historias-de-usuario.md) | As 35 histórias com critérios de aceitação |
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
