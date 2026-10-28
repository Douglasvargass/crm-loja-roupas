package br.senai.crmloja.modules.crm.stock;

import br.senai.crmloja.core.audit.AuditService;
import br.senai.crmloja.core.config.AppProperties;
import br.senai.crmloja.core.http.BusinessRuleException;
import br.senai.crmloja.core.http.InvalidInputException;
import br.senai.crmloja.core.http.NotFoundException;
import br.senai.crmloja.core.http.PageResponse;
import br.senai.crmloja.modules.crm.product.Product;
import br.senai.crmloja.modules.crm.product.ProductRepository;
import br.senai.crmloja.modules.crm.product.dto.ProductResponse;
import br.senai.crmloja.modules.crm.sale.Sale;
import br.senai.crmloja.modules.crm.stock.dto.StockAlertsResponse;
import br.senai.crmloja.modules.crm.stock.dto.StockMovementResponse;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockService {

    private final StockMovementRepository movements;
    private final ProductRepository products;
    private final AuditService audit;
    private final AppProperties props;

    public StockService(StockMovementRepository movements, ProductRepository products, AuditService audit,
                        AppProperties props) {
        this.movements = movements;
        this.products = products;
        this.audit = audit;
        this.props = props;
    }

    // Único lugar que muda product.stock_qty (RN08). O chamador já travou o produto e está numa
    // transação: o saldo e a movimentação são gravados juntos ou nenhum dos dois.
    @Transactional(propagation = Propagation.MANDATORY)
    public StockMovement move(Product lockedProduct, int delta, StockMovementType type, Sale sale, String reason,
                              Long actorId) {
        int balance = lockedProduct.getStockQty() + delta;
        if (balance < 0) {
            throw new BusinessRuleException("negative_stock", "Saldo atual " + lockedProduct.getStockQty()
                    + "; ajuste de " + delta + " deixaria estoque negativo.");
        }
        lockedProduct.setStockQty(balance);
        return movements.save(new StockMovement(lockedProduct, sale, type, delta, balance, reason, actorId));
    }

    @Transactional
    public StockMovementResponse entry(Long productId, int quantity, String reason, Long actorId) {
        if (quantity <= 0) throw new InvalidInputException("invalid_quantity", "A quantidade deve ser maior que zero.");
        Product p = lockProduct(productId);
        return StockMovementResponse.from(move(p, quantity, StockMovementType.ENTRY, null, blankToNull(reason), actorId));
    }

    @Transactional
    public StockMovementResponse adjust(Long productId, int quantity, String reason, Long actorId) {
        if (quantity == 0) throw new InvalidInputException("invalid_quantity", "O ajuste não pode ser zero.");
        if (reason == null || reason.isBlank()) {
            throw new InvalidInputException("reason_required", "Informe o motivo do ajuste.");
        }
        Product p = lockProduct(productId);
        int before = p.getStockQty();
        StockMovement m = move(p, quantity, StockMovementType.ADJUSTMENT, null, reason.trim(), actorId);
        audit.record(actorId, "crm", "stock_adjusted", "product", productId,
                Map.of("quantity", quantity, "before", before, "after", m.getBalanceAfter(), "reason", reason.trim()));
        return StockMovementResponse.from(m);
    }

    @Transactional(readOnly = true)
    public PageResponse<StockMovementResponse> history(Long productId, StockMovementType type, LocalDate from,
                                                       LocalDate to, int page, int size) {
        Specification<StockMovement> spec = (root, q, cb) -> {
            // fetch só na consulta da página; a de contagem não aceita fetch.
            if (q != null && q.getResultType() != Long.class && q.getResultType() != long.class) {
                root.fetch("product", JoinType.INNER);
                root.fetch("sale", JoinType.LEFT);
            }
            List<Predicate> where = new ArrayList<>();
            if (productId != null) where.add(cb.equal(root.get("product").get("id"), productId));
            if (type != null) where.add(cb.equal(root.get("type"), type));
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
        return PageResponse.of(movements.findAll(spec, pageable), StockMovementResponse::from);
    }

    @Transactional(readOnly = true)
    public StockAlertsResponse alerts() {
        return new StockAlertsResponse(
                products.findLowStock().stream().map(ProductResponse::from).toList(),
                products.findOutOfStock().stream().map(ProductResponse::from).toList());
    }

    private Product lockProduct(Long id) {
        return products.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Produto não encontrado."));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
