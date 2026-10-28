package br.senai.crmloja.core.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.OffsetDateTime;

// Só o hash SHA-256 é salvo: um vazamento do banco não entrega sessões válidas.
@Entity
@Table(name = "refresh_token")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    // Identifica a "sessão" (cadeia de rotações): reuso de token derruba a cadeia inteira.
    @Column(name = "session_started_at", nullable = false)
    private OffsetDateTime sessionStartedAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @Column(name = "replaced_by_token_id")
    private Long replacedByTokenId;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected RefreshToken() {}

    public RefreshToken(Long userId, String tokenHash, OffsetDateTime sessionStartedAt,
                        OffsetDateTime expiresAt, String userAgent) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.sessionStartedAt = sessionStartedAt;
        this.expiresAt = expiresAt;
        this.userAgent = userAgent;
    }

    @PrePersist
    void prePersist() { createdAt = OffsetDateTime.now(); }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public OffsetDateTime getSessionStartedAt() { return sessionStartedAt; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public OffsetDateTime getRevokedAt() { return revokedAt; }
    public boolean isRevoked() { return revokedAt != null; }
    public void revoke(OffsetDateTime at, Long replacedBy) {
        this.revokedAt = at;
        this.replacedByTokenId = replacedBy;
    }
}
