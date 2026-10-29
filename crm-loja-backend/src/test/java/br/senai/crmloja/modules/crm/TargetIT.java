package br.senai.crmloja.modules.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.senai.crmloja.core.identity.AppUser;
import br.senai.crmloja.support.IntegrationTest;
import br.senai.crmloja.support.TestData;
import br.senai.crmloja.support.TestUsers;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.Cookie;
import java.time.YearMonth;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

// Spec crm/06: metas por vendedor e o painel do vendedor. Cada teste cria vendedores próprios, então os números
// não dependem do que os outros testes gravaram no banco compartilhado.
class TargetIT extends IntegrationTest {

    @Autowired JdbcTemplate jdbc;

    static final String MES = YearMonth.now(ZoneId.of("America/Sao_Paulo")).toString();

    Cookie manager;
    long category;

    @BeforeEach
    void setUp() throws Exception {
        manager = loginAs("MANAGER");
        category = createCategory(manager);
    }

    AppUser newSeller() {
        return users.create(TestData.unique("vend") + "@loja.local", "SELLER", false);
    }

    Cookie as(AppUser u) throws Exception {
        return login(u.getLogin(), TestUsers.PASSWORD);
    }

    long confirmedSaleAs(Cookie who, long product, int qty) throws Exception {
        long sale = body(mvc.perform(post("/crm/sales").cookie(who).contentType(APPLICATION_JSON).content("{}"))
                .andReturn()).get("id").asLong();
        mvc.perform(post("/crm/sales/" + sale + "/items").cookie(who).contentType(APPLICATION_JSON)
                .content("{\"productId\":" + product + ",\"quantity\":" + qty + "}")).andExpect(status().isOk());
        mvc.perform(post("/crm/sales/" + sale + "/confirm").cookie(who)).andExpect(status().isOk());
        return sale;
    }

    void setTarget(long sellerId, String month, String amount) throws Exception {
        mvc.perform(put("/crm/targets/" + sellerId).param("month", month).cookie(manager)
                        .contentType(APPLICATION_JSON).content("{\"amount\":" + amount + "}"))
                .andExpect(status().isOk());
    }

    JsonNode panelOf(Cookie who) throws Exception {
        return body(mvc.perform(get("/crm/my-panel").cookie(who)).andExpect(status().isOk()).andReturn());
    }

    JsonNode rowOf(JsonNode board, long sellerId) {
        for (JsonNode r : board.get("rows")) if (r.get("sellerId").asLong() == sellerId) return r;
        throw new AssertionError("vendedor " + sellerId + " não está no quadro");
    }

    @Test // REQ-01, REQ-05 a REQ-09
    void gerenteDefineMetaEVendedorAcompanhaNoPainel() throws Exception {
        AppUser seller = newSeller();
        Cookie c = as(seller);
        long product = createProduct(manager, category, "100.00", 50);
        setTarget(seller.getId(), MES, "1000.00");
        confirmedSaleAs(c, product, 3);   // 300,00

        JsonNode p = panelOf(c);
        assertThat(p.get("month").asText()).isEqualTo(MES);
        assertThat(p.get("closed").asBoolean()).isFalse();
        assertThat(p.get("sellerName").asText()).isEqualTo(seller.getFullName());
        assertThat(p.get("target").decimalValue()).isEqualByComparingTo("1000.00");
        assertThat(p.get("revenue").decimalValue()).isEqualByComparingTo("300.00");
        assertThat(p.get("confirmedSales").asLong()).isEqualTo(1);
        assertThat(p.get("percent").decimalValue()).isEqualByComparingTo("30.0");
        assertThat(p.get("remaining").decimalValue()).isEqualByComparingTo("700.00");
        assertThat(p.get("today").get("revenue").decimalValue()).isEqualByComparingTo("300.00");
        assertThat(p.get("topProducts").get(0).get("quantity").asLong()).isEqualTo(3);
        assertThat(p.get("recentSales")).hasSize(1);
        assertThat(p.get("businessDaysInMonth").asInt()).isPositive();
        // REQ-09: nada da loja inteira no painel do vendedor.
        assertThat(p.has("totalCustomers")).isFalse();
        assertThat(p.has("sellers")).isFalse();
        assertThat(p.has("lowStock")).isFalse();
    }

    @Test // REQ-09, REQ-10: cada vendedor só vê o que ele abriu; vendedor não mexe em meta
    void vendedorSoVeOsProprioNumerosENaoMexeEmMeta() throws Exception {
        AppUser a = newSeller();
        AppUser b = newSeller();
        Cookie ca = as(a);
        long product = createProduct(manager, category, "50.00", 50);
        confirmedSaleAs(as(b), product, 5);

        JsonNode p = panelOf(ca);
        assertThat(p.get("revenue").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(p.get("recentSales")).isEmpty();

        mvc.perform(get("/crm/targets").cookie(ca)).andExpect(status().isForbidden());
        mvc.perform(put("/crm/targets/" + a.getId()).cookie(ca).contentType(APPLICATION_JSON).content("{\"amount\":1}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/crm/dashboard").cookie(ca)).andExpect(status().isForbidden());
    }

    @Test // REQ-10
    void semMetaOPainelMostraORitmoESituacaoSemMeta() throws Exception {
        JsonNode p = panelOf(as(newSeller()));
        assertThat(p.get("target").isNull()).isTrue();
        assertThat(p.get("remaining").isNull()).isTrue();
        assertThat(p.get("status").asText()).isEqualTo("NO_TARGET");
    }

    @Test // REQ-01 a REQ-03
    void metaInvalidaERecusada() throws Exception {
        AppUser seller = newSeller();
        String url = "/crm/targets/" + seller.getId();
        for (String amount : new String[]{"0", "-5", "null", "10.999"}) {
            mvc.perform(put(url).param("month", MES).cookie(manager).contentType(APPLICATION_JSON)
                    .content("{\"amount\":" + amount + "}")).andExpect(status().isBadRequest());
        }
        String passado = YearMonth.parse(MES).minusMonths(1).toString();
        mvc.perform(put(url).param("month", passado).cookie(manager).contentType(APPLICATION_JSON).content("{\"amount\":100}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("month_closed"));
        String longe = YearMonth.parse(MES).plusMonths(13).toString();
        mvc.perform(put(url).param("month", longe).cookie(manager).contentType(APPLICATION_JSON).content("{\"amount\":100}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("month_too_far"));
        mvc.perform(put(url).param("month", "2026-13").cookie(manager).contentType(APPLICATION_JSON).content("{\"amount\":100}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("invalid_month"));

        AppUser estoquista = users.create(TestData.unique("estq") + "@loja.local", "STOCK_KEEPER", false);
        mvc.perform(put("/crm/targets/" + estoquista.getId()).cookie(manager).contentType(APPLICATION_JSON).content("{\"amount\":100}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("not_a_seller"));
        mvc.perform(put("/crm/targets/99999999").cookie(manager).contentType(APPLICATION_JSON).content("{\"amount\":100}"))
                .andExpect(status().isNotFound());
    }

    @Test // REQ-04: toda mudança de valor fica na auditoria; salvar o mesmo valor não gera registro
    void mudarAMetaFicaNaAuditoria() throws Exception {
        AppUser seller = newSeller();
        setTarget(seller.getId(), MES, "1000");
        setTarget(seller.getId(), MES, "1500.00");
        setTarget(seller.getId(), MES, "1500");   // mesmo valor: nada muda

        String sql = "select count(*) from audit_event where action = 'sales_target_set' and entity_id = ?";
        assertThat(jdbc.queryForObject(sql, Long.class, seller.getId())).isEqualTo(2);
        var last = jdbc.queryForMap("""
                select payload->>'from' as de, payload->>'to' as para, payload->>'month' as mes
                  from audit_event where action = 'sales_target_set' and entity_id = ? order by id desc limit 1""",
                seller.getId());
        assertThat(last.get("de")).isEqualTo("1000.00");
        assertThat(last.get("para")).isEqualTo("1500.00");
        assertThat(last.get("mes")).isEqualTo(MES);
    }

    @Test // REQ-11
    void quadroDoGerenteMostraCadaVendedorComMetaERealizado() throws Exception {
        AppUser a = newSeller();
        AppUser b = newSeller();
        long product = createProduct(manager, category, "100.00", 50);
        setTarget(a.getId(), MES, "500");
        confirmedSaleAs(as(a), product, 6);   // 600,00 → 120%

        JsonNode board = body(mvc.perform(get("/crm/targets").param("month", MES).cookie(manager))
                .andExpect(status().isOk()).andReturn());
        assertThat(board.get("editable").asBoolean()).isTrue();
        JsonNode ra = rowOf(board, a.getId());
        assertThat(ra.get("target").decimalValue()).isEqualByComparingTo("500.00");
        assertThat(ra.get("revenue").decimalValue()).isEqualByComparingTo("600.00");
        assertThat(ra.get("percent").decimalValue()).isEqualByComparingTo("120.0");
        assertThat(ra.get("status").asText()).isEqualTo("ACHIEVED");
        assertThat(rowOf(board, b.getId()).get("status").asText()).isEqualTo("NO_TARGET");

        // Equipe técnica vale por gerente.
        mvc.perform(get("/crm/targets").cookie(loginAs("DEVELOPER"))).andExpect(status().isOk());
    }

    @Test // REQ-06: venda cancelada sai do realizado
    void vendaCanceladaSaiDoRealizado() throws Exception {
        AppUser seller = newSeller();
        Cookie c = as(seller);
        long product = createProduct(manager, category, "40.00", 20);
        long sale = confirmedSaleAs(c, product, 5);   // 200,00
        mvc.perform(post("/crm/sales/" + sale + "/cancel").cookie(manager).contentType(APPLICATION_JSON)
                .content("{\"reason\":\"Cliente desistiu\"}")).andExpect(status().isOk());

        JsonNode p = panelOf(c);
        assertThat(p.get("revenue").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(p.get("cancelledSales").asLong()).isEqualTo(1);
        assertThat(p.get("recentSales").get(0).get("status").asText()).isEqualTo("CANCELLED");
    }

    @Test // REQ-09: o painel é do vendedor; mês futuro não tem painel
    void gerenteNaoTemMeuPainelEMesFuturoERecusado() throws Exception {
        mvc.perform(get("/crm/my-panel").cookie(manager)).andExpect(status().isForbidden());
        String proximo = YearMonth.parse(MES).plusMonths(1).toString();
        mvc.perform(get("/crm/my-panel").param("month", proximo).cookie(as(newSeller())))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("invalid_month"));
    }
}
