package br.senai.crmloja.modules.crm.target;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

// Conta do ritmo (spec crm/06, REQ-05 a REQ-08), sem banco. Outubro/2026: dia 1 é quinta; domingos 4, 11, 18 e 25.
class PaceTest {

    static final LocalDate SABADO_10_OUT = LocalDate.of(2026, 10, 10);

    @Test
    void diaUtilESegundaASabadoEDomingoNaoConta() {
        assertThat(Pace.isBusinessDay(LocalDate.of(2026, 10, 10))).isTrue();    // sábado
        assertThat(Pace.isBusinessDay(LocalDate.of(2026, 10, 11))).isFalse();   // domingo
        assertThat(Pace.isBusinessDay(LocalDate.of(2026, 10, 12))).isTrue();    // segunda (feriado não é descontado)
    }

    @Test
    void calendarioDoMesAtualPassadoEFuturo() {
        var atual = Pace.calendar(YearMonth.of(2026, 10), SABADO_10_OUT);
        assertThat(atual.inMonth()).isEqualTo(27);     // 31 dias − 4 domingos
        assertThat(atual.elapsed()).isEqualTo(9);      // 1 a 10, menos o domingo 4
        assertThat(atual.left()).isEqualTo(18);
        assertThat(atual.closed()).isFalse();

        var setembro = Pace.calendar(YearMonth.of(2026, 9), SABADO_10_OUT);
        assertThat(setembro.inMonth()).isEqualTo(26);
        assertThat(setembro.elapsed()).isEqualTo(26);
        assertThat(setembro.left()).isZero();
        assertThat(setembro.closed()).isTrue();

        var novembro = Pace.calendar(YearMonth.of(2026, 11), SABADO_10_OUT);   // começa num domingo
        assertThat(novembro.inMonth()).isEqualTo(25);
        assertThat(novembro.elapsed()).isZero();
        assertThat(novembro.left()).isEqualTo(25);
    }

    @Test
    void ritmoProjecaoEQuantoFaltaPorDia() {
        var cal = Pace.calendar(YearMonth.of(2026, 10), SABADO_10_OUT);
        var r = Pace.compute(new BigDecimal("2700.00"), new BigDecimal("900.00"), cal);
        assertThat(r.perBusinessDay()).isEqualByComparingTo("100.00");   // 900 ÷ 9 dias úteis
        assertThat(r.projection()).isEqualByComparingTo("2700.00");      // 100 × 27
        assertThat(r.remaining()).isEqualByComparingTo("1800.00");
        assertThat(r.neededPerDay()).isEqualByComparingTo("100.00");     // 1800 ÷ 18 dias restantes
        assertThat(r.percent()).isEqualByComparingTo("33.3");
        assertThat(r.status()).isEqualTo(Pace.Status.ON_TRACK);
    }

    @Test
    void situacaoPelaProjecao() {
        var cal = Pace.calendar(YearMonth.of(2026, 10), SABADO_10_OUT);
        BigDecimal meta = new BigDecimal("2700.00");
        assertThat(Pace.compute(meta, new BigDecimal("750.00"), cal).status()).isEqualTo(Pace.Status.ATTENTION);   // proj. 2250 = 83%
        assertThat(Pace.compute(meta, new BigDecimal("600.00"), cal).status()).isEqualTo(Pace.Status.BEHIND);      // proj. 1800 = 67%
        assertThat(Pace.compute(meta, new BigDecimal("2700.00"), cal).status()).isEqualTo(Pace.Status.ACHIEVED);
    }

    @Test
    void mesFechadoSoBateOuNaoBate() {
        var setembro = Pace.calendar(YearMonth.of(2026, 9), SABADO_10_OUT);
        var naoBateu = Pace.compute(new BigDecimal("5000.00"), new BigDecimal("4999.99"), setembro);
        assertThat(naoBateu.status()).isEqualTo(Pace.Status.MISSED);
        assertThat(naoBateu.projection()).isEqualByComparingTo("4999.99");   // fechado: projeção = realizado
        assertThat(naoBateu.neededPerDay()).isNull();
        assertThat(Pace.compute(new BigDecimal("5000.00"), new BigDecimal("5000.00"), setembro).status())
                .isEqualTo(Pace.Status.ACHIEVED);
    }

    @Test
    void semMetaEMesQueAindaNaoComecou() {
        var cal = Pace.calendar(YearMonth.of(2026, 10), SABADO_10_OUT);
        var semMeta = Pace.compute(null, new BigDecimal("900.00"), cal);
        assertThat(semMeta.status()).isEqualTo(Pace.Status.NO_TARGET);
        assertThat(semMeta.remaining()).isNull();
        assertThat(semMeta.percent()).isNull();
        assertThat(semMeta.projection()).isEqualByComparingTo("2700.00");   // o ritmo aparece mesmo sem meta

        var novembro = Pace.calendar(YearMonth.of(2026, 11), SABADO_10_OUT);
        var futuro = Pace.compute(new BigDecimal("3000.00"), BigDecimal.ZERO, novembro);
        assertThat(futuro.status()).isEqualTo(Pace.Status.NOT_STARTED);
        assertThat(futuro.perBusinessDay()).isEqualByComparingTo("0.00");
        assertThat(futuro.neededPerDay()).isEqualByComparingTo("120.00");   // 3000 ÷ 25
    }

    @Test
    void ultimoDiaUtilPedeTudoQueFaltaHoje() {
        // 31/10/2026 é sábado, último dia útil de outubro: não sobra dia depois de hoje.
        var cal = Pace.calendar(YearMonth.of(2026, 10), LocalDate.of(2026, 10, 31));
        assertThat(cal.left()).isZero();
        var r = Pace.compute(new BigDecimal("3000.00"), new BigDecimal("2500.00"), cal);
        assertThat(r.neededPerDay()).isEqualByComparingTo("500.00");
    }
}
