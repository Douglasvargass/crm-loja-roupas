package br.senai.crmloja.modules.crm.target.dto;

import br.senai.crmloja.modules.crm.dashboard.DashboardResponse.DailyRevenue;
import br.senai.crmloja.modules.crm.dashboard.DashboardResponse.TopProduct;
import br.senai.crmloja.modules.crm.target.Pace;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

// Contratos da spec crm/06 (painel do vendedor e metas). Espelho em crm-loja-frontend/src/data/dto.ts.
public final class TargetDtos {

    private TargetDtos() {}

    public record TargetRequest(
            @NotNull(message = "Informe o valor da meta.")
            @DecimalMin(value = "0.01", message = "A meta precisa ser maior que zero.")
            @Digits(integer = 10, fraction = 2, message = "Use no máximo 2 casas decimais.")
            BigDecimal amount) {}

    public record TargetSaved(Long sellerId, String month, BigDecimal amount) {}

    // Painel do vendedor: só os números dele. Nada da loja inteira nem de outros vendedores (REQ-09).
    public record SellerPanel(
            String month,                // aaaa-mm
            boolean closed,              // o mês já terminou
            Long sellerId,
            String sellerName,
            BigDecimal target,           // null = meta ainda não definida
            BigDecimal revenue,
            long confirmedSales,
            BigDecimal averageTicket,
            long cancelledSales,
            BigDecimal percent,          // % da meta
            BigDecimal remaining,        // quanto falta
            BigDecimal perBusinessDay,   // média por dia útil até agora
            BigDecimal projection,       // fechamento se mantiver o ritmo
            BigDecimal neededPerDay,     // quanto precisa por dia útil daqui para a frente
            int businessDaysInMonth,
            int businessDaysElapsed,
            int businessDaysLeft,
            Pace.Status status,
            Today today,                 // null em mês fechado
            List<DailyRevenue> revenueByDay,
            List<TopProduct> topProducts,
            List<RecentSale> recentSales) {}

    public record Today(long sales, BigDecimal revenue) {}

    public record RecentSale(Long id, String status, OffsetDateTime createdAt, OffsetDateTime confirmedAt,
                             String customerName, BigDecimal total) {}

    // Quadro do gerente: uma linha por vendedor ativo.
    public record TargetBoard(
            String month,
            boolean closed,
            boolean editable,            // a meta deste mês ainda pode ser definida ou corrigida
            int businessDaysInMonth,
            int businessDaysElapsed,
            int businessDaysLeft,
            BigDecimal totalTarget,
            BigDecimal totalRevenue,
            List<BoardRow> rows) {}

    public record BoardRow(Long sellerId, String sellerName, BigDecimal target, BigDecimal revenue, long confirmedSales,
                           BigDecimal percent, BigDecimal projection, BigDecimal remaining, Pace.Status status) {}
}
