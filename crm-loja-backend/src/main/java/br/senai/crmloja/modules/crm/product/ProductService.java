package br.senai.crmloja.modules.crm.product;

import br.senai.crmloja.core.audit.AuditService;
import br.senai.crmloja.core.http.BusinessRuleException;
import br.senai.crmloja.core.http.NotFoundException;
import br.senai.crmloja.core.http.PageResponse;
import br.senai.crmloja.core.text.TextSearch;
import br.senai.crmloja.modules.crm.category.Category;
import br.senai.crmloja.modules.crm.category.CategoryService;
import br.senai.crmloja.modules.crm.product.dto.ProductDetailResponse;
import br.senai.crmloja.modules.crm.product.dto.ProductRequest;
import br.senai.crmloja.modules.crm.product.dto.ProductResponse;
import br.senai.crmloja.modules.crm.sale.SaleItemRepository;
import br.senai.crmloja.modules.crm.stock.StockMovementRepository;
import br.senai.crmloja.modules.crm.stock.StockMovementType;
import br.senai.crmloja.modules.crm.stock.StockService;
import br.senai.crmloja.modules.crm.stock.dto.StockMovementResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

    // Filtro de situação da lista (HU14). LOW_STOCK segue a RN10.
    public enum AvailabilityFilter { ALL, AVAILABLE, OUT_OF_STOCK, LOW_STOCK, INACTIVE }

    static final String INITIAL_STOCK_REASON = "Estoque inicial";

    private final ProductRepository products;
    private final CategoryService categories;
    private final StockService stock;
    private final StockMovementRepository movements;
    private final SaleItemRepository saleItems;
    private final AuditService audit;
    private final EntityManager em;

    public ProductService(ProductRepository products, CategoryService categories, StockService stock,
                          StockMovementRepository movements, SaleItemRepository saleItems, AuditService audit,
                          EntityManager em) {
        this.products = products;
        this.categories = categories;
        this.stock = stock;
        this.movements = movements;
        this.saleItems = saleItems;
        this.audit = audit;
        this.em = em;
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> search(String name, Long categoryId, String size, String code,
                                                AvailabilityFilter availability, int page, int pageSize) {
        Specification<Product> spec = (root, q, cb) -> {
            if (q != null && q.getResultType() != Long.class && q.getResultType() != long.class) {
                root.fetch("category", JoinType.INNER);
            }
            List<Predicate> where = new ArrayList<>();
            if (!TextSearch.isBlank(name)) where.add(TextSearch.containsFolded(cb, root.get("name"), name));
            if (categoryId != null) where.add(cb.equal(root.get("category").get("id"), categoryId));
            if (!TextSearch.isBlank(size)) where.add(cb.equal(cb.upper(root.get("size")), size.trim().toUpperCase()));
            if (!TextSearch.isBlank(code)) {
                where.add(cb.like(cb.upper(root.get("code")), "%" + code.trim().toUpperCase() + "%"));
            }
            switch (availability == null ? AvailabilityFilter.ALL : availability) {
                case AVAILABLE -> where.add(cb.and(cb.isTrue(root.get("active")), cb.gt(root.get("stockQty"), 0)));
                case OUT_OF_STOCK -> where.add(cb.and(cb.isTrue(root.get("active")), cb.equal(root.get("stockQty"), 0)));
                case LOW_STOCK -> where.add(cb.and(cb.isTrue(root.get("active")), cb.gt(root.get("stockQty"), 0),
                        cb.le(root.get("stockQty"), root.get("minStock"))));
                case INACTIVE -> where.add(cb.isFalse(root.get("active")));
                case ALL -> { }
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(pageSize, 1), 100),
                Sort.by("name").ascending().and(Sort.by("code")));
        return PageResponse.of(products.findAll(spec, pageable), ProductResponse::from);
    }

    @Transactional(readOnly = true)
    public ProductDetailResponse detail(Long id) {
        Product p = find(id);
        List<StockMovementResponse> recent = movements.findByProduct_IdOrderByIdDesc(id, PageRequest.of(0, 10))
                .stream().map(StockMovementResponse::from).toList();
        return new ProductDetailResponse(ProductResponse.from(p), recent, saleItems.totalSoldOf(id));
    }

    // HU09: estoque inicial > 0 vira uma movimentação ENTRY "Estoque inicial".
    @Transactional
    public ProductResponse create(ProductRequest req, Long actorId) {
        Category category = activeCategory(req.categoryId());
        String code = req.code().trim().toUpperCase();
        ensureCodeFree(code, null);
        Product p = new Product(actorId);
        apply(p, req, category, code);
        products.saveAndFlush(p);
        int initial = req.initialStock() == null ? 0 : req.initialStock();
        if (initial > 0) stock.move(p, initial, StockMovementType.ENTRY, null, INITIAL_STOCK_REASON, actorId);
        return ProductResponse.from(p);
    }

    // HU12: o estoque não é editável aqui. Preço novo não mexe em vendas já registradas
    // (sale_item guarda o preço da época) e vai para a auditoria.
    @Transactional
    public ProductResponse update(Long id, ProductRequest req, Long actorId) {
        Product p = find(id);
        Category category = p.getCategory().getId().equals(req.categoryId())
                ? p.getCategory() : activeCategory(req.categoryId());
        String code = req.code().trim().toUpperCase();
        ensureCodeFree(code, id);
        var oldPrice = p.getPrice();
        apply(p, req, category, code);
        p.setUpdatedBy(actorId);
        if (oldPrice.compareTo(p.getPrice()) != 0) {
            audit.record(actorId, "crm", "product_price_changed", "product", id,
                    Map.of("from", oldPrice, "to", p.getPrice()));
        }
        return ProductResponse.from(products.saveAndFlush(p));
    }

    // RN07: só sai do banco o produto sem vendas e sem movimentação além do estoque inicial.
    @Transactional
    public void delete(Long id) {
        Product p = find(id);
        var history = movements.kindsOf(id);
        boolean onlyInitial = history.isEmpty() || (history.size() == 1
                && history.get(0).getType() == StockMovementType.ENTRY
                && INITIAL_STOCK_REASON.equals(history.get(0).getReason()));
        if (saleItems.countByProduct_Id(id) > 0 || !onlyInitial) {
            throw new BusinessRuleException("product_has_history",
                    "Este produto já tem vendas ou movimentações. Quer desativá-lo em vez de excluir?");
        }
        if (!history.isEmpty()) {
            // Libera, só nesta transação e só para este produto, o trigger que protege stock_movement (V5).
            em.createNativeQuery("select set_config('crm.product_purge', :id, true)")
                    .setParameter("id", id.toString()).getSingleResult();
            em.createNativeQuery("delete from stock_movement where product_id = :id")
                    .setParameter("id", id).executeUpdate();
        }
        products.delete(p);
    }

    @Transactional
    public ProductResponse setActive(Long id, boolean active, Long actorId) {
        Product p = find(id);
        if (p.isActive() != active) {
            p.setActive(active);
            p.setUpdatedBy(actorId);
            audit.record(actorId, "crm", active ? "product_activated" : "product_deactivated", "product", id, Map.of());
        }
        return ProductResponse.from(p);
    }

    public Product find(Long id) {
        return products.findById(id).orElseThrow(() -> new NotFoundException("Produto não encontrado."));
    }

    private Category activeCategory(Long id) {
        Category c = categories.find(id);
        if (!c.isActive()) {
            throw new BusinessRuleException("category_inactive", "A categoria " + c.getName() + " está inativa.");
        }
        return c;
    }

    private void ensureCodeFree(String code, Long selfId) {
        products.findByCodeIgnoreCase(code)
                .filter(other -> !other.getId().equals(selfId))
                .ifPresent(other -> {
                    throw new BusinessRuleException("product_code_taken", "Código já utilizado.");
                });
    }

    private static void apply(Product p, ProductRequest req, Category category, String code) {
        p.setCode(code);
        p.setName(req.name().trim());
        p.setDescription(TextSearch.isBlank(req.description()) ? null : req.description().trim());
        p.setCategory(category);
        p.setSize(req.size().trim().toUpperCase());
        p.setColor(req.color().trim());
        p.setPrice(req.price().setScale(2, RoundingMode.HALF_UP));
        p.setMinStock(req.minStock());
    }
}
