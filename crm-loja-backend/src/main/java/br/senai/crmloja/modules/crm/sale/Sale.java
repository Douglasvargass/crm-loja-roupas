package br.senai.crmloja.modules.crm.sale;

import br.senai.crmloja.modules.crm.customer.Customer;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

@Entity
@Table(name = "sale")
public class Sale {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Opcional (RN11): sem cliente a venda aparece como "Consumidor não identificado".
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(nullable = false, columnDefinition = "sale_status")
    private SaleStatus status = SaleStatus.DRAFT;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total = BigDecimal.ZERO.setScale(2);

    // V14: cupom. A venda guarda uma cópia da regra (tipo, valor, mínimo) para o total não mudar se o cupom mudar.
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal discount = BigDecimal.ZERO.setScale(2);

    @Column(name = "coupon_id")
    private Long couponId;

    @Column(name = "coupon_code", length = 30)
    private String couponCode;

    @Column(name = "coupon_kind", length = 10)
    private String couponKind;

    @Column(name = "coupon_value", precision = 12, scale = 2)
    private BigDecimal couponValue;

    @Column(name = "coupon_min_order", precision = 12, scale = 2)
    private BigDecimal couponMinOrder;

    @Column(name = "confirmed_at")
    private OffsetDateTime confirmedAt;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    @Column(name = "cancel_reason")
    private String cancelReason;

    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    // Spec crm/07: STORE = balcão; ONLINE = pedido do espaço do cliente. Definido ao abrir e nunca muda.
    @Column(nullable = false, updatable = false)
    private String channel = "STORE";

    @Column(name = "cancelled_by")
    private Long cancelledBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<SaleItem> items = new ArrayList<>();

    protected Sale() {}

    public Sale(Customer customer, Long createdBy) {
        this.customer = customer;
        this.createdBy = createdBy;
    }

    @PrePersist
    void prePersist() { createdAt = updatedAt = OffsetDateTime.now(); }

    @PreUpdate
    void preUpdate() { updatedAt = OffsetDateTime.now(); }

    // RN13: total sempre recalculado no servidor a partir dos itens, menos o desconto do cupom (RN61).
    // Abaixo do mínimo do cupom, o desconto fica zero (a confirmação recusa e pede para tirar o cupom).
    public void recalculateTotal() {
        BigDecimal gross = getSubtotal();
        BigDecimal d = BigDecimal.ZERO;
        if (couponKind != null && gross.compareTo(couponMinOrder == null ? BigDecimal.ZERO : couponMinOrder) >= 0) {
            // RN61: o desconto nunca zera o pedido. Em % o teto de 90% vem do cadastro do cupom; em R$ o valor
            // é fixo e poderia passar do subtotal, então o mesmo teto vale aqui. Sem isto um cupom de valor
            // alto zera o total e o checkout da loja quebra em ck_payment_amount > 0.
            BigDecimal teto = gross.multiply(new BigDecimal("0.90")).setScale(2, java.math.RoundingMode.HALF_UP);
            d = "PERCENT".equals(couponKind)
                    ? gross.multiply(couponValue).divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP)
                    : couponValue.min(teto);
        }
        discount = d.setScale(2, java.math.RoundingMode.HALF_UP);
        total = gross.subtract(discount).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    // Soma dos itens, antes do desconto.
    public BigDecimal getSubtotal() {
        return items.stream().map(SaleItem::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2);
    }

    public void applyCoupon(Long id, String code, String kind, BigDecimal value, BigDecimal minOrder) {
        couponId = id;
        couponCode = code;
        couponKind = kind;
        couponValue = value;
        couponMinOrder = minOrder;
        recalculateTotal();
    }

    public void removeCoupon() {
        applyCoupon(null, null, null, null, null);
    }

    // O pedido já alcança o mínimo do cupom? (sem cupom, sempre sim)
    public boolean couponMinReached() {
        return couponKind == null || getSubtotal().compareTo(couponMinOrder) >= 0;
    }

    public Optional<SaleItem> itemOf(Long productId) {
        return items.stream().filter(i -> i.getProduct().getId().equals(productId)).findFirst();
    }

    public void addItem(SaleItem item) {
        items.add(item);
        recalculateTotal();
    }

    public void removeItem(SaleItem item) {
        items.remove(item);
        recalculateTotal();
    }

    public void markConfirmed(OffsetDateTime at) {
        status = SaleStatus.CONFIRMED;
        confirmedAt = at;
    }

    public void markCancelled(OffsetDateTime at, String reason, Long by) {
        status = SaleStatus.CANCELLED;
        cancelledAt = at;
        cancelReason = reason;
        cancelledBy = by;
    }

    public int itemsQuantity() {
        return items.stream().mapToInt(SaleItem::getQuantity).sum();
    }

    public Long getId() { return id; }
    public Customer getCustomer() { return customer; }
    public SaleStatus getStatus() { return status; }
    public BigDecimal getTotal() { return total; }
    public BigDecimal getDiscount() { return discount; }
    public Long getCouponId() { return couponId; }
    public String getCouponCode() { return couponCode; }
    public BigDecimal getCouponMinOrder() { return couponMinOrder; }
    public OffsetDateTime getConfirmedAt() { return confirmedAt; }
    public OffsetDateTime getCancelledAt() { return cancelledAt; }
    public String getCancelReason() { return cancelReason; }
    public Long getCreatedBy() { return createdBy; }
    public String getChannel() { return channel; }
    public void markOnline() { this.channel = "ONLINE"; }
    public Long getCancelledBy() { return cancelledBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public List<SaleItem> getItems() { return items; }
}
