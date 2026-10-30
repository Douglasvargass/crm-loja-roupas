package br.senai.crmloja.modules.crm.dashboard;

import br.senai.crmloja.core.config.AppProperties;
import br.senai.crmloja.core.http.InvalidInputException;
import br.senai.crmloja.core.http.NotFoundException;
import br.senai.crmloja.modules.crm.dashboard.DashboardResponse.CategorySales;
import br.senai.crmloja.modules.crm.dashboard.DashboardResponse.Comparison;
import br.senai.crmloja.modules.crm.dashboard.DashboardResponse.DailyRevenue;
import br.senai.crmloja.modules.crm.dashboard.DashboardResponse.Period;
import br.senai.crmloja.modules.crm.dashboard.DashboardResponse.Selected;
import br.senai.crmloja.modules.crm.dashboard.DashboardResponse.Seller;
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
//
// Toda venda é lida pelos itens (sale_item), seja com filtro ou sem: o total da venda é sempre a soma
// dos subtotais dos itens, então o resultado sem filtro é idêntico ao de somar sale.total, e com filtro
// de categoria/produto só entram os itens daquela categoria/produto.
@Service
public class DashboardService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final NamedParameterJdbcTemplate jdbc;
    private final AppProperties props;

    public DashboardService(NamedParameterJdbcTemplate jdbc, AppProperties props) {
        this.jdbc = jdbc;
        this.props = props;
    }

    public DashboardResponse build(LocalDate from, LocalDate to) {
        return build(from, to, null, null, null, null);
    }

    // Usado pela Thalita: período e, se pedido, um vendedor.
    public DashboardResponse build(LocalDate from, LocalDate to, Long sellerId) {
        return build(from, to, null, null, null, sellerId);
    }

    // categoryId, productId e day são os filtros dos cliques nos gráficos; sellerId é quem abriu a venda.
    // Todos opcionais. O vendedor não é um gráfico, então vale para tudo (cartões e os três gráficos).
    @Transactional(readOnly = true)
    public DashboardResponse build(LocalDate from, LocalDate to, Long categoryId, Long productId, LocalDate day,
                                   Long sellerId) {
        return build(from, to, categoryId, productId, day, sellerId, null);
    }

    // channel: STORE (balcão) ou ONLINE (espaço do cliente, spec crm/07); null = os dois.
    @Transactional(readOnly = true)
    public DashboardResponse build(LocalDate from, LocalDate to, Long categoryId, Long productId, LocalDate day,
                                   Long sellerId, String channel) {
        if (channel != null && !channel.equals("STORE") && !channel.equals("ONLINE")) {
            throw new InvalidInputException("invalid_filter", "Canal inválido: use STORE (loja física) ou ONLINE.");
        }
        Who who = new Who(sellerId, channel);
        LocalDate today = LocalDate.now(props.zone());
        if (from == null) from = today.withDayOfMonth(1);   // padrão: este mês
        if (to == null) to = today;
        if (from.isAfter(to)) throw new InvalidInputException("invalid_period", "A data inicial é depois da final.");
        if (ChronoUnit.DAYS.between(from, to) > 366) {
            throw new InvalidInputException("invalid_period", "Escolha um período de até um ano.");
        }
        if (day != null && (day.isBefore(from) || day.isAfter(to))) {
            throw new InvalidInputException("invalid_filter", "O dia escolhido está fora do período.");
        }
        String categoryName = categoryId == null ? null : name("select name from category where id = :id", categoryId, "Categoria não encontrada.");
        String productName = productId == null ? null : name("select name from product where id = :id", productId, "Produto não encontrado.");
        String sellerName = sellerId == null ? null : name("select full_name from app_user where id = :id", sellerId, "Vendedor não encontrado.");

        // Cartões: todos os filtros. Dia escolhido estreita o período (e a comparação passa a ser com o dia anterior).
        LocalDate kFrom = day != null ? day : from;
        LocalDate kTo = day != null ? day : to;
        long days = ChronoUnit.DAYS.between(kFrom, kTo) + 1;
        LocalDate prevTo = kFrom.minusDays(1);
        LocalDate prevFrom = prevTo.minusDays(days - 1);

        var current = salesTotals(kFrom, kTo, categoryId, productId, who);
        var previous = salesTotals(prevFrom, prevTo, categoryId, productId, who);
        long cancelled = cancelledCount(kFrom, kTo, categoryId, productId, who);
        BigDecimal avg = current.count() == 0 ? ZERO
                : current.revenue().divide(BigDecimal.valueOf(current.count()), 2, RoundingMode.HALF_UP);

        return new DashboardResponse(
                count("select count(*) from customer"), count("select count(*) from product"),
                new Period(kFrom, kTo),
                new Selected(categoryId, categoryName, productId, productName, day, sellerId, sellerName),
                current.count(), current.revenue(), avg, cancelled,
                new Comparison(prevFrom, prevTo, previous.count(), previous.revenue()),
                // Cada gráfico ignora o PRÓPRIO filtro e respeita os dos outros:
                topProducts(kFrom, kTo, categoryId, who),             // sem productId
                revenueByDay(from, to, categoryId, productId, who),   // sem day, período inteiro
                salesByCategory(kFrom, kTo, productId, who),          // sem categoryId
                lowStock(), outOfStock(), sellers());
    }

    private record Totals(long count, BigDecimal revenue) {}

    private Totals salesTotals(LocalDate from, LocalDate to, Long categoryId, Long productId, Who who) {
        return jdbc.queryForObject("""
                select count(distinct s.id) as qty, coalesce(sum(si.subtotal * (case when s.discount > 0 then s.total / (s.total + s.discount) else 1 end)), 0) as revenue
                  from sale s
                  join sale_item si on si.sale_id = s.id
                  join product p on p.id = si.product_id
                 where s.status = 'CONFIRMED' and s.confirmed_at >= :from and s.confirmed_at < :to"""
                        + itemFilter(categoryId, productId) + sellerFilter(who),
                params(from, to, categoryId, productId, who),
                (rs, i) -> new Totals(rs.getLong("qty"), money(rs.getBigDecimal("revenue"))));
    }

    private long cancelledCount(LocalDate from, LocalDate to, Long categoryId, Long productId, Who who) {
        Long n = jdbc.queryForObject("""
                select count(distinct s.id)
                  from sale s
                  join sale_item si on si.sale_id = s.id
                  join product p on p.id = si.product_id
                 where s.status = 'CANCELLED' and s.cancelled_at >= :from and s.cancelled_at < :to"""
                        + itemFilter(categoryId, productId) + sellerFilter(who),
                params(from, to, categoryId, productId, who), Long.class);
        return n == null ? 0 : n;
    }

    private List<TopProduct> topProducts(LocalDate from, LocalDate to, Long categoryId, Who who) {
        return jdbc.query("""
                select si.product_id, p.code, p.name, sum(si.quantity) as qty, sum(si.subtotal * (case when s.discount > 0 then s.total / (s.total + s.discount) else 1 end)) as revenue
                  from sale_item si
                  join sale s on s.id = si.sale_id
                  join product p on p.id = si.product_id
                 where s.status = 'CONFIRMED' and s.confirmed_at >= :from and s.confirmed_at < :to"""
                        + itemFilter(categoryId, null) + sellerFilter(who) + """

                 group by si.product_id, p.code, p.name
                 order by qty desc, revenue desc, p.name
                 limit 5""", params(from, to, categoryId, null, who),
                (rs, i) -> new TopProduct(rs.getLong("product_id"), rs.getString("code"), rs.getString("name"),
                        rs.getLong("qty"), money(rs.getBigDecimal("revenue"))));
    }

    // Um ponto por dia, inclusive os dias sem venda (o gráfico não pula datas).
    private List<DailyRevenue> revenueByDay(LocalDate from, LocalDate to, Long categoryId, Long productId,
                                            Who who) {
        var params = params(from, to, categoryId, productId, who).addValue("tz", props.timeZone());
        Map<LocalDate, DailyRevenue> byDay = new HashMap<>();
        jdbc.query("""
                select (s.confirmed_at at time zone :tz)::date as day, count(distinct s.id) as qty, sum(si.subtotal * (case when s.discount > 0 then s.total / (s.total + s.discount) else 1 end)) as revenue
                  from sale s
                  join sale_item si on si.sale_id = s.id
                  join product p on p.id = si.product_id
                 where s.status = 'CONFIRMED' and s.confirmed_at >= :from and s.confirmed_at < :to"""
                        + itemFilter(categoryId, productId) + sellerFilter(who) + """

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

    private List<CategorySales> salesByCategory(LocalDate from, LocalDate to, Long productId, Who who) {
        return jdbc.query("""
                select c.id, c.name, sum(si.quantity) as qty, sum(si.subtotal * (case when s.discount > 0 then s.total / (s.total + s.discount) else 1 end)) as revenue
                  from sale_item si
                  join sale s on s.id = si.sale_id
                  join product p on p.id = si.product_id
                  join category c on c.id = p.category_id
                 where s.status = 'CONFIRMED' and s.confirmed_at >= :from and s.confirmed_at < :to"""
                        + itemFilter(null, productId) + sellerFilter(who) + """

                 group by c.id, c.name
                 order by revenue desc, c.name""", params(from, to, null, productId, who),
                (rs, i) -> new CategorySales(rs.getLong("id"), rs.getString("name"), rs.getLong("qty"),
                        money(rs.getBigDecimal("revenue"))));
    }

    // Opções do filtro: contas ativas com papel de vendedor no módulo crm, por nome.
    private List<Seller> sellers() {
        return jdbc.query("""
                select u.id, u.full_name
                  from app_user u
                  join user_module_access a on a.user_id = u.id and a.is_active
                  join module m on m.id = a.module_id and m.code = 'crm'
                  join module_role r on r.id = a.module_role_id and r.code = 'SELLER'
                 where u.is_active
                 order by u.full_name""", (rs, i) -> new Seller(rs.getLong("id"), rs.getString("full_name")));
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

    private String name(String sql, Long id, String notFound) {
        List<String> found = jdbc.queryForList(sql, Map.of("id", id), String.class);
        if (found.isEmpty()) throw new NotFoundException(notFound);
        return found.get(0);
    }

    // Só entra no SQL o filtro que foi informado; os valores vão sempre como parâmetro (nunca concatenados).
    private static String itemFilter(Long categoryId, Long productId) {
        return (categoryId == null ? "" : " and p.category_id = :categoryId")
                + (productId == null ? "" : " and si.product_id = :productId");
    }

    // Vendedor = quem abriu a venda (sale.created_by); canal = balcão ou online (spec crm/07).
    private record Who(Long sellerId, String channel) {}

    private static String sellerFilter(Who who) {
        return (who.sellerId() == null ? "" : " and s.created_by = :sellerId")
                + (who.channel() == null ? "" : " and s.channel = :channel");
    }

    private MapSqlParameterSource params(LocalDate from, LocalDate to, Long categoryId, Long productId,
                                         Who who) {
        OffsetDateTime start = from.atStartOfDay(props.zone()).toOffsetDateTime();
        OffsetDateTime end = to.plusDays(1).atStartOfDay(props.zone()).toOffsetDateTime();
        var p = new MapSqlParameterSource().addValue("from", start).addValue("to", end);
        if (categoryId != null) p.addValue("categoryId", categoryId);
        if (productId != null) p.addValue("productId", productId);
        if (who.sellerId() != null) p.addValue("sellerId", who.sellerId());
        if (who.channel() != null) p.addValue("channel", who.channel());
        return p;
    }

    private static BigDecimal money(BigDecimal v) {
        return v == null ? ZERO : v.setScale(2, RoundingMode.HALF_UP);
    }
}
