package br.senai.crmloja.modules.crm;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.senai.crmloja.support.IntegrationTest;
import br.senai.crmloja.support.TestData;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CustomerIT extends IntegrationTest {

    Cookie seller;

    @BeforeEach
    void setUp() throws Exception {
        seller = loginAs("SELLER");
    }

    @Test // CT01, CT02
    void cadastraEEncontraPorNomeSemAcentoEPorCpfComPontuacao() throws Exception {
        String cpf = TestData.cpf();
        String name = "João Ávila " + TestData.unique("Z");
        mvc.perform(post("/crm/customers").cookie(seller).contentType(APPLICATION_JSON)
                        .content(TestData.customerJson(name, cpf)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cpf").value(cpf))
                .andExpect(jsonPath("$.phone").value("48991234567"))
                .andExpect(jsonPath("$.zipCode").value("88010000"));

        String folded = name.replace("João Ávila", "joao avila");
        mvc.perform(get("/crm/customers").cookie(seller).param("search", folded))
                .andExpect(jsonPath("$.totalElements").value(1));

        String masked = cpf.substring(0, 3) + "." + cpf.substring(3, 6) + "." + cpf.substring(6, 9) + "-" + cpf.substring(9);
        mvc.perform(get("/crm/customers").cookie(seller).param("search", masked))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].fullName").value(name));
    }

    @Test // CT14
    void recusaClienteSemNome() throws Exception {
        mvc.perform(post("/crm/customers").cookie(seller).contentType(APPLICATION_JSON)
                        .content(TestData.customerJson("", TestData.cpf())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_input"));
    }

    @Test // CT15
    void recusaCpfComDigitosRepetidos() throws Exception {
        mvc.perform(post("/crm/customers").cookie(seller).contentType(APPLICATION_JSON)
                        .content(TestData.customerJson("Maria Teste", "111.111.111-11")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_cpf"));
    }

    @Test // CT16, HU04
    void recusaCpfDuplicadoNoCadastroENaEdicao() throws Exception {
        String cpf = TestData.cpf();
        mvc.perform(post("/crm/customers").cookie(seller).contentType(APPLICATION_JSON)
                .content(TestData.customerJson("Primeiro Cliente", cpf))).andExpect(status().isCreated());
        mvc.perform(post("/crm/customers").cookie(seller).contentType(APPLICATION_JSON)
                        .content(TestData.customerJson("Segundo Cliente", cpf)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("cpf_taken"));

        long other = createCustomer(seller);
        mvc.perform(put("/crm/customers/" + other).cookie(seller).contentType(APPLICATION_JSON)
                        .content(TestData.customerJson("Outro Cliente", cpf)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("cpf_taken"));
    }

    @Test // CT17
    void recusaEmailIncompleto() throws Exception {
        String body = TestData.customerJson("Joana Teste", TestData.cpf()).replace("cliente@email.com", "joao@");
        mvc.perform(post("/crm/customers").cookie(seller).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("E-mail inválido."));
    }

    @Test // CT29, HU05
    void clienteComVendaNaoEExcluidoSoInativado() throws Exception {
        Cookie manager = loginAs("MANAGER");
        long customer = createCustomer(manager);
        long product = createProduct(manager, createCategory(manager), "10.00", 5);
        long sale = body(mvc.perform(post("/crm/sales").cookie(manager).contentType(APPLICATION_JSON)
                .content("{\"customerId\":" + customer + "}")).andReturn()).get("id").asLong();
        mvc.perform(post("/crm/sales/" + sale + "/items").cookie(manager).contentType(APPLICATION_JSON)
                .content("{\"productId\":" + product + ",\"quantity\":1}")).andExpect(status().isOk());

        mvc.perform(delete("/crm/customers/" + customer).cookie(manager))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("customer_has_sales"));

        mvc.perform(patch("/crm/customers/" + customer + "/deactivate").cookie(manager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        // Inativo não abre venda nova, mas o histórico continua.
        mvc.perform(post("/crm/sales").cookie(manager).contentType(APPLICATION_JSON)
                        .content("{\"customerId\":" + customer + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("customer_inactive"));
        mvc.perform(get("/crm/customers/" + customer + "/sales").cookie(manager))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test // HU05
    void clienteSemVendaEExcluido() throws Exception {
        Cookie manager = loginAs("MANAGER");
        long customer = createCustomer(manager);
        mvc.perform(delete("/crm/customers/" + customer).cookie(manager)).andExpect(status().isNoContent());
        mvc.perform(get("/crm/customers/" + customer).cookie(manager)).andExpect(status().isNotFound());
    }

    @Test // CT32
    void semCookieDevolve401() throws Exception {
        mvc.perform(get("/crm/customers"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthenticated"));
    }

    @Test // RN24
    void vendedorNaoExcluiCliente() throws Exception {
        long customer = createCustomer(seller);
        mvc.perform(delete("/crm/customers/" + customer).cookie(seller)).andExpect(status().isForbidden());
    }
}
