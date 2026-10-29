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

    // RN13: total sempre recalculado no servidor a partir dos itens.
    public void recalculateTotal() {
        total = items.stream().map(SaleItem::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2);
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
