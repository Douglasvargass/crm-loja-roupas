package br.senai.crmloja.modules.crm.sale;

import br.senai.crmloja.modules.crm.product.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

// RN12: código, nome e preço são copiados do produto na inclusão. Mudar o produto depois
// não altera a venda (CT30).
@Entity
@Table(name = "sale_item")
public class SaleItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_id", nullable = false)
    private Sale sale;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "product_code", nullable = false)
    private String productCode;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    protected SaleItem() {}

    public SaleItem(Sale sale, Product product, int quantity) {
        this.sale = sale;
        this.product = product;
        this.productCode = product.getCode();
        this.productName = product.getName();
        this.unitPrice = product.getPrice();
        setQuantity(quantity);
    }

    // Espaço do cliente (spec crm/07): se o preço do produto mudou com a peça no carrinho, vale o novo (com aviso).
    // Só para venda aberta; venda confirmada nunca muda de preço (RN12).
    public void reprice(BigDecimal price) {
        this.unitPrice = price;
        setQuantity(quantity);
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
        this.subtotal = unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2);
    }

    public Long getId() { return id; }
    public Sale getSale() { return sale; }
    public Product getProduct() { return product; }
    public String getProductCode() { return productCode; }
    public String getProductName() { return productName; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public int getQuantity() { return quantity; }
    public BigDecimal getSubtotal() { return subtotal; }
}
