package br.senai.crmloja.modules.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.senai.crmloja.core.identity.AppUser;
import br.senai.crmloja.support.IntegrationTest;
import br.senai.crmloja.support.TestData;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

// Spec crm/12: previsão de vendas e metas sugeridas. O banco é compartilhado com os outros testes, então cada teste
// grava um histórico retroativo próprio (vendas confirmadas de dois vendedores novos, sem itens, que o dashboard não
// conta) e confere proporções, não totais absolutos.
class ForecastIT extends IntegrationTest {

    static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    @Autowired JdbcTemplate jdbc;

    Cookie manager;

    @BeforeEach
    void setUp() throws Exception {
        manager = loginAs("MANAGER");
    }

    AppUser newSeller() {
        return users.create(TestData.unique("prev") + "@loja.local", "SELLER", false);
    }

    // Uma venda confirmada por dia, de `days` dias atrás até ontem, ao meio-dia no fuso da loja.
    void history(AppUser seller, int days, String totalPerDay) {
        LocalDate today = LocalDate.now(ZONE);
        for (int i = days; i >= 1; i--) {
            var at = today.minusDays(i).atTime(12, 0).atZone(ZONE).toOffsetDateTime();
            jdbc.update("insert into sale (status, total, confirmed_at, created_by, created_at) "
                    + "values ('CONFIRMED'::sale_status, ?::numeric, ?, ?, ?)", totalPerDay, at, seller.getId(), at);
        }
    }

    JsonNode rowOf(JsonNode s, long sellerId) {
        for (JsonNode r : s.get("rows")) if (r.get("sellerId").asLong() == sellerId) return r;
        throw new AssertionError("vendedor " + sellerId + " não está nas sugestões");
    }

    @Test // REQ-01, REQ-02
    void gerenteVePrevisaoPorPeriodoComOsFatores() throws Exception {
        history(newSeller(), 40, "500.00");

        JsonNode f = body(mvc.perform(get("/crm/forecast").param("months", "2").cookie(manager))
                .andExpect(status().isOk()).andReturn());
        // 7 dias, 30 dias, fechamento do mês e 2 meses.
        assertThat(f.get("periods")).hasSize(5);
        assertThat(f.get("periods").get(0).get("key").asText()).isEqualTo("NEXT_7");
        assertThat(f.get("periods").get(2).get("key").asText()).isEqualTo("THIS_MONTH");
        for (JsonNode p : f.get("periods")) {
            assertThat(p.get("low").decimalValue()).isLessThanOrEqualTo(p.get("revenue").decimalValue());
            assertThat(p.get("high").decimalValue()).isGreaterThanOrEqualTo(p.get("revenue").decimalValue());
        }
        // Gráfico: 28 dias realizados + 60 previstos.
        assertThat(f.get("days")).hasSize(88);
        assertThat(f.get("days").get(0).get("actual").isNull()).isFalse();
        assertThat(f.get("days").get(87).get("revenue").isNull()).isFalse();
        assertThat(f.get("model").get("weekday")).hasSize(7);
        assertThat(f.get("model").get("events")).hasSize(7);
    }

    @Test // REQ-01
    void mesesForaDoLimiteESoGerente() throws Exception {
        mvc.perform(get("/crm/forecast").param("months", "13").cookie(manager))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("invalid_months"));
        mvc.perform(get("/crm/forecast").cookie(loginAs("SELLER"))).andExpect(status().isForbidden());
        mvc.perform(get("/crm/forecast").cookie(loginAs("STOCK_KEEPER"))).andExpect(status().isForbidden());
    }

    @Test // REQ-03
    void metaSugeridaSegueAParteDeCadaVendedorEOCrescimento() throws Exception {
        AppUser maior = newSeller();
        AppUser menor = newSeller();
        AppUser semVenda = newSeller();
        history(maior, 40, "2000.00");
        history(menor, 40, "1000.00");
        String next = YearMonth.now(ZONE).plusMonths(1).toString();

        JsonNode s0 = body(mvc.perform(get("/crm/targets/suggestions").param("month", next).cookie(manager))
                .andExpect(status().isOk()).andReturn());
        JsonNode a = rowOf(s0, maior.getId());
        JsonNode b = rowOf(s0, menor.getId());
        assertThat(a.get("suggested").decimalValue().doubleValue() / b.get("suggested").decimalValue().doubleValue())
                .isCloseTo(2.0, within(0.05));
        assertThat(a.get("sharePercent").decimalValue().doubleValue()
                / b.get("sharePercent").decimalValue().doubleValue()).isCloseTo(2.0, within(0.05));

        JsonNode none = rowOf(s0, semVenda.getId());
        assertThat(none.get("suggested").isNull()).isTrue();
        assertThat(none.get("reason").asText()).contains("Sem vendas nos últimos 90 dias");

        JsonNode s10 = body(mvc.perform(get("/crm/targets/suggestions").param("month", next).param("growth", "10")
                .cookie(manager)).andExpect(status().isOk()).andReturn());
        assertThat(s10.get("growthPercent").asInt()).isEqualTo(10);
        assertThat(rowOf(s10, maior.getId()).get("suggested").decimalValue().doubleValue()
                / a.get("suggested").decimalValue().doubleValue()).isCloseTo(1.10, within(0.02));
        // Toda sugestão é redonda (centena).
        assertThat(a.get("suggested").decimalValue().remainder(java.math.BigDecimal.valueOf(100)).signum()).isZero();
    }

    @Test // REQ-03
    void sugestaoRecusaMesFechadoCrescimentoForaDoLimiteEVendedor() throws Exception {
        String past = YearMonth.now(ZONE).minusMonths(1).toString();
        mvc.perform(get("/crm/targets/suggestions").param("month", past).cookie(manager))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("month_closed"));
        mvc.perform(get("/crm/targets/suggestions").param("growth", "60").cookie(manager))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("invalid_growth"));
        mvc.perform(get("/crm/targets/suggestions").cookie(loginAs("SELLER"))).andExpect(status().isForbidden());
    }
}
