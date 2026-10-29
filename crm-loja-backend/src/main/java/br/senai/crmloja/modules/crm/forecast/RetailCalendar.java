package br.senai.crmloja.modules.crm.forecast;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Set;
import java.util.function.IntFunction;

// Calendário da loja para a previsão de vendas (spec crm/12, RN57 e RN58): feriados e datas comerciais.
// Os feriados saem de regras (não de uma lista de datas), para valer em qualquer ano. No shopping, feriado tem o
// horário de domingo. O seed de demonstração usa o mesmo isHoliday.
public final class RetailCalendar {

    private RetailCalendar() {}

    // Feriados de data fixa: nacionais, o estadual de Santa Catarina e o municipal de Tubarão.
    private static final Set<MonthDay> FIXED = Set.of(
            MonthDay.of(1, 1),    // Confraternização Universal
            MonthDay.of(4, 21),   // Tiradentes
            MonthDay.of(5, 1),    // Dia do Trabalho
            MonthDay.of(8, 11),   // Dia do Estado de Santa Catarina (estadual)
            MonthDay.of(9, 7),    // Independência do Brasil
            MonthDay.of(9, 15),   // Nossa Senhora da Piedade (municipal de Tubarão)
            MonthDay.of(10, 12),  // Nossa Senhora Aparecida
            MonthDay.of(11, 2),   // Finados
            MonthDay.of(11, 15),  // Proclamação da República
            MonthDay.of(11, 20),  // Consciência Negra
            MonthDay.of(12, 25)); // Natal

    public static boolean isHoliday(LocalDate d) {
        if (FIXED.contains(MonthDay.from(d))) return true;
        LocalDate easter = easter(d.getYear());
        return d.equals(easter.minusDays(47))   // Carnaval
                || d.equals(easter.minusDays(2))    // Sexta-feira Santa
                || d.equals(easter.plusDays(60));   // Corpus Christi
    }

    // Data comercial: o movimento sobe aos poucos nos `rampDays` dias antes da data e cai depois dela.
    // `defaultUplift` é a estimativa padrão do varejo para o dia de pico (1,5 = 50% acima do normal); vale até o
    // histórico ter a janela inteira da data, quando o aumento passa a ser aprendido (RN58).
    public record Event(String name, int rampDays, double defaultUplift, IntFunction<LocalDate> dateInYear) {

        public LocalDate date(int year) {
            return dateInYear.apply(year);
        }

        // Quanto da rampa já subiu no dia d (0 fora da janela; perto de 1 no dia da data).
        public double progress(LocalDate d) {
            LocalDate peak = date(d.getYear());
            long left = ChronoUnit.DAYS.between(d, peak);
            if (left < 0 || left > rampDays) return 0;
            return 1.0 - (double) left / (rampDays + 1);
        }

        public LocalDate windowStart(int year) {
            return date(year).minusDays(rampDays);
        }
    }

    public static final List<Event> EVENTS = List.of(
            new Event("Dia das Mães", 7, 1.50, y -> secondSunday(y, 5)),
            new Event("Dia dos Namorados", 6, 1.30, y -> LocalDate.of(y, 6, 12)),
            new Event("Dia dos Pais", 7, 1.40, y -> secondSunday(y, 8)),
            new Event("Dia do Cliente", 4, 1.20, y -> LocalDate.of(y, 9, 15)),
            new Event("Dia das Crianças", 5, 1.30, y -> LocalDate.of(y, 10, 12)),
            new Event("Black Friday", 3, 1.80, RetailCalendar::blackFriday),
            new Event("Natal", 12, 2.00, y -> LocalDate.of(y, 12, 24)));

    // Feriado ou dentro da janela de alguma data comercial: fica fora do aprendizado do "dia normal".
    public static boolean isSpecial(LocalDate d) {
        if (isHoliday(d)) return true;
        for (Event e : EVENTS) if (e.progress(d) > 0) return true;
        return false;
    }

    // Semana do salário: pagamento por volta do dia 5 e adiantamento por volta do dia 20.
    public static boolean isPayday(LocalDate d) {
        int dom = d.getDayOfMonth();
        return (dom >= 5 && dom <= 9) || (dom >= 20 && dom <= 24);
    }

    static LocalDate secondSunday(int year, int month) {
        return LocalDate.of(year, month, 1).with(TemporalAdjusters.dayOfWeekInMonth(2, DayOfWeek.SUNDAY));
    }

    static LocalDate blackFriday(int year) {
        return LocalDate.of(year, 11, 1).with(TemporalAdjusters.lastInMonth(DayOfWeek.FRIDAY));
    }

    // Algoritmo de Meeus/Jones/Butcher: domingo de Páscoa no calendário gregoriano.
    static LocalDate easter(int year) {
        int a = year % 19;
        int b = year / 100;
        int c = year % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31;
        int day = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(year, month, day);
    }
}
