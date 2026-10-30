package br.senai.crmloja.modules.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.senai.crmloja.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

// Conta do cliente pelo site e "Esqueci minha senha" (V15, RN65 a RN68).
class AccountIT extends IntegrationTest {

    static final AtomicLong SEQ = new AtomicLong(System.nanoTime() % 100_000);

    @Autowired JdbcTemplate jdbc;

    // CPF de teste válido e diferente a cada chamada (dígitos verificadores calculados).
    static String cpf() {
        String base = String.format("%09d", (SEQ.incrementAndGet() * 7919) % 1_000_000_000L);
        if (base.chars().distinct().count() == 1) base = "123456789";
        int d1 = dv(base, 10), d2 = dv(base + d1, 11);
        return base + d1 + d2;
    }

    static int dv(String s, int weight) {
        int sum = 0;
        for (char c : s.toCharArray()) sum += (c - '0') * weight--;
        int r = (sum * 10) % 11;
        return r == 10 ? 0 : r;
    }

    String signupBody(String email, String cpf, String password, boolean terms) {
        return "{\"fullName\":\"Ana Teste Cadastro\",\"cpf\":\"" + cpf + "\",\"email\":\"" + email + "\",\"phone\":\"(48) 99123-4567\","
                + "\"zipCode\":\"88701-000\",\"street\":\"Rua A\",\"number\":\"10\",\"district\":\"Centro\",\"city\":\"Tubarão\","
                + "\"state\":\"sc\",\"password\":\"" + password + "\",\"acceptTerms\":" + terms + "}";
    }

    String email() {
        return "cliente" + SEQ.incrementAndGet() + "@exemplo.com";
    }

    @Test // RN65: cria a conta de cliente e entra com o e-mail
    void clienteCriaContaEEntraComOEmail() throws Exception {
        String email = email();
        mvc.perform(post("/loja/signup").contentType(APPLICATION_JSON).content(signupBody(email.toUpperCase(), cpf(), "senha-segura-1", true)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.login").value(email));
        Cookie c = login(email, "senha-segura-1");
        mvc.perform(get("/loja/me").cookie(c)).andExpect(status().isOk()).andExpect(jsonPath("$.email").value(email));
        mvc.perform(get("/crm/products").cookie(c)).andExpect(status().isForbidden());

        // Mesmo e-mail de novo: recusa.
        mvc.perform(post("/loja/signup").contentType(APPLICATION_JSON).content(signupBody(email, cpf(), "senha-segura-1", true)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("email_in_use"));
    }

    @Test // RN65 e RN66: recusas
    void cadastroRecusaSemAceiteSenhaFracaECpfDeOutroEmail() throws Exception {
        mvc.perform(post("/loja/signup").contentType(APPLICATION_JSON).content(signupBody(email(), cpf(), "senha-segura-1", false)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("terms_required"));
        mvc.perform(post("/loja/signup").contentType(APPLICATION_JSON).content(signupBody(email(), cpf(), "123", true)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("weak_password"));

        String cpf = cpf();
        String email = email();
        mvc.perform(post("/loja/signup").contentType(APPLICATION_JSON).content(signupBody(email, cpf, "senha-segura-1", true)))
                .andExpect(status().isCreated());
        mvc.perform(post("/loja/signup").contentType(APPLICATION_JSON).content(signupBody(email(), cpf, "senha-segura-1", true)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("cpf_has_account"));
    }

    @Test // RN67 e RN68: link de uso único, válido por 30 minutos
    void esqueciASenhaTrocaPeloLinkUmaVezSo() throws Exception {
        String email = email();
        mvc.perform(post("/loja/signup").contentType(APPLICATION_JSON).content(signupBody(email, cpf(), "senha-antiga-1", true)))
                .andExpect(status().isCreated());

        // A resposta é igual para e-mail com e sem conta.
        String withAccount = mvc.perform(post("/auth/forgot-password").contentType(APPLICATION_JSON).content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String without = mvc.perform(post("/auth/forgot-password").contentType(APPLICATION_JSON).content("{\"email\":\"ninguem-" + email + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(withAccount).isEqualTo(without);

        // O link enviado traz o token; o banco guarda só o hash. Aqui o teste grava um token conhecido.
        Long userId = jdbc.queryForObject("select id from app_user where lower(email) = ?", Long.class, email);
        jdbc.update("insert into password_reset_token (user_id, token_hash, expires_at) values (?, ?, ?)",
                userId, br.senai.crmloja.modules.crm.store.AccountServiceAccess.sha256("token-de-teste-" + userId), OffsetDateTime.now().plusMinutes(30));

        mvc.perform(post("/auth/reset-password").contentType(APPLICATION_JSON)
                        .content("{\"token\":\"token-de-teste-" + userId + "\",\"password\":\"senha-nova-123\"}"))
                .andExpect(status().isOk());
        login(email, "senha-nova-123");
        // Usado uma vez: não vale de novo.
        mvc.perform(post("/auth/reset-password").contentType(APPLICATION_JSON)
                        .content("{\"token\":\"token-de-teste-" + userId + "\",\"password\":\"outra-senha-123\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("invalid_reset_link"));

        // Vencido: não vale.
        jdbc.update("insert into password_reset_token (user_id, token_hash, expires_at) values (?, ?, ?)",
                userId, br.senai.crmloja.modules.crm.store.AccountServiceAccess.sha256("vencido-" + userId), OffsetDateTime.now().minusMinutes(1));
        mvc.perform(post("/auth/reset-password").contentType(APPLICATION_JSON)
                        .content("{\"token\":\"vencido-" + userId + "\",\"password\":\"outra-senha-123\"}"))
                .andExpect(status().isConflict());

        mvc.perform(post("/auth/forgot-password").contentType(APPLICATION_JSON).content("{\"email\":\"sem-arroba\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("invalid_email"));
    }
}
