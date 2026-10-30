package br.senai.crmloja.modules.crm.sale;

import br.senai.crmloja.core.audit.AuditService;
import br.senai.crmloja.core.config.AppProperties;
import br.senai.crmloja.core.http.BusinessRuleException;
import br.senai.crmloja.core.http.InvalidInputException;
import br.senai.crmloja.core.http.NotFoundException;
import br.senai.crmloja.core.http.PageResponse;
import br.senai.crmloja.modules.crm.customer.Customer;
import br.senai.crmloja.modules.crm.customer.CustomerRepository;
import br.senai.crmloja.modules.crm.product.Product;
import br.senai.crmloja.modules.crm.product.ProductRepository;
import br.senai.crmloja.modules.crm.sale.dto.SaleResponse;
import br.senai.crmloja.modules.crm.sale.dto.SaleSummary;
import br.senai.crmloja.modules.crm.stock.StockMovementType;
import br.senai.crmloja.modules.crm.stock.StockService;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SaleService {

    private final SaleRepository sales;
    private final ProductRepository products;
    private final CustomerRepository customers;
    private final StockService stock;
    private final AuditService audit;
    private final AppProperties props;
    private final JdbcTemplate jdbc;
    private final br.senai.crmloja.modules.crm.coupon.CouponService coupons;

    public SaleService(SaleRepository sales, ProductRepository products, CustomerRepository customers,
                       StockService stock, AuditService audit, AppProperties props, JdbcTemplate jdbc,
                       br.senai.crmloja.modules.crm.coupon.CouponService coupons) {
        this.jdbc = jdbc;
        this.coupons = coupons;
        this.sales = sales;
        this.products = products;
        this.customers = customers;
        this.stock = stock;
        this.audit = audit;
        this.props = props;
    }

    // HU21: venda nasce aberta (DRAFT), total zero, cliente opcional.
    @Transactional
    public SaleResponse open(Long customerId, Long actorId) {
        return open(customerId, actorId, false);
    }

    // Espaço do cliente (spec crm/07): o carrinho é uma venda aberta com canal ONLINE e o cliente já vinculado.
    @Transactional
    public SaleResponse open(Long customerId, Long actorId, boolean online) {
        Customer customer = null;
        if (customerId != null) {
            customer = customers.findById(customerId)
                    .orElseThrow(() -> new NotFoundException("Cliente não encontrado."));
            if (!customer.isActive()) {
                throw new BusinessRuleException("customer_inactive", "Este cliente está inativo e não pode comprar.");
            }
        }
        Sale sale = new Sale(customer, actorId);
        if (online) sale.markOnline();
        return SaleResponse.from(sales.save(sale));
    }

    // HU22: o mesmo produto de novo soma na mesma linha. O estoque é conferido aqui para avisar
    // cedo, mas quem garante é a confirmação, que trava os produtos.
    @Transactional
    public SaleResponse addItem(Long saleId, Long productId, int quantity) {
        Sale sale = draft(saleId);
        Product product = products.findById(productId)
                .orElseThrow(() -> new NotFoundException("Produto não encontrado."));
        if (!product.isActive() || product.getStockQty() == 0) {
            throw new BusinessRuleException("product_unavailable",
                    "O produto " + product.getName() + " não está disponível para venda.");
        }
        var existing = sale.itemOf(productId);
        int newQty = existing.map(SaleItem::getQuantity).orElse(0) + quantity;
        checkStock(product, newQty);
        if (existing.isPresent()) {
            existing.get().setQuantity(newQty);
            sale.recalculateTotal();
        } else {
            sale.addItem(new SaleItem(sale, product, quantity));
        }
        sales.flush();
        return SaleResponse.from(sale);
    }

    @Transactional
    public SaleResponse updateItem(Long saleId, Long itemId, int quantity) {
        Sale sale = draft(saleId);
        SaleItem item = item(sale, itemId);
        checkStock(item.getProduct(), quantity);
        item.setQuantity(quantity);
        sale.recalculateTotal();
        return SaleResponse.from(sale);
    }

    @Transactional
    public SaleResponse removeItem(Long saleId, Long itemId) {
        Sale sale = draft(saleId);
        sale.removeItem(item(sale, itemId));
        return SaleResponse.from(sale);
    }

    // HU23: confirma e baixa o estoque. Tudo ou nada: qualquer exceção desfaz a transação inteira.
    @Transactional
    public SaleResponse confirm(Long saleId, Long actorId) {
        Sale sale = sales.findByIdForUpdate(saleId)                   // trava a linha da venda
                .orElseThrow(() -> new NotFoundException("Venda não encontrada."));

        if (sale.getStatus() != SaleStatus.DRAFT)                     // segunda confirmação para aqui
            throw new BusinessRuleException("sale_not_draft", "Esta venda já foi confirmada ou cancelada.");
        if (sale.getItems().isEmpty())
            throw new BusinessRuleException("sale_empty", "Adicione ao menos um produto antes de confirmar.");

        Map<Long, Product> locked = lockProductsOf(sale);

        // 1º valida todos os itens; só depois mexe em estoque.
        for (SaleItem item : sale.getItems()) {
            Product p = locked.get(item.getProduct().getId());
            if (!p.isActive())
                throw new BusinessRuleException("product_inactive", "O produto " + p.getName() + " está inativo.");
            if (p.getStockQty() < item.getQuantity())
                throw new BusinessRuleException("insufficient_stock", "Estoque insuficiente para " + p.getName()
                        + ": disponível " + p.getStockQty() + ", pedido " + item.getQuantity() + ".");
        }
        // Cupom (RN63): confere de novo e conta o uso; se não vale mais, nada foi baixado ainda.
        coupons.redeem(sale);
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

    // RN61/RN62: aplica ou troca o cupom na venda aberta (balcão ou carrinho).
    @Transactional
    public SaleResponse applyCoupon(Long saleId, String code) {
        Sale sale = sales.findByIdForUpdate(saleId).orElseThrow(() -> new NotFoundException("Venda não encontrada."));
        coupons.apply(sale, code);
        return SaleResponse.from(sale);
    }

    @Transactional
    public SaleResponse removeCoupon(Long saleId) {
        Sale sale = sales.findByIdForUpdate(saleId).orElseThrow(() -> new NotFoundException("Venda não encontrada."));
        if (sale.getStatus() != SaleStatus.DRAFT) {
            throw new BusinessRuleException("sale_not_draft", "Só dá para mexer no cupom antes de fechar o pedido.");
        }
        sale.removeCoupon();
        return SaleResponse.from(sale);
    }

    // HU24: cancelar exige motivo. Se estava confirmada, devolve o estoque de cada item.
    @Transactional
    public SaleResponse cancel(Long saleId, String reason, Long actorId) {
        if (reason == null || reason.isBlank()) {
            throw new InvalidInputException("reason_required", "Informe o motivo do cancelamento.");
        }
        Sale sale = sales.findByIdForUpdate(saleId)
                .orElseThrow(() -> new NotFoundException("Venda não encontrada."));
        if (sale.getStatus() == SaleStatus.CANCELLED) {
            throw new BusinessRuleException("sale_already_cancelled", "Esta venda já foi cancelada.");
        }
        SaleStatus previous = sale.getStatus();
        if (previous == SaleStatus.CONFIRMED) {
            Map<Long, Product> locked = lockProductsOf(sale);
            for (SaleItem item : sale.getItems()) {
                stock.move(locked.get(item.getProduct().getId()), item.getQuantity(),
                        StockMovementType.SALE_CANCELLATION, sale, null, actorId);
            }
        }
        sale.markCancelled(OffsetDateTime.now(), reason.trim(), actorId);
        // Pedido online pago e cancelado: o pagamento simulado vira estornado (spec crm/07, REQ-10).
        jdbc.update("update payment set status = 'REFUNDED', refunded_at = now() where sale_id = ? and status = 'APPROVED'",
                sale.getId());
        audit.record(actorId, "crm", "sale_cancelled", "sale", sale.getId(),
                Map.of("from", previous.name(), "total", sale.getTotal(), "reason", reason.trim()));
        return SaleResponse.from(sale);
    }

    @Transactional(readOnly = true)
    public SaleResponse detail(Long id) {
        return SaleResponse.from(find(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<SaleSummary> search(Long id, Long customerId, LocalDate from, LocalDate to, SaleStatus status,
                                            int page, int size) {
        Specification<Sale> spec = (root, q, cb) -> {
            if (q != null && q.getResultType() != Long.class && q.getResultType() != long.class) {
                root.fetch("customer", JoinType.LEFT);
            }
            List<Predicate> where = new ArrayList<>();
            if (id != null) where.add(cb.equal(root.get("id"), id));
            if (customerId != null) where.add(cb.equal(root.get("customer").get("id"), customerId));
            if (status != null) where.add(cb.equal(root.get("status"), status));
            if (from != null) {
                where.add(cb.greaterThanOrEqualTo(root.get("createdAt"),
                        from.atStartOfDay(props.zone()).toOffsetDateTime()));
            }
            if (to != null) {
                where.add(cb.lessThan(root.get("createdAt"),
                        to.plusDays(1).atStartOfDay(props.zone()).toOffsetDateTime()));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "id"));
        return PageResponse.of(sales.findAll(spec, pageable), SaleSummary::from);
    }

    @Transactional(readOnly = true)
    public List<SaleSummary> historyOf(Long customerId) {
        return sales.findHistoryOf(customerId).stream().map(SaleSummary::from).toList();
    }

    private Sale find(Long id) {
        return sales.findById(id).orElseThrow(() -> new NotFoundException("Venda não encontrada."));
    }

    // RN14: só venda aberta aceita mexer nos itens. Trava para dois cliques não se atropelarem.
    private Sale draft(Long id) {
        Sale sale = sales.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Venda não encontrada."));
        if (sale.getStatus() != SaleStatus.DRAFT) {
            throw new BusinessRuleException("sale_not_draft", "Só é possível alterar itens de uma venda aberta.");
        }
        return sale;
    }

    private static SaleItem item(Sale sale, Long itemId) {
        return sale.getItems().stream().filter(i -> i.getId().equals(itemId)).findFirst()
                .orElseThrow(() -> new NotFoundException("Item não encontrado nesta venda."));
    }

    private static void checkStock(Product p, int quantity) {
        if (quantity > p.getStockQty()) {
            throw new BusinessRuleException("insufficient_stock", "Estoque insuficiente para " + p.getName()
                    + ": tem " + p.getStockQty() + ", você pediu " + quantity + ".");
        }
    }

    // Trava os produtos em ordem de id: duas vendas simultâneas não se bloqueiam mutuamente.
    private Map<Long, Product> lockProductsOf(Sale sale) {
        List<Long> ids = sale.getItems().stream().map(i -> i.getProduct().getId()).sorted().toList();
        return products.findAllByIdInForUpdate(ids).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
    }
}
