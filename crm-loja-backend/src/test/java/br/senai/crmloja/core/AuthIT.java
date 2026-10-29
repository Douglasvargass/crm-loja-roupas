package br.senai.crmloja.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.senai.crmloja.core.identity.AppUserRepository;
import br.senai.crmloja.support.IntegrationTest;
import br.senai.crmloja.support.TestData;
import br.senai.crmloja.support.TestUsers;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

class AuthIT extends IntegrationTest {

    @Autowired AppUserRepository appUsers;

    MvcResult loginResult(String login, String password) throws Exception {
        return mvc.perform(post("/auth/login").contentType(APPLICATION_JSON)
                .content("{\"login\":\"" + login + "\",\"password\":\"" + password + "\"}")).andReturn();
    }

    @Test // HU33
    void loginGravaCookiesHttpOnlyEMeDevolvePapel() throws Exception {
        MvcResult r = loginResult(users.ensure("SELLER"), TestUsers.PASSWORD);
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        Cookie access = r.getResponse().getCookie("access_token");
        assertThat(access).isNotNull();
        assertThat(access.isHttpOnly()).isTrue();
        assertThat(r.getResponse().getCookie("refresh_token").getPath()).isEqualTo("/auth");

        mvc.perform(get("/auth/me").cookie(access))
                .andExpect(jsonPath("$.role").value("SELLER"))
                .andExpect(jsonPath("$.mustChangePassword").value(false));
    }

    @Test // HU33: mesma frase para senha errada, login inexistente e conta inativa
    void loginInvalidoTemMensagemUnica() throws Exception {
        String login = users.ensure("SELLER");
        var u = users.create(TestData.unique("inativo") + "@loja.local", "SELLER", false);
        u.setActive(false);
        appUsers.save(u);

        for (String[] attempt : new String[][]{{login, "errada"}, {"ninguem@loja.local", "x"},
                {u.getLogin(), TestUsers.PASSWORD}}) {
            mvc.perform(post("/auth/login").contentType(APPLICATION_JSON)
                            .content("{\"login\":\"" + attempt[0] + "\",\"password\":\"" + attempt[1] + "\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("Login ou senha incorretos"));
        }
    }

    @Test // CT35, HU34
    void trocaDeSenhaPendenteBloqueiaOCrmAteTrocar() throws Exception {
        String login = TestData.unique("novo") + "@loja.local";
        users.create(login, "SELLER", true);
        Cookie access = login(login, TestUsers.PASSWORD);

        mvc.perform(get("/crm/customers").cookie(access))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("password_change_required"));

        mvc.perform(post("/auth/change-password").cookie(access).contentType(APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + TestUsers.PASSWORD + "\",\"newPassword\":\"curta\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("weak_password"));

        MvcResult changed = mvc.perform(post("/auth/change-password").cookie(access).contentType(APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + TestUsers.PASSWORD + "\",\"newPassword\":\"nova-senha-forte\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(false))
                .andReturn();

        mvc.perform(get("/crm/customers").cookie(changed.getResponse().getCookie("access_token")))
                .andExpect(status().isOk());
    }

    @Test // CT36
    void reusoDeRefreshRevogaASessaoInteira() throws Exception {
        MvcResult r = loginResult(users.ensure("MANAGER"), TestUsers.PASSWORD);
        Cookie first = r.getResponse().getCookie("refresh_token");

        MvcResult rotated = mvc.perform(post("/auth/refresh").cookie(first)).andExpect(status().isOk()).andReturn();
        Cookie second = rotated.getResponse().getCookie("refresh_token");
        assertThat(second.getValue()).isNotEqualTo(first.getValue());

        // Reapresentar o primeiro (já trocado) indica roubo: 401 e o segundo também morre.
        mvc.perform(post("/auth/refresh").cookie(first))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("session_revoked"));
        mvc.perform(post("/auth/refresh").cookie(second)).andExpect(status().isUnauthorized());
    }

    @Test // CT37
    void gerenteNaoMudaOProprioPapel() throws Exception {
        var self = users.create(TestData.unique("gerente") + "@loja.local", "MANAGER", false);
        Cookie access = login(self.getLogin(), TestUsers.PASSWORD);
        mvc.perform(patch("/admin/users/" + self.getId()).cookie(access).contentType(APPLICATION_JSON)
                        .content("{\"role\":\"SELLER\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("self_role_change"));
    }

    @Test // HU35
    void gerenteCriaContaComSenhaProvisoriaEMudaPapel() throws Exception {
        Cookie manager = loginAs("MANAGER");
        String login = TestData.unique("vendedora") + "@loja.local";
        MvcResult created = mvc.perform(post("/admin/users").cookie(manager).contentType(APPLICATION_JSON)
                        .content("{\"login\":\"" + login + "\",\"fullName\":\"Vera Vendedora\",\"role\":\"SELLER\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.mustChangePassword").value(true))
                .andReturn();
        long id = body(created).get("user").get("id").asLong();
        String provisional = body(created).get("provisionalPassword").asText();
        assertThat(provisional).hasSize(10);

        login(login, provisional);   // senha provisória funciona
        mvc.perform(patch("/admin/users/" + id).cookie(manager).contentType(APPLICATION_JSON)
                        .content("{\"role\":\"STOCK_KEEPER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("STOCK_KEEPER"));
        mvc.perform(get("/admin/users").cookie(loginAs("SELLER"))).andExpect(status().isForbidden());
    }

    @Test // login curto no padrão nome.sobrenome; maiúscula ou espaço são recusados
    void loginCurtoNoPadraoNomeSobrenome() throws Exception {
        Cookie manager = loginAs("MANAGER");
        String login = "ana.silva" + TestData.unique("");
        mvc.perform(post("/admin/users").cookie(manager).contentType(APPLICATION_JSON)
                        .content("{\"login\":\"" + login + "\",\"fullName\":\"Ana Silva\",\"role\":\"SELLER\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.login").value(login));
        for (String ruim : new String[]{"Ana.Silva", "ana silva", "ana..silva", ".ana"}) {
            mvc.perform(post("/admin/users").cookie(manager).contentType(APPLICATION_JSON)
                            .content("{\"login\":\"" + ruim + "\",\"fullName\":\"Ana Silva\",\"role\":\"SELLER\"}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void logoutRevogaORefresh() throws Exception {
        MvcResult r = loginResult(users.ensure("SELLER"), TestUsers.PASSWORD);
        Cookie refresh = r.getResponse().getCookie("refresh_token");
        mvc.perform(post("/auth/logout").cookie(refresh)).andExpect(status().isNoContent());
        mvc.perform(post("/auth/refresh").cookie(refresh)).andExpect(status().isUnauthorized());
    }
}
