package br.senai.crmloja.modules.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.senai.crmloja.support.IntegrationTest;
import br.senai.crmloja.support.TestData;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class SaleIT extends IntegrationTest {

    @Autowired JdbcTemplate jdbc;

    Cookie manager;
    Cookie seller;
    long category;

    @BeforeEach
    void setUp() throws Exception {
        manager = loginAs("MANAGER");
        seller = loginAs("SELLER");
        category = createCategory(manager);
    }

    long openSale(Long customerId) throws Exception {
        String body = customerId == null ? "{}" : "{\"customerId\":" + customerId + "}";
        return body(mvc.perform(post("/crm/sales").cookie(seller).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn()).get("id").asLong();
    }

    JsonNode addItem(long sale, long product, int qty) throws Exception {
        return body(mvc.perform(post("/crm/sales/" + sale + "/items").cookie(seller).contentType(APPLICATION_JSON)
                        .content("{\"productId\":" + product + ",\"quantity\":" + qty + "}"))
                .andExpect(status().isOk()).andReturn());
    }

    int stockOf(long product) {
        return jdbc.queryForObject("select stock_qty from product where id = ?", Integer.class, product);
    }

    int movements(long sale, String type) {
        return jdbc.queryForObject("select count(*) from stock_movement where sale_id = ? and type = ?::stock_movement_type",
                Integer.class, sale, type);
    }

    @Test // CT07, CT08, CT09, CT11
    void cicloCompletoConfirmaEDepoisCancelaComEstorno() throws Exception {
        long customer = createCustomer(manager);
        long a = createProduct(manager, category, "49.90", 10);
        long b = createProduct(manager, category, "120.00", 10);
        long sale = openSale(customer);

        addItem(sale, a, 3);
        JsonNode s = addItem(sale, b, 2);
        assertThat(s.get("total").decimalValue()).isEqualByComparingTo("389.70");      // 3×49,90 + 2×120

        // Mesmo produto de novo soma na mesma linha.
        s = addItem(sale, a, 1);
        assertThat(s.get("items").size()).isEqualTo(2);
        long itemA = s.get("items").get(0).get("id").asLong();
        mvc.perform(put("/crm/sales/" + sale + "/items/" + itemA).cookie(seller).contentType(APPLICATION_JSON)
                        .content("{\"quantity\":3}"))
                .andExpect(jsonPath("$.items[0].subtotal").value(149.70));

        mvc.perform(post("/crm/sales/" + sale + "/confirm").cookie(seller))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.total").value(389.70))
                .andExpect(jsonPath("$.confirmedAt").isNotEmpty());
        assertThat(stockOf(a)).isEqualTo(7);
        assertThat(stockOf(b)).isEqualTo(8);
        assertThat(movements(sale, "SALE")).isEqualTo(2);

        mvc.perform(get("/crm/customers/" + customer + "/sales").cookie(seller))
                .andExpect(jsonPath("$[0].id").value(sale))
                .andExpect(jsonPath("$[0].itemsQuantity").value(5));
        mvc.perform(get("/crm/customers/" + customer).cookie(seller))     // HU03: resumo de compras
                .andExpect(jsonPath("$.confirmedPurchases").value(1))
                .andExpect(jsonPath("$.confirmedTotal").value(389.70));

        mvc.perform(post("/crm/sales/" + sale + "/cancel").cookie(manager).contentType(APPLICATION_JSON)
                        .content("{\"reason\":\"Cliente desistiu\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelReason").value("Cliente desistiu"));
        assertThat(stockOf(a)).isEqualTo(10);
        assertThat(stockOf(b)).isEqualTo(10);
        assertThat(movements(sale, "SALE_CANCELLATION")).isEqualTo(2);
    }

    @Test // CT22
    void vendaAcimaDoEstoqueERecusada() throws Exception {
        long p = createProduct(manager, category, "10", 13);
        long sale = openSale(null);
        mvc.perform(post("/crm/sales/" + sale + "/items").cookie(seller).contentType(APPLICATION_JSON)
                        .content("{\"productId\":" + p + ",\"quantity\":20}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("insufficient_stock"));
        assertThat(stockOf(p)).isEqualTo(13);
    }

    @Test // CT23
    void confirmacaoETudoOuNada() throws Exception {
        long ok = createProduct(manager, category, "10", 5);
        long scarce = createProduct(manager, category, "10", 5);
        long sale = openSale(null);
        addItem(sale, ok, 2);
        addItem(sale, scarce, 5);

        // Outra venda leva o estoque do segundo produto antes da confirmação desta.
        long other = openSale(null);
        addItem(other, scarce, 4);
        mvc.perform(post("/crm/sales/" + other + "/confirm").cookie(seller)).andExpect(status().isOk());

        mvc.perform(post("/crm/sales/" + sale + "/confirm").cookie(seller))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("insufficient_stock"));
        assertThat(stockOf(ok)).isEqualTo(5);
        assertThat(movements(sale, "SALE")).isZero();
    }

    @Test // CT24
    void segundaConfirmacaoERecusada() throws Exception {
        long p = createProduct(manager, category, "10", 5);
        long sale = openSale(null);
        addItem(sale, p, 1);
        mvc.perform(post("/crm/sales/" + sale + "/confirm").cookie(seller)).andExpect(status().isOk());
        mvc.perform(post("/crm/sales/" + sale + "/confirm").cookie(seller))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("sale_not_draft"));
        assertThat(stockOf(p)).isEqualTo(4);
        mvc.perform(post("/crm/sales/" + sale + "/items").cookie(seller).contentType(APPLICATION_JSON)
                        .content("{\"productId\":" + p + ",\"quantity\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("sale_not_draft"));
    }

    @Test // CT25
    void confirmacoesSimultaneasBaixamUmaVezSo() throws Exception {
        long p = createProduct(manager, category, "10", 5);
        long sale = openSale(null);
        addItem(sale, p, 2);

        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> confirm = () -> {
                start.await();
                return mvc.perform(post("/crm/sales/" + sale + "/confirm").cookie(seller))
                        .andReturn().getResponse().getStatus();
            };
            List<Future<Integer>> results = new ArrayList<>(List.of(pool.submit(confirm), pool.submit(confirm)));
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (var f : results) statuses.add(f.get());
            assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        } finally {
            pool.shutdown();
        }
        assertThat(stockOf(p)).isEqualTo(3);
        assertThat(movements(sale, "SALE")).isEqualTo(1);
    }

    @Test // CT26, RN18
    void cancelamentoExigeMotivoENaoRepete() throws Exception {
        long p = createProduct(manager, category, "10", 5);
        long sale = openSale(null);
        addItem(sale, p, 2);
        mvc.perform(post("/crm/sales/" + sale + "/confirm").cookie(seller)).andExpect(status().isOk());

        mvc.perform(post("/crm/sales/" + sale + "/cancel").cookie(manager).contentType(APPLICATION_JSON)
                        .content("{\"reason\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("reason_required"));
        mvc.perform(post("/crm/sales/" + sale + "/cancel").cookie(manager).contentType(APPLICATION_JSON)
                .content("{\"reason\":\"Erro de digitação\"}")).andExpect(status().isOk());
        mvc.perform(post("/crm/sales/" + sale + "/cancel").cookie(manager).contentType(APPLICATION_JSON)
                        .content("{\"reason\":\"De novo\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("sale_already_cancelled"));
        assertThat(stockOf(p)).isEqualTo(5);
        assertThat(movements(sale, "SALE_CANCELLATION")).isEqualTo(1);
    }

    @Test // HU24: cancelar venda aberta não mexe no estoque
    void cancelarVendaAbertaNaoMexeNoEstoque() throws Exception {
        long p = createProduct(manager, category, "10", 5);
        long sale = openSale(null);
        addItem(sale, p, 2);
        mvc.perform(post("/crm/sales/" + sale + "/cancel").cookie(manager).contentType(APPLICATION_JSON)
                .content("{\"reason\":\"Desistência\"}")).andExpect(status().isOk());
        assertThat(stockOf(p)).isEqualTo(5);
        assertThat(movements(sale, "SALE_CANCELLATION")).isZero();
    }

    @Test // CT30
    void vendaPreservaNomeEPrecoDaEpoca() throws Exception {
        String code = TestData.unique("FRZ");
        long p = body(mvc.perform(post("/crm/products").cookie(manager).contentType(APPLICATION_JSON)
                .content(TestData.productJson(code, category, "50.00", 5))).andReturn()).get("id").asLong();
        long sale = openSale(null);
        addItem(sale, p, 1);
        mvc.perform(post("/crm/sales/" + sale + "/confirm").cookie(seller)).andExpect(status().isOk());

        String changed = TestData.productJson(code, category, "80.00", 0).replace("Produto " + code, "Nome Novo");
        mvc.perform(put("/crm/products/" + p).cookie(manager).contentType(APPLICATION_JSON).content(changed))
                .andExpect(status().isOk());

        mvc.perform(get("/crm/sales/" + sale).cookie(seller))
                .andExpect(jsonPath("$.items[0].unitPrice").value(50.00))
                .andExpect(jsonPath("$.items[0].productName").value("Produto " + code))
                .andExpect(jsonPath("$.total").value(50.00));
    }

    @Test // CT31
    void vendaSemItensNaoConfirma() throws Exception {
        long sale = openSale(null);
        mvc.perform(post("/crm/sales/" + sale + "/confirm").cookie(seller))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("sale_empty"));
    }

    @Test // CT33
    void vendedorNaoCancelaVenda() throws Exception {
        long sale = openSale(null);
        mvc.perform(post("/crm/sales/" + sale + "/cancel").cookie(seller).contentType(APPLICATION_JSON)
                .content("{\"reason\":\"x\"}")).andExpect(status().isForbidden());
    }

    @Test // HU22, RN22
    void produtoSemEstoqueOuInativoNaoEntraNaVenda() throws Exception {
        long zero = createProduct(manager, category, "10", 0);
        long sale = openSale(null);
        mvc.perform(post("/crm/sales/" + sale + "/items").cookie(seller).contentType(APPLICATION_JSON)
                        .content("{\"productId\":" + zero + ",\"quantity\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("product_unavailable"));
    }

    @Test // HU25
    void consultaPorNumeroEStatus() throws Exception {
        long sale = openSale(null);
        mvc.perform(get("/crm/sales").cookie(manager).param("id", "" + sale).param("status", "DRAFT"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].customerName").isEmpty());
        mvc.perform(get("/crm/sales").cookie(manager).param("id", "" + sale).param("status", "CONFIRMED"))
                .andExpect(jsonPath("$.totalElements").value(0));
    }
}
