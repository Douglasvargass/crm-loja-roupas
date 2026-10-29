package br.senai.crmloja.modules.crm.seed;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.temporal.TemporalAdjusters;
import java.util.Set;

/**
 * Calendário da loja: quando o shopping roda em horário reduzido e quando o movimento sobe.
 *
 * <p>Os feriados saem das regras, não de uma lista fixa de datas, para o seeder continuar correto em qualquer
 * ano. No Farol Shopping, feriado tem o mesmo horário de domingo (14h–21h), por isso {@link #ehFeriado} é o que
 * decide a janela de funcionamento do dia.
 */
final class Feriados {

    private Feriados() {}

    /** Feriados de data fixa: nacionais, o estadual de Santa Catarina e o municipal de Tubarão. */
    private static final Set<MonthDay> FIXOS = Set.of(
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

    static boolean ehFeriado(LocalDate d) {
        if (FIXOS.contains(MonthDay.from(d))) return true;
        LocalDate pascoa = pascoa(d.getYear());
        return d.equals(pascoa.minusDays(47))  // Carnaval
                || d.equals(pascoa.minusDays(2))   // Sexta-feira Santa
                || d.equals(pascoa.plusDays(60));  // Corpus Christi
    }

    /**
     * Multiplicador de movimento das datas comerciais. A véspera pesa mais que o próprio dia em presente:
     * quem compra para o Dia dos Pais compra no sábado anterior.
     */
    static double pesoComercial(LocalDate d) {
        double peso = 1.0;
        peso = Math.max(peso, rampa(d, segundoDomingo(d.getYear(), 5), 7, 1.70));   // Dia das Mães
        peso = Math.max(peso, rampa(d, LocalDate.of(d.getYear(), 6, 12), 6, 1.45)); // Dia dos Namorados
        peso = Math.max(peso, rampa(d, segundoDomingo(d.getYear(), 8), 7, 1.55));   // Dia dos Pais
        peso = Math.max(peso, rampa(d, LocalDate.of(d.getYear(), 9, 15), 4, 1.30)); // Dia do Cliente
        peso = Math.max(peso, rampa(d, LocalDate.of(d.getYear(), 10, 12), 5, 1.40));// Dia das Crianças
        peso = Math.max(peso, rampa(d, blackFriday(d.getYear()), 3, 2.10));         // Black Friday
        peso = Math.max(peso, rampa(d, LocalDate.of(d.getYear(), 12, 24), 12, 2.30)); // Natal

        // Liquidação de inverno no fim de agosto: entra mais gente, com ticket menor.
        if (d.getMonthValue() == 8 && d.getDayOfMonth() >= 24) peso = Math.max(peso, 1.18);
        return peso;
    }

    /**
     * Cresce linearmente até a data e cai depois dela. Fora da janela devolve 1.0, que é o neutro de
     * {@code Math.max} em {@link #pesoComercial}.
     */
    private static double rampa(LocalDate d, LocalDate alvo, int diasAntes, double pico) {
        long falta = java.time.temporal.ChronoUnit.DAYS.between(d, alvo);
        if (falta < 0 || falta > diasAntes) return 1.0;
        double progresso = 1.0 - (double) falta / (diasAntes + 1);
        return 1.0 + (pico - 1.0) * progresso;
    }

    /** Nas datas de presente a procura muda de prateleira, não só de volume. */
    static double pesoCategoria(String familia, LocalDate d) {
        LocalDate pais = segundoDomingo(d.getYear(), 8);
        if (naJanela(d, pais, 7)) {
            return switch (familia) {
                case "CAL" -> 1.50; case "JAQ" -> 1.40; case "ACE" -> 1.40; case "CAM" -> 1.20;
                default -> 0.60;  // vestido não é presente de Dia dos Pais
            };
        }
        LocalDate maes = segundoDomingo(d.getYear(), 5);
        if (naJanela(d, maes, 7)) {
            return switch (familia) {
                case "VES" -> 1.60; case "ACE" -> 1.30; case "CAM" -> 1.10;
                default -> 0.80;
            };
        }
        if (naJanela(d, LocalDate.of(d.getYear(), 10, 12), 5)) {
            return switch (familia) {
                case "CAM" -> 1.50; case "ACE" -> 1.30;
                default -> 0.90;
            };
        }
        return 1.0;
    }

    private static boolean naJanela(LocalDate d, LocalDate alvo, int diasAntes) {
        long falta = java.time.temporal.ChronoUnit.DAYS.between(d, alvo);
        return falta >= 0 && falta <= diasAntes;
    }

    private static LocalDate segundoDomingo(int ano, int mes) {
        return LocalDate.of(ano, mes, 1).with(TemporalAdjusters.dayOfWeekInMonth(2, DayOfWeek.SUNDAY));
    }

    private static LocalDate blackFriday(int ano) {
        return LocalDate.of(ano, 11, 1).with(TemporalAdjusters.lastInMonth(DayOfWeek.FRIDAY));
    }

    /** Algoritmo de Meeus/Jones/Butcher: domingo de Páscoa no calendário gregoriano. */
    private static LocalDate pascoa(int ano) {
        int a = ano % 19;
        int b = ano / 100;
        int c = ano % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int mes = (h + l - 7 * m + 114) / 31;
        int dia = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(ano, mes, dia);
    }
}
