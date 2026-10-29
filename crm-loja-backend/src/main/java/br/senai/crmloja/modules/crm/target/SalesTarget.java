package br.senai.crmloja.modules.crm.target;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

// Meta mensal de um vendedor (spec crm/06). Vendedor e mês não mudam depois de criados: corrigir é mudar o valor.
@Entity
@Table(name = "sales_target")
public class SalesTarget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_id", nullable = false, updatable = false)
    private Long sellerId;

    // Sempre o dia 1 do mês (CHECK no banco).
    @Column(name = "period_month", nullable = false, updatable = false)
    private LocalDate periodMonth;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected SalesTarget() {}

    public SalesTarget(Long sellerId, LocalDate periodMonth, BigDecimal amount, Long createdBy) {
        this.sellerId = sellerId;
        this.periodMonth = periodMonth;
        this.amount = amount;
        this.createdBy = createdBy;
        this.updatedBy = createdBy;
    }

    @PrePersist
    void prePersist() { createdAt = updatedAt = OffsetDateTime.now(); }

    @PreUpdate
    void preUpdate() { updatedAt = OffsetDateTime.now(); }

    public Long getId() { return id; }
    public Long getSellerId() { return sellerId; }
    public LocalDate getPeriodMonth() { return periodMonth; }
    public BigDecimal getAmount() { return amount; }

    public void changeAmount(BigDecimal amount, Long actorId) {
        this.amount = amount;
        this.updatedBy = actorId;
    }
}
