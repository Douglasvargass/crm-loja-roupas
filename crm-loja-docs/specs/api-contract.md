# Contrato da API

Fonte da verdade da interface. Mudou rota, DTO ou papel → atualizar este arquivo na MESMA mudança.

## 6. API REST

O back-end expõe as rotas **sem** prefixo; o front chama `/api/...` e o proxy do Vite (e o nginx, se houver servidor) tira o `/api` antes de repassar. Assim tela e API ficam na mesma origem e o cookie httpOnly funciona sem CORS. O contrato completo mora em `crm-loja-docs/specs/api-contract.md` e muda junto com qualquer rota ou DTO.

**Formato de erro, sempre:** `{"error": "<frase em português>", "code": "<código estável, quando houver>"}`. Códigos HTTP: 200, 201 (criado), 204 (sem corpo), 400 (validação), 401 (sem sessão), 403 (sem papel), 404, 409 (regra de negócio), 500 (logado, com `"Erro interno"` para a tela).

**Papéis:** G = Gerente (admin do módulo), V = Vendedor, E = Estoquista, C = Cliente (módulo loja, sem acesso ao crm). Os papéis técnicos `DEVELOPER` (Engenheiro(a) de Software) e `MARKETING_DEV` (Marketing e Desenvolvimento) valem por G + V + E. Operações e Compras (`PURCHASING`) vale por E. Super admin passa em tudo. Login no padrão `nome.sobrenome` (e-mail completo também é aceito).

| Método | Rota | O que faz | Papéis | HU |
| --- | --- | --- | --- | --- |
| POST | `/auth/login` | Entra; grava cookies `access_token` e `refresh_token` | público | HU33 |
| POST | `/auth/refresh` | Troca o refresh por um par novo (rotação) | cookie | HU33 |
| POST | `/auth/logout` | Revoga o refresh e limpa os cookies | logado | HU33 |
| POST | `/auth/change-password` | Troca a senha e revoga as outras sessões | logado | HU34 |
| GET | `/auth/me` | Perfil e papel no módulo crm | logado | HU33 |
| GET | `/crm/customers?search=&active=&page=` | Lista e busca por nome ou CPF | G V | HU02 |
| GET | `/crm/customers/{id}` | Detalhe com resumo de compras | G V | HU03 |
| POST | `/crm/customers` | Cadastra | G V | HU01 |
| PUT | `/crm/customers/{id}` | Altera | G V | HU04 |
| DELETE | `/crm/customers/{id}` | Exclui se não tiver vendas (senão 409) | G | HU05 |
| PATCH | `/crm/customers/{id}/deactivate` | Inativa | G | HU05 |
| GET | `/crm/customers/{id}/sales` | Histórico de compras | G V | HU06 |
| GET | `/crm/categories` | Lista com quantidade de produtos | G V E | HU08 |
| POST · PUT | `/crm/categories[/{id}]` | Cadastra ou altera | G | HU07, HU08 |
| DELETE | `/crm/categories/{id}` | Exclui se não tiver produtos | G | HU08 |
| GET | `/crm/products?name=&categoryId=&size=&code=&availability=` | Lista com filtros combináveis | G V E | HU10, HU14, HU15 |
| GET | `/crm/products/{id}` | Detalhe com últimas movimentações | G V E | HU11 |
| POST | `/crm/products` | Cadastra (estoque inicial gera `ENTRY`) | G E | HU09 |
| PUT | `/crm/products/{id}` | Altera (ignora estoque; preço novo vai para a auditoria) | G E | HU12 |
| DELETE · PATCH | `/crm/products/{id}` · `/deactivate` | Exclui sem histórico ou inativa | G | HU13 |
| GET | `/crm/stock/movements?productId=&type=&from=&to=` | Histórico | G E | HU20 |
| POST | `/crm/stock/entries` | `{productId, quantity, reason?}` | G E | HU17 |
| POST | `/crm/stock/adjustments` | `{productId, quantity (±), reason}` | G E | HU18 |
| GET | `/crm/stock/alerts` | Estoque baixo e zerado | G E | HU19 |
| GET | `/crm/sales?id=&customerId=&from=&to=&status=` | Consulta | G V | HU25 |
| GET | `/crm/sales/{id}` | Detalhe com itens e preços praticados | G V | HU26 |
| POST | `/crm/sales` | Abre venda `{customerId?}` | G V | HU21 |
| POST · PUT · DELETE | `/crm/sales/{id}/items[/{itemId}]` | Inclui, altera quantidade, remove item | G V | HU22 |
| POST | `/crm/sales/{id}/confirm` | Confirma e baixa estoque | G V | HU23 |
| POST | `/crm/sales/{id}/cancel` | `{reason}`; estorna se confirmada | G | HU24 |
| GET | `/crm/dashboard?from=&to=&categoryId=&productId=&day=&sellerId=` | Todos os indicadores numa chamada; `sellerId` = quem abriu a venda (a resposta traz `sellers` com as opções e `selected.sellerId/sellerName`) | G | HU27–HU30 |
| GET · POST · PATCH | `/admin/users?module=crm` | Lista, cria, muda papel, inativa | G | HU35 |
| POST | `/admin/users/{id}/reset-password?module=crm\|loja` | Senha provisória nova (troca obrigatória), sessões encerradas; devolve `{user, provisionalPassword}`; 409 `self_reset` | G | HU35 |
| GET | `/crm/assistant/status` | `{enabled, model}`: se o assistente de IA está ligado | G V E | HU36 |
| POST | `/crm/assistant/chat` | `{conversationId?, message}` → `{conversationId, answer, toolsUsed}`; 409 `assistant_disabled` sem chave | G V E | HU36 |
| PATCH | `/crm/customers/{id}/activate` · `/crm/products/{id}/activate` | Reativa cliente ou produto inativo | G | HU05, HU13 |
| GET | `/crm/my-panel?month=aaaa-mm` | Painel de **quem está logado**: meta, vendido, %, falta, ritmo, projeção, hoje, vendas por dia, mais vendidos, últimas vendas. Sem parâmetro de vendedor. Mês futuro: 400 `invalid_month` | V | HU37 |
| GET | `/crm/targets?month=aaaa-mm` | Quadro de metas: cada vendedor ativo com meta, vendido, %, projeção, falta, situação; totais da equipe; `editable` | G | HU38, HU39 |
| GET | `/crm/requests?status=&page=` | Chamados de peça em falta; vendedor recebe só os que abriu | G V E | HU47 |
| POST | `/crm/requests` | `{description, productId?, size?, color?, quantity, customerId?}`; 409 `product_in_stock` se a peça tem estoque | G V | HU47 |
| POST | `/crm/requests/{id}/resolve` · `/cancel` | `{note}`; resolver: G E; cancelar: G E ou quem abriu (outro vendedor: 404); 409 `request_closed` | G V E | HU48 |
| GET · POST | `/crm/requests/alerts` · `/alerts/seen` | Sino: abertos (G E) ou novidades dos meus (V); marcar como visto | G V E | HU49 |
| GET | `/auth/view-as/targets` | Pessoas que podem ser vistas (ativas, com papel, menos eu) | G | HU50 |
| POST · DELETE | `/auth/view-as` | `{userId}` liga o "Ver como" (cookie `view_as`, 30 min); DELETE sai. Durante o modo, toda alteração recebe 403 `read_only_view_as` e `/auth/me` traz `viewAs` | G (sair: todos) | HU50 |
| GET · POST | `/loja/requests` | Peças que o cliente pediu · pedir peça `{description, size?, color?, quantity}` (vira chamado ligado ao cliente; peça com estoque 409 `product_in_stock`) | C | HU57 |
| GET · POST | `/loja/assistant/status` · `/loja/assistant/chat` | Thalita do cliente: `{enabled}` · `{conversationId?, message}` | C | HU57 |
| GET | `/crm/stock/planning?horizon=` | Reposição: `{today, windowDays, horizonDays, summary, items[]}`; cada item com `dailyAvg`, `daysLeft`, `runOutDate`, `incoming`, `nextArrival`, `openRequestsQty`, `suggestedQty`, `status` (OUT, CRITICAL, ATTENTION, OK, STALE, NEW), `needsOrder`, `covered`, `arrivesAfterRunOut`. `horizon` de 7 a 90 (padrão 30); fora disso 400 `invalid_horizon` | G E | HU51 |
| GET | `/crm/purchase-orders?status=&late=&page=` · `/{id}` | Pedidos de compra com as peças; `late=true` só os abertos com a data vencida | G E | HU52 |
| POST | `/crm/purchase-orders` | `{supplier, expectedDate, note?, items:[{productId, quantity}]}`; 400 `date_in_past`, `date_too_far`, `duplicated_product`, `too_many_items`; 409 `product_inactive` | G (`PURCHASING`) | HU52 |
| POST | `/crm/purchase-orders/{id}/ship` | `{invoiceNumber?}`: saiu do fornecedor → IN_TRANSIT; 409 `wrong_step` | G (`PURCHASING`) | HU55 |
| POST | `/crm/purchase-orders/{id}/arrive` | Chegou na loja → CHECKING (em conferência); 409 `wrong_step` | G E | HU55 |
| POST | `/crm/purchase-orders/{id}/receive` | `{invoiceNumber, bonusInvoiceNumber?, note?, items?:[{productId, receivedQuantity}]}` (NF obrigatória; vale a do envio; peça sem contagem = bateu): entrada de cada peça, o extra pela nota de bonificação, fecha os chamados que cobre; devolve `{order, requestsResolved, bonusUnits}`; 400 `invoice_required` · `bonus_invoice_required`; 409 `short_delivery` · `order_closed` | G E | HU53 |
| POST | `/crm/purchase-orders/{id}/return` | `{note, items?}`: a nota volta ao fornecedor; nada entra; pedido volta a OPEN (`returnCount`+1); 409 `wrong_step` | G E | HU55 |
| POST | `/crm/purchase-orders/{id}/reschedule` · `/cancel` | `{expectedDate, note}` · `{note}`, motivo obrigatório; 400 `same_date`; 409 `order_closed` | G (`PURCHASING`) | HU54 |
| GET | `/crm/export/customers.csv` · `products.csv` · `sales.csv` | Exporta a lista com os mesmos filtros da tela (separador `;`, UTF-8 com BOM) | G | HU32 |
| GET | `/loja/products?name=&categoryId=&size=&page=` · `/loja/categories` | Vitrine: só ativo com estoque, sem estoque exato (`lastUnits`). O gerente também lê, para a Prévia da loja | C G | HU42 |
| GET · POST · PUT · DELETE | `/loja/cart` · `/loja/cart/items[/{itemId}]` | Carrinho do cliente da sessão (venda aberta online) | C | HU43 |
| POST | `/loja/checkout` | `{method: PIX\|CARD, cardNumber?}`; 409 `price_changed` (carrinho atualizado), `payment_declined` (final 0002), `cart_empty`, `insufficient_stock` | C | HU44 |
| GET | `/loja/orders` · `/loja/orders/{id}` | Meus pedidos (online e balcão); de outro cliente: 404 | C | HU45 |
| GET · PUT | `/loja/me` | Meus dados; nome e CPF não mudam | C | HU45 |
| GET | `/crm/demo-agent` | Diário do agente de demonstração: `{enabled, aiEnabled, days[14]}`. Fora dos perfis dev/demo, `enabled=false` | G | HU46 |
| PUT | `/crm/targets/{sellerId}?month=aaaa-mm` | `{amount}` define ou corrige a meta. 409 `month_closed` · `month_too_far` · `not_a_seller` · `target_conflict`; 400 valor ≤ 0 ou mais de 2 casas | G | HU38 |
| GET | `/crm/forecast?months=` | Previsão de vendas: `{today, historyFrom, historyDays, model, periods[], days[]}`. `periods`: NEXT_7, NEXT_30, THIS_MONTH (com `realized`) e um por mês (`months` de 1 a 12, padrão 3), cada um com `revenue`, `low`, `high`, `sales`. `model`: `weekday[]`, `payday`, `holiday`, `monthSeasonality`, `events[]` (`learned`), `accuracy`. `days`: 28 realizados (`actual`) + 60 previstos. 400 `invalid_months`; 409 `not_enough_history` | G | HU58 |
| GET | `/crm/targets/suggestions?month=aaaa-mm&growth=` | Metas sugeridas: `{month, monthForecast, growthPercent, basisFrom, basisTo, storeRevenue, rows[]}`; cada linha com `revenue` e `sharePercent` (90 dias), `currentTarget`, `suggested` (null + `reason` sem venda). 400 `invalid_growth` · `invalid_month`; 409 `month_closed` · `not_enough_history` | G | HU59 |
| GET | `/crm/products/{id}/images` | Galeria do produto: `[{id, view (FRONT, BACK, SIDE, DETAIL, OTHER), position, url, source, license, illustration}]` | todos | HU60 |
| POST | `/crm/products/{id}/images` | multipart `file` + `view`: envia foto. 400 `invalid_image` · `image_too_large`; 409 `too_many_images` | G | HU60 |
| DELETE | `/crm/products/{id}/images/{imageId}` · POST `…/{imageId}/cover` | Exclui a foto · faz dela a capa | G | HU60 |
| GET | `/media/images/{imageId}` · `/media/products/{id}/cover` | A imagem em si (sem login, com cache) · a capa do produto (404 sem foto) | público | HU60 |
| GET · POST | `/crm/coupons` · PATCH `/crm/coupons/{id}/active?value=` | Lista e cria cupons `{code, kind (PERCENT, FIXED), value, minOrder, validFrom, validUntil, maxUses}` · liga ou desliga | G | HU61 |
| POST · DELETE | `/crm/sales/{id}/coupon` | `{code}` aplica ou troca · tira o cupom da venda aberta; a venda traz `subtotal`, `discount`, `couponCode` | G V | HU61 |
| POST · DELETE | `/loja/cart/coupon` | O mesmo no carrinho do cliente | C | HU61 |
| POST | `/loja/signup` | Cria cliente + conta `{fullName, cpf, email, phone, zipCode, street, number, complement, district, city, state, password, acceptTerms}` → `{login}`. 400 `terms_required` · `weak_password` · `invalid_signup`; 409 `email_in_use` · `cpf_registered` · `cpf_has_account` | público | HU62 |
| POST | `/auth/forgot-password` · `/auth/reset-password` | `{email}` → mensagem igual sempre · `{token, password}`. 409 `invalid_reset_link` | público | HU63 |

### Exemplo de resposta do dashboard

DTOs em inglês, 1:1 com o `dto.ts` do front; o `adapters.ts` traduz para a tela.

```json
{
  "totalCustomers": 148,
  "totalProducts": 86,
  "period": { "from": "2026-09-27", "to": "2026-10-26" },
  "confirmedSales": 151,
  "revenue": 40126.60,
  "cancelledSales": 3,
  "topProducts": [ { "productId": 7, "name": "Camiseta Básica", "quantity": 59 } ],
  "lowStock":    [ { "productId": 12, "name": "Calça Jeans Skinny 40", "stockQty": 2, "minStock": 4 } ],
  "outOfStock":  [ { "productId": 3, "name": "Jaqueta Couro Sintético M" } ]
}
```

Os números ilustram só o formato; na aplicação vêm destas consultas:

```sql
-- Vendas e faturamento no período (só confirmadas)
SELECT count(*), coalesce(sum(total), 0) FROM sale
 WHERE status = 'CONFIRMED' AND confirmed_at >= :from AND confirmed_at < :to_exclusive;

-- Mais vendidos
SELECT si.product_id, si.product_name, sum(si.quantity) AS qty
  FROM sale_item si JOIN sale s ON s.id = si.sale_id
 WHERE s.status = 'CONFIRMED' AND s.confirmed_at >= :from AND s.confirmed_at < :to_exclusive
 GROUP BY si.product_id, si.product_name ORDER BY qty DESC LIMIT 5;

-- Estoque baixo / sem estoque
SELECT * FROM product WHERE is_active AND stock_qty > 0 AND stock_qty <= min_stock;
SELECT * FROM product WHERE is_active AND stock_qty = 0;
```

O fim do período é exclusivo (`< dia seguinte 00:00` no fuso `America/Sao_Paulo`), para a venda das 23:59 não ficar de fora.
