package br.senai.crmloja.modules.crm.target;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;

// Conta do ritmo de vendas do mês (spec crm/06, REQ-05 a REQ-08). Funções puras, sem banco: o painel do vendedor,
// o quadro do gerente e a Talita usam exatamente a mesma conta, e ela é testada sozinha (PaceTest).
public final class Pace {

    private Pace() {}

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    // Abaixo de 80% da meta na projeção o vendedor está "abaixo do ritmo"; entre 80% e 100%, "atenção".
    private static final BigDecimal ATTENTION_FLOOR = new BigDecimal("0.80");

    public enum Status { NO_TARGET, NOT_STARTED, ACHIEVED, ON_TRACK, ATTENTION, BEHIND, MISSED }

    // Dias úteis do mês. Dia útil = segunda a sábado (a loja de shopping abre aos sábados; domingo não conta,
    // mas a venda de domingo entra no realizado). Feriado não é descontado: o shopping abre em horário reduzido.
    public record Calendar(int inMonth, int elapsed, int left, boolean closed) {}

    public record Result(BigDecimal perBusinessDay, BigDecimal projection, BigDecimal remaining,
                         BigDecimal neededPerDay, BigDecimal percent, Status status) {}

    public static boolean isBusinessDay(LocalDate d) {
        return d.getDayOfWeek() != DayOfWeek.SUNDAY;
    }

    // elapsed conta de 1º até hoje, inclusive; left conta de amanhã até o fim do mês.
    public static Calendar calendar(YearMonth month, LocalDate today) {
        int inMonth = count(month.atDay(1), month.atEndOfMonth());
        YearMonth current = YearMonth.from(today);
        if (month.isBefore(current)) return new Calendar(inMonth, inMonth, 0, true);
        if (month.isAfter(current)) return new Calendar(inMonth, 0, inMonth, false);
        int elapsed = count(month.atDay(1), today);
        return new Calendar(inMonth, elapsed, inMonth - elapsed, false);
    }

    public static Result compute(BigDecimal target, BigDecimal revenue, Calendar cal) {
        BigDecimal perDay = cal.elapsed() == 0 ? money(BigDecimal.ZERO)
                : revenue.divide(BigDecimal.valueOf(cal.elapsed()), 2, RoundingMode.HALF_UP);
        // Projeção: o ritmo de até agora mantido no mês inteiro. Mês fechado: a projeção é o próprio realizado.
        BigDecimal projection = cal.closed() || cal.elapsed() == 0 ? money(revenue)
                : revenue.multiply(BigDecimal.valueOf(cal.inMonth()))
                        .divide(BigDecimal.valueOf(cal.elapsed()), 2, RoundingMode.HALF_UP);

        if (target == null) {
            return new Result(perDay, projection, null, null, null, Status.NO_TARGET);
        }
        BigDecimal remaining = money(target.subtract(revenue).max(BigDecimal.ZERO));
        BigDecimal percent = revenue.multiply(HUNDRED).divide(target, 1, RoundingMode.HALF_UP);
        // Quanto precisa vender por dia útil daqui para a frente. Último dia útil (left = 0): tudo o que falta, hoje.
        BigDecimal needed = cal.closed() ? null
                : cal.left() == 0 ? remaining
                : remaining.divide(BigDecimal.valueOf(cal.left()), 2, RoundingMode.HALF_UP);
        return new Result(perDay, projection, remaining, needed, percent, status(target, revenue, projection, cal));
    }

    private static Status status(BigDecimal target, BigDecimal revenue, BigDecimal projection, Calendar cal) {
        if (revenue.compareTo(target) >= 0) return Status.ACHIEVED;
        if (cal.closed()) return Status.MISSED;
        if (cal.elapsed() == 0) return Status.NOT_STARTED;
        if (projection.compareTo(target) >= 0) return Status.ON_TRACK;
        if (projection.compareTo(target.multiply(ATTENTION_FLOOR)) >= 0) return Status.ATTENTION;
        return Status.BEHIND;
    }

    private static int count(LocalDate from, LocalDate to) {
        int n = 0;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) if (isBusinessDay(d)) n++;
        return n;
    }

    private static BigDecimal money(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}
