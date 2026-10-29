package br.senai.crmloja.modules.crm.seed;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/**
 * Calendário da loja: quando o shopping roda em horário reduzido e quando o movimento sobe.
 *
 * <p>Os feriados saem das regras, não de uma lista fixa de datas, para o seeder continuar correto em qualquer
 * ano. No Farol Shopping, feriado tem o mesmo horário de domingo (14h–21h), por isso {@link #ehFeriado} é o que
 * decide a janela de funcionamento do dia.
 */
final class Feriados {

    private Feriados() {}

    /** Feriados: a mesma regra da previsão de vendas (forecast/RetailCalendar), para os dois nunca divergirem. */
    static boolean ehFeriado(LocalDate d) {
        return br.senai.crmloja.modules.crm.forecast.RetailCalendar.isHoliday(d);
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
}
