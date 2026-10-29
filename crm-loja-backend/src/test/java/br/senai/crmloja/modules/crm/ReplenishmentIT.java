package br.senai.crmloja.modules.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.senai.crmloja.core.config.AppProperties;
import br.senai.crmloja.modules.crm.assistant.AssistantTools;
import br.senai.crmloja.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

// Spec crm/11: painel "O que vai faltar" e pedidos de compra. Cada teste usa produtos próprios.
class ReplenishmentIT extends IntegrationTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired AppProperties props;
    @Autowired AssistantTools tools;

    Cookie manager;
    Cookie compras;
    Cookie estoque;
    long category;
    LocalDate hoje;

    @BeforeEach
    void setUp() throws Exception {
        manager = loginAs("MANAGER");
        compras = loginAs("PURCHASING");
        estoque = loginAs("STOCK_KEEPER");
        category = createCategory(manager);
        hoje = LocalDate.now(props.zone());
    }

    void sell(long product, int qty) throws Exception {
        long sale = body(mvc.perform(post("/crm/sales").cookie(manager).contentType(APPLICATION_JSON).content("{}"))
                .andReturn()).get("id").asLong();
        mvc.perform(post("/crm/sales/" + sale + "/items").cookie(manager).contentType(APPLICATION_JSON)
                .content("{\"productId\":" + product + ",\"quantity\":" + qty + "}")).andExpect(status().isOk());
        mvc.perform(post("/crm/sales/" + sale + "/confirm").cookie(manager)).andExpect(status().isOk());
    }

    JsonNode planningItem(Cookie who, long product) throws Exception {
        JsonNode plan = body(mvc.perform(get("/crm/stock/planning").cookie(who)).andExpect(status().isOk()).andReturn());
        for (JsonNode i : plan.get("items")) if (i.get("productId").asLong() == product) return i;
        throw new AssertionError("Produto " + product + " fora do painel");
    }

    String order(String supplier, LocalDate date, long product, int qty) {
        return "{\"supplier\":\"" + supplier + "\",\"expectedDate\":\"" + date + "\",\"items\":[{\"productId\":"
                + product + ",\"quantity\":" + qty + "}]}";
    }

    JsonNode create(String json) throws Exception {
        return body(mvc.perform(post("/crm/purchase-orders").cookie(compras).contentType(APPLICATION_JSON).content(json))
                .andExpect(status().isCreated()).andReturn());
    }

    @Test // RN43/RN44: vende 15 de 20 → acaba em 10 dias, pedir 12; com o pedido a caminho, não precisa pedir mais
    void painelCalculaEDescontaOQueEstaACaminho() throws Exception {
        long p = createProduct(manager, category, "90.00", 20);   // estoque mínimo 2 (TestData)
        sell(p, 15);

        JsonNode i = planningItem(compras, p);
        assertThat(i.get("stock").asInt()).isEqualTo(5);
        assertThat(i.get("soldInWindow").asInt()).isEqualTo(15);
        assertThat(i.get("daysLeft").asInt()).isEqualTo(10);
        assertThat(i.get("status").asText()).isEqualTo("ATTENTION");
        assertThat(i.get("suggestedQty").asInt()).isEqualTo(12);   // 0,5 × 30 + 2 − 5
        assertThat(i.get("needsOrder").asBoolean()).isTrue();

        create(order("Malharia Teste", hoje.plusDays(4), p, 12));
        JsonNode depois = planningItem(compras, p);
        assertThat(depois.get("incoming").asInt()).isEqualTo(12);
        assertThat(depois.get("nextArrival").asText()).isEqualTo(hoje.plusDays(4).toString());
        assertThat(depois.get("suggestedQty").asInt()).isZero();
        assertThat(depois.get("covered").asBoolean()).isTrue();
        assertThat(depois.get("needsOrder").asBoolean()).isFalse();
    }

    @Test // RN46: peça com estoque e sem venda em 30 dias aparece parada, com o valor empatado no resumo
    void pecaSemVendaApareceParada() throws Exception {
        long p = createProduct(manager, category, "50.00", 8);
        // Peça recém-cadastrada é NEW; para ser "parada" precisa existir há mais que a janela de 30 dias.
        assertThat(planningItem(estoque, p).get("status").asText()).isEqualTo("NEW");
        jdbc.update("update product set created_at = now() - interval '60 days' where id = ?", p);
        JsonNode i = planningItem(estoque, p);
        assertThat(i.get("status").asText()).isEqualTo("STALE");
        assertThat(i.get("daysLeft").isNull()).isTrue();
        JsonNode plan = body(mvc.perform(get("/crm/stock/planning").cookie(manager)).andReturn());
        assertThat(plan.get("summary").get("staleValue").decimalValue()).isGreaterThanOrEqualTo(new java.math.BigDecimal("400.00"));
        mvc.perform(get("/crm/stock/planning").param("horizon", "5").cookie(manager)).andExpect(status().isBadRequest());
        mvc.perform(get("/crm/stock/planning").cookie(loginAs("SELLER"))).andExpect(status().isForbidden());
    }

    @Test // PC-01, PC-02: quem compra registra; validações; estoquista e vendedor não registram
    void registrarPedidoEValidacoes() throws Exception {
        long p = createProduct(manager, category, "70.00", 3);
        JsonNode o = create(order("Confecções Teste", hoje.plusDays(7), p, 10));
        assertThat(o.get("status").asText()).isEqualTo("OPEN");
        assertThat(o.get("late").asBoolean()).isFalse();
        assertThat(o.get("totalUnits").asInt()).isEqualTo(10);
        assertThat(o.get("items").get(0).get("productId").asLong()).isEqualTo(p);

        mvc.perform(post("/crm/purchase-orders").cookie(compras).contentType(APPLICATION_JSON)
                        .content(order("X Ltda", hoje.minusDays(1), p, 1)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("date_in_past"));
        mvc.perform(post("/crm/purchase-orders").cookie(compras).contentType(APPLICATION_JSON)
                .content("{\"supplier\":\"X Ltda\",\"expectedDate\":\"" + hoje + "\",\"items\":[]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/crm/purchase-orders").cookie(compras).contentType(APPLICATION_JSON)
                        .content("{\"supplier\":\"X Ltda\",\"expectedDate\":\"" + hoje + "\",\"items\":[{\"productId\":" + p
                                + ",\"quantity\":1},{\"productId\":" + p + ",\"quantity\":2}]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("duplicated_product"));
        mvc.perform(post("/crm/purchase-orders").cookie(compras).contentType(APPLICATION_JSON)
                .content(order("X Ltda", hoje, p, 0))).andExpect(status().isBadRequest());
        mvc.perform(post("/crm/purchase-orders").cookie(estoque).contentType(APPLICATION_JSON)
                .content(order("X Ltda", hoje, p, 1))).andExpect(status().isForbidden());
        mvc.perform(get("/crm/purchase-orders").cookie(loginAs("SELLER"))).andExpect(status().isForbidden());
    }

    @Test // PC-03: receber dá entrada de cada peça, fecha o chamado da peça e não recebe duas vezes
    void receberDaEntradaEFechaChamado() throws Exception {
        long p = createProduct(manager, category, "120.00", 0);
        long chamado = body(mvc.perform(post("/crm/requests").cookie(loginAs("SELLER")).contentType(APPLICATION_JSON)
                        .content("{\"productId\":" + p + ",\"description\":\"Cliente quer esta\",\"quantity\":2}"))
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();
        long id = create(order("Jeans Teste", hoje.plusDays(2), p, 5)).get("id").asLong();

        JsonNode r = body(mvc.perform(post("/crm/purchase-orders/" + id + "/receive").cookie(estoque)
                        .contentType(APPLICATION_JSON).content("{\"invoiceNumber\":\"123456\",\"note\":\"Conferido na nota\"}"))
                .andExpect(status().isOk()).andReturn());
        assertThat(r.get("order").get("status").asText()).isEqualTo("RECEIVED");
        assertThat(r.get("order").get("closingNote").asText()).isEqualTo("Conferido na nota");
        assertThat(r.get("order").get("invoiceNumber").asText()).isEqualTo("123456");
        assertThat(r.get("requestsResolved").asInt()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select stock_qty from product where id = ?", Integer.class, p)).isEqualTo(5);
        assertThat(jdbc.queryForObject("select status from stock_request where id = ?", String.class, chamado)).isEqualTo("RESOLVED");
        assertThat(jdbc.queryForObject("select reason from stock_movement where product_id = ? and type = 'ENTRY' order by id desc limit 1",
                String.class, p)).isEqualTo("Pedido de compra nº " + id + " · Jeans Teste · NF 123456");

        mvc.perform(post("/crm/purchase-orders/" + id + "/receive").cookie(estoque))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("order_closed"));
    }

    @Test // PC-04, PC-05: mudar data exige motivo e fica na auditoria; cancelar exige motivo; atrasado aparece no filtro
    void mudarDataCancelarEAtrasado() throws Exception {
        long p = createProduct(manager, category, "60.00", 1);
        long id = create(order("Fornecedor Teste", hoje.plusDays(3), p, 4)).get("id").asLong();

        mvc.perform(post("/crm/purchase-orders/" + id + "/reschedule").cookie(compras).contentType(APPLICATION_JSON)
                .content("{\"expectedDate\":\"" + hoje.plusDays(9) + "\",\"note\":\"\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/crm/purchase-orders/" + id + "/reschedule").cookie(compras).contentType(APPLICATION_JSON)
                        .content("{\"expectedDate\":\"" + hoje.plusDays(3) + "\",\"note\":\"Mesma data\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("same_date"));
        mvc.perform(post("/crm/purchase-orders/" + id + "/reschedule").cookie(compras).contentType(APPLICATION_JSON)
                        .content("{\"expectedDate\":\"" + hoje.plusDays(9) + "\",\"note\":\"Fornecedor atrasou a produção\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rescheduleCount").value(1))
                .andExpect(jsonPath("$.expectedDate").value(hoje.plusDays(9).toString()));
        mvc.perform(post("/crm/purchase-orders/" + id + "/reschedule").cookie(estoque).contentType(APPLICATION_JSON)
                .content("{\"expectedDate\":\"" + hoje.plusDays(10) + "\",\"note\":\"Tentativa\"}")).andExpect(status().isForbidden());

        mvc.perform(post("/crm/purchase-orders/" + id + "/cancel").cookie(compras).contentType(APPLICATION_JSON)
                        .content("{\"note\":\"Achamos preço melhor em outro fornecedor\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(jdbc.queryForObject("select count(*) from audit_event where entity_type = 'purchase_order' and entity_id = ?",
                Long.class, id)).isEqualTo(3);   // criado + data mudou + cancelado

        // Pedido aberto com a data vencida: só dá para existir vindo de antes (a tela não aceita data passada).
        Long atrasado = jdbc.queryForObject("insert into purchase_order (supplier, expected_date) values ('Atrasado Teste', ?) returning id",
                Long.class, hoje.minusDays(2));
        jdbc.update("insert into purchase_order_item (order_id, product_id, quantity) values (?, ?, 3)", atrasado, p);
        JsonNode lista = body(mvc.perform(get("/crm/purchase-orders").param("late", "true").param("size", "100").cookie(estoque))
                .andExpect(status().isOk()).andReturn());
        boolean achou = false;
        for (JsonNode o : lista.get("content")) {
            assertThat(o.get("late").asBoolean()).isTrue();
            achou |= o.get("id").asLong() == atrasado;
        }
        assertThat(achou).isTrue();
    }

    @Test // REQ-06: pedido feito → em trânsito (com NF) → em conferência → entrada; cada etapa com o papel certo
    void etapasDoPedidoAteAEntrada() throws Exception {
        long p = createProduct(manager, category, "40.00", 2);
        long id = create(order("Etapas Teste", hoje.plusDays(5), p, 6)).get("id").asLong();

        // Sem nota fiscal não dá entrada.
        mvc.perform(post("/crm/purchase-orders/" + id + "/receive").cookie(estoque).contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("invoice_required"));
        // Saiu do fornecedor: quem compra marca; o estoquista não.
        mvc.perform(post("/crm/purchase-orders/" + id + "/ship").cookie(estoque)).andExpect(status().isForbidden());
        mvc.perform(post("/crm/purchase-orders/" + id + "/ship").cookie(compras).contentType(APPLICATION_JSON)
                        .content("{\"invoiceNumber\":\"NF-777\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_TRANSIT"))
                .andExpect(jsonPath("$.invoiceNumber").value("NF-777"));
        // Em trânsito ainda conta como a caminho na Reposição.
        assertThat(planningItem(compras, p).get("incoming").asInt()).isEqualTo(6);
        mvc.perform(post("/crm/purchase-orders/" + id + "/ship").cookie(compras))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("wrong_step"));

        // Chegou na loja: quem recebe marca; aí não dá mais para mudar a data.
        mvc.perform(post("/crm/purchase-orders/" + id + "/arrive").cookie(estoque))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CHECKING"));
        mvc.perform(post("/crm/purchase-orders/" + id + "/reschedule").cookie(compras).contentType(APPLICATION_JSON)
                        .content("{\"expectedDate\":\"" + hoje.plusDays(9) + "\",\"note\":\"Tentativa\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("wrong_step"));

        // Conferido: a NF do envio vale para a entrada.
        mvc.perform(post("/crm/purchase-orders/" + id + "/receive").cookie(estoque))
                .andExpect(status().isOk()).andExpect(jsonPath("$.order.status").value("RECEIVED"))
                .andExpect(jsonPath("$.order.invoiceNumber").value("NF-777"));
        assertThat(jdbc.queryForObject("select stock_qty from product where id = ?", Integer.class, p)).isEqualTo(8);

        // Dashboard: a entrada aparece nas notas da semana.
        JsonNode plan = body(mvc.perform(get("/crm/stock/planning").cookie(compras)).andExpect(status().isOk()).andReturn());
        assertThat(plan.get("summary").get("receivedThisWeek").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from audit_event where entity_type = 'purchase_order' and entity_id = ?",
                Long.class, id)).isEqualTo(4);   // criado + enviado + chegou + recebido
    }

    @Test // REQ-08 (conferência): chegou a menos → devolve a nota; chegou a mais → nota de bonificação
    void conferenciaDevolveANotaOuEntraComBonificacao() throws Exception {
        long p = createProduct(manager, category, "30.00", 0);
        long id = create(order("Conferência Teste", hoje.plusDays(3), p, 10)).get("id").asLong();
        mvc.perform(post("/crm/purchase-orders/" + id + "/arrive").cookie(estoque)).andExpect(status().isOk());

        // Chegaram 8 de 10: não dá entrada.
        mvc.perform(post("/crm/purchase-orders/" + id + "/receive").cookie(estoque).contentType(APPLICATION_JSON)
                        .content("{\"invoiceNumber\":\"NF-1\",\"items\":[{\"productId\":" + p + ",\"receivedQuantity\":8}]}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("short_delivery"));
        assertThat(jdbc.queryForObject("select stock_qty from product where id = ?", Integer.class, p)).isZero();

        // A nota volta: pedido vira "pedido feito" de novo, nada entrou, motivo na auditoria.
        mvc.perform(post("/crm/purchase-orders/" + id + "/return").cookie(estoque).contentType(APPLICATION_JSON)
                        .content("{\"note\":\"Vieram 8 de 10\",\"items\":[{\"productId\":" + p + ",\"receivedQuantity\":8}]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.returnCount").value(1)).andExpect(jsonPath("$.arrivedAt").isEmpty());
        mvc.perform(post("/crm/purchase-orders/" + id + "/return").cookie(estoque).contentType(APPLICATION_JSON)
                .content("{\"note\":\"de novo\"}")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("wrong_step"));

        // Segunda remessa: chegaram 12 de 10. Sem nota de bonificação, recusa; com ela, entram 10 + 2.
        mvc.perform(post("/crm/purchase-orders/" + id + "/arrive").cookie(estoque)).andExpect(status().isOk());
        mvc.perform(post("/crm/purchase-orders/" + id + "/receive").cookie(estoque).contentType(APPLICATION_JSON)
                        .content("{\"invoiceNumber\":\"NF-2\",\"items\":[{\"productId\":" + p + ",\"receivedQuantity\":12}]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("bonus_invoice_required"));
        JsonNode r = body(mvc.perform(post("/crm/purchase-orders/" + id + "/receive").cookie(estoque).contentType(APPLICATION_JSON)
                        .content("{\"invoiceNumber\":\"NF-2\",\"bonusInvoiceNumber\":\"NFB-9\",\"items\":[{\"productId\":" + p
                                + ",\"receivedQuantity\":12}]}"))
                .andExpect(status().isOk()).andReturn());
        assertThat(r.get("bonusUnits").asInt()).isEqualTo(2);
        assertThat(r.get("order").get("bonusInvoiceNumber").asText()).isEqualTo("NFB-9");
        assertThat(r.get("order").get("items").get(0).get("receivedQuantity").asInt()).isEqualTo(12);
        assertThat(jdbc.queryForObject("select stock_qty from product where id = ?", Integer.class, p)).isEqualTo(12);
        assertThat(jdbc.queryForObject("select count(*) from stock_movement where product_id = ? and type = 'ENTRY' and reason like 'Bonifica%'",
                Integer.class, p)).isEqualTo(1);
    }

    @Test // Thalita: previsão e pedidos só para quem cuida do estoque; mesma conta da tela
    void talitaConsultaPrevisaoEPedidos() throws Exception {
        long p = createProduct(manager, category, "80.00", 10);
        sell(p, 9);
        var r = tools.execute("previsao_estoque", Map.of("produto_id", p), "PURCHASING");
        assertThat(r.error()).isFalse();
        JsonNode j = json.readTree(r.content());
        assertThat(j.get("pecas").get(0).get("productId").asLong()).isEqualTo(p);
        assertThat(j.get("pecas").get(0).get("status").asText()).isEqualTo("CRITICAL");
        assertThat(tools.execute("pedidos_compra", Map.of("status", "OPEN"), "STOCK_KEEPER").error()).isFalse();
        assertThat(tools.execute("previsao_estoque", Map.of(), "SELLER").error()).isTrue();
    }
}
