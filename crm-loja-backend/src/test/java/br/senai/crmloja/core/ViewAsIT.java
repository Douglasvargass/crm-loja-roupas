package br.senai.crmloja.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.senai.crmloja.core.identity.AppUser;
import br.senai.crmloja.support.IntegrationTest;
import br.senai.crmloja.support.TestData;
import br.senai.crmloja.support.TestUsers;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

// Spec crm/10: "Ver como". Só gerente, só leitura, só de quem pediu, e o token nunca vira login da pessoa vista.
class ViewAsIT extends IntegrationTest {

    @Autowired JdbcTemplate jdbc;

    AppUser user(String role) {
        return users.create(TestData.unique(role.toLowerCase()) + "@loja.local", role, false);
    }

    Cookie as(AppUser u) throws Exception {
        return login(u.getLogin(), TestUsers.PASSWORD);
    }

    Cookie start(Cookie access, long targetId) throws Exception {
        return mvc.perform(post("/auth/view-as").cookie(access).contentType(APPLICATION_JSON)
                        .content("{\"userId\":" + targetId + "}"))
                .andExpect(status().isNoContent()).andReturn().getResponse().getCookie("view_as");
    }

    @Test
    void gerenteVeComoVendedorSoParaOlhar() throws Exception {
        AppUser manager = user("MANAGER");
        AppUser seller = user("SELLER");
        Cookie m = as(manager);
        Cookie va = start(m, seller.getId());
        assertThat(va.isHttpOnly()).isTrue();

        mvc.perform(get("/auth/me").cookie(m, va)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(seller.getId()))
                .andExpect(jsonPath("$.role").value("SELLER"))
                .andExpect(jsonPath("$.viewAs.viewerId").value(manager.getId()));
        // Vê o que o vendedor vê...
        mvc.perform(get("/crm/my-panel").cookie(m, va)).andExpect(status().isOk())
                .andExpect(jsonPath("$.sellerId").value(seller.getId()));
        // ...e não vê o que o vendedor não vê, mesmo sendo gerente de verdade.
        mvc.perform(get("/crm/dashboard").cookie(m, va)).andExpect(status().isForbidden());
        // Só leitura: nenhuma alteração passa.
        mvc.perform(post("/crm/sales").cookie(m, va).contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("read_only_view_as"));

        mvc.perform(delete("/auth/view-as").cookie(m, va)).andExpect(status().isNoContent());
        mvc.perform(get("/auth/me").cookie(m)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(manager.getId()))
                .andExpect(jsonPath("$.viewAs").isEmpty());

        String sql = "select count(*) from audit_event where action = ? and entity_id = ? and user_id = ?";
        assertThat(jdbc.queryForObject(sql, Long.class, "view_as_started", seller.getId(), manager.getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject(sql, Long.class, "view_as_ended", seller.getId(), manager.getId())).isEqualTo(1);
    }

    @Test
    void soQuemValePorGerenteUsa() throws Exception {
        Cookie s = as(user("SELLER"));
        AppUser other = user("SELLER");
        mvc.perform(get("/auth/view-as/targets").cookie(s)).andExpect(status().isForbidden());
        mvc.perform(post("/auth/view-as").cookie(s).contentType(APPLICATION_JSON).content("{\"userId\":" + other.getId() + "}"))
                .andExpect(status().isForbidden());
        // Equipe técnica vale por gerente.
        Cookie dev = as(user("DEVELOPER"));
        start(dev, other.getId());
    }

    @Test
    void naoVeASiMesmoNemQuemNaoExiste() throws Exception {
        AppUser manager = user("MANAGER");
        Cookie m = as(manager);
        mvc.perform(post("/auth/view-as").cookie(m).contentType(APPLICATION_JSON).content("{\"userId\":" + manager.getId() + "}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/auth/view-as").cookie(m).contentType(APPLICATION_JSON).content("{\"userId\":99999999}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void cookieDeOutraPessoaEIgnorado() throws Exception {
        AppUser a = user("MANAGER");
        AppUser b = user("MANAGER");
        Cookie vaDeA = start(as(a), user("SELLER").getId());
        // B usa o cookie que foi emitido para A: continua sendo B.
        mvc.perform(get("/auth/me").cookie(as(b), vaDeA)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(b.getId()))
                .andExpect(jsonPath("$.viewAs").isEmpty());
    }

    @Test
    void tokenDoVerComoNaoServeDeLogin() throws Exception {
        Cookie va = start(as(user("MANAGER")), user("SELLER").getId());
        mvc.perform(get("/auth/me").cookie(new Cookie("access_token", va.getValue())))
                .andExpect(status().isUnauthorized());
    }
}
