package br.senai.crmloja.modules.crm.sale.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

// Resumo das compras confirmadas de um cliente. sum() volta null quando não há vendas.
public record PurchaseSummary(long count, BigDecimal total, OffsetDateTime lastPurchaseAt) {
    public PurchaseSummary {
        if (total == null) total = BigDecimal.ZERO.setScale(2);
    }
}
