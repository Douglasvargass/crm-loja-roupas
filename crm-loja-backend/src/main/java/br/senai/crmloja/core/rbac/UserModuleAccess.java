package br.senai.crmloja.core.rbac;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "user_module_access")
public class UserModuleAccess {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "module_id", nullable = false)
    private Long moduleId;

    @Column(name = "module_role_id", nullable = false)
    private Long moduleRoleId;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected UserModuleAccess() {}

    public UserModuleAccess(Long userId, Long moduleId, Long moduleRoleId) {
        this.userId = userId;
        this.moduleId = moduleId;
        this.moduleRoleId = moduleRoleId;
    }

    @PrePersist
    void prePersist() { createdAt = updatedAt = OffsetDateTime.now(); }

    @PreUpdate
    void preUpdate() { updatedAt = OffsetDateTime.now(); }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getModuleId() { return moduleId; }
    public Long getModuleRoleId() { return moduleRoleId; }
    public void setModuleRoleId(Long moduleRoleId) { this.moduleRoleId = moduleRoleId; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
