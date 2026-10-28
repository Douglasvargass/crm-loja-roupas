## 5. Regras de negócio

As regras ficam no Service e, sempre que possível, também no banco (CHECK, UNIQUE, trigger). Quebra de regra devolve `409` com um `code` estável, para a tela escolher a frase; validação de campo devolve `400`. O estoque só muda junto com uma movimentação, e a venda só mexe no estoque ao ser confirmada ou ao ser cancelada depois de confirmada.

| ID | Regra | Garantia no banco | Resposta |
| --- | --- | --- | --- |
| RN01 | CPF obrigatório, único, 11 dígitos, dígito verificador válido | `ux_customer_cpf`, `ck_customer_cpf_digits` | 400 `invalid_cpf` · 409 `cpf_taken` |
| RN02 | Cliente com vendas não é excluído; só inativado | `fk_sale_customer` RESTRICT | 409 `customer_has_sales` |
| RN03 | Nome de categoria único, sem diferenciar maiúsculas | `ux_category_name_ci` | 409 `category_name_taken` |
| RN04 | Categoria com produtos não é excluída | `fk_product_category` RESTRICT | 409 `category_has_products` |
| RN05 | Código de produto único | `ux_product_code` | 409 `product_code_taken` |
| RN06 | Preço > 0; estoque e estoque mínimo ≥ 0 | `ck_product_*` | 400 |
| RN07 | Produto com vendas ou movimentações além da inicial não é excluído; só inativado | FKs RESTRICT | 409 `product_has_history` |
| RN08 | Estoque nunca é editado direto; toda mudança grava `stock_movement` com saldo após | Trigger de imutabilidade | — |
| RN09 | Nenhuma movimentação deixa saldo negativo | `ck_product_stock`, `ck_stock_movement_balance` | 409 `negative_stock` |
| RN10 | Estoque baixo: `0 < stock_qty ≤ min_stock`; sem estoque: `stock_qty = 0` | — | — |
| RN11 | Venda nasce `DRAFT`; cliente é opcional | Default da coluna | — |
| RN12 | Item copia código, nome e preço do produto na inclusão | Colunas próprias em `sale_item` | — |
| RN13 | Subtotal = quantidade × preço; total = soma, sempre calculado no servidor | `ck_sale_item_subtotal` | — |
| RN14 | Só venda `DRAFT` aceita incluir, alterar ou remover itens | — | 409 `sale_not_draft` |
| RN15 | Confirmar exige ≥ 1 item e estoque para **todos**; tudo ou nada | Transação + travas | 409 `sale_empty` · `insufficient_stock` · `product_inactive` |
| RN16 | Confirmar só a partir de `DRAFT` (sem baixa duplicada) | `ux_stock_movement_sale_item` | 409 `sale_not_draft` |
| RN17 | Confirmação grava uma movimentação `SALE` por item, ligada à venda | `ck_stock_movement_sale` | — |
| RN18 | Cancelar exige motivo; a partir de `DRAFT` ou `CONFIRMED` | `ck_sale_cancelled` | 400 `reason_required` · 409 `sale_already_cancelled` |
| RN19 | Cancelar venda confirmada grava `SALE_CANCELLATION` por item e devolve o estoque | `ux_stock_movement_sale_item` | — |
| RN20 | Venda nunca é apagada | FKs RESTRICT, sem rota de DELETE | — |
| RN21 | Dashboard conta e soma só vendas `CONFIRMED` no período; canceladas à parte | — | — |
| RN22 | Produto inativo ou sem estoque não aparece na busca da tela de venda | — | — |
| RN23 | Confirmar, cancelar, ajustar estoque, inativar e mudar papel gravam `audit_event` na mesma transação | Trigger de imutabilidade | — |
| RN24 | Cada rota exige papel (seção 6); o front só esconde, quem barra é o back-end | — | 401 sem sessão · 403 sem papel |
| RN25 | O gerente não muda o próprio papel e a loja nunca fica sem gerente ativo | Contagem dentro da transação | 409 `self_role_change` · `last_admin` |

### Ciclo de vida da venda

&#91;embedded content: ciclo de vida da venda · 3 status\]

| Status atual (tela · código) | Ação | Novo status | Efeito no estoque |
| --- | --- | --- | --- |
| Aberta · `DRAFT` | Incluir, alterar ou remover item | `DRAFT` | Nenhum |
| Aberta · `DRAFT` | Confirmar | `CONFIRMED` | Baixa de cada item (`SALE`) |
| Aberta · `DRAFT` | Cancelar | `CANCELLED` | Nenhum |
| Confirmada · `CONFIRMED` | Cancelar | `CANCELLED` | Devolução de cada item (`SALE_CANCELLATION`) |
| Confirmada · `CONFIRMED` | Confirmar de novo | recusado, 409 | Nenhum |
| Cancelada · `CANCELLED` | Qualquer ação | recusado, 409 | Nenhum |

### Lógica da confirmação (pseudocódigo)

```java
@Service
@RequiredArgsConstructor
public class SaleService {

    private final SaleRepository sales;
    private final ProductRepository products;
    private final StockService stock;
    private final AuditService audit;

    // Confirma a venda e baixa o estoque. Tudo ou nada: qualquer exceção desfaz a transação inteira.
    @Transactional
    public SaleResponse confirm(Long saleId, Long actorId) {
        Sale sale = sales.findByIdForUpdate(saleId)                   // trava a linha da venda
            .orElseThrow(() -> new NotFoundException("Venda não encontrada."));

        if (sale.getStatus() != SaleStatus.DRAFT)                     // segunda confirmação para aqui
            throw new BusinessRuleException("sale_not_draft", "Esta venda já foi confirmada ou cancelada.");
        if (sale.getItems().isEmpty())
            throw new BusinessRuleException("sale_empty", "Adicione ao menos um produto antes de confirmar.");

        // Trava os produtos em ordem de id: duas vendas simultâneas não se bloqueiam mutuamente.
        List<Long> ids = sale.getItems().stream().map(i -> i.getProduct().getId()).sorted().toList();
        Map<Long, Product> locked = products.findAllByIdInForUpdate(ids).stream()
            .collect(Collectors.toMap(Product::getId, p -> p));

        // 1º valida todos os itens; só depois mexe em estoque.
        for (SaleItem item : sale.getItems()) {
            Product p = locked.get(item.getProduct().getId());
            if (!p.isActive())
                throw new BusinessRuleException("product_inactive", "O produto " + p.getName() + " está inativo.");
            if (p.getStockQty() < item.getQuantity())
                throw new BusinessRuleException("insufficient_stock", "Estoque insuficiente para " + p.getName()
                    + ": disponível " + p.getStockQty() + ", pedido " + item.getQuantity() + ".");
        }
        // 2º baixa e registra a movimentação ligada à venda.
        for (SaleItem item : sale.getItems()) {
            stock.move(locked.get(item.getProduct().getId()), -item.getQuantity(),
                       StockMovementType.SALE, sale, null, actorId);
        }
        sale.recalculateTotal();                  // total sempre calculado no servidor
        sale.markConfirmed(OffsetDateTime.now()); // CONFIRMED + confirmed_at
        audit.record(actorId, "crm", "sale_confirmed", "sale", sale.getId(),
                     Map.of("total", sale.getTotal(), "items", sale.getItems().size()));
        return SaleResponse.from(sale);
    }
}
```

```java
public interface SaleRepository extends JpaRepository<Sale, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Sale s where s.id = :id")
    Optional<Sale> findByIdForUpdate(@Param("id") Long id);
}

public interface ProductRepository extends JpaRepository<Product, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id in :ids order by p.id")
    List<Product> findAllByIdInForUpdate(@Param("ids") List<Long> ids);
}
```

`StockService.move` calcula o saldo novo, recusa saldo negativo (`negative_stock`), atualiza `product.stock_qty` e grava a `stock_movement` com `balance_after`, tudo no mesmo `@Transactional` de quem chamou. O cancelamento é simétrico: trava a venda, recusa se já estiver `CANCELLED`, devolve o estoque só se estava `CONFIRMED`, grava motivo, data, quem cancelou e o `audit_event`. Mesmo que o Service falhe em barrar uma segunda baixa, o índice `ux_stock_movement_sale_item` barra no banco.
