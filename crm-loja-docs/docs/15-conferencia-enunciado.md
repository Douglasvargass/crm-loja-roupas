# Conferência do enunciado (Mini Projeto SENAI)

Cada critério do enunciado "Projeto — MVP CRM para Loja de Roupas", e onde ele está atendido no sistema.
Conferido em 29/10/2026 contra o código e os testes. **HU** = história de usuário (doc 03), **CT** = caso de
teste (doc 06), **RN** = regra de negócio (doc 04).

Legenda: ✅ atendido e coberto por teste automático · ✅ᵐ atendido, conferido pelo roteiro manual (doc 12).

## 1. Cadastro e gerenciamento de clientes

| Critério do enunciado | Onde está | Situação |
| --- | --- | --- |
| Cadastrar com nome completo, CPF, telefone, e-mail e endereço | HU01 · `POST /crm/customers` · endereço pelo CEP (ViaCEP) | ✅ CT01 |
| Consultar a lista | HU02 · `/clientes` | ✅ CT02 |
| Pesquisar por nome ou CPF | HU02 · busca sem acento e CPF com ou sem pontuação | ✅ CT02 |
| Visualizar dados completos | HU03 · `/clientes/:id` com resumo de compras | ✅ SaleIT |
| Alterar dados | HU04 · `PUT /crm/customers/{id}` | ✅ CT16 |
| Excluir respeitando vínculos com vendas | HU05 · sem vendas apaga; com vendas 409 e oferece inativar (RN02) | ✅ CT29 |
| Validar obrigatórios e impedir cadastros inválidos | RN01 · CPF com dígito verificador, e-mail, campos vazios | ✅ CT14–CT17 |

## 2. Cadastro e gerenciamento de produtos

| Critério do enunciado | Onde está | Situação |
| --- | --- | --- |
| Cadastrar com nome, descrição, categoria, tamanho, cor, código, preço e quantidade | HU09 · `POST /crm/products` (estoque inicial vira movimentação `ENTRY`) | ✅ CT04 |
| Consultar e pesquisar por nome, categoria, tamanho ou código | HU10 · filtros combináveis em `/produtos` | ✅ CatalogStockIT |
| Visualizar detalhes | HU11 · `/produtos/:id` com últimas movimentações e total vendido | ✅ CT04–CT06 |
| Alterar dados | HU12 · `PUT /crm/products/{id}` (estoque não é editável aqui) | ✅ CT30 |
| Excluir só sem vínculos a preservar | HU13 · só com "Estoque inicial"; com histórico 409 e oferece inativar (RN07) | ✅ CatalogStockIT |
| Validar obrigatórios; impedir preço ou quantidade negativos | RN06 · `CHECK` no banco + validação no Service | ✅ CT18, CT19 |
| Identificar ativos e indisponíveis | HU14 · etiquetas Disponível, Sem estoque, Estoque baixo, Inativo + filtro | ✅ CatalogStockIT |

## 3. Cadastro e gerenciamento de categorias

| Critério do enunciado | Onde está | Situação |
| --- | --- | --- |
| Cadastrar categorias (camisetas, calças, vestidos, jaquetas, acessórios) | HU07 · as 5 vêm nos dados de demonstração | ✅ CatalogStockIT |
| Consultar, alterar e excluir respeitando vínculos | HU08 · com produtos 409 `category_has_products` (RN04) | ✅ CT28 |
| Associar cada produto a uma categoria | `product.category_id NOT NULL`, categoria ativa (RN04) | ✅ CatalogStockIT |
| Filtrar produtos por categoria | HU15 · `categoryId=` | ✅ CatalogStockIT |

## 4. Controle de estoque

| Critério do enunciado | Onde está | Situação |
| --- | --- | --- |
| Exibir a quantidade disponível | HU16 · `/estoque` e lista de produtos | ✅ CT05 |
| Registrar entradas e ajustes | HU17, HU18 · `POST /crm/stock/entries` e `/adjustments` (ajuste com motivo) | ✅ CT05, CT06 |
| Atualizar automaticamente após venda confirmada | HU23 · uma movimentação `SALE` por item (RN17) | ✅ CT08 |
| Impedir venda acima do estoque | RN15 · tudo ou nada na confirmação | ✅ CT22, CT23 |
| Consultar estoque baixo | HU19 · `0 < estoque ≤ mínimo` (RN10) | ✅ DashboardIT |
| Identificar sem estoque | HU19 · `estoque = 0` | ✅ DashboardIT |
| Histórico com data, produto, quantidade e tipo | HU20 · `stock_movement` imutável por trigger, com saldo após | ✅ CT38 |
| Filtrar por disponibilidade e nível de estoque | HU14 · filtro Todos / Disponíveis / Sem estoque / Estoque baixo / Inativos | ✅ CatalogStockIT |

## 5. Registro e gerenciamento de vendas

| Critério do enunciado | Onde está | Situação |
| --- | --- | --- |
| Venda associada a cliente cadastrado, quando identificado | HU21 · cliente opcional ("Consumidor não identificado") | ✅ CT07 |
| Adicionar um ou mais produtos | HU22 · carrinho | ✅ CT07 |
| Informar a quantidade de cada produto | HU22 · mesmo produto de novo soma na linha | ✅ CT07 |
| Calcular subtotal e total automaticamente | RN13 · no servidor, `CHECK subtotal = preço × qtd` | ✅ CT07 |
| Registrar data, itens, quantidades, preços praticados e status | tabelas `sale` e `sale_item` (RN11, RN12) | ✅ CT08 |
| Consultar por número, cliente, data ou status | HU25 · `GET /crm/sales?id=&customerId=&from=&to=&status=` | ✅ SaleIT |
| Visualizar detalhes | HU26 · `/vendas/:id` | ✅ CT30 |
| Histórico de compras do cliente | HU06 · `GET /crm/customers/{id}/sales` | ✅ CT09 |
| Impedir confirmação com estoque insuficiente | RN15 · 409 `insufficient_stock` dizendo qual produto | ✅ CT22, CT23 |
| Atualizar estoque sem movimentações duplicadas | RN16 · trava de linha + índice único `(sale, product, type)`; duas threads | ✅ CT24, CT25 |
| Cancelar registrando motivo e devolvendo ao estoque quando aplicável | HU24 · RN18, RN19 (confirmada estorna; aberta não mexe) | ✅ CT11, CT26 |
| Preservar histórico e valores praticados mesmo com produto alterado | RN12 · `sale_item` copia código, nome e preço; RN20 venda nunca é apagada | ✅ CT30 |

## 6. Dashboard gerencial

| Critério do enunciado | Onde está | Situação |
| --- | --- | --- |
| Total de clientes cadastrados | HU27 · cartão "Clientes cadastrados" | ✅ CT10 |
| Total de produtos cadastrados | HU27 · cartão "Produtos cadastrados" | ✅ CT10 |
| Quantidade de vendas num período selecionado | HU28 · Hoje, 7 dias, 30 dias, Este mês, Personalizado | ✅ CT10, virada de período |
| Valor total das vendas confirmadas no período | HU28 · só `CONFIRMED` (RN21); canceladas à parte | ✅ CT10, CT12 |
| Produtos mais vendidos | HU29 · top 5 por quantidade, gráfico clicável | ✅ CT10 |
| Produtos com estoque baixo | HU30 · lista com atalho "Registrar entrada" | ✅ DashboardIT |
| Produtos sem estoque | HU30 | ✅ DashboardIT |
| Indicadores atualizam após vendas, cancelamentos e alterações | consulta ao banco a cada carregamento; o front invalida o cache após toda mutação | ✅ CT10, CT12 |

Além do pedido: comparação com o período anterior, ticket médio, faturamento por dia, vendas por categoria,
filtros cruzados ao clicar nos gráficos e **filtro por vendedor(a)** (29/10/2026).

## Critérios gerais

| Critério | Como é atendido | Situação |
| --- | --- | --- |
| CRUD completo | Clientes, produtos e categorias com criar, listar, detalhe, alterar e excluir/inativar respeitando vínculos | ✅ |
| Validação de dados | `@Valid` nos DTOs + regras no Service + `CHECK`/`UNIQUE` no banco; erro `{error, code}` com frase em português | ✅ |
| Persistência | PostgreSQL 16; no modo sem Docker os dados ficam em `.data/postgres` entre execuções | ✅ᵐ CT13 |
| Relacionamentos | FKs `RESTRICT` entre venda, item, produto, categoria e cliente; ERD em `specs/erd.md` | ✅ |
| Dashboard com dados reais | `DashboardService` só faz SQL sobre as tabelas; nenhum valor fixo (teste CT10/CT12 prova a variação) | ✅ |
| Navegação | Menu lateral por papel, tela inicial por papel, 404 amigável | ✅ᵐ |
| Feedback ao usuário | Avisos verdes/vermelhos de 4 s, confirmação em exclusões e cancelamentos, erro ao lado do campo | ✅ᵐ |
| Pesquisa e filtros | Clientes (nome/CPF), produtos (nome, código, categoria, tamanho, situação), vendas (número, cliente, período, status), estoque (produto, tipo, período) | ✅ |
| Consistência dos dados | Transação + trava de linha; estoque só muda pelo `StockService.move`; histórico imutável por trigger | ✅ CT24, CT25, CT38 |
| Testes | 116 automáticos (válidos, inválidos e exceção) + roteiro manual com 109 itens | ✅ |
| Apresentação | Roteiro de 10 passos no doc 08, com 4 provas de robustez | ✅ᵐ |

## Entregáveis

| Entregável | Onde |
| --- | --- |
| Aplicação web funcional | https://crm-loja.onrender.com (demo) ou `./mvnw spring-boot:test-run` + `pnpm dev` |
| CRUD completo das entidades | módulos `customer`, `product`, `category` (+ `sale`, `stock`) |
| Dashboard com indicadores funcionais | `/` (gerente e equipe técnica) |
| Dados de teste | `DevDataSeeder` + `HistoricoVendasSeeder` (3 meses de vendas realistas, doc 14), metas, pedidos de compra, contas dos professores e o agente que fecha cada dia (doc 07) |
| Código organizado e identificado | monólito modular `core/` + `modules/crm/`, um pacote por assunto; todo arquivo de código tem comentário explicando o porquê (conferido em 29/10/2026) |
| Documentação com instruções para executar | `README.md`, doc 11 (guia de comandos), doc 07 |
| Demonstração prática | doc 08 (roteiro) e doc 12 (checklist) |

## O que vai além do enunciado

Login com 3 papéis de balcão + 2 técnicos, troca de senha obrigatória, gestão de usuários, auditoria, Talita
(assistente de IA com os dados reais), publicação online automática, **Meu painel do vendedor e metas** (spec 06)
os chamados de peça em falta (spec 09), o agente de vendas da demonstração (spec 08), o espaço do cliente (spec 07), o "Ver como" (spec 10), a exportação para planilha (HU32) e a Reposição com pedidos de compra (spec 11). Nada disso substitui um item obrigatório;
está tudo em cima dos módulos pedidos.

## Observações sobre o enunciado

- "Relacionamentos: animais e tutores" é o exemplo do outro projeto da turma; aqui vale vendas e produtos.
- A numeração pula de "6. Dashboard" para "3. Critérios gerais"; é só formatação do documento.
