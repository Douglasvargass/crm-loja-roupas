package br.senai.crmloja.modules.crm.product.dto;

import br.senai.crmloja.modules.crm.product.Availability;
import br.senai.crmloja.modules.crm.product.Product;
import java.math.BigDecimal;

// availability e lowStock já vêm calculados pelo back-end: a tela não refaz a regra de situação do produto (RN10).
public record ProductResponse(Long id, String code, String name, String description, Long categoryId,
                              String categoryName, String size, String color, BigDecimal price,
                              int stockQty, int minStock, boolean active, Availability availability,
                              boolean lowStock) {

    public static ProductResponse from(Product p) {
        return new ProductResponse(p.getId(), p.getCode(), p.getName(), p.getDescription(),
                p.getCategory().getId(), p.getCategory().getName(), p.getSize(), p.getColor(), p.getPrice(),
                p.getStockQty(), p.getMinStock(), p.isActive(), p.availability(), p.isLowStock());
    }
}
