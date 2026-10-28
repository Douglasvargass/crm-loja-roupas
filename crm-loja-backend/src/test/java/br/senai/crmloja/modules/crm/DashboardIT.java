package br.senai.crmloja.modules.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.senai.crmloja.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class DashboardIT extends IntegrationTest {

    @Autowired JdbcTemplate jdbc;

    Cookie manager;
    long category;

    @BeforeEach
    void setUp() throws Exception {
        manager = loginAs("MANAGER");
        category = createCategory(manager);
    }

    JsonNode dashboard(String from, String to) throws Exception {
        var req = get("/crm/dashboard").cookie(manager);
        if (from != null) req = req.param("from", from).param("to", to);
        return body(mvc.perform(req).andExpect(status().isOk()).andReturn());
    }

    long confirmedSale(long product, int qty) throws Exception {
        long sale = body(mvc.perform(post("/crm/sales").cookie(manager).contentType(APPLICATION_JSON).content("{}"))
                .andReturn()).get("id").asLong();
        mvc.perform(post("/crm/sales/" + sale + "/items").cookie(manager).contentType(APPLICATION_JSON)
                .content("{\"productId\":" + product + ",\"quantity\":" + qty + "}")).andExpect(status().isOk());
        mvc.perform(post("/crm/sales/" + sale + "/confirm").cookie(manager)).andExpect(status().isOk());
        return sale;
    }

    @Test // CT10, CT12
    void indicadoresAcompanhamVendaECancelamento() throws Exception {
        long product = createProduct(manager, category, "100.00", 50);
        JsonNode before = dashboard(null, null);

        long sale = confirmedSale(product, 40);   // 40 un. garante o topo da lista de mais vendidos
        JsonNode after = dashboard(null, null);
        assertThat(after.get("confirmedSales").asLong()).isEqualTo(before.get("confirmedSales").asLong() + 1);
        assertThat(after.get("revenue").decimalValue())
                .isEqualByComparingTo(before.get("revenue").decimalValue().add(new java.math.BigDecimal("4000.00")));
        assertThat(after.get("topProducts").get(0).get("productId").asLong()).isEqualTo(product);

        mvc.perform(post("/crm/sales/" + sale + "/cancel").cookie(manager).contentType(APPLICATION_JSON)
                .content("{\"reason\":\"Teste\"}")).andExpect(status().isOk());
        JsonNode cancelled = dashboard(null, null);
        assertThat(cancelled.get("confirmedSales").asLong()).isEqualTo(before.get("confirmedSales").asLong());
        assertThat(cancelled.get("revenue").decimalValue()).isEqualByComparingTo(before.get("revenue").decimalValue());
        assertThat(cancelled.get("cancelledSales").asLong()).isEqualTo(before.get("cancelledSales").asLong() + 1);
    }

    @Test // virada de período: 23:59 do último dia entra; 00:00 do dia seguinte, não
    void fimDoPeriodoEExclusivoNoFusoDaLoja() throws Exception {
        long product = createProduct(manager, category, "10.00", 10);
        long inside = confirmedSale(product, 1);
        long outside = confirmedSale(product, 1);
        jdbc.update("update sale set confirmed_at = '2020-01-31 23:59:00-03' where id = ?", inside);
        jdbc.update("update sale set confirmed_at = '2020-02-01 00:00:00-03' where id = ?", outside);

        JsonNode jan = dashboard("2020-01-01", "2020-01-31");
        assertThat(jan.get("confirmedSales").asLong()).isEqualTo(1);
        assertThat(jan.get("revenueByDay").size()).isEqualTo(31);
        assertThat(jan.get("revenueByDay").get(30).get("sales").asLong()).isEqualTo(1);

        JsonNode feb = dashboard("2020-02-01", "2020-02-01");
        assertThat(feb.get("confirmedSales").asLong()).isEqualTo(1);
        assertThat(feb.get("previousPeriod").get("confirmedSales").asLong()).isEqualTo(1);   // 31/01
    }

    @Test // CT34
    void estoquistaNaoVeODashboard() throws Exception {
        mvc.perform(get("/crm/dashboard").cookie(loginAs("STOCK_KEEPER"))).andExpect(status().isForbidden());
    }

    @Test
    void periodoInvertidoERecusado() throws Exception {
        mvc.perform(get("/crm/dashboard").cookie(manager).param("from", "2026-02-01").param("to", "2026-01-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_period"));
    }
}
