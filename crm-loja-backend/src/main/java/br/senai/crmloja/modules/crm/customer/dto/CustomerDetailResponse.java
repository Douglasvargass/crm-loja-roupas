package br.senai.crmloja.modules.crm.customer.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

// Ficha do cliente (HU03): dados + resumo das compras confirmadas.
public record CustomerDetailResponse(CustomerResponse customer, long confirmedPurchases,
                                     BigDecimal confirmedTotal, OffsetDateTime lastPurchaseAt) {
}
