package br.senai.crmloja.modules.crm.forecast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import br.senai.crmloja.modules.crm.forecast.ForecastModel.Day;
import br.senai.crmloja.modules.crm.forecast.ForecastModel.EventFactor;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

// Conta da previsão (spec crm/12, RN55 a RN59), sem banco. Um histórico sintético com fatores conhecidos: o modelo
// precisa recuperar cada fator e prever o que o gerador faria.
class ForecastModelTest {

    static final double BASE = 1000;
    // Segunda a domingo: sábado vende o dobro da segunda.
    static final double[] WEEK = {0.70, 0.76, 0.84, 0.94, 1.20, 1.40, 0.92};
    static final double PAYDAY = 1.10;
    static final double HOLIDAY = 1.05;
    static final double CRIANCAS = 1.40;   // pico do Dia das Crianças no histórico

    static double truth(LocalDate d) {
        double day = RetailCalendar.isHoliday(d) ? HOLIDAY : WEEK[d.getDayOfWeek().getValue() - 1];
        double pay = RetailCalendar.isPayday(d) ? PAYDAY : 1.0;
        double ev = 1.0;
        var criancas = RetailCalendar.EVENTS.stream().filter(e -> e.name().equals("Dia das Crianças")).findFirst().orElseThrow();
        double p = criancas.progress(d);
        if (p > 0) ev = 1 + (CRIANCAS - 1) * p;
        return BASE * day * pay * ev;
    }

    static List<Day> history(LocalDate from, LocalDate to) {
        List<Day> h = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            double r = truth(d);
            h.add(new Day(d, r, r / 200));
        }
        return h;
    }

    // Agosto a outubro de 2026, como os dados de demonstração.
    static final List<Day> TRES_MESES = history(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 10, 28));

    @Test
    void poucosDiasComVendaNaoPreve() {
        assertThat(ForecastModel.fit(TRES_MESES.subList(0, 13))).isNull();
        List<Day> semVenda = new ArrayList<>();
        for (Day d : TRES_MESES) semVenda.add(new Day(d.date(), 0, 0));
        assertThat(ForecastModel.fit(semVenda)).isNull();
    }

    @Test
    void aprendeODiaDaSemanaEOSalario() {
        ForecastModel m = ForecastModel.fit(TRES_MESES);
        double sabadoSobreSegunda = m.weekday(DayOfWeek.SATURDAY) / m.weekday(DayOfWeek.MONDAY);
        assertThat(sabadoSobreSegunda).isCloseTo(2.0, within(0.05));
        assertThat(m.paydayLearned()).isTrue();
        assertThat(m.payday()).isCloseTo(PAYDAY, within(0.03));
    }

    @Test
    void feriadoAprendidoComOsFeriadosDoHistorico() {
        ForecastModel m = ForecastModel.fit(TRES_MESES);
        // 11/08 e 07/09 são feriados fora de datas comerciais (15/09 e 12/10 caem em datas comerciais).
        assertThat(m.holidayLearned()).isTrue();
        assertThat(m.holiday() / m.weekday(DayOfWeek.MONDAY)).isCloseTo(HOLIDAY / WEEK[0], within(0.08));
    }

    @Test
    void dataComercialDoHistoricoEAprendidaEADeForaUsaAEstimativaPadrao() {
        ForecastModel m = ForecastModel.fit(TRES_MESES);
        EventFactor criancas = event(m, "Dia das Crianças");
        assertThat(criancas.learned()).isTrue();
        assertThat(criancas.uplift()).isCloseTo(CRIANCAS, within(0.08));

        EventFactor blackFriday = event(m, "Black Friday");
        assertThat(blackFriday.learned()).isFalse();
        assertThat(blackFriday.uplift()).isEqualTo(1.80);
    }

    @Test
    void preveODiaNormalEADataComercial() {
        ForecastModel m = ForecastModel.fit(TRES_MESES);
        // Segunda 16/11/2026: dia normal (fora do salário, sem data comercial, não é feriado).
        LocalDate segunda = LocalDate.of(2026, 11, 16);
        assertThat(m.predict(segunda).revenue()).isCloseTo(truth(segunda), within(truth(segunda) * 0.04));

        // Black Friday 27/11/2026 (sexta): sobe pela estimativa padrão, ~1,8 × uma sexta normal.
        LocalDate bf = LocalDate.of(2026, 11, 27);
        LocalDate sextaNormal = LocalDate.of(2026, 11, 13);
        var p = m.predict(bf);
        assertThat(p.events()).containsExactly("Black Friday");
        assertThat(p.revenue() / m.predict(sextaNormal).revenue()).isCloseTo(1.80, within(0.05));
    }

    @Test
    void mesDoAnoSoComUmAnoDeHistorico() {
        assertThat(ForecastModel.fit(TRES_MESES).monthFactors()).isNull();
        var umAno = history(LocalDate.of(2025, 9, 1), LocalDate.of(2026, 10, 28));
        assertThat(ForecastModel.fit(umAno).monthFactors()).hasSize(12);
    }

    @Test
    void faixaProvavelEAcertoDasUltimasSemanas() {
        ForecastModel m = ForecastModel.fit(TRES_MESES);
        var semana = m.predict(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 8));
        var r = m.range(semana);
        assertThat(r.low()).isLessThan(r.total());
        assertThat(r.high()).isGreaterThan(r.total());

        // Histórico sem ruído: a previsão refeita 28 dias atrás acerta quase exato.
        assertThat(ForecastModel.weeklyErrorPercent(TRES_MESES)).isLessThan(5.0);
        assertThat(ForecastModel.weeklyErrorPercent(TRES_MESES.subList(0, 50))).isNull();
    }

    @Test
    void calendarioDeFeriadosEDatasComerciais() {
        assertThat(RetailCalendar.isHoliday(LocalDate.of(2026, 9, 15))).isTrue();     // municipal de Tubarão
        assertThat(RetailCalendar.isHoliday(LocalDate.of(2026, 4, 3))).isTrue();      // Sexta-feira Santa 2026
        assertThat(RetailCalendar.isHoliday(LocalDate.of(2026, 10, 13))).isFalse();
        assertThat(RetailCalendar.blackFriday(2026)).isEqualTo(LocalDate.of(2026, 11, 27));
        assertThat(RetailCalendar.secondSunday(2026, 5)).isEqualTo(LocalDate.of(2026, 5, 10));
        assertThat(RetailCalendar.isPayday(LocalDate.of(2026, 11, 5))).isTrue();
        assertThat(RetailCalendar.isPayday(LocalDate.of(2026, 11, 10))).isFalse();
    }

    static EventFactor event(ForecastModel m, String name) {
        return m.events().stream().filter(e -> e.event().name().equals(name)).findFirst().orElseThrow();
    }
}
