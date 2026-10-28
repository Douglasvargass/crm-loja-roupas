# 05-dashboard · Dashboard gerencial

## Contexto

Parte do MVP de CRM da loja de roupas (enunciado "Mini Projeto SENAI – Loja"). Histórias e regras completas em docs/03-historias-de-usuario.md e docs/04-regras-de-negocio.md.

## Requisitos

### REQ-01 · HU27 · Indicadores gerais

Como gerente, quero ver total de clientes e de produtos cadastrados.

### REQ-02 · HU28 · Vendas por período

Como gerente, quero escolher um período e ver quantidade de vendas e valor faturado.

- Atalhos: Hoje, 7 dias, 30 dias, Este mês, Personalizado. Padrão: Este mês.
- Contam só vendas `CONFIRMED` (canceladas aparecem num contador à parte).

### REQ-03 · HU29 · Mais vendidos

Como gerente, quero ver o top 5 de produtos por quantidade vendida no período, em gráfico de barras.

### REQ-04 · HU30 · Alertas de estoque

Como gerente, quero ver as listas de estoque baixo e zerado, com atalho para registrar entrada.

- Todos os indicadores vêm de consulta ao banco a cada carregamento; nenhum valor é fixo.

## Regras de negócio envolvidas

- RN21: Dashboard conta e soma só vendas `CONFIRMED` no período; canceladas à parte

## Fora de escopo

- Escopo por loja ou região (uma loja só).
- Descontos, formas de pagamento, nota fiscal, troca e devolução parcial.
