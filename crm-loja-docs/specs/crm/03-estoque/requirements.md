# 03-estoque · Estoque: saldo, entradas, ajustes, alertas e histórico

## Contexto

Parte do MVP de CRM da loja de roupas (enunciado "Mini Projeto SENAI – Loja"). Histórias e regras completas em docs/03-historias-de-usuario.md e docs/04-regras-de-negocio.md.

## Requisitos

### REQ-01 · HU16 · Ver saldo

Como estoquista, quero ver a quantidade disponível de cada produto, para saber o que repor.

### REQ-02 · HU17 · Registrar entrada

Como estoquista, quero lançar a chegada de mercadoria, para aumentar o saldo.

- Quantidade deve ser maior que zero.
- Gera movimentação `ENTRY` com data, quantidade, saldo após e observação (ex.: nota do fornecedor).

### REQ-03 · HU18 · Registrar ajuste

Como estoquista, quero corrigir o saldo (perda, avaria, inventário), para que o sistema bata com a prateleira.

- Quantidade com sinal (+ ou −), diferente de zero, com motivo obrigatório.
- Ajuste que deixaria o saldo negativo é recusado: "Saldo atual 3; ajuste de −5 deixaria estoque negativo".

### REQ-04 · HU19 · Estoque baixo e zerado

Como gerente, quero listar produtos abaixo do mínimo e sem estoque, para planejar compras.

- Baixo = `0 < estoque ≤ estoque_minimo`; zerado = `estoque = 0`.

### REQ-05 · HU20 · Histórico de movimentações

Como gerente, quero ver todas as movimentações com filtros por produto, tipo e período, para auditar o estoque.

- Cada linha: data/hora, produto, tipo, quantidade, saldo após, venda de origem (se houver), observação.

## Regras de negócio envolvidas

- RN08: Estoque nunca é editado direto; toda mudança grava `stock_movement` com saldo após
- RN09: Nenhuma movimentação deixa saldo negativo
- RN10: Estoque baixo: `0 < stock_qty ≤ min_stock`; sem estoque: `stock_qty = 0`

## Fora de escopo

- Escopo por loja ou região (uma loja só).
- Descontos, formas de pagamento, nota fiscal, troca e devolução parcial.
