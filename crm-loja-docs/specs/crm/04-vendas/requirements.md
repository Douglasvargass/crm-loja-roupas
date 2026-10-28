# 04-vendas · Vendas: carrinho, confirmação, cancelamento e histórico

## Contexto

Parte do MVP de CRM da loja de roupas (enunciado "Mini Projeto SENAI – Loja"). Histórias e regras completas em docs/03-historias-de-usuario.md e docs/04-regras-de-negocio.md.

## Requisitos

### REQ-01 · HU21 · Abrir venda

Como vendedor, quero iniciar uma venda e, se o cliente se identificar, vinculá-lo, para registrar a compra.

- Cliente é opcional; a venda sem cliente aparece como "Consumidor não identificado".
- A venda nasce com status `DRAFT` (na tela, Aberta) e total zero.

### REQ-02 · HU22 · Adicionar e remover itens

Como vendedor, quero incluir produtos e quantidades, para montar o pedido.

- Só produtos ativos com estoque > 0 aparecem na busca.
- O preço unitário é copiado do produto no momento da inclusão.
- Subtotal = quantidade × preço; total = soma dos subtotais, recalculados na hora.
- Incluir o mesmo produto de novo soma a quantidade na mesma linha.
- Quantidade acima do disponível mostra aviso imediato.

### REQ-03 · HU23 · Confirmar venda

Como vendedor, quero confirmar a venda, para concluir e baixar o estoque.

- Venda sem itens não pode ser confirmada.
- O estoque de **todos** os itens é revalidado na confirmação; se um faltar, nada é baixado e a mensagem diz qual produto falhou.
- Ao confirmar: status `CONFIRMED`, data de confirmação gravada, uma movimentação `SALE` por item.
- Confirmar de novo uma venda já confirmada é recusado (sem baixa duplicada).
- Venda confirmada não pode ter itens alterados.

### REQ-04 · HU24 · Cancelar venda

Como gerente, quero cancelar uma venda informando o motivo, para corrigir erros ou desistências.

- Motivo obrigatório.
- Venda `CONFIRMED` cancelada gera `SALE_CANCELLATION` por item e devolve o estoque.
- Venda `DRAFT` cancelada não mexe no estoque.
- Venda já `CANCELLED` não pode ser cancelada de novo.
- A venda cancelada continua visível no histórico.

### REQ-05 · HU25 · Consultar vendas

Como gerente, quero buscar vendas por número, cliente, período e status.

- Filtros combináveis; lista mostra número, data, cliente, itens, total, status.

### REQ-06 · HU26 · Ver detalhes da venda

Como vendedor, quero abrir uma venda e ver itens, preços praticados e status.

- Os preços exibidos são os da venda, mesmo que o produto tenha mudado de preço depois.
- Venda cancelada mostra data e motivo do cancelamento.

## Regras de negócio envolvidas

- RN11: Venda nasce `DRAFT`; cliente é opcional
- RN12: Item copia código, nome e preço do produto na inclusão
- RN13: Subtotal = quantidade × preço; total = soma, sempre calculado no servidor
- RN14: Só venda `DRAFT` aceita incluir, alterar ou remover itens
- RN15: Confirmar exige ≥ 1 item e estoque para **todos**; tudo ou nada
- RN16: Confirmar só a partir de `DRAFT` (sem baixa duplicada)
- RN17: Confirmação grava uma movimentação `SALE` por item, ligada à venda
- RN18: Cancelar exige motivo; a partir de `DRAFT` ou `CONFIRMED`
- RN19: Cancelar venda confirmada grava `SALE_CANCELLATION` por item e devolve o estoque
- RN20: Venda nunca é apagada
- RN22: Produto inativo ou sem estoque não aparece na busca da tela de venda

## Fora de escopo

- Escopo por loja ou região (uma loja só).
- Descontos, formas de pagamento, nota fiscal, troca e devolução parcial.
