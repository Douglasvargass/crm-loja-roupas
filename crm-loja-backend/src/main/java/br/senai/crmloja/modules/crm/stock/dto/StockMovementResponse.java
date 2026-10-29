package br.senai.crmloja.modules.crm.stock.dto;

import br.senai.crmloja.modules.crm.stock.StockMovement;
import br.senai.crmloja.modules.crm.stock.StockMovementType;
import java.time.OffsetDateTime;

// Uma linha do histórico de estoque (HU20): data, peça, tipo, quantidade com sinal e o saldo logo depois.
// saleId liga a baixa à venda que a causou.
public record StockMovementResponse(Long id, OffsetDateTime createdAt, Long productId, String productCode,
                                    String productName, StockMovementType type, int quantity, int balanceAfter,
                                    Long saleId, String reason) {

    public static StockMovementResponse from(StockMovement m) {
        return new StockMovementResponse(m.getId(), m.getCreatedAt(), m.getProduct().getId(),
                m.getProduct().getCode(), m.getProduct().getName(), m.getType(), m.getQuantity(),
                m.getBalanceAfter(), m.getSale() == null ? null : m.getSale().getId(), m.getReason());
    }
}
