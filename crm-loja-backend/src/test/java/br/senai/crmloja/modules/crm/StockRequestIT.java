package br.senai.crmloja.modules.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.senai.crmloja.core.identity.AppUser;
import br.senai.crmloja.modules.crm.assistant.AssistantTools;
import br.senai.crmloja.support.IntegrationTest;
import br.senai.crmloja.support.TestData;
import br.senai.crmloja.support.TestUsers;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

// Spec crm/09: chamados de peça em falta. Cada teste usa vendedor e produto próprios.
class StockRequestIT extends IntegrationTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired AssistantTools tools;

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

    JsonNode open(Cookie who, String json) throws Exception {
        return body(mvc.perform(post("/crm/requests").cookie(who).contentType(APPLICATION_JSON).content(json))
                .andExpect(status().isCreated()).andReturn());
    }

    @Test // REQ-01, REQ-02: vendedor abre chamado de peça zerada; peça com estoque não abre
    void vendedorAbreChamadoDePecaEmFaltaEComEstoqueERecusado() throws Exception {
        Cookie seller = as(newSeller());
        long zerado = createProduct(manager, category, "120.00", 0);
        long comEstoque = createProduct(manager, category, "50.00", 10);

        JsonNode r = open(seller, "{\"productId\":" + zerado + ",\"description\":\"Calça 44 azul\",\"size\":\"44\",\"quantity\":2}");
        assertThat(r.get("status").asText()).isEqualTo("OPEN");
        assertThat(r.get("productId").asLong()).isEqualTo(zerado);
        assertThat(r.get("openedVia").asText()).isEqualTo("SCREEN");

        mvc.perform(post("/crm/requests").cookie(seller).contentType(APPLICATION_JSON)
                        .content("{\"productId\":" + comEstoque + ",\"description\":\"Camiseta\",\"quantity\":1}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("product_in_stock"));
        // Peça que nem existe no catálogo também vale (sem productId).
        open(seller, "{\"description\":\"Bermuda de linho bege 42\"}");
    }

    @Test // REQ-01: validações e quem pode abrir
    void validacoesEQuemAbre() throws Exception {
        Cookie seller = as(newSeller());
        mvc.perform(post("/crm/requests").cookie(seller).contentType(APPLICATION_JSON).content("{\"description\":\"ab\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/crm/requests").cookie(seller).contentType(APPLICATION_JSON)
                .content("{\"description\":\"Vestido\",\"quantity\":0}")).andExpect(status().isBadRequest());
        mvc.perform(post("/crm/requests").cookie(seller).contentType(APPLICATION_JSON)
                .content("{\"description\":\"Vestido\",\"productId\":99999999}")).andExpect(status().isNotFound());
        mvc.perform(post("/crm/requests").cookie(loginAs("STOCK_KEEPER")).contentType(APPLICATION_JSON)
                .content("{\"description\":\"Vestido\"}")).andExpect(status().isForbidden());
    }

    @Test // REQ-05: vendedor vê só os dele; estoque e gerente veem todos
    void vendedorVeSoOsDele() throws Exception {
        AppUser a = newSeller();
        AppUser b = newSeller();
        long idB = open(as(b), "{\"description\":\"Jaqueta jeans G\"}").get("id").asLong();

        JsonNode listaA = body(mvc.perform(get("/crm/requests").cookie(as(a))).andExpect(status().isOk()).andReturn());
        listaA.get("content").forEach(r -> assertThat(r.get("id").asLong()).isNotEqualTo(idB));

        JsonNode listaEstoque = body(mvc.perform(get("/crm/requests").param("status", "OPEN").param("size", "100")
                .cookie(loginAs("STOCK_KEEPER"))).andExpect(status().isOk()).andReturn());
        boolean achou = false;
        for (JsonNode r : listaEstoque.get("content")) achou |= r.get("id").asLong() == idB;
        assertThat(achou).isTrue();

        // O vendedor A nem descobre que o chamado de B existe.
        mvc.perform(post("/crm/requests/" + idB + "/cancel").cookie(as(a)).contentType(APPLICATION_JSON)
                .content("{\"note\":\"teste\"}")).andExpect(status().isNotFound());
    }

    @Test // REQ-06: estoque resolve com nota; vendedor não resolve, mas cancela o dele; fechado não fecha de novo
    void resolverECancelar() throws Exception {
        AppUser seller = newSeller();
        Cookie c = as(seller);
        long id1 = open(c, "{\"description\":\"Boné azul\"}").get("id").asLong();
        long id2 = open(c, "{\"description\":\"Cinto preto\"}").get("id").asLong();

        mvc.perform(post("/crm/requests/" + id1 + "/resolve").cookie(c).contentType(APPLICATION_JSON)
                .content("{\"note\":\"ok\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/crm/requests/" + id1 + "/resolve").cookie(loginAs("STOCK_KEEPER")).contentType(APPLICATION_JSON)
                .content("{\"note\":\"\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/crm/requests/" + id1 + "/resolve").cookie(loginAs("STOCK_KEEPER")).contentType(APPLICATION_JSON)
                        .content("{\"note\":\"Pedido ao fornecedor, chega sexta\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RESOLVED"));
        mvc.perform(post("/crm/requests/" + id1 + "/cancel").cookie(manager).contentType(APPLICATION_JSON)
                .content("{\"note\":\"de novo\"}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("request_closed"));

        mvc.perform(post("/crm/requests/" + id2 + "/cancel").cookie(c).contentType(APPLICATION_JSON)
                        .content("{\"note\":\"Cliente desistiu\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(jdbc.queryForObject("select count(*) from audit_event where entity_type = 'stock_request' and entity_id in (?, ?)",
                Long.class, id1, id2)).isEqualTo(4);   // 2 aberturas + 1 resolução + 1 cancelamento
    }

    @Test // REQ-04, REQ-07: a peça chegou → resolve os mais antigos que a entrada cobre e avisa quem abriu
    void entradaDeEstoqueResolveEAvisaQuemAbriu() throws Exception {
        AppUser seller = newSeller();
        Cookie c = as(seller);
        long product = createProduct(manager, category, "80.00", 0);
        long r1 = open(c, "{\"productId\":" + product + ",\"description\":\"Peça 1\",\"quantity\":1}").get("id").asLong();
        long r2 = open(c, "{\"productId\":" + product + ",\"description\":\"Peça 2\",\"quantity\":2}").get("id").asLong();
        long r3 = open(c, "{\"productId\":" + product + ",\"description\":\"Peça 3\",\"quantity\":5}").get("id").asLong();

        // O gerente vê os 3 no sino.
        JsonNode sino = body(mvc.perform(get("/crm/requests/alerts").cookie(manager)).andExpect(status().isOk()).andReturn());
        assertThat(sino.get("kind").asText()).isEqualTo("OPEN_REQUESTS");
        assertThat(sino.get("count").asLong()).isGreaterThanOrEqualTo(3);

        mvc.perform(post("/crm/stock/entries").cookie(manager).contentType(APPLICATION_JSON)
                .content("{\"productId\":" + product + ",\"quantity\":3}")).andExpect(status().isCreated());

        String st = "select status from stock_request where id = ?";
        assertThat(jdbc.queryForObject(st, String.class, r1)).isEqualTo("RESOLVED");
        assertThat(jdbc.queryForObject(st, String.class, r2)).isEqualTo("RESOLVED");
        assertThat(jdbc.queryForObject(st, String.class, r3)).isEqualTo("OPEN");   // 1 + 2 = 3: não sobra para 5

        JsonNode meu = body(mvc.perform(get("/crm/requests/alerts").cookie(c)).andExpect(status().isOk()).andReturn());
        assertThat(meu.get("kind").asText()).isEqualTo("MY_UPDATES");
        assertThat(meu.get("count").asLong()).isEqualTo(2);
        assertThat(meu.get("items").get(0).get("closingNote").asText()).startsWith("A peça chegou");

        mvc.perform(post("/crm/requests/alerts/seen").cookie(c)).andExpect(status().isOk());
        JsonNode depois = body(mvc.perform(get("/crm/requests/alerts").cookie(c)).andReturn());
        assertThat(depois.get("count").asLong()).isZero();
    }

    @Test // Operações e Compras recebe os chamados (sino) e resolve, mas não vende nem abre chamado
    void operacoesECompraRecebeEResolveOsChamados() throws Exception {
        Cookie compras = loginAs("PURCHASING");
        long id = open(as(newSeller()), "{\"description\":\"Calça sarja 40 preta\"}").get("id").asLong();

        JsonNode sino = body(mvc.perform(get("/crm/requests/alerts").cookie(compras)).andExpect(status().isOk()).andReturn());
        assertThat(sino.get("kind").asText()).isEqualTo("OPEN_REQUESTS");
        mvc.perform(post("/crm/requests/" + id + "/resolve").cookie(compras).contentType(APPLICATION_JSON)
                        .content("{\"note\":\"Pedido feito ao fornecedor\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RESOLVED"));

        mvc.perform(post("/crm/requests").cookie(compras).contentType(APPLICATION_JSON).content("{\"description\":\"Teste\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/crm/sales").cookie(compras).contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/crm/stock/alerts").cookie(compras)).andExpect(status().isOk());
    }

    @Test // REQ-08: a Talita só abre depois de confirmado, e só para quem atende
    void talitaAbreChamadoSoComConfirmacao() throws Exception {
        AppUser seller = newSeller();
        var semConfirmar = tools.execute("abrir_chamado", Map.of("descricao", "Vestido longo M"), "SELLER", seller.getId());
        assertThat(semConfirmar.error()).isTrue();
        assertThat(semConfirmar.content()).contains("Posso abrir o chamado?");

        var ok = tools.execute("abrir_chamado", Map.of("descricao", "Vestido longo M", "confirmado", true), "SELLER", seller.getId());
        assertThat(ok.error()).isFalse();
        assertThat(ok.content()).contains("\"openedVia\":\"ASSISTANT\"");

        var estoquista = tools.execute("abrir_chamado", Map.of("descricao", "Vestido", "confirmado", true), "STOCK_KEEPER", 1L);
        assertThat(estoquista.error()).isTrue();
        assertThat(tools.execute("listar_chamados", Map.of(), "STOCK_KEEPER", 1L).error()).isFalse();
    }
}
