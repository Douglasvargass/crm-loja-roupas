package br.senai.crmloja.modules.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.senai.crmloja.core.audit.AuditEventRepository;
import br.senai.crmloja.support.IntegrationTest;
import br.senai.crmloja.support.TestData;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class CatalogStockIT extends IntegrationTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired AuditEventRepository auditEvents;

    Cookie manager;
    long category;

    @BeforeEach
    void setUp() throws Exception {
        manager = loginAs("MANAGER");
        category = createCategory(manager);
    }

    @Test // RN03
    void nomeDeCategoriaUnicoSemDiferenciarMaiusculas() throws Exception {
        String name = TestData.unique("Calças");
        mvc.perform(post("/crm/categories").cookie(manager).contentType(APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\"}")).andExpect(status().isCreated());
        mvc.perform(post("/crm/categories").cookie(manager).contentType(APPLICATION_JSON)
                        .content("{\"name\":\"" + name.toUpperCase() + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("category_name_taken"));
    }

    @Test // CT04, CT05, CT06
    void estoqueInicialEntradaEAjusteGeramMovimentacoes() throws Exception {
        long product = createProduct(manager, category, "59.90", 10);
        Cookie stockKeeper = loginAs("STOCK_KEEPER");

        mvc.perform(get("/crm/stock/movements").cookie(stockKeeper).param("productId", "" + product))
                .andExpect(jsonPath("$.content[0].type").value("ENTRY"))
                .andExpect(jsonPath("$.content[0].balanceAfter").value(10))
                .andExpect(jsonPath("$.content[0].reason").value("Estoque inicial"));

        mvc.perform(post("/crm/stock/entries").cookie(stockKeeper).contentType(APPLICATION_JSON)
                        .content("{\"productId\":" + product + ",\"quantity\":5,\"reason\":\"NF 123\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.balanceAfter").value(15));

        mvc.perform(post("/crm/stock/adjustments").cookie(stockKeeper).contentType(APPLICATION_JSON)
                        .content("{\"productId\":" + product + ",\"quantity\":-2,\"reason\":\"Avaria\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("ADJUSTMENT"))
                .andExpect(jsonPath("$.balanceAfter").value(13));

        mvc.perform(get("/crm/products/" + product).cookie(stockKeeper))
                .andExpect(jsonPath("$.product.stockQty").value(13))
                .andExpect(jsonPath("$.recentMovements.length()").value(3));
        assertThat(auditEvents.findByEntityTypeAndEntityIdOrderByIdAsc("product", product))
                .anyMatch(e -> e.getAction().equals("stock_adjusted"));
    }

    @Test // CT18, CT19
    void recusaPrecoZeroOuNegativoEEstoqueNegativo() throws Exception {
        for (String price : new String[]{"0", "-10"}) {
            mvc.perform(post("/crm/products").cookie(manager).contentType(APPLICATION_JSON)
                            .content(TestData.productJson(TestData.unique("X"), category, price, 1)))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/crm/products").cookie(manager).contentType(APPLICATION_JSON)
                        .content(TestData.productJson(TestData.unique("X"), category, "10", -1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("O estoque inicial não pode ser negativo."));
    }

    @Test // CT20
    void recusaCodigoDuplicado() throws Exception {
        String code = TestData.unique("DUP");
        mvc.perform(post("/crm/products").cookie(manager).contentType(APPLICATION_JSON)
                .content(TestData.productJson(code, category, "10", 0))).andExpect(status().isCreated());
        mvc.perform(post("/crm/products").cookie(manager).contentType(APPLICATION_JSON)
                        .content(TestData.productJson(code.toLowerCase(), category, "10", 0)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("product_code_taken"));
    }

    @Test // CT21
    void recusaEntradaComQuantidadeZero() throws Exception {
        long product = createProduct(manager, category, "10", 1);
        mvc.perform(post("/crm/stock/entries").cookie(manager).contentType(APPLICATION_JSON)
                        .content("{\"productId\":" + product + ",\"quantity\":0}"))
                .andExpect(status().isBadRequest());
    }

    @Test // CT27
    void ajusteQueDeixariaSaldoNegativoERecusado() throws Exception {
        long product = createProduct(manager, category, "10", 13);
        mvc.perform(post("/crm/stock/adjustments").cookie(manager).contentType(APPLICATION_JSON)
                        .content("{\"productId\":" + product + ",\"quantity\":-20,\"reason\":\"Inventário\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("negative_stock"));
        mvc.perform(get("/crm/products/" + product).cookie(manager))
                .andExpect(jsonPath("$.product.stockQty").value(13));
    }

    @Test // CT28
    void categoriaComProdutoNaoEExcluida() throws Exception {
        createProduct(manager, category, "10", 0);
        mvc.perform(delete("/crm/categories/" + category).cookie(manager))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("category_has_products"));
    }

    @Test // CT38
    void bancoRecusaUpdateEDeleteEmStockMovement() throws Exception {
        createProduct(manager, category, "10", 3);
        assertThatThrownBy(() -> jdbc.update("update stock_movement set quantity = 99"))
                .hasMessageContaining("registro permanente");
        assertThatThrownBy(() -> jdbc.update("delete from stock_movement"))
                .hasMessageContaining("registro permanente");
    }

    @Test // HU13
    void produtoSoComEstoqueInicialPodeSerExcluido() throws Exception {
        long product = createProduct(manager, category, "10", 4);
        mvc.perform(delete("/crm/products/" + product).cookie(manager)).andExpect(status().isNoContent());
        mvc.perform(get("/crm/products/" + product).cookie(manager)).andExpect(status().isNotFound());
    }

    @Test // HU13, RN07
    void produtoComMovimentacaoSoPodeSerInativado() throws Exception {
        long product = createProduct(manager, category, "10", 4);
        mvc.perform(post("/crm/stock/entries").cookie(manager).contentType(APPLICATION_JSON)
                .content("{\"productId\":" + product + ",\"quantity\":1}")).andExpect(status().isCreated());
        mvc.perform(delete("/crm/products/" + product).cookie(manager))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("product_has_history"));
        mvc.perform(patch("/crm/products/" + product + "/deactivate").cookie(manager))
                .andExpect(jsonPath("$.availability").value("INACTIVE"));
    }

    @Test // HU10, HU14, HU19
    void filtrosCombinadosESituacaoDeEstoque() throws Exception {
        long low = createProduct(manager, category, "10", 2);      // mínimo 2 → baixo
        long zero = createProduct(manager, category, "10", 0);
        createProduct(manager, category, "10", 9);

        mvc.perform(get("/crm/products").cookie(manager).param("categoryId", "" + category).param("size", "m"))
                .andExpect(jsonPath("$.totalElements").value(3));
        mvc.perform(get("/crm/products").cookie(manager).param("categoryId", "" + category)
                        .param("availability", "LOW_STOCK"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(low));
        mvc.perform(get("/crm/products").cookie(manager).param("categoryId", "" + category)
                        .param("availability", "OUT_OF_STOCK"))
                .andExpect(jsonPath("$.content[0].id").value(zero))
                .andExpect(jsonPath("$.content[0].availability").value("OUT_OF_STOCK"));
        mvc.perform(get("/crm/stock/alerts").cookie(manager))
                .andExpect(jsonPath("$.lowStock[?(@.id == " + low + ")]").exists())
                .andExpect(jsonPath("$.outOfStock[?(@.id == " + zero + ")]").exists());
    }

    @Test // HU08
    void categoriaInativaNaoRecebeProdutoNovo() throws Exception {
        mvc.perform(put("/crm/categories/" + category).cookie(manager).contentType(APPLICATION_JSON)
                        .content("{\"name\":\"" + TestData.unique("Inativa") + "\",\"active\":false}"))
                .andExpect(status().isOk());
        mvc.perform(post("/crm/products").cookie(manager).contentType(APPLICATION_JSON)
                        .content(TestData.productJson(TestData.unique("Y"), category, "10", 0)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("category_inactive"));
    }
}
