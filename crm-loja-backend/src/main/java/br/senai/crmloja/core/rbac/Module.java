package br.senai.crmloja.core.rbac;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// Módulo do sistema (ex.: "crm" da equipe, "loja" dos clientes). Cada módulo tem os seus papéis; desligar o
// módulo (is_active = false) tira o acesso de todo mundo a ele sem apagar nada.
@Entity
@Table(name = "module")
public class Module {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    protected Module() {}

    public Module(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
