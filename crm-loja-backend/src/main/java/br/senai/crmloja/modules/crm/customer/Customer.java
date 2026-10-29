package br.senai.crmloja.modules.crm.customer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.OffsetDateTime;

// Cliente da loja (HU01). CPF guardado só com os 11 números (CHAR(11)) e único; CEP com 8 e UF com 2.
// Cliente com venda não se exclui, só inativa (RN02): o histórico de compras continua inteiro.
@Entity
@Table(name = "customer")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 11)
    private String cpf;

    @Column(nullable = false)
    private String phone;

    @Column(nullable = false)
    private String email;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "zip_code", nullable = false, length = 8)
    private String zipCode;

    @Column(nullable = false)
    private String street;

    @Column(nullable = false)
    private String number;

    private String complement;

    @Column(nullable = false)
    private String district;

    @Column(nullable = false)
    private String city;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 2)
    private String state;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    // Conta de acesso à loja online (V9). Só leitura aqui: quem liga é o StoreService/seeder, por SQL.
    @Column(name = "user_id", insertable = false, updatable = false)
    private Long userId;

    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Customer() {}

    public Customer(Long createdBy) { this.createdBy = createdBy; }

    @PrePersist
    void prePersist() { createdAt = updatedAt = OffsetDateTime.now(); }

    @PreUpdate
    void preUpdate() { updatedAt = OffsetDateTime.now(); }

    public Long getId() { return id; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getCpf() { return cpf; }
    public void setCpf(String cpf) { this.cpf = cpf; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getZipCode() { return zipCode; }
    public void setZipCode(String zipCode) { this.zipCode = zipCode; }
    public String getStreet() { return street; }
    public void setStreet(String street) { this.street = street; }
    public String getNumber() { return number; }
    public void setNumber(String number) { this.number = number; }
    public String getComplement() { return complement; }
    public void setComplement(String complement) { this.complement = complement; }
    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public Long getUserId() { return userId; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
