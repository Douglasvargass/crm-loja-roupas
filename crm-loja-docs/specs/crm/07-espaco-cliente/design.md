# 07-espaco-cliente · Design

| Peça | Onde |
| --- | --- |
| Banco | `V9__crm_online_store.sql` (+ `U9`): `sale.channel` (STORE/ONLINE), `customer.user_id` (único), tabela `payment` (só aprovado, 4 últimos dígitos, estorno) |
| Acesso | Módulo `loja` com papel `CUSTOMER` (`LojaModuleSeeder`); `RbacService.effectiveRoleOf` = papel do crm ou, se não houver, o da loja. O cliente não tem papel no crm, então `/crm/**` e `/admin/**` recusam |
| Regras | `modules/crm/store/StoreService`: cliente sempre pela sessão (`customer.user_id`); carrinho = venda DRAFT ONLINE; pagamento = `SaleService.confirm` + linha em `payment`; preço mudou → reprecifica e 409 `price_changed` (fica gravado: `noRollbackFor`); cartão final `0002` → 409 `payment_declined` com auditoria |
| Venda | `Sale.channel` + `markOnline()`; `SaleItem.reprice`; `SaleService.open(..., online)`; `cancel` estorna o pagamento |
| Rotas | `StoreController` (`/loja/**`, só CUSTOMER) |
| Dashboard | filtro `channel` (STORE/ONLINE) |
| Demonstração | `ClientesDemoSeeder`: `vitor.freitas` e `matheus.carvalho`, senha `cliente12345` com troca obrigatória |
| Telas | `StoreShell` (layout próprio) e `pages/store/StorePages.tsx` (Vitrine, Carrinho com pagamento, Meus pedidos, Pedido, Meus dados); `RequireStaff` e `RequireCustomer` separam os dois mundos |

**Testes:** `StoreIT` (9): só entra na loja e troca a senha; vitrine sem estoque exato; carrinho e Pix; cartão recusado e aprovado; preço que muda; pedido de outro cliente; meus dados sem nome e CPF; cancelamento estorna e dashboard filtra canal; remover item.
