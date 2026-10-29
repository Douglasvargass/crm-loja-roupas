package br.senai.crmloja.modules.crm.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// Todos os indicadores numa chamada (HU27–HU30). Nada é fixo: tudo vem de consulta ao banco.
//
// Filtros cruzados (clicar num gráfico): `selected` devolve o que está filtrado. Os números dos cartões
// respeitam todos os filtros; cada gráfico respeita os filtros dos OUTROS gráficos, mas não o próprio,
// para você ainda ver as demais barras e poder trocar a seleção. `period` já vem estreitado para o
// dia escolhido, quando há um.
// O vendedor (quem abriu a venda) é um filtro à parte, escolhido numa lista: vale para cartões e gráficos,
// e `sellers` traz as opções (contas ativas com papel de vendedor).
public record DashboardResponse(
        long totalCustomers,
        long totalProducts,
        Period period,
        Selected selected,
        long confirmedSales,
        BigDecimal revenue,
        BigDecimal averageTicket,
        long cancelledSales,
        Comparison previousPeriod,
        List<TopProduct> topProducts,
        List<DailyRevenue> revenueByDay,
        List<CategorySales> salesByCategory,
        List<StockAlert> lowStock,
        List<StockAlert> outOfStock,
        List<Seller> sellers) {

    public record Period(LocalDate from, LocalDate to) {}

    public record Selected(Long categoryId, String categoryName, Long productId, String productName, LocalDate day,
                           Long sellerId, String sellerName) {}

    public record Seller(Long id, String fullName) {}

    public record Comparison(LocalDate from, LocalDate to, long confirmedSales, BigDecimal revenue) {}

    public record TopProduct(Long productId, String code, String name, long quantity, BigDecimal revenue) {}

    public record DailyRevenue(LocalDate date, long sales, BigDecimal revenue) {}

    public record CategorySales(Long categoryId, String category, long quantity, BigDecimal revenue) {}

    public record StockAlert(Long productId, String code, String name, int stockQty, int minStock) {}
}
