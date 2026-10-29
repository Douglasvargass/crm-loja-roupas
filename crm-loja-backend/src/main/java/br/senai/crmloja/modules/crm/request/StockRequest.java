package br.senai.crmloja.modules.crm.request;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

// Chamado de peça em falta (spec crm/09). Status em texto com CHECK no banco (não enum do Postgres): mais simples
// de evoluir, e a regra de fechamento (data + nota) é garantida pelo ck_stock_request_closed.
@Entity
@Table(name = "stock_request")
public class StockRequest {

    public enum Status { OPEN, RESOLVED, CANCELLED }

    public enum Via { SCREEN, ASSISTANT }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", updatable = false)
    private Long productId;

    @Column(nullable = false, updatable = false)
    private String description;

    @Column(updatable = false)
    private String size;

    @Column(updatable = false)
    private String color;

    @Column(nullable = false, updatable = false)
    private int quantity;

    @Column(name = "customer_id", updatable = false)
    private Long customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.OPEN;

    @Enumerated(EnumType.STRING)
    @Column(name = "opened_via", nullable = false, updatable = false)
    private Via openedVia;

    @Column(name = "opened_by", updatable = false)
    private Long openedBy;

    @Column(name = "closing_note")
    private String closingNote;

    @Column(name = "closed_by")
    private Long closedBy;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    @Column(name = "requester_seen_at")
    private OffsetDateTime requesterSeenAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected StockRequest() {}

    public StockRequest(Long productId, String description, String size, String color, int quantity, Long customerId,
                        Via via, Long openedBy) {
        this.productId = productId;
        this.description = description;
        this.size = size;
        this.color = color;
        this.quantity = quantity;
        this.customerId = customerId;
        this.openedVia = via;
        this.openedBy = openedBy;
    }

    @PrePersist
    void prePersist() { createdAt = updatedAt = OffsetDateTime.now(); }

    @PreUpdate
    void preUpdate() { updatedAt = OffsetDateTime.now(); }

    void close(Status to, String note, Long actorId, OffsetDateTime at) {
        this.status = to;
        this.closingNote = note;
        this.closedBy = actorId;
        this.closedAt = at;
        this.requesterSeenAt = null;   // novo fechamento = aviso novo para quem abriu
    }

    void markSeen(OffsetDateTime at) { this.requesterSeenAt = at; }

    public Long getId() { return id; }
    public Long getProductId() { return productId; }
    public String getDescription() { return description; }
    public String getSize() { return size; }
    public String getColor() { return color; }
    public int getQuantity() { return quantity; }
    public Long getCustomerId() { return customerId; }
    public Status getStatus() { return status; }
    public Via getOpenedVia() { return openedVia; }
    public Long getOpenedBy() { return openedBy; }
    public String getClosingNote() { return closingNote; }
    public OffsetDateTime getClosedAt() { return closedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
