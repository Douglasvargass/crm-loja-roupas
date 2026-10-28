package br.senai.crmloja.modules.crm.sale.dto;

import br.senai.crmloja.modules.crm.sale.Sale;
import br.senai.crmloja.modules.crm.sale.SaleStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

// Linha das listas de vendas (HU25) e do histórico do cliente (HU06).
public record SaleSummary(Long id, OffsetDateTime createdAt, OffsetDateTime confirmedAt, Long customerId,
                          String customerName, int itemsQuantity, BigDecimal total, SaleStatus status) {

    public static SaleSummary from(Sale s) {
        return new SaleSummary(s.getId(), s.getCreatedAt(), s.getConfirmedAt(),
                s.getCustomer() == null ? null : s.getCustomer().getId(),
                s.getCustomer() == null ? null : s.getCustomer().getFullName(),
                s.itemsQuantity(), s.getTotal(), s.getStatus());
    }
}
