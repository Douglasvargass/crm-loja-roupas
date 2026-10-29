package br.senai.crmloja.modules.crm.forecast;

import br.senai.crmloja.core.config.AppProperties;
import br.senai.crmloja.core.http.BusinessRuleException;
import br.senai.crmloja.core.http.InvalidInputException;
import br.senai.crmloja.modules.crm.forecast.ForecastModel.Day;
import br.senai.crmloja.modules.crm.forecast.ForecastModel.Prediction;
import br.senai.crmloja.modules.crm.forecast.ForecastModel.Range;
import br.senai.crmloja.modules.crm.forecast.dto.ForecastDtos.Accuracy;
import br.senai.crmloja.modules.crm.forecast.dto.ForecastDtos.DayPoint;
import br.senai.crmloja.modules.crm.forecast.dto.ForecastDtos.EventInfo;
import br.senai.crmloja.modules.crm.forecast.dto.ForecastDtos.Factor;
import br.senai.crmloja.modules.crm.forecast.dto.ForecastDtos.Model;
import br.senai.crmloja.modules.crm.forecast.dto.ForecastDtos.MonthSeasonality;
import br.senai.crmloja.modules.crm.forecast.dto.ForecastDtos.Period;
import br.senai.crmloja.modules.crm.forecast.dto.ForecastDtos.SalesForecast;
import br.senai.crmloja.modules.crm.forecast.dto.ForecastDtos.SuggestionRow;
import br.senai.crmloja.modules.crm.forecast.dto.ForecastDtos.TargetSuggestions;
import br.senai.crmloja.modules.crm.forecast.dto.ForecastDtos.WeekdayFactor;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Previsão de vendas e metas sugeridas (spec crm/12). Só leitura: lê o histórico diário de vendas confirmadas e
// entrega à conta pura (ForecastModel). Nada é gravado; aplicar uma meta sugerida usa o PUT de metas de sempre.
@Service
public class ForecastService {

    static final int HISTORY_DAYS = 400;      // um ano inteiro + a janela do nível atual
    static final int CHART_PAST = 28;
    static final int CHART_AHEAD = 60;
    static final int BASIS_DAYS = 90;         // parte de cada vendedor na loja (RN60)
    static final int MONTHS_AHEAD = 12;
    static final Locale PT = Locale.forLanguageTag("pt-BR");

    private final NamedParameterJdbcTemplate jdbc;
    private final AppProperties props;

    public ForecastService(NamedParameterJdbcTemplate jdbc, AppProperties props) {
        this.jdbc = jdbc;
        this.props = props;
    }

    private record Fitted(LocalDate today, List<Day> history, ForecastModel model) {}

    // ------------------------------------------------------------------------------------------ previsão

    @Transactional(readOnly = true)
    public SalesForecast forecast(Integer monthsParam) {
        int months = monthsParam == null ? 3 : monthsParam;
        if (months < 1 || months > MONTHS_AHEAD) {
            throw new InvalidInputException("invalid_months", "Escolha prever de 1 a 12 meses.");
        }
        Fitted f = fit();
        LocalDate today = f.today();
        ForecastModel m = f.model();

        List<Period> periods = new ArrayList<>();
        periods.add(period("NEXT_7", "Próximos 7 dias", today, today.plusDays(6), m, null, f.history()));
        periods.add(period("NEXT_30", "Próximos 30 dias", today, today.plusDays(29), m, null, f.history()));
        YearMonth current = YearMonth.from(today);
        BigDecimal realized = realized(f.history(), current.atDay(1), today.minusDays(1));
        periods.add(period("THIS_MONTH", "Fechamento de " + monthName(current), today, current.atEndOfMonth(), m, realized, f.history()));
        for (int i = 1; i <= months; i++) {
            YearMonth ym = current.plusMonths(i);
            periods.add(period("MONTH_" + ym, capitalize(monthName(ym)) + " de " + ym.getYear(), ym.atDay(1),
                    ym.atEndOfMonth(), m, null, f.history()));
        }

        List<DayPoint> days = new ArrayList<>();
        List<Day> h = f.history();
        for (Day d : h.subList(Math.max(0, h.size() - CHART_PAST), h.size())) {
            days.add(new DayPoint(d.date(), money(d.revenue()), null, null, null, null,
                    RetailCalendar.isHoliday(d.date()), m.predict(d.date()).events()));
        }
        for (Prediction p : m.predict(today, today.plusDays(CHART_AHEAD - 1))) {
            Range r = m.range(List.of(p));
            days.add(new DayPoint(p.date(), null, money(p.revenue()), money(r.low()), money(r.high()),
                    Math.round(p.sales()), p.holiday(), p.events()));
        }
        return new SalesForecast(today, h.get(0).date(), h.size(), model(m, h, today), periods, days);
    }

    private Period period(String key, String label, LocalDate from, LocalDate to, ForecastModel m, BigDecimal realized,
                          List<Day> history) {
        List<Prediction> ps = from.isAfter(to) ? List.of() : m.predict(from, to);
        Range r = m.range(ps);
        double realizedSales = 0;
        if (realized != null) {
            for (Day d : history) if (!d.date().isBefore(from.withDayOfMonth(1)) && d.date().isBefore(from)) realizedSales += d.sales();
        }
        long sales = Math.round(realizedSales + ps.stream().mapToDouble(Prediction::sales).sum());
        BigDecimal base = realized == null ? BigDecimal.ZERO : realized;
        LocalDate shownFrom = realized == null ? from : from.withDayOfMonth(1);
        return new Period(key, label, shownFrom, to, money(base.doubleValue() + r.total()),
                money(base.doubleValue() + r.low()), money(base.doubleValue() + r.high()), sales, realized);
    }

    private Model model(ForecastModel m, List<Day> h, LocalDate today) {
        List<WeekdayFactor> wd = new ArrayList<>();
        for (DayOfWeek d : DayOfWeek.values()) wd.add(new WeekdayFactor(d.name(), round2(m.weekday(d))));
        List<EventInfo> events = new ArrayList<>();
        for (var ef : m.events()) {
            LocalDate next = ef.event().date(today.getYear());
            if (next.isBefore(today)) next = ef.event().date(today.getYear() + 1);
            events.add(new EventInfo(ef.event().name(), next, ef.event().rampDays(), round2(ef.uplift()), ef.learned()));
        }
        events.sort((a, b) -> a.date().compareTo(b.date()));
        double[] mf = m.monthFactors();
        List<Double> monthList = null;
        if (mf != null) {
            monthList = new ArrayList<>();
            for (double v : mf) monthList.add(round2(v));
        }
        Double err = ForecastModel.weeklyErrorPercent(h);
        return new Model(money(m.baselineRevenue()), BigDecimal.valueOf(m.baselineSales()).setScale(1, RoundingMode.HALF_UP),
                wd, new Factor(round2(m.payday()), m.paydayLearned()), new Factor(round2(m.holiday()), m.holidayLearned()),
                new MonthSeasonality(mf != null, monthList), events, err == null ? null : new Accuracy(err, 4));
    }

    // ------------------------------------------------------------------------------------ metas sugeridas

    @Transactional(readOnly = true)
    public TargetSuggestions suggestTargets(YearMonth month, Integer growthParam) {
        int growth = growthParam == null ? 0 : growthParam;
        if (growth < -20 || growth > 50) {
            throw new InvalidInputException("invalid_growth", "O crescimento vai de -20% a +50%.");
        }
        LocalDate todayCheck = LocalDate.now(props.zone());
        YearMonth current = YearMonth.from(todayCheck);
        if (month.isBefore(current)) {
            throw new BusinessRuleException("month_closed", "Esse mês já fechou: a meta dele não muda mais.");
        }
        if (month.isAfter(current.plusMonths(MONTHS_AHEAD))) {
            throw new InvalidInputException("invalid_month", "Escolha um mês de até " + MONTHS_AHEAD + " meses à frente.");
        }
        Fitted f = fit();
        LocalDate today = f.today();
        BigDecimal monthForecast = monthForecast(f, month);

        LocalDate basisTo = today.minusDays(1);
        LocalDate basisFrom = today.minusDays(BASIS_DAYS);
        var params = new MapSqlParameterSource()
                .addValue("from", basisFrom.atStartOfDay(props.zone()).toOffsetDateTime())
                .addValue("to", today.atStartOfDay(props.zone()).toOffsetDateTime())
                .addValue("month", month.atDay(1));
        BigDecimal store = jdbc.queryForObject(
                "select coalesce(sum(total), 0) from sale where status = 'CONFIRMED' and confirmed_at >= :from and confirmed_at < :to",
                params, BigDecimal.class);
        BigDecimal storeRevenue = store == null ? BigDecimal.ZERO : store.setScale(2, RoundingMode.HALF_UP);
        BigDecimal multiplier = BigDecimal.ONE.add(BigDecimal.valueOf(growth).movePointLeft(2));

        List<SuggestionRow> rows = jdbc.query("""
                select u.id, u.full_name, t.amount, coalesce(sum(s.total), 0) as revenue
                  from app_user u
                  join user_module_access a on a.user_id = u.id and a.is_active
                  join module m on m.id = a.module_id and m.code = 'crm'
                  join module_role r on r.id = a.module_role_id and r.code = 'SELLER'
                  left join sales_target t on t.seller_id = u.id and t.period_month = :month
                  left join sale s on s.created_by = u.id and s.status = 'CONFIRMED'
                                  and s.confirmed_at >= :from and s.confirmed_at < :to
                 where u.is_active
                 group by u.id, u.full_name, t.amount
                 order by u.full_name""", params, (rs, i) -> {
            BigDecimal revenue = rs.getBigDecimal("revenue").setScale(2, RoundingMode.HALF_UP);
            if (revenue.signum() == 0 || storeRevenue.signum() == 0) {
                return new SuggestionRow(rs.getLong("id"), rs.getString("full_name"), revenue, BigDecimal.ZERO,
                        rs.getBigDecimal("amount"), null, "Sem vendas nos últimos 90 dias: defina a meta à mão.");
            }
            BigDecimal share = revenue.divide(storeRevenue, 6, RoundingMode.HALF_UP);
            BigDecimal suggested = roundToHundred(monthForecast.multiply(share).multiply(multiplier));
            return new SuggestionRow(rs.getLong("id"), rs.getString("full_name"), revenue,
                    share.movePointRight(2).setScale(1, RoundingMode.HALF_UP), rs.getBigDecimal("amount"), suggested, null);
        });
        return new TargetSuggestions(month.toString(), monthForecast, growth, basisFrom, basisTo, storeRevenue, rows);
    }

    // Mês atual: o que já vendeu até ontem + a previsão de hoje até o fim. Mês futuro: a previsão do mês inteiro.
    private BigDecimal monthForecast(Fitted f, YearMonth month) {
        LocalDate today = f.today();
        ForecastModel m = f.model();
        if (month.equals(YearMonth.from(today))) {
            BigDecimal realized = realized(f.history(), month.atDay(1), today.minusDays(1));
            return money(realized.doubleValue() + m.range(m.predict(today, month.atEndOfMonth())).total());
        }
        return money(m.range(m.predict(month.atDay(1), month.atEndOfMonth())).total());
    }

    // Arredonda para a centena (meta "redonda"), nunca abaixo de R$ 100.
    static BigDecimal roundToHundred(BigDecimal v) {
        BigDecimal r = v.divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
        return r.max(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
    }

    // ------------------------------------------------------------------------------------------ histórico

    // Um dia por linha, de forma contínua, do primeiro dia com venda (até 400 dias atrás) até ontem.
    private Fitted fit() {
        LocalDate today = LocalDate.now(props.zone());
        LocalDate from = today.minusDays(HISTORY_DAYS);
        var params = new MapSqlParameterSource()
                .addValue("tz", props.timeZone())
                .addValue("from", from.atStartOfDay(props.zone()).toOffsetDateTime())
                .addValue("to", today.atStartOfDay(props.zone()).toOffsetDateTime());
        Map<LocalDate, double[]> byDay = new HashMap<>();
        jdbc.query("""
                select (confirmed_at at time zone :tz)::date as day, count(*) as qty, sum(total) as revenue
                  from sale
                 where status = 'CONFIRMED' and confirmed_at >= :from and confirmed_at < :to
                 group by day""", params, rs -> {
            byDay.put(rs.getObject("day", LocalDate.class),
                    new double[]{rs.getBigDecimal("revenue").doubleValue(), rs.getLong("qty")});
        });
        LocalDate first = byDay.keySet().stream().min(LocalDate::compareTo).orElse(today);
        List<Day> history = new ArrayList<>();
        for (LocalDate d = first; d.isBefore(today); d = d.plusDays(1)) {
            double[] v = byDay.getOrDefault(d, new double[]{0, 0});
            history.add(new Day(d, v[0], v[1]));
        }
        ForecastModel model = ForecastModel.fit(history);
        if (model == null) {
            throw new BusinessRuleException("not_enough_history",
                    "Ainda não há vendas suficientes para prever: são precisos pelo menos 14 dias com venda.");
        }
        return new Fitted(today, history, model);
    }

    private static BigDecimal realized(List<Day> h, LocalDate from, LocalDate to) {
        double s = 0;
        for (Day d : h) if (!d.date().isBefore(from) && !d.date().isAfter(to)) s += d.revenue();
        return money(s);
    }

    private static String monthName(YearMonth ym) {
        return ym.getMonth().getDisplayName(TextStyle.FULL, PT);
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static BigDecimal money(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }

    private static double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
