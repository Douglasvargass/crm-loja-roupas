# 11-reposicao-pedidos-compra · Design

| Peça | Onde |
| --- | --- |
| Tabelas | `V10__crm_purchase_order.sql` (+ `U10`): `purchase_order` (status em texto com CHECK; fechado exige data, cancelado exige motivo) e `purchase_order_item` (uma linha por peça, 1 a 9999); índice `ix_sale_item_product` para a soma das vendas por peça |
| Conta de cada peça | `modules/crm/planning/Coverage.java`: função pura (sem banco e sem relógio), coberta por `CoverageTest` |
| Painel | `StockPlanningService` (uma consulta com 4 subconsultas: vendido em 30 dias, última venda, a caminho, chamados) e `StockPlanningController`: `GET /crm/stock/planning?horizon=` |
| Pedidos | `modules/crm/purchase/`: `PurchaseOrderService` (SQL direto, como os chamados) e `PurchaseOrderController`: `GET /crm/purchase-orders`, `GET /{id}`, `POST`, `POST /{id}/receive`, `/reschedule`, `/cancel` |
| Receber | `PurchaseOrderService.receive` chama `StockService.entry` para cada peça na mesma transação (a entrada já fecha os chamados, spec crm/09) e grava a mudança antes de contar os chamados fechados (`em.flush()`) |
| Permissões | Ver e receber: G E (Operações e Compras vale por E). Registrar, mudar data, cancelar: G e `PURCHASING`. Na tela: `can.replenishment`, `can.buy`, `can.receivePurchase` |
| Talita | `previsao_estoque` e `pedidos_compra` (papéis G E); prompt com as regras e onde fica cada coisa |
| Demonstração | `PedidosCompraDemoSeeder` (@Order 55, dev e demo, só com a tabela vazia): 3 pedidos do Vinicius Clemente com as peças que acabam primeiro; um chega em 3 dias, outro em 10, e um está 2 dias atrasado |
| Telas | `ReplenishmentPage` (`/reposicao`, tela inicial de Operações e Compras): 6 cartões, filtros, busca, marcar peças e "Pedir marcadas". `PurchaseOrdersPage` (`/pedidos-compra`): abas, novo pedido (vem montado da Reposição pela URL `?novo=id:qtd,...`), receber, mudar data, cancelar |
| Pendências ao entrar | Pedidos atrasados e peças que acabam em 7 dias (G e E) |

**Por que SQL direto e não entidade JPA:** o pedido e os itens são gravados juntos e toda leitura precisa do nome
das peças e das pessoas. É o mesmo caminho do `StockRequestService`, e o `ddl-auto=validate` não precisa conhecer
as tabelas novas.

**Testes:** `CoverageTest` (7, a conta caso a caso) e `ReplenishmentIT` (6): painel calcula e desconta o que está a
caminho; peça parada e dinheiro parado; registrar e validações; receber dá entrada, fecha o chamado e não recebe duas
vezes; mudar data, cancelar, auditoria e filtro de atrasados; Talita. `AssistantIT` passa a contar 15 ferramentas
para o gerente e 16 para a equipe técnica.
