package br.senai.crmloja.modules.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.senai.crmloja.core.identity.AppUser;
import br.senai.crmloja.core.identity.AppUserRepository;
import br.senai.crmloja.core.rbac.ModuleRepository;
import br.senai.crmloja.core.rbac.ModuleRoleRepository;
import br.senai.crmloja.core.rbac.UserModuleAccess;
import br.senai.crmloja.core.rbac.UserModuleAccessRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

// Spec crm/07: espaço do cliente. Cada teste cria o próprio cliente, com conta ligada ao cadastro.
class StoreIT extends IntegrationTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired AppUserRepository appUsers;
    @Autowired ModuleRepository modules;
    @Autowired ModuleRoleRepository roles;
    @Autowired UserModuleAccessRepository accesses;
    @Autowired PasswordEncoder encoder;
    @Autowired TransactionTemplate tx;

    @Autowired br.senai.crmloja.modules.crm.assistant.AssistantTools tools;

    Cookie manager;
    long category;

    @BeforeEach
    void setUp() throws Exception {
        manager = loginAs("MANAGER");
        category = createCategory(manager);
    }

    record Client(long userId, long customerId, String login) {}

    Client newClient(boolean mustChange) throws Exception {
        long customerId = createCustomer(manager);
        String login = TestData.unique("cli") + "@loja.local";
        AppUser u = tx.execute(s -> {
            AppUser a = new AppUser(login, "Cliente Teste", encoder.encode(TestUsers.PASSWORD));
            a.setMustChangePassword(mustChange);
            appUsers.save(a);
            var loja = modules.findByCode("loja").orElseThrow();
            var r = roles.findByModuleIdAndCode(loja.getId(), "CUSTOMER").orElseThrow();
            accesses.save(new UserModuleAccess(a.getId(), loja.getId(), r.getId()));
            return a;
        });
        jdbc.update("update customer set user_id = ? where id = ?", u.getId(), customerId);
        return new Client(u.getId(), customerId, login);
    }

    Cookie as(Client c) throws Exception {
        return login(c.login(), TestUsers.PASSWORD);
    }

    JsonNode addToCart(Cookie c, long product, int qty) throws Exception {
        return body(mvc.perform(post("/loja/cart/items").cookie(c).contentType(APPLICATION_JSON)
                .content("{\"productId\":" + product + ",\"quantity\":" + qty + "}")).andExpect(status().isOk()).andReturn());
    }

    JsonNode pay(Cookie c, String json) throws Exception {
        return body(mvc.perform(post("/loja/checkout").cookie(c).contentType(APPLICATION_JSON).content(json))
                .andExpect(status().isOk()).andReturn());
    }

    int stock(long product) {
        return jdbc.queryForObject("select stock_qty from product where id = ?", Integer.class, product);
    }

    @Test // REQ-01, REQ-02: cliente só entra na loja, e primeiro troca a senha
    void clienteSoEntraNaLojaEPrimeiroTrocaASenha() throws Exception {
        Client novo = newClient(true);
        Cookie c = as(novo);
        mvc.perform(get("/loja/cart").cookie(c)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("password_change_required"));

        Client ok = newClient(false);
        Cookie k = as(ok);
        mvc.perform(get("/auth/me").cookie(k)).andExpect(jsonPath("$.role").value("CUSTOMER"));
        mvc.perform(get("/crm/customers").cookie(k)).andExpect(status().isForbidden());
        mvc.perform(get("/crm/products").cookie(k)).andExpect(status().isForbidden());
        mvc.perform(get("/crm/requests").cookie(k)).andExpect(status().isForbidden());
        // E a equipe não usa a loja como cliente.
        mvc.perform(get("/loja/cart").cookie(manager)).andExpect(status().isForbidden());
    }

    @Test // REQ-04: vitrine só com o que dá para comprar, sem estoque exato
    void vitrineMostraSoOQueTemEstoqueSemQuantidade() throws Exception {
        Cookie c = as(newClient(false));
        long com = createProduct(manager, category, "59.90", 10);
        long sem = createProduct(manager, category, "59.90", 0);
        JsonNode v = body(mvc.perform(get("/loja/products").param("categoryId", String.valueOf(category)).cookie(c))
                .andExpect(status().isOk()).andReturn());
        var ids = new java.util.HashSet<Long>();
        v.get("content").forEach(p -> ids.add(p.get("id").asLong()));
        assertThat(ids).contains(com).doesNotContain(sem);
        assertThat(v.get("content").get(0).has("stockQty")).isFalse();
    }

    @Test // REQ-05, REQ-07: carrinho e pagamento por Pix baixam o estoque uma vez só
    void carrinhoEPixConfirmamOPedidoEBaixamOEstoque() throws Exception {
        Client cli = newClient(false);
        Cookie c = as(cli);
        long product = createProduct(manager, category, "100.00", 10);
        addToCart(c, product, 2);
        JsonNode cart = addToCart(c, product, 1);   // mesmo produto: soma na linha
        assertThat(cart.get("items")).hasSize(1);
        assertThat(cart.get("items").get(0).get("quantity").asInt()).isEqualTo(3);
        assertThat(cart.get("total").decimalValue()).isEqualByComparingTo("300.00");
        assertThat(stock(product)).isEqualTo(10);   // carrinho não reserva

        JsonNode order = pay(c, "{\"method\":\"PIX\"}");
        assertThat(order.get("sale").get("status").asText()).isEqualTo("CONFIRMED");
        assertThat(order.get("sale").get("channel").asText()).isEqualTo("ONLINE");
        assertThat(order.get("paymentMethod").asText()).isEqualTo("PIX");
        assertThat(stock(product)).isEqualTo(7);

        // Carrinho volta vazio; pagar de novo não cobra nada.
        assertThat(body(mvc.perform(get("/loja/cart").cookie(c)).andReturn()).get("count").asInt()).isZero();
        mvc.perform(post("/loja/checkout").cookie(c).contentType(APPLICATION_JSON).content("{\"method\":\"PIX\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("cart_empty"));
        assertThat(jdbc.queryForObject("select count(*) from payment p join sale s on s.id = p.sale_id where s.customer_id = ?",
                Long.class, cli.customerId())).isEqualTo(1);
    }

    @Test // REQ-06: cartão de teste final 0002 recusa e nada muda; outro cartão aprova e guarda só os 4 dígitos
    void cartaoDeTesteRecusaECartaoBomAprova() throws Exception {
        Cookie c = as(newClient(false));
        long product = createProduct(manager, category, "80.00", 5);
        addToCart(c, product, 1);
        mvc.perform(post("/loja/checkout").cookie(c).contentType(APPLICATION_JSON)
                        .content("{\"method\":\"CARD\",\"cardNumber\":\"4000 0000 0000 0002\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("payment_declined"));
        assertThat(stock(product)).isEqualTo(5);

        JsonNode order = pay(c, "{\"method\":\"CARD\",\"cardNumber\":\"4111 1111 1111 1111\"}");
        assertThat(order.get("cardLast4").asText()).isEqualTo("1111");
        assertThat(stock(product)).isEqualTo(4);
    }

    @Test // Decisão 1: preço mudou com a peça no carrinho → carrinho atualiza, avisa, e o próximo pagamento usa o novo
    void precoQueMudaAtualizaOCarrinhoEAvisa() throws Exception {
        Cookie c = as(newClient(false));
        long product = createProduct(manager, category, "50.00", 5);
        addToCart(c, product, 2);
        jdbc.update("update product set price = 60.00 where id = ?", product);

        mvc.perform(post("/loja/checkout").cookie(c).contentType(APPLICATION_JSON).content("{\"method\":\"PIX\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("price_changed"));
        assertThat(body(mvc.perform(get("/loja/cart").cookie(c)).andReturn()).get("total").decimalValue())
                .isEqualByComparingTo("120.00");
        JsonNode order = pay(c, "{\"method\":\"PIX\"}");
        assertThat(order.get("sale").get("total").decimalValue()).isEqualByComparingTo("120.00");
    }

    @Test // REQ-08: pedido de outro cliente não existe para mim
    void pedidoDeOutroClienteNaoAparece() throws Exception {
        Cookie a = as(newClient(false));
        Cookie b = as(newClient(false));
        long product = createProduct(manager, category, "30.00", 5);
        addToCart(b, product, 1);
        long pedidoB = pay(b, "{\"method\":\"PIX\"}").get("sale").get("id").asLong();

        mvc.perform(get("/loja/orders/" + pedidoB).cookie(a)).andExpect(status().isNotFound());
        assertThat(body(mvc.perform(get("/loja/orders").cookie(a)).andReturn())).isEmpty();
        assertThat(body(mvc.perform(get("/loja/orders").cookie(b)).andReturn())).hasSize(1);
    }

    @Test // REQ-09: o cliente muda contato e endereço, nunca nome nem CPF
    void meusDadosNaoMudamNomeNemCpf() throws Exception {
        Client cli = newClient(false);
        Cookie c = as(cli);
        String cpfAntes = jdbc.queryForObject("select cpf from customer where id = ?", String.class, cli.customerId());
        mvc.perform(put("/loja/me").cookie(c).contentType(APPLICATION_JSON).content("""
                        {"phone":"(48) 99999-1111","email":"novo@email.com","zipCode":"88701-000","street":"Rua Nova",
                         "number":"5","complement":null,"district":"Centro","city":"Tubarão","state":"SC",
                         "fullName":"Outro Nome","cpf":"52998224725"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("novo@email.com"))
                .andExpect(jsonPath("$.cpf").value(cpfAntes));
        // Os campos "fullName" e "cpf" mandados pelo cliente são ignorados.
        assertThat(jdbc.queryForObject("select cpf from customer where id = ?", String.class, cli.customerId())).isEqualTo(cpfAntes);
        assertThat(jdbc.queryForObject("select full_name from customer where id = ?", String.class, cli.customerId()))
                .doesNotContain("Outro Nome");
    }

    @Test // REQ-10: gerente cancela pedido online pago → estoque volta e pagamento estornado; dashboard filtra canal
    void cancelarPedidoOnlineEstornaEDashboardFiltraCanal() throws Exception {
        Cookie c = as(newClient(false));
        long product = createProduct(manager, category, "40.00", 5);
        addToCart(c, product, 2);
        long sale = pay(c, "{\"method\":\"PIX\"}").get("sale").get("id").asLong();

        String today = java.time.LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo")).toString();
        JsonNode online = body(mvc.perform(get("/crm/dashboard").param("from", today).param("to", today)
                .param("productId", String.valueOf(product)).param("channel", "ONLINE").cookie(manager))
                .andExpect(status().isOk()).andReturn());
        assertThat(online.get("revenue").decimalValue()).isEqualByComparingTo("80.00");
        JsonNode balcao = body(mvc.perform(get("/crm/dashboard").param("from", today).param("to", today)
                .param("productId", String.valueOf(product)).param("channel", "STORE").cookie(manager)).andReturn());
        assertThat(balcao.get("revenue").decimalValue()).isEqualByComparingTo("0.00");

        mvc.perform(post("/crm/sales/" + sale + "/cancel").cookie(manager).contentType(APPLICATION_JSON)
                .content("{\"reason\":\"Cliente pediu\"}")).andExpect(status().isOk());
        assertThat(stock(product)).isEqualTo(5);
        assertThat(jdbc.queryForObject("select status from payment where sale_id = ?", String.class, sale)).isEqualTo("REFUNDED");
    }

    @Test // Talita do cliente e pedido de peça (29/10): o cliente pede, ele e o gerente veem; ninguém vê o do outro
    void clientePedePecaETalitaDoClienteSoTemAsFerramentasDele() throws Exception {
        Client a = newClient(false);
        Client b = newClient(false);
        long id = body(mvc.perform(post("/loja/requests").cookie(as(a)).contentType(APPLICATION_JSON)
                        .content("{\"description\":\"Vestido longo verde\",\"size\":\"M\",\"quantity\":1}"))
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();

        JsonNode minhas = body(mvc.perform(get("/loja/requests").cookie(as(a))).andExpect(status().isOk()).andReturn());
        assertThat(minhas.get("content").get(0).get("id").asLong()).isEqualTo(id);
        assertThat(minhas.get("content").get(0).get("customerId").asLong()).isEqualTo(a.customerId());
        JsonNode outro = body(mvc.perform(get("/loja/requests").cookie(as(b))).andExpect(status().isOk()).andReturn());
        outro.get("content").forEach(r -> assertThat(r.get("id").asLong()).isNotEqualTo(id));

        JsonNode doGerente = body(mvc.perform(get("/crm/requests").param("status", "OPEN").param("size", "100").cookie(manager))
                .andExpect(status().isOk()).andReturn());
        boolean achou = false;
        for (JsonNode r : doGerente.get("content")) achou |= r.get("id").asLong() == id;
        assertThat(achou).isTrue();

        // Ferramentas: o cliente só recebe as dele; a equipe não recebe as do cliente.
        var doCliente = tools.definitionsFor("CUSTOMER").stream().map(AssistantTools.ToolDef::name).toList();
        assertThat(doCliente).containsExactlyInAnyOrder("vitrine", "categorias_loja", "meus_pedidos", "detalhe_meu_pedido",
                "minhas_solicitacoes", "pedir_peca");
        assertThat(tools.definitionsFor("DEVELOPER").stream().map(AssistantTools.ToolDef::name).toList())
                .doesNotContain("pedir_peca", "meus_pedidos");
        assertThat(tools.execute("pedir_peca", Map.of("descricao", "Boné vermelho"), "CUSTOMER", a.userId()).error()).isTrue();
        assertThat(tools.execute("pedir_peca", Map.of("descricao", "Boné vermelho", "confirmado", true), "CUSTOMER", a.userId())
                .error()).isFalse();
        assertThat(tools.execute("consultar_dashboard", Map.of(), "CUSTOMER", a.userId()).error()).isTrue();

        // Cada um na sua conversa.
        mvc.perform(get("/loja/assistant/status").cookie(as(a))).andExpect(status().isOk());
        mvc.perform(get("/loja/assistant/status").cookie(manager)).andExpect(status().isForbidden());
        mvc.perform(get("/crm/assistant/status").cookie(as(a))).andExpect(status().isForbidden());
    }

    @Test // Revisão 29/10: cliente com login não se exclui (oferece inativar); gerente reseta a senha dele pela tela
    void clienteComLoginNaoSeExcluiEGerenteResetaASenha() throws Exception {
        Client c = newClient(false);
        mvc.perform(delete("/crm/customers/" + c.customerId()).cookie(manager))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("customer_has_login"));
        JsonNode ficha = body(mvc.perform(get("/crm/customers/" + c.customerId()).cookie(manager)).andExpect(status().isOk()).andReturn());
        assertThat(ficha.get("userId").asLong()).isEqualTo(c.userId());

        Cookie antes = as(c);
        JsonNode r = body(mvc.perform(post("/admin/users/" + c.userId() + "/reset-password").param("module", "loja").cookie(manager))
                .andExpect(status().isOk()).andReturn());
        String provisoria = r.get("provisionalPassword").asText();
        assertThat(provisoria).hasSizeGreaterThanOrEqualTo(10);
        // A sessão antiga caiu; a senha antiga não entra; a provisória entra e exige troca.
        mvc.perform(get("/loja/cart").cookie(antes)).andExpect(status().isForbidden());
        mvc.perform(post("/auth/login").contentType(APPLICATION_JSON)
                .content("{\"login\":\"" + c.login() + "\",\"password\":\"" + TestUsers.PASSWORD + "\"}")).andExpect(status().isUnauthorized());
        JsonNode me = body(mvc.perform(post("/auth/login").contentType(APPLICATION_JSON)
                .content("{\"login\":\"" + c.login() + "\",\"password\":\"" + provisoria + "\"}")).andExpect(status().isOk()).andReturn());
        assertThat(me.get("mustChangePassword").asBoolean()).isTrue();
        // Ninguém reseta a própria senha por aqui, e o vendedor não reseta a de ninguém.
        mvc.perform(post("/admin/users/" + c.userId() + "/reset-password").param("module", "loja").cookie(loginAs("SELLER")))
                .andExpect(status().isForbidden());
    }

    @Test // Prévia da loja (29/10): o gerente lê a vitrine como o cliente vê; carrinho e pedidos continuam só do cliente
    void gerenteVeAVitrineMasNaoMexeEmCarrinho() throws Exception {
        mvc.perform(get("/loja/products").cookie(manager)).andExpect(status().isOk());
        mvc.perform(get("/loja/categories").cookie(manager)).andExpect(status().isOk());
        mvc.perform(get("/loja/cart").cookie(manager)).andExpect(status().isForbidden());
        mvc.perform(get("/loja/orders").cookie(manager)).andExpect(status().isForbidden());
        // Vendedor e estoquista não têm a prévia.
        mvc.perform(get("/loja/products").cookie(loginAs("SELLER"))).andExpect(status().isForbidden());
        mvc.perform(get("/loja/products").cookie(loginAs("STOCK_KEEPER"))).andExpect(status().isForbidden());
    }

    @Test // REQ-05: remover item do carrinho
    void removerItemDoCarrinho() throws Exception {
        Cookie c = as(newClient(false));
        long product = createProduct(manager, category, "20.00", 5);
        long itemId = addToCart(c, product, 1).get("items").get(0).get("itemId").asLong();
        JsonNode cart = body(mvc.perform(delete("/loja/cart/items/" + itemId).cookie(c)).andExpect(status().isOk()).andReturn());
        assertThat(cart.get("items")).isEmpty();
    }
}
