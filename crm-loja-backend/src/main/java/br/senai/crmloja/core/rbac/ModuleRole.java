package br.senai.crmloja.core.rbac;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "module_role")
public class ModuleRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "module_id", nullable = false)
    private Long moduleId;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "is_admin", nullable = false)
    private boolean admin;

    protected ModuleRole() {}

    public ModuleRole(Long moduleId, String code, String name, boolean admin) {
        this.moduleId = moduleId;
        this.code = code;
        this.name = name;
        this.admin = admin;
    }

    public Long getId() { return id; }
    public Long getModuleId() { return moduleId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public boolean isAdmin() { return admin; }
    public void setAdmin(boolean admin) { this.admin = admin; }
}
