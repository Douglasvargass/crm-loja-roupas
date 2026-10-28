package br.senai.crmloja.modules.crm.sale;

// Espelha o tipo sale_status do Postgres (V4). Na tela: Aberta, Confirmada, Cancelada.
public enum SaleStatus {
    DRAFT, CONFIRMED, CANCELLED
}
