package br.senai.crmloja.modules.crm.planning;

import br.senai.crmloja.core.config.AppProperties;
import br.senai.crmloja.core.http.InvalidInputException;
import br.senai.crmloja.modules.crm.planning.Coverage.Status;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Painel "O que vai faltar" (spec crm/11): para cada peça ativa, quanto vende por dia, em quantos dias acaba, o que
// já está a caminho (pedidos de compra abertos), os chamados abertos e quanto pedir. Só leitura; a conta de cada peça
// é a do Coverage.
@Service
public class StockPlanningService {

    // Janela da média de vendas: os últimos 30 dias (mesmo critério do dashboard: venda confirmada, pela data da confirmação).
    public static final int WINDOW_DAYS = 30;
    public static final int DEFAULT_HORIZON = 30;

    public record Item(Long productId, String code, String name, String size, String color, String categoryName,
                       BigDecimal price, int stock, int minStock, int soldInWindow, double dailyAvg, Integer daysLeft,
                       LocalDate runOutDate, int incoming, LocalDate nextArrival, int openRequestsQty, int suggestedQty,
                       Status status, boolean needsOrder, boolean covered, boolean arrivesAfterRunOut,
                       OffsetDateTime lastSaleAt) {}

    // Números do topo da tela. staleValue = estoque parado × preço de venda (quanto a loja deixaria de faturar).
    // openOrders = pedidos ainda não recebidos (feito + em trânsito + em conferência), separados em ordered, inTransit e
    // checking. receivedThisWeek = notas que deram entrada nos últimos 7 dias.
    public record Summary(int outOfStock, int critical, int attention, int toOrder, int openRequests, int openOrders,
                          int ordered, int inTransit, int checking, int lateOrders, int arrivingThisWeek,
                          int receivedThisWeek, int stale, BigDecimal staleValue) {}

    public record Planning(LocalDate today, int windowDays, int horizonDays, Summary summary, List<Item> items) {}

    private final NamedParameterJdbcTemplate jdbc;
    private final AppProperties props;

    public StockPlanningService(NamedParameterJdbcTemplate jdbc, AppProperties props) {
        this.jdbc = jdbc;
        this.props = props;
    }

    @Transactional(readOnly = true)
    public Planning build(Integer horizonDays) {
        int horizon = horizonDays == null ? DEFAULT_HORIZON : horizonDays;
        if (horizon < 7 || horizon > 90) {
            throw new InvalidInputException("invalid_horizon", "Escolha cobrir de 7 a 90 dias.");
        }
        LocalDate today = LocalDate.now(props.zone());
        var params = new MapSqlParameterSource()
                .addValue("from", today.minusDays(WINDOW_DAYS - 1L).atStartOfDay(props.zone()).toOffsetDateTime());

        List<Item> items = jdbc.query(SQL, params, (rs, i) -> {
            int stock = rs.getInt("stock_qty");
            int sold = rs.getInt("sold");
            int incoming = rs.getInt("incoming");
            LocalDate next = rs.getObject("next_arrival", LocalDate.class);
            int req = rs.getInt("req_qty");
            int daysInCatalog = (int) java.time.temporal.ChronoUnit.DAYS.between(
                    rs.getObject("created_at", OffsetDateTime.class).atZoneSameInstant(props.zone()).toLocalDate(), today);
            Coverage c = Coverage.of(stock, rs.getInt("min_stock"), sold, WINDOW_DAYS, incoming, next, req, horizon, today,
                    daysInCatalog);
            return new Item(rs.getLong("id"), rs.getString("code"), rs.getString("name"), rs.getString("size"),
                    rs.getString("color"), rs.getString("category_name"), rs.getBigDecimal("price"), stock,
                    rs.getInt("min_stock"), sold, Math.round(c.dailyAvg() * 100) / 100.0, c.daysLeft(), c.runOutDate(),
                    incoming, next, req, c.suggestedQty(), c.status(), c.needsOrder(), c.covered(),
                    c.arrivesAfterRunOut(), rs.getObject("last_sale", OffsetDateTime.class));
        });
        items = items.stream().sorted(Comparator.comparing(Item::status)
                .thenComparing(Item::daysLeft, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Item::name, String.CASE_INSENSITIVE_ORDER)).toList();

        var orderParams = new MapSqlParameterSource().addValue("today", today).addValue("week", today.plusDays(7))
                .addValue("weekAgo", today.minusDays(6).atStartOfDay(props.zone()).toOffsetDateTime());
        Integer openRequests = jdbc.queryForObject("select count(*) from stock_request where status = 'OPEN'",
                orderParams, Integer.class);
        var orders = jdbc.queryForMap("""
                select count(*) filter (where status in ('OPEN', 'IN_TRANSIT', 'CHECKING')) as open_orders,
                       count(*) filter (where status = 'OPEN') as ordered,
                       count(*) filter (where status = 'IN_TRANSIT') as in_transit,
                       count(*) filter (where status = 'CHECKING') as checking,
                       count(*) filter (where status in ('OPEN', 'IN_TRANSIT') and expected_date < :today) as late_orders,
                       count(*) filter (where status in ('OPEN', 'IN_TRANSIT') and expected_date between :today and :week) as arriving,
                       count(*) filter (where status = 'RECEIVED' and closed_at >= :weekAgo) as received_week
                  from purchase_order""", orderParams);

        BigDecimal staleValue = items.stream().filter(i -> i.status() == Status.STALE)
                .map(i -> i.price().multiply(BigDecimal.valueOf(i.stock()))).reduce(BigDecimal.ZERO, BigDecimal::add);
        Summary s = new Summary(
                count(items, Status.OUT), count(items, Status.CRITICAL), count(items, Status.ATTENTION),
                (int) items.stream().filter(Item::needsOrder).count(),
                openRequests == null ? 0 : openRequests,
                num(orders, "open_orders"), num(orders, "ordered"), num(orders, "in_transit"), num(orders, "checking"),
                num(orders, "late_orders"), num(orders, "arriving"), num(orders, "received_week"),
                count(items, Status.STALE), staleValue);
        return new Planning(today, WINDOW_DAYS, horizon, s, items);
    }

    private static int num(java.util.Map<String, Object> row, String key) {
        return ((Number) row.get(key)).intValue();
    }

    private static int count(List<Item> items, Status st) {
        return (int) items.stream().filter(i -> i.status() == st).count();
    }

    // Uma linha por peça ativa. Cada subconsulta soma uma coisa (vendido na janela, última venda de todas, a caminho,
    // chamados abertos), para nenhuma junção multiplicar a outra.
    private static final String SQL = """
            select p.id, p.code, p.name, p.size, p.color, c.name as category_name, p.price, p.stock_qty, p.min_stock, p.created_at,
                   coalesce(s.sold, 0) as sold, ls.last_sale, coalesce(po.incoming, 0) as incoming, po.next_arrival,
                   coalesce(r.req_qty, 0) as req_qty
              from product p
              join category c on c.id = p.category_id
              left join (select si.product_id, sum(si.quantity) as sold
                           from sale_item si join sale sa on sa.id = si.sale_id
                          where sa.status = 'CONFIRMED' and sa.confirmed_at >= :from
                          group by si.product_id) s on s.product_id = p.id
              left join (select si.product_id, max(sa.confirmed_at) as last_sale
                           from sale_item si join sale sa on sa.id = si.sale_id
                          where sa.status = 'CONFIRMED'
                          group by si.product_id) ls on ls.product_id = p.id
              left join (select i.product_id, sum(i.quantity) as incoming, min(o.expected_date) as next_arrival
                           from purchase_order_item i join purchase_order o on o.id = i.order_id
                          where o.status in ('OPEN', 'IN_TRANSIT', 'CHECKING')
                          group by i.product_id) po on po.product_id = p.id
              left join (select product_id, sum(quantity) as req_qty
                           from stock_request where status = 'OPEN' and product_id is not null
                          group by product_id) r on r.product_id = p.id
             where p.is_active""";
}
