package br.senai.crmloja.modules.crm.forecast;

import br.senai.crmloja.modules.crm.forecast.RetailCalendar.Event;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Previsão de vendas por dia (spec crm/12, RN55 a RN59). Conta pura: sem banco e sem relógio. A tela, as metas
// sugeridas e a Thalita usam exatamente esta conta, e ela é testada sozinha (ForecastModelTest).
//
// Em palavras simples: previsão do dia = nível atual × dia da semana (ou feriado) × semana do salário × data
// comercial × mês do ano. Cada fator é aprendido com o histórico; o que o histórico ainda não tem (Black Friday sem
// um ano de dados, por exemplo) usa a estimativa padrão do varejo e fica marcado como tal.
public final class ForecastModel {

    // Um dia do histórico: faturamento e número de vendas confirmadas.
    public record Day(LocalDate date, double revenue, double sales) {}

    public record EventFactor(Event event, double uplift, boolean learned) {}

    // O que a previsão de um dia levou em conta.
    public record Prediction(LocalDate date, double revenue, double sales, boolean holiday, List<String> events) {}

    public record Range(double total, double low, double high) {}

    static final int MIN_DAYS_WITH_SALES = 14;
    static final int WEEKDAY_WINDOW = 84;
    static final int BASE_WINDOW = 28;
    static final int ERROR_WINDOW = 56;
    static final int LOCAL_RADIUS = 14;
    static final int YEAR = 365;
    static final double Z80 = 1.28;

    private final double[] weekday = new double[7];   // índice 0 = segunda
    private double payday = 1.0;
    private boolean paydayLearned;
    private double holiday;
    private boolean holidayLearned;
    private final Map<String, EventFactor> events = new LinkedHashMap<>();
    private double[] month;                            // 12 fatores, ou null sem um ano de histórico
    private double baselineRevenue;
    private double baselineSales;
    private double sigma = 0.25;

    private ForecastModel() {}

    // Aprende com o histórico (dias contínuos, do mais antigo até ontem). Devolve null quando não dá para prever.
    public static ForecastModel fit(List<Day> history) {
        long withSales = history.stream().filter(d -> d.revenue() > 0).count();
        if (withSales < MIN_DAYS_WITH_SALES) return null;
        ForecastModel m = new ForecastModel();
        m.learn(history);
        return m;
    }

    private void learn(List<Day> h) {
        int n = h.size();
        List<Day> recent = h.subList(Math.max(0, n - WEEKDAY_WINDOW), n);

        // 1) Dia da semana: média de cada dia da semana nos dias normais ÷ média das médias.
        double[] sum = new double[7];
        int[] cnt = new int[7];
        for (Day d : recent) {
            if (RetailCalendar.isSpecial(d.date())) continue;
            int i = d.date().getDayOfWeek().getValue() - 1;
            sum[i] += d.revenue();
            cnt[i]++;
        }
        double meanOfMeans = 0;
        int seen = 0;
        for (int i = 0; i < 7; i++) {
            if (cnt[i] >= 2) {
                meanOfMeans += sum[i] / cnt[i];
                seen++;
            }
        }
        meanOfMeans = seen == 0 ? 0 : meanOfMeans / seen;
        for (int i = 0; i < 7; i++) {
            weekday[i] = cnt[i] >= 2 && meanOfMeans > 0 ? (sum[i] / cnt[i]) / meanOfMeans : 1.0;
        }
        normalize(weekday);

        // 2) Semana do salário: tirado o dia da semana, dias 5–9 e 20–24 contra os outros dias normais.
        double in = 0, out = 0;
        int nIn = 0, nOut = 0;
        for (Day d : recent) {
            if (RetailCalendar.isSpecial(d.date())) continue;
            double r = d.revenue() / weekdayOf(d.date());
            if (RetailCalendar.isPayday(d.date())) { in += r; nIn++; } else { out += r; nOut++; }
        }
        if (nIn >= 4 && nOut >= 4 && out > 0) {
            payday = clamp((in / nIn) / (out / nOut), 0.8, 1.4);
            paydayLearned = true;
        }

        // 3) Feriado: real ÷ (nível local × salário) nos feriados fora de datas comerciais. Sem 2 feriados, vale domingo.
        double hs = 0;
        int hn = 0;
        for (Day d : h) {
            if (!RetailCalendar.isHoliday(d.date()) || inAnyEvent(d.date())) continue;
            double level = localLevel(h, d.date());
            if (level <= 0) continue;
            hs += d.revenue() / (level * paydayOf(d.date()));
            hn++;
        }
        if (hn >= 2) {
            holiday = clamp(hs / hn, 0.3, 3.0);
            holidayLearned = true;
        } else {
            holiday = weekday[DayOfWeek.SUNDAY.getValue() - 1];
        }

        // 4) Datas comerciais: aprende o pico quando a janela inteira está no histórico.
        LocalDate first = h.get(0).date();
        LocalDate last = h.get(n - 1).date();
        for (Event e : RetailCalendar.EVENTS) {
            double upliftSum = 0;
            int occurrences = 0;
            for (int y = first.getYear(); y <= last.getYear(); y++) {
                LocalDate start = e.windowStart(y);
                LocalDate peak = e.date(y);
                if (start.isBefore(first) || peak.isAfter(last)) continue;
                double actual = 0, expected = 0, progressSum = 0;
                int days = 0;
                for (LocalDate d = start; !d.isAfter(peak); d = d.plusDays(1)) {
                    Day day = h.get((int) ChronoUnit.DAYS.between(first, d));
                    actual += day.revenue();
                    expected += localLevel(h, d) * dayOf(d) * paydayOf(d);
                    progressSum += e.progress(d);
                    days++;
                }
                if (expected <= 0 || days == 0) continue;
                double ratio = actual / expected;
                double avgProgress = progressSum / days;
                upliftSum += clamp(1 + (ratio - 1) / avgProgress, 1.0, 3.0);
                occurrences++;
            }
            events.put(e.name(), occurrences > 0
                    ? new EventFactor(e, upliftSum / occurrences, true)
                    : new EventFactor(e, e.defaultUplift(), false));
        }

        // 5) Mês do ano: só com um ano inteiro de histórico (o último ano).
        if (n >= YEAR) {
            double[] ms = new double[12];
            int[] mc = new int[12];
            double all = 0;
            for (Day d : h.subList(n - YEAR, n)) {
                double r = d.revenue() / (dayOf(d.date()) * paydayOf(d.date()) * eventOf(d.date()));
                ms[d.date().getMonthValue() - 1] += r;
                mc[d.date().getMonthValue() - 1]++;
                all += r;
            }
            double avg = all / YEAR;
            month = new double[12];
            for (int i = 0; i < 12; i++) month[i] = mc[i] == 0 || avg <= 0 ? 1.0 : (ms[i] / mc[i]) / avg;
        }

        // 6) Nível atual: últimos 28 dias, cada um dividido por todos os seus fatores.
        double rs = 0, ss = 0;
        List<Day> base = h.subList(Math.max(0, n - BASE_WINDOW), n);
        for (Day d : base) {
            double f = factor(d.date());
            rs += d.revenue() / f;
            ss += d.sales() / f;
        }
        baselineRevenue = rs / base.size();
        baselineSales = ss / base.size();

        // 7) Tamanho do erro diário (para a faixa provável): dias normais dos últimos 56.
        List<Double> logs = new ArrayList<>();
        for (Day d : h.subList(Math.max(0, n - ERROR_WINDOW), n)) {
            if (d.revenue() <= 0 || RetailCalendar.isSpecial(d.date())) continue;
            double fitted = localLevel(h, d.date()) * dayOf(d.date()) * paydayOf(d.date());
            if (fitted > 0) logs.add(Math.log(d.revenue() / fitted));
        }
        if (logs.size() >= 10) {
            double mean = logs.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double var = logs.stream().mapToDouble(x -> (x - mean) * (x - mean)).sum() / (logs.size() - 1);
            sigma = clamp(Math.sqrt(var), 0.05, 0.8);
        }
    }

    // ------------------------------------------------------------------------------------------- prever

    public Prediction predict(LocalDate d) {
        double f = factor(d);
        List<String> active = new ArrayList<>();
        for (EventFactor ef : events.values()) if (ef.event().progress(d) > 0) active.add(ef.event().name());
        return new Prediction(d, baselineRevenue * f, baselineSales * f, RetailCalendar.isHoliday(d), active);
    }

    public List<Prediction> predict(LocalDate from, LocalDate to) {
        List<Prediction> out = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) out.add(predict(d));
        return out;
    }

    // Soma do período com a faixa provável (~80%): o erro de cada dia se compensa em parte na soma.
    public Range range(List<Prediction> days) {
        double total = 0, squares = 0;
        for (Prediction p : days) {
            total += p.revenue();
            squares += p.revenue() * p.revenue();
        }
        if (total <= 0) return new Range(0, 0, 0);
        double rel = Z80 * sigma * Math.sqrt(squares) / total;
        return new Range(total, Math.max(0, total * (1 - rel)), total * (1 + rel));
    }

    // Erro médio por semana (%), refazendo a previsão como se fosse 28 dias atrás (RN59). null com menos de 56 dias.
    public static Double weeklyErrorPercent(List<Day> history) {
        int n = history.size();
        if (n < 2 * BASE_WINDOW) return null;
        ForecastModel past = fit(history.subList(0, n - BASE_WINDOW));
        if (past == null) return null;
        double errors = 0;
        int weeks = 0;
        for (int w = 0; w < 4; w++) {
            double actual = 0, predicted = 0;
            for (int i = 0; i < 7; i++) {
                Day d = history.get(n - BASE_WINDOW + w * 7 + i);
                actual += d.revenue();
                predicted += past.predict(d.date()).revenue();
            }
            if (actual <= 0) continue;
            errors += Math.abs(predicted - actual) / actual;
            weeks++;
        }
        return weeks == 0 ? null : Math.round(errors / weeks * 1000) / 10.0;
    }

    // ------------------------------------------------------------------------------------------- fatores

    double factor(LocalDate d) {
        return dayOf(d) * paydayOf(d) * eventOf(d) * monthOf(d);
    }

    // Dia da semana, ou o fator do feriado (o shopping abre em horário de domingo).
    private double dayOf(LocalDate d) {
        return RetailCalendar.isHoliday(d) ? holiday : weekdayOf(d);
    }

    private double weekdayOf(LocalDate d) {
        return weekday[d.getDayOfWeek().getValue() - 1];
    }

    private double paydayOf(LocalDate d) {
        return RetailCalendar.isPayday(d) ? payday : 1.0;
    }

    // Datas comerciais que se sobrepõem: vale a maior.
    private double eventOf(LocalDate d) {
        double f = 1.0;
        for (EventFactor ef : events.values()) {
            double p = ef.event().progress(d);
            if (p > 0) f = Math.max(f, 1 + (ef.uplift() - 1) * p);
        }
        return f;
    }

    private double monthOf(LocalDate d) {
        return month == null ? 1.0 : month[d.getMonthValue() - 1];
    }

    private static boolean inAnyEvent(LocalDate d) {
        for (Event e : RetailCalendar.EVENTS) if (e.progress(d) > 0) return true;
        return false;
    }

    // Nível "normal" perto de um dia: média dos dias normais a até 14 dias dele, sem dia da semana e salário.
    private double localLevel(List<Day> h, LocalDate d) {
        LocalDate first = h.get(0).date();
        double s = 0;
        int c = 0;
        for (int off = -LOCAL_RADIUS; off <= LOCAL_RADIUS; off++) {
            LocalDate x = d.plusDays(off);
            long idx = ChronoUnit.DAYS.between(first, x);
            if (idx < 0 || idx >= h.size() || RetailCalendar.isSpecial(x)) continue;
            s += h.get((int) idx).revenue() / (weekdayOf(x) * paydayOf(x));
            c++;
        }
        return c == 0 ? 0 : s / c;
    }

    private static void normalize(double[] f) {
        double avg = 0;
        for (double v : f) avg += v;
        avg /= f.length;
        if (avg > 0) for (int i = 0; i < f.length; i++) f[i] /= avg;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    // ------------------------------------------------------------------------------------------- leitura

    public double weekday(DayOfWeek d) { return weekday[d.getValue() - 1]; }
    public double payday() { return payday; }
    public boolean paydayLearned() { return paydayLearned; }
    public double holiday() { return holiday; }
    public boolean holidayLearned() { return holidayLearned; }
    public List<EventFactor> events() { return List.copyOf(events.values()); }
    public double[] monthFactors() { return month == null ? null : month.clone(); }
    public double baselineRevenue() { return baselineRevenue; }
    public double baselineSales() { return baselineSales; }
    public double sigma() { return sigma; }
}
