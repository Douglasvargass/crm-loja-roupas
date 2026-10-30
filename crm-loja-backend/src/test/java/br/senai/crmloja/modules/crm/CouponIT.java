package br.senai.crmloja.modules.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.senai.crmloja.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

// Cupom de desconto (V14, RN61 a RN64) na venda do balcão: aplicar, total líquido, mínimo, validade, limite de usos.
class CouponIT extends IntegrationTest {

    static final LocalDate TODAY = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
    static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired JdbcTemplate jdbc;

    Cookie manager;
    long product;

    @BeforeEach
    void setUp() throws Exception {
        manager = loginAs("MANAGER");
        product = createProduct(manager, createCategory(manager), "100.00", 50);
    }

    String coupon(String kind, String value, String min, Integer max) throws Exception {
        String code = "T" + System.nanoTime() % 100_000_000 + "X" + SEQ.incrementAndGet();
        String body = "{\"code\":\"" + code.toLowerCase() + "\",\"kind\":\"" + kind + "\",\"value\":" + value
                + ",\"minOrder\":" + min + ",\"validFrom\":\"" + TODAY.minusDays(1) + "\",\"validUntil\":\""
                + TODAY.plusDays(10) + "\"" + (max == null ? "" : ",\"maxUses\":" + max) + "}";
        mvc.perform(post("/crm/coupons").cookie(manager).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.code").value(code));
        return code;
    }

    long saleWith(int qty) throws Exception {
        long sale = body(mvc.perform(post("/crm/sales").cookie(manager).contentType(APPLICATION_JSON).content("{}"))
                .andReturn()).get("id").asLong();
        mvc.perform(post("/crm/sales/" + sale + "/items").cookie(manager).contentType(APPLICATION_JSON)
                .content("{\"productId\":" + product + ",\"quantity\":" + qty + "}")).andExpect(status().isOk());
        return sale;
    }

    org.springframework.test.web.servlet.ResultActions applyRaw(long sale, String code) throws Exception {
        return mvc.perform(post("/crm/sales/" + sale + "/coupon").cookie(manager).contentType(APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\"}"));
    }

    JsonNode apply(long sale, String code) throws Exception {
        return body(applyRaw(sale, code).andExpect(status().isOk()).andReturn());
    }

    @Test
    void percentualDescontaNoTotalEContaOUsoNaConfirmacao() throws Exception {
        String code = coupon("PERCENT", "10", "0", null);
        long sale = saleWith(3);   // 300,00
        JsonNode s = apply(sale, code.toLowerCase());   // vale em maiúsculas ou minúsculas
        assertThat(s.get("subtotal").decimalValue()).isEqualByComparingTo("300.00");
        assertThat(s.get("discount").decimalValue()).isEqualByComparingTo("30.00");
        assertThat(s.get("total").decimalValue()).isEqualByComparingTo("270.00");
        assertThat(s.get("couponCode").asText()).isEqualTo(code);

        // Mexer nos itens recalcula o desconto.
        long item = s.get("items").get(0).get("id").asLong();
        JsonNode s2 = body(mvc.perform(put("/crm/sales/" + sale + "/items/" + item).cookie(manager)
                .contentType(APPLICATION_JSON).content("{\"quantity\":2}")).andReturn());
        assertThat(s2.get("total").decimalValue()).isEqualByComparingTo("180.00");

        mvc.perform(post("/crm/sales/" + sale + "/confirm").cookie(manager)).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(180.00)).andExpect(jsonPath("$.discount").value(20.00));
        Integer uses = jdbc.queryForObject("select uses from coupon where code = ?", Integer.class, code);
        assertThat(uses).isEqualTo(1);
    }

    @Test
    void valorFixoComMinimoELimiteDeUsos() throws Exception {
        String code = coupon("FIXED", "50", "200", 1);
        applyRaw(saleWith(1), code)   // 100,00 < mínimo
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("coupon_min_order"));

        long sale = saleWith(3);
        assertThat(apply(sale, code).get("total").decimalValue()).isEqualByComparingTo("250.00");
        mvc.perform(post("/crm/sales/" + sale + "/confirm").cookie(manager)).andExpect(status().isOk());

        applyRaw(saleWith(3), code)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("coupon_exhausted"));
    }

    @Test
    void cupomDesativadoInexistenteERemover() throws Exception {
        String code = coupon("PERCENT", "5", "0", null);
        long sale = saleWith(2);
        apply(sale, code);
        // Desativado depois de aplicado: a confirmação recusa e nada é baixado.
        long id = jdbc.queryForObject("select id from coupon where code = ?", Long.class, code);
        mvc.perform(patch("/crm/coupons/" + id + "/active").param("value", "false").cookie(manager)).andExpect(status().isOk());
        mvc.perform(post("/crm/sales/" + sale + "/confirm").cookie(manager))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("coupon_inactive"));
        JsonNode removed = body(mvc.perform(delete("/crm/sales/" + sale + "/coupon").cookie(manager))
                .andExpect(status().isOk()).andReturn());
        assertThat(removed.get("discount").decimalValue()).isEqualByComparingTo("0");
        assertThat(removed.get("total").decimalValue()).isEqualByComparingTo("200.00");
        mvc.perform(post("/crm/sales/" + sale + "/confirm").cookie(manager)).andExpect(status().isOk());

        applyRaw(saleWith(1), "NAOEXISTE")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("coupon_not_found"));
        mvc.perform(get("/crm/coupons").cookie(loginAs("SELLER"))).andExpect(status().isForbidden());
    }
}
