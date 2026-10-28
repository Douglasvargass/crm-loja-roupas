package br.senai.crmloja.modules.crm.stock;

// Espelha o tipo stock_movement_type do Postgres (V4).
public enum StockMovementType {
    ENTRY, ADJUSTMENT, SALE, SALE_CANCELLATION
}
