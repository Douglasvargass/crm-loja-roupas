package br.senai.crmloja.modules.crm.target;

import br.senai.crmloja.core.audit.AuditService;
import br.senai.crmloja.core.config.AppProperties;
import br.senai.crmloja.core.http.BusinessRuleException;
import br.senai.crmloja.core.http.InvalidInputException;
import br.senai.crmloja.core.http.NotFoundException;
import br.senai.crmloja.core.identity.AppUserRepository;
import br.senai.crmloja.core.rbac.RbacService;
import br.senai.crmloja.modules.crm.dashboard.DashboardResponse;
import br.senai.crmloja.modules.crm.dashboard.DashboardResponse.DailyRevenue;
import br.senai.crmloja.modules.crm.dashboard.DashboardService;
import br.senai.crmloja.modules.crm.target.dto.TargetDtos.BoardRow;
import br.senai.crmloja.modules.crm.target.dto.TargetDtos.RecentSale;
import br.senai.crmloja.modules.crm.target.dto.TargetDtos.SellerPanel;
import br.senai.crmloja.modules.crm.target.dto.TargetDtos.TargetBoard;
import br.senai.crmloja.modules.crm.target.dto.TargetDtos.TargetSaved;
import br.senai.crmloja.modules.crm.target.dto.TargetDtos.Today;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Metas por vendedor e o painel de cada vendedor (spec crm/06). Vendedor = quem abriu a venda (sale.created_by),
// a mesma regra do filtro "Vendedor(a)" do dashboard; só vendas CONFIRMED pela data de confirmação no fuso da loja.
@Service
public class TargetService {

    // Até quantos meses à frente o gerente pode deixar a meta definida.
    static final int MONTHS_AHEAD = 12;

    private final SalesTargetRepository targets;
    private final AppUserRepository users;
    private final RbacService rbac;
    private final DashboardService dashboard;
    private final AuditService audit;
    private final NamedParameterJdbcTemplate jdbc;
    private final AppProperties props;

    public TargetService(SalesTargetRepository targets, AppUserRepository users, RbacService rbac,
                         DashboardService dashboard, AuditService audit, NamedParameterJdbcTemplate jdbc,
                         AppProperties props) {
        this.targets = targets;
        this.users = users;
        this.rbac = rbac;
        this.dashboard = dashboard;
        this.audit = audit;
        this.jdbc = jdbc;
        this.props = props;
    }

    // "aaaa-mm" → mês; vazio = mês atual no fuso da loja.
    public YearMonth parseMonth(String raw) {
        if (raw == null || raw.isBlank()) return currentMonth();
        try {
            return YearMonth.parse(raw.trim());
        } catch (DateTimeParseException e) {
            throw new InvalidInputException("invalid_month", "Mês inválido: use o formato aaaa-mm (ex.: 2026-10).");
        }
    }

    // ----------------------------------------------------------------------------------- definir meta

    // REQ-01 a REQ-04: uma meta por vendedor e mês, maior que zero, só para mês atual ou futuro (até 12 meses),
    // só para quem é vendedor ativo. Mudança de valor vai para a auditoria com o valor anterior.
    @Transactional
    public TargetSaved set(Long sellerId, YearMonth month, BigDecimal amount, Long actorId) {
        YearMonth current = currentMonth();
        if (month.isBefore(current)) {
            throw new BusinessRuleException("month_closed", "Esse mês já fechou: a meta dele não muda mais.");
        }
        if (month.isAfter(current.plusMonths(MONTHS_AHEAD))) {
            throw new BusinessRuleException("month_too_far", "Defina metas de no máximo " + MONTHS_AHEAD + " meses à frente.");
        }
        var seller = users.findById(sellerId).orElseThrow(() -> new NotFoundException("Vendedor não encontrado."));
        if (!seller.isActive() || !"SELLER".equals(rbac.roleCodeOf(sellerId, "crm").orElse(null))) {
            throw new BusinessRuleException("not_a_seller", "Essa pessoa não é vendedor(a) ativo(a) na loja.");
        }
        BigDecimal value = amount.setScale(2, RoundingMode.HALF_UP);
        LocalDate first = month.atDay(1);

        var existing = targets.findBySellerIdAndPeriodMonth(sellerId, first);
        BigDecimal before = existing.map(SalesTarget::getAmount).orElse(null);
        if (before != null && before.compareTo(value) == 0) {
            return new TargetSaved(sellerId, month.toString(), before);   // nada mudou, nada a auditar
        }
        try {
            if (existing.isPresent()) {
                existing.get().changeAmount(value, actorId);
                targets.flush();
            } else {
                targets.saveAndFlush(new SalesTarget(sellerId, first, value, actorId));
            }
        } catch (DataIntegrityViolationException e) {
            // Dois gerentes salvando a mesma meta ao mesmo tempo: o índice único segura o segundo.
            throw new BusinessRuleException("target_conflict", "Outra pessoa acabou de definir esta meta. Atualize a página.");
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("month", month.toString());
        payload.put("from", before == null ? null : before.toPlainString());
        payload.put("to", value.toPlainString());
        audit.record(actorId, "crm", "sales_target_set", "app_user", sellerId, payload);
        return new TargetSaved(sellerId, month.toString(), value);
    }

    // -------------------------------------------------------------------------------- painel do vendedor

    // REQ-05 a REQ-10. Reaproveita o dashboard filtrado pelo vendedor, mas devolve só os números dele.
    @Transactional(readOnly = true)
    public SellerPanel panel(Long sellerId, YearMonth month) {
        LocalDate today = LocalDate.now(props.zone());
        if (month.isAfter(YearMonth.from(today))) {
            throw new InvalidInputException("invalid_month", "Esse mês ainda não começou.");
        }
        Pace.Calendar cal = Pace.calendar(month, today);
        LocalDate from = month.atDay(1);
        LocalDate to = cal.closed() ? month.atEndOfMonth() : today;

        DashboardResponse d = dashboard.build(from, to, null, null, null, sellerId);
        BigDecimal target = targets.findBySellerIdAndPeriodMonth(sellerId, from).map(SalesTarget::getAmount).orElse(null);
        Pace.Result pace = Pace.compute(target, d.revenue(), cal);

        Today todayStats = null;
        if (!cal.closed() && !d.revenueByDay().isEmpty()) {
            DailyRevenue last = d.revenueByDay().get(d.revenueByDay().size() - 1);
            todayStats = new Today(last.sales(), last.revenue());
        }
        return new SellerPanel(month.toString(), cal.closed(), sellerId, d.selected().sellerName(), target,
                d.revenue(), d.confirmedSales(), d.averageTicket(), d.cancelledSales(),
                pace.percent(), pace.remaining(), pace.perBusinessDay(), pace.projection(), pace.neededPerDay(),
                cal.inMonth(), cal.elapsed(), cal.left(), pace.status(), todayStats,
                d.revenueByDay(), d.topProducts(), recentSales(sellerId, from, month.atEndOfMonth()));
    }

    // Últimas 10 vendas que o vendedor abriu no mês, de qualquer status (aberta, confirmada, cancelada).
    private List<RecentSale> recentSales(Long sellerId, LocalDate from, LocalDate to) {
        return jdbc.query("""
                select s.id, s.status::text as status, s.created_at, s.confirmed_at, s.total, c.full_name as customer
                  from sale s
                  left join customer c on c.id = s.customer_id
                 where s.created_by = :seller and s.created_at >= :from and s.created_at < :to
                 order by s.created_at desc, s.id desc
                 limit 10""",
                range(from, to).addValue("seller", sellerId),
                (rs, i) -> new RecentSale(rs.getLong("id"), rs.getString("status"),
                        rs.getObject("created_at", OffsetDateTime.class), rs.getObject("confirmed_at", OffsetDateTime.class),
                        rs.getString("customer"), rs.getBigDecimal("total")));
    }

    // ----------------------------------------------------------------------------------- quadro do gerente

    // REQ-11: cada vendedor ativo com meta, realizado, % e situação. Sem ranking: ordem alfabética.
    @Transactional(readOnly = true)
    public TargetBoard board(YearMonth month) {
        LocalDate today = LocalDate.now(props.zone());
        YearMonth current = YearMonth.from(today);
        if (month.isAfter(current.plusMonths(MONTHS_AHEAD))) {
            throw new InvalidInputException("invalid_month", "Escolha um mês de até " + MONTHS_AHEAD + " meses à frente.");
        }
        Pace.Calendar cal = Pace.calendar(month, today);
        LocalDate to = cal.closed() ? month.atEndOfMonth() : today;   // mês futuro: intervalo vazio, realizado zero

        List<BoardRow> rows = jdbc.query("""
                select u.id, u.full_name, t.amount, count(s.id) as qty, coalesce(sum(s.total), 0) as revenue
                  from app_user u
                  join user_module_access a on a.user_id = u.id and a.is_active
                  join module m on m.id = a.module_id and m.code = 'crm'
                  join module_role r on r.id = a.module_role_id and r.code = 'SELLER'
                  left join sales_target t on t.seller_id = u.id and t.period_month = :month
                  left join sale s on s.created_by = u.id and s.status = 'CONFIRMED'
                                  and s.confirmed_at >= :from and s.confirmed_at < :to
                 where u.is_active
                 group by u.id, u.full_name, t.amount
                 order by u.full_name""",
                range(month.atDay(1), to).addValue("month", month.atDay(1)),
                (rs, i) -> {
                    BigDecimal target = rs.getBigDecimal("amount");
                    BigDecimal revenue = rs.getBigDecimal("revenue").setScale(2, RoundingMode.HALF_UP);
                    Pace.Result p = Pace.compute(target, revenue, cal);
                    return new BoardRow(rs.getLong("id"), rs.getString("full_name"), target, revenue, rs.getLong("qty"),
                            p.percent(), p.projection(), p.remaining(), p.status());
                });
        BigDecimal totalTarget = rows.stream().map(BoardRow::target).filter(t -> t != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalRevenue = rows.stream().map(BoardRow::revenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        boolean editable = !month.isBefore(current);
        return new TargetBoard(month.toString(), cal.closed(), editable, cal.inMonth(), cal.elapsed(), cal.left(),
                totalTarget, totalRevenue, rows);
    }

    private YearMonth currentMonth() {
        return YearMonth.now(props.zone());
    }

    // Fim exclusivo: até o dia seguinte 00:00 no fuso da loja, para a venda das 23:59 entrar (igual ao dashboard).
    private MapSqlParameterSource range(LocalDate from, LocalDate to) {
        return new MapSqlParameterSource()
                .addValue("from", from.atStartOfDay(props.zone()).toOffsetDateTime())
                .addValue("to", to.plusDays(1).atStartOfDay(props.zone()).toOffsetDateTime());
    }
}
