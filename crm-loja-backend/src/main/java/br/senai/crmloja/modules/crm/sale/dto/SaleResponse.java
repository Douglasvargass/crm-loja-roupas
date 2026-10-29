package br.senai.crmloja.modules.crm.sale.dto;

import br.senai.crmloja.modules.crm.sale.Sale;
import br.senai.crmloja.modules.crm.sale.SaleItem;
import br.senai.crmloja.modules.crm.sale.SaleStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

// Detalhe da venda (HU26): preços e nomes são os gravados na venda, não os atuais do produto.
public record SaleResponse(Long id, SaleStatus status, Long customerId, String customerName, String customerCpf,
                           BigDecimal total, OffsetDateTime createdAt, OffsetDateTime confirmedAt,
                           OffsetDateTime cancelledAt, String cancelReason, List<Item> items, String channel) {

    public record Item(Long id, Long productId, String productCode, String productName, BigDecimal unitPrice,
                       int quantity, BigDecimal subtotal, int availableStock) {
        static Item from(SaleItem i) {
            return new Item(i.getId(), i.getProduct().getId(), i.getProductCode(), i.getProductName(),
                    i.getUnitPrice(), i.getQuantity(), i.getSubtotal(), i.getProduct().getStockQty());
        }
    }

    public static SaleResponse from(Sale s) {
        var c = s.getCustomer();
        return new SaleResponse(s.getId(), s.getStatus(), c == null ? null : c.getId(),
                c == null ? null : c.getFullName(), c == null ? null : c.getCpf(), s.getTotal(),
                s.getCreatedAt(), s.getConfirmedAt(), s.getCancelledAt(), s.getCancelReason(),
                s.getItems().stream().map(Item::from).toList(), s.getChannel());
    }
}
