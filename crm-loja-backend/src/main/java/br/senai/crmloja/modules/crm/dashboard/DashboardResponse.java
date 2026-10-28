package br.senai.crmloja.modules.crm.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// Todos os indicadores numa chamada (HU27–HU30). Nada é fixo: tudo vem de consulta ao banco.
public record DashboardResponse(
        long totalCustomers,
        long totalProducts,
        Period period,
        long confirmedSales,
        BigDecimal revenue,
        BigDecimal averageTicket,
        long cancelledSales,
        Comparison previousPeriod,
        List<TopProduct> topProducts,
        List<DailyRevenue> revenueByDay,
        List<CategorySales> salesByCategory,
        List<StockAlert> lowStock,
        List<StockAlert> outOfStock) {

    public record Period(LocalDate from, LocalDate to) {}

    public record Comparison(LocalDate from, LocalDate to, long confirmedSales, BigDecimal revenue) {}

    public record TopProduct(Long productId, String code, String name, long quantity, BigDecimal revenue) {}

    public record DailyRevenue(LocalDate date, long sales, BigDecimal revenue) {}

    public record CategorySales(String category, long quantity, BigDecimal revenue) {}

    public record StockAlert(Long productId, String code, String name, int stockQty, int minStock) {}
}
