package br.senai.crmloja.modules.crm.planning;

import static org.assertj.core.api.Assertions.assertThat;

import br.senai.crmloja.modules.crm.planning.Coverage.Status;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

// Spec crm/11, RN43 a RN46: a conta do painel "O que vai faltar", caso a caso.
class CoverageTest {

    static final LocalDate HOJE = LocalDate.of(2026, 10, 29);

    @Test // RN43: 60 vendidas em 30 dias = 2 por dia; 6 em estoque acabam em 3 dias → crítico
    void vendeDoisPorDiaETemSeisAcabaEmTresDias() {
        Coverage c = Coverage.of(6, 2, 60, 30, 0, null, 0, 30, HOJE);
        assertThat(c.dailyAvg()).isEqualTo(2.0);
        assertThat(c.daysLeft()).isEqualTo(3);
        assertThat(c.runOutDate()).isEqualTo(HOJE.plusDays(3));
        assertThat(c.status()).isEqualTo(Status.CRITICAL);
        // Pedir: 2 × 30 + mínimo 2 − 6 em estoque = 56.
        assertThat(c.suggestedQty()).isEqualTo(56);
        assertThat(c.needsOrder()).isTrue();
    }

    @Test // RN44: o que está a caminho entra na conta; se cobre, não precisa pedir de novo
    void pedidoACaminhoQueCobreZeraASugestao() {
        Coverage c = Coverage.of(6, 2, 60, 30, 60, HOJE.plusDays(2), 0, 30, HOJE);
        assertThat(c.suggestedQty()).isZero();
        assertThat(c.covered()).isTrue();
        assertThat(c.arrivesAfterRunOut()).isFalse();
        assertThat(c.needsOrder()).isFalse();
    }

    @Test // RN44: pedido que chega depois de acabar fica marcado
    void pedidoQueChegaDepoisDeAcabar() {
        Coverage c = Coverage.of(6, 2, 60, 30, 10, HOJE.plusDays(5), 0, 30, HOJE);
        assertThat(c.arrivesAfterRunOut()).isTrue();
        assertThat(c.covered()).isFalse();
        assertThat(c.suggestedQty()).isEqualTo(46);   // 62 − 6 − 10
    }

    @Test // RN45: chamados abertos da peça somam no quanto pedir
    void chamadosAbertosSomamNaSugestao() {
        Coverage sem = Coverage.of(0, 2, 30, 30, 0, null, 0, 30, HOJE);
        Coverage com = Coverage.of(0, 2, 30, 30, 0, null, 3, 30, HOJE);
        assertThat(sem.status()).isEqualTo(Status.OUT);
        assertThat(com.suggestedQty()).isEqualTo(sem.suggestedQty() + 3);
    }

    @Test // RN46: tem estoque e não vendeu nada no período → parada, sem previsão e sem sugestão
    void semVendaComEstoqueEParada() {
        Coverage c = Coverage.of(12, 2, 0, 30, 0, null, 0, 30, HOJE);
        assertThat(c.status()).isEqualTo(Status.STALE);
        assertThat(c.daysLeft()).isNull();
        assertThat(c.runOutDate()).isNull();
        assertThat(c.suggestedQty()).isZero();
        assertThat(c.needsOrder()).isFalse();
    }

    @Test // Revisão 29/10: peça mais nova que a janela e sem venda é NEW (cedo para julgar), não parada
    void pecaNovaSemVendaNaoEParada() {
        Coverage nova = Coverage.of(12, 2, 0, 30, 0, null, 0, 30, HOJE, 3);
        Coverage velha = Coverage.of(12, 2, 0, 30, 0, null, 0, 30, HOJE, 45);
        assertThat(nova.status()).isEqualTo(Status.NEW);
        assertThat(velha.status()).isEqualTo(Status.STALE);
        assertThat(nova.needsOrder()).isFalse();
    }

    @Test // Faixas: até 7 dias crítico, até 21 atenção, acima disso ok; no mínimo também é crítico
    void faixasDeUrgencia() {
        assertThat(Coverage.of(30, 2, 30, 30, 0, null, 0, 30, HOJE).status()).isEqualTo(Status.OK);         // 30 dias
        assertThat(Coverage.of(15, 2, 30, 30, 0, null, 0, 30, HOJE).status()).isEqualTo(Status.ATTENTION);  // 15 dias
        assertThat(Coverage.of(7, 2, 30, 30, 0, null, 0, 30, HOJE).status()).isEqualTo(Status.CRITICAL);   // 7 dias
        assertThat(Coverage.of(5, 5, 3, 30, 0, null, 0, 30, HOJE).status()).isEqualTo(Status.CRITICAL);    // no mínimo
    }

    @Test // Cobrir mais dias pede mais
    void horizonteMaiorPedeMais() {
        int d30 = Coverage.of(10, 2, 30, 30, 0, null, 0, 30, HOJE).suggestedQty();
        int d60 = Coverage.of(10, 2, 30, 30, 0, null, 0, 60, HOJE).suggestedQty();
        assertThat(d30).isEqualTo(22);   // 30 + 2 − 10
        assertThat(d60).isEqualTo(52);   // 60 + 2 − 10
    }
}
