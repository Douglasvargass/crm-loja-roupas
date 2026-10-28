package br.senai.crmloja.support;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import jakarta.servlet.http.Cookie;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

// Base dos testes de integração: Postgres 16 real (embutido, sem Docker), todas as migrations Flyway
// do zero, login de verdade e chamadas pela API com MockMvc. Assim CHECKs e triggers também são testados.
// O banco é um só para a suíte; cada teste usa dados próprios (CPF e código únicos).
@SpringBootTest
@AutoConfigureMockMvc
public abstract class IntegrationTest {

    private static final EmbeddedPostgres PG = start();

    private static EmbeddedPostgres start() {
        try {
            return EmbeddedPostgres.builder().start();
        } catch (IOException e) {
            throw new IllegalStateException("Não consegui subir o Postgres embutido", e);
        }
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> PG.getJdbcUrl("postgres", "postgres"));
        r.add("spring.datasource.username", () -> "postgres");
        r.add("spring.datasource.password", () -> "postgres");
        r.add("app.jwt-secret", () -> "segredo-de-teste-com-mais-de-32-caracteres-0123456789");
    }

    @Autowired protected MockMvc mvc;
    @Autowired protected TestUsers users;   // cria usuários por papel com senha conhecida
    @Autowired protected ObjectMapper json;

    protected Cookie loginAs(String role) throws Exception {
        return login(users.ensure(role), TestUsers.PASSWORD);
    }

    protected Cookie login(String login, String password) throws Exception {
        MvcResult r = mvc.perform(post("/auth/login").contentType(APPLICATION_JSON)
                        .content(json.writeValueAsString(new LoginBody(login, password))))
                .andExpect(status().isOk()).andReturn();
        return r.getResponse().getCookie("access_token");
    }

    protected JsonNode body(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsString());
    }

    protected String toJson(Object o) throws Exception {
        return json.writeValueAsString(o);
    }

    // Atalhos de cenário: criam registros pela API, como a tela faria.
    protected long createCategory(Cookie manager) throws Exception {
        MvcResult r = mvc.perform(post("/crm/categories").cookie(manager).contentType(APPLICATION_JSON)
                        .content("{\"name\":\"" + TestData.unique("Categoria") + "\"}"))
                .andExpect(status().isCreated()).andReturn();
        return body(r).get("id").asLong();
    }

    protected long createProduct(Cookie manager, long categoryId, String price, int stock) throws Exception {
        MvcResult r = mvc.perform(post("/crm/products").cookie(manager).contentType(APPLICATION_JSON)
                        .content(TestData.productJson(TestData.unique("P"), categoryId, price, stock)))
                .andExpect(status().isCreated()).andReturn();
        return body(r).get("id").asLong();
    }

    protected long createCustomer(Cookie manager) throws Exception {
        MvcResult r = mvc.perform(post("/crm/customers").cookie(manager).contentType(APPLICATION_JSON)
                        .content(TestData.customerJson("Cliente " + TestData.unique("T"), TestData.cpf())))
                .andExpect(status().isCreated()).andReturn();
        return body(r).get("id").asLong();
    }

    private record LoginBody(String login, String password) {}
}
