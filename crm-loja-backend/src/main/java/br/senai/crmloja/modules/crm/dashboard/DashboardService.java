package br.senai.crmloja.modules.crm.dashboard;

import br.senai.crmloja.core.config.AppProperties;
import br.senai.crmloja.core.http.InvalidInputException;
import br.senai.crmloja.modules.crm.dashboard.DashboardResponse.CategorySales;
import br.senai.crmloja.modules.crm.dashboard.DashboardResponse.Comparison;
import br.senai.crmloja.modules.crm.dashboard.DashboardResponse.DailyRevenue;
import br.senai.crmloja.modules.crm.dashboard.DashboardResponse.Period;
import br.senai.crmloja.modules.crm.dashboard.DashboardResponse.StockAlert;
import br.senai.crmloja.modules.crm.dashboard.DashboardResponse.TopProduct;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Consultas SQL diretas: agregações ficam mais claras em SQL do que em JPQL.
// O fim do período é exclusivo (< dia seguinte 00:00 no fuso da loja): a venda das 23:59 entra.
@Service
public class DashboardService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final NamedParameterJdbcTemplate jdbc;
    private final AppProperties props;

    public DashboardService(NamedParameterJdbcTemplate jdbc, AppProperties props) {
        this.jdbc = jdbc;
        this.props = props;
    }

    @Transactional(readOnly = true)
    public DashboardResponse build(LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now(props.zone());
        if (from == null) from = today.withDayOfMonth(1);   // padrão: este mês
        if (to == null) to = today;
        if (from.isAfter(to)) throw new InvalidInputException("invalid_period", "A data inicial é depois da final.");
        if (ChronoUnit.DAYS.between(from, to) > 366) {
            throw new InvalidInputException("invalid_period", "Escolha um período de até um ano.");
        }

        var params = range(from, to);
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        LocalDate prevTo = from.minusDays(1);
        LocalDate prevFrom = prevTo.minusDays(days - 1);

        long totalCustomers = count("select count(*) from customer");
        long totalProducts = count("select count(*) from product");

        var current = salesTotals(params);
        var previous = salesTotals(range(prevFrom, prevTo));
        long cancelled = jdbc.queryForObject("""
                select count(*) from sale
                 where status = 'CANCELLED' and cancelled_at >= :from and cancelled_at < :to""", params, Long.class);
        BigDecimal avg = current.count() == 0 ? ZERO
                : current.revenue().divide(BigDecimal.valueOf(current.count()), 2, RoundingMode.HALF_UP);

        return new DashboardResponse(totalCustomers, totalProducts, new Period(from, to),
                current.count(), current.revenue(), avg, cancelled,
                new Comparison(prevFrom, prevTo, previous.count(), previous.revenue()),
                topProducts(params), revenueByDay(params, from, to), salesByCategory(params),
                lowStock(), outOfStock());
    }

    private record Totals(long count, BigDecimal revenue) {}

    private Totals salesTotals(MapSqlParameterSource params) {
        return jdbc.queryForObject("""
                select count(*) as qty, coalesce(sum(total), 0) as revenue from sale
                 where status = 'CONFIRMED' and confirmed_at >= :from and confirmed_at < :to""",
                params, (rs, i) -> new Totals(rs.getLong("qty"), money(rs.getBigDecimal("revenue"))));
    }

    private List<TopProduct> topProducts(MapSqlParameterSource params) {
        return jdbc.query("""
                select si.product_id, p.code, p.name, sum(si.quantity) as qty, sum(si.subtotal) as revenue
                  from sale_item si
                  join sale s on s.id = si.sale_id
                  join product p on p.id = si.product_id
                 where s.status = 'CONFIRMED' and s.confirmed_at >= :from and s.confirmed_at < :to
                 group by si.product_id, p.code, p.name
                 order by qty desc, revenue desc, p.name
                 limit 5""", params,
                (rs, i) -> new TopProduct(rs.getLong("product_id"), rs.getString("code"), rs.getString("name"),
                        rs.getLong("qty"), money(rs.getBigDecimal("revenue"))));
    }

    // Um ponto por dia, inclusive os dias sem venda (o gráfico não pula datas).
    private List<DailyRevenue> revenueByDay(MapSqlParameterSource params, LocalDate from, LocalDate to) {
        params.addValue("tz", props.timeZone());
        Map<LocalDate, DailyRevenue> byDay = new HashMap<>();
        jdbc.query("""
                select (confirmed_at at time zone :tz)::date as day, count(*) as qty, sum(total) as revenue
                  from sale
                 where status = 'CONFIRMED' and confirmed_at >= :from and confirmed_at < :to
                 group by day""", params,
                rs -> {
                    LocalDate day = rs.getObject("day", LocalDate.class);
                    byDay.put(day, new DailyRevenue(day, rs.getLong("qty"), money(rs.getBigDecimal("revenue"))));
                });
        List<DailyRevenue> series = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            series.add(byDay.getOrDefault(d, new DailyRevenue(d, 0, ZERO)));
        }
        return series;
    }

    private List<CategorySales> salesByCategory(MapSqlParameterSource params) {
        return jdbc.query("""
                select c.name, sum(si.quantity) as qty, sum(si.subtotal) as revenue
                  from sale_item si
                  join sale s on s.id = si.sale_id
                  join product p on p.id = si.product_id
                  join category c on c.id = p.category_id
                 where s.status = 'CONFIRMED' and s.confirmed_at >= :from and s.confirmed_at < :to
                 group by c.name
                 order by revenue desc""", params,
                (rs, i) -> new CategorySales(rs.getString("name"), rs.getLong("qty"), money(rs.getBigDecimal("revenue"))));
    }

    private List<StockAlert> lowStock() {
        return jdbc.query("""
                select id, code, name, stock_qty, min_stock from product
                 where is_active and stock_qty > 0 and stock_qty <= min_stock
                 order by stock_qty, name""", (rs, i) -> alert(rs));
    }

    private List<StockAlert> outOfStock() {
        return jdbc.query("""
                select id, code, name, stock_qty, min_stock from product
                 where is_active and stock_qty = 0
                 order by name""", (rs, i) -> alert(rs));
    }

    private static StockAlert alert(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new StockAlert(rs.getLong("id"), rs.getString("code"), rs.getString("name"),
                rs.getInt("stock_qty"), rs.getInt("min_stock"));
    }

    private long count(String sql) {
        Long n = jdbc.queryForObject(sql, Map.of(), Long.class);
        return n == null ? 0 : n;
    }

    private MapSqlParameterSource range(LocalDate from, LocalDate to) {
        OffsetDateTime start = from.atStartOfDay(props.zone()).toOffsetDateTime();
        OffsetDateTime end = to.plusDays(1).atStartOfDay(props.zone()).toOffsetDateTime();
        return new MapSqlParameterSource().addValue("from", start).addValue("to", end);
    }

    private static BigDecimal money(BigDecimal v) {
        return v == null ? ZERO : v.setScale(2, RoundingMode.HALF_UP);
    }
}
