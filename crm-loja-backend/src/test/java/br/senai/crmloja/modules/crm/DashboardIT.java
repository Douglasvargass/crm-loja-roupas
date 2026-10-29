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

    @Test // papéis técnicos valem por gerente
    void equipeTecnicaVeODashboardEOsUsuarios() throws Exception {
        for (String role : new String[]{"DEVELOPER", "MARKETING_DEV"}) {
            Cookie c = loginAs(role);
            mvc.perform(get("/crm/dashboard").cookie(c)).andExpect(status().isOk());
            mvc.perform(get("/admin/users").cookie(c)).andExpect(status().isOk());
            mvc.perform(get("/crm/stock/alerts").cookie(c)).andExpect(status().isOk());
        }
    }

    @Test // filtro por vendedor: cartões e gráficos mostram só as vendas de quem abriu a venda
    void filtrarPorVendedorMostraSoAsVendasDele() throws Exception {
        long product = createProduct(manager, category, "10.00", 100);
        var vendedorA = users.create(br.senai.crmloja.support.TestData.unique("vend-a") + "@loja.local", "SELLER", false);
        var vendedorB = users.create(br.senai.crmloja.support.TestData.unique("vend-b") + "@loja.local", "SELLER", false);
        Cookie a = login(vendedorA.getLogin(), br.senai.crmloja.support.TestUsers.PASSWORD);
        Cookie b = login(vendedorB.getLogin(), br.senai.crmloja.support.TestUsers.PASSWORD);
        confirmedSaleAs(a, product, 3);   // A: 30,00
        confirmedSaleAs(b, product, 1);   // B: 10,00
        String today = java.time.LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo")).toString();

        JsonNode soA = body(mvc.perform(get("/crm/dashboard?from=" + today + "&to=" + today + "&sellerId=" + vendedorA.getId())
                .cookie(manager)).andExpect(status().isOk()).andReturn());
        assertThat(soA.get("confirmedSales").asLong()).isEqualTo(1);
        assertThat(soA.get("revenue").decimalValue()).isEqualByComparingTo("30.00");
        assertThat(soA.get("selected").get("sellerId").asLong()).isEqualTo(vendedorA.getId());
        assertThat(soA.get("selected").get("sellerName").asText()).isEqualTo(vendedorA.getFullName());
        assertThat(soA.get("topProducts").get(0).get("quantity").asLong()).isEqualTo(3);   // gráfico respeita o vendedor
        // A lista de opções traz os dois vendedores ativos.
        var ids = new java.util.HashSet<Long>();
        soA.get("sellers").forEach(s -> ids.add(s.get("id").asLong()));
        assertThat(ids).contains(vendedorA.getId(), vendedorB.getId());

        JsonNode todos = filtered("");
        assertThat(todos.get("selected").get("sellerId").isNull()).isTrue();
        assertThat(todos.get("confirmedSales").asLong()).isGreaterThanOrEqualTo(2);

        mvc.perform(get("/crm/dashboard?sellerId=99999999").cookie(manager)).andExpect(status().isNotFound());
    }

    long confirmedSaleAs(Cookie who, long product, int qty) throws Exception {
        long sale = body(mvc.perform(post("/crm/sales").cookie(who).contentType(APPLICATION_JSON).content("{}"))
                .andReturn()).get("id").asLong();
        mvc.perform(post("/crm/sales/" + sale + "/items").cookie(who).contentType(APPLICATION_JSON)
                .content("{\"productId\":" + product + ",\"quantity\":" + qty + "}")).andExpect(status().isOk());
        mvc.perform(post("/crm/sales/" + sale + "/confirm").cookie(who)).andExpect(status().isOk());
        return sale;
    }

    @Test
    void periodoInvertidoERecusado() throws Exception {
        mvc.perform(get("/crm/dashboard").cookie(manager).param("from", "2026-02-01").param("to", "2026-01-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_period"));
    }

    // --- Filtros cruzados (cliques nos gráficos) ---------------------------------------------------------

    JsonNode filtered(String query) throws Exception {
        String today = java.time.LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo")).toString();
        return body(mvc.perform(get("/crm/dashboard?from=" + today + "&to=" + today + query).cookie(manager))
                .andExpect(status().isOk()).andReturn());
    }

    long confirmedSaleOf(long productA, int qtyA, long productB, int qtyB) throws Exception {
        long sale = body(mvc.perform(post("/crm/sales").cookie(manager).contentType(APPLICATION_JSON).content("{}"))
                .andReturn()).get("id").asLong();
        for (long[] item : new long[][]{{productA, qtyA}, {productB, qtyB}}) {
            mvc.perform(post("/crm/sales/" + sale + "/items").cookie(manager).contentType(APPLICATION_JSON)
                    .content("{\"productId\":" + item[0] + ",\"quantity\":" + item[1] + "}")).andExpect(status().isOk());
        }
        mvc.perform(post("/crm/sales/" + sale + "/confirm").cookie(manager)).andExpect(status().isOk());
        return sale;
    }

    @Test
    void filtrarPorCategoriaSomaSoOsItensDelaEMantemAsOutrasBarras() throws Exception {
        long catB = createCategory(manager);
        long pa = createProduct(manager, category, "100.00", 20);   // categoria A: 2 × 100 = 200
        long pb = createProduct(manager, catB, "10.00", 20);        // categoria B: 3 × 10 = 30
        confirmedSaleOf(pa, 2, pb, 3);

        JsonNode a = filtered("&categoryId=" + category);
        assertThat(a.get("confirmedSales").asLong()).isEqualTo(1);
        assertThat(a.get("revenue").decimalValue()).isEqualByComparingTo("200.00");
        assertThat(a.get("averageTicket").decimalValue()).isEqualByComparingTo("200.00");
        assertThat(a.get("selected").get("categoryId").asLong()).isEqualTo(category);
        assertThat(a.get("selected").get("categoryName").asText()).isNotBlank();
        // Os produtos mostram só os da categoria escolhida...
        assertThat(a.get("topProducts")).hasSize(1);
        assertThat(a.get("topProducts").get(0).get("productId").asLong()).isEqualTo(pa);
        // ...mas o gráfico de categorias ignora o próprio filtro: a outra categoria continua ali para trocar a seleção.
        var cats = new java.util.HashMap<Long, java.math.BigDecimal>();
        a.get("salesByCategory").forEach(c -> cats.put(c.get("categoryId").asLong(), c.get("revenue").decimalValue()));
        assertThat(cats.get(category)).isEqualByComparingTo("200.00");
        assertThat(cats.get(catB)).isEqualByComparingTo("30.00");
        // O gráfico por dia respeita a categoria: hoje vale 200, não 230.
        JsonNode today = a.get("revenueByDay").get(a.get("revenueByDay").size() - 1);
        assertThat(today.get("revenue").decimalValue()).isEqualByComparingTo("200.00");
    }

    @Test
    void filtrarPorProdutoEPorDiaCombinaComOsOutrosFiltros() throws Exception {
        long catB = createCategory(manager);
        long pa = createProduct(manager, category, "100.00", 20);
        long pb = createProduct(manager, catB, "10.00", 20);
        confirmedSaleOf(pa, 2, pb, 3);
        String today = java.time.LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo")).toString();

        JsonNode p = filtered("&productId=" + pb);
        assertThat(p.get("revenue").decimalValue()).isEqualByComparingTo("30.00");
        assertThat(p.get("confirmedSales").asLong()).isEqualTo(1);
        // O gráfico de categorias respeita o produto: só a categoria do produto aparece.
        assertThat(p.get("salesByCategory")).hasSize(1);
        assertThat(p.get("salesByCategory").get(0).get("categoryId").asLong()).isEqualTo(catB);
        // O gráfico de produtos ignora o próprio filtro: o outro produto continua listado.
        var ids = new java.util.HashSet<Long>();
        p.get("topProducts").forEach(t -> ids.add(t.get("productId").asLong()));
        assertThat(ids).contains(pa, pb);

        // Categoria A + produto B: nenhum item bate nos dois, então zera (sem erro).
        JsonNode none = filtered("&categoryId=" + category + "&productId=" + pb);
        assertThat(none.get("confirmedSales").asLong()).isZero();
        assertThat(none.get("revenue").decimalValue()).isEqualByComparingTo("0.00");

        // Dia: período dos cartões estreita para o dia e o gráfico por dia continua mostrando o período inteiro.
        JsonNode d = filtered("&productId=" + pb + "&day=" + today);
        assertThat(d.get("period").get("from").asText()).isEqualTo(today);
        assertThat(d.get("period").get("to").asText()).isEqualTo(today);
        assertThat(d.get("selected").get("day").asText()).isEqualTo(today);
        assertThat(d.get("revenue").decimalValue()).isEqualByComparingTo("30.00");
    }

    @Test
    void diaEntreOsFiltrosRecortaOPeriodoENaoMudaOGraficoPorDia() throws Exception {
        long pa = createProduct(manager, category, "50.00", 20);
        long sale = confirmedSale(pa, 2);
        jdbc.update("update sale set confirmed_at = '2020-03-10 12:00:00-03' where id = ?", sale);
        confirmedSaleOf(pa, 1, createProduct(manager, createCategory(manager), "5.00", 5), 1);   // hoje

        JsonNode d = body(mvc.perform(get("/crm/dashboard?from=2020-03-01&to=2020-03-31&productId=" + pa + "&day=2020-03-10")
                .cookie(manager)).andExpect(status().isOk()).andReturn());
        assertThat(d.get("revenue").decimalValue()).isEqualByComparingTo("100.00");
        assertThat(d.get("confirmedSales").asLong()).isEqualTo(1);
        assertThat(d.get("revenueByDay")).hasSize(31);                     // o gráfico mostra o mês todo
        assertThat(d.get("previousPeriod").get("from").asText()).isEqualTo("2020-03-09");   // compara com o dia anterior
    }

    @Test
    void filtroInvalidoDevolveErroClaro() throws Exception {
        mvc.perform(get("/crm/dashboard?from=2020-03-01&to=2020-03-31&day=2020-04-01").cookie(manager))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_filter"));
        mvc.perform(get("/crm/dashboard?categoryId=99999999").cookie(manager))
                .andExpect(status().isNotFound());
        mvc.perform(get("/crm/dashboard?productId=99999999").cookie(manager))
                .andExpect(status().isNotFound());
    }
}
