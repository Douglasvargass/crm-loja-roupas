package br.senai.crmloja.modules.crm;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.senai.crmloja.support.IntegrationTest;
import org.junit.jupiter.api.Test;

// Spec crm/08: nos testes o perfil não é dev/demo, então o agente não existe e nada é gerado sozinho.
class DemoAgentIT extends IntegrationTest {

    @Test
    void foraDaDemonstracaoODiarioVemDesligadoEVazio() throws Exception {
        mvc.perform(get("/crm/demo-agent").cookie(loginAs("MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.days").isEmpty());
    }

    @Test
    void soOGerenteVeODiario() throws Exception {
        mvc.perform(get("/crm/demo-agent").cookie(loginAs("SELLER"))).andExpect(status().isForbidden());
        mvc.perform(get("/crm/demo-agent").cookie(loginAs("STOCK_KEEPER"))).andExpect(status().isForbidden());
        mvc.perform(get("/crm/demo-agent")).andExpect(status().isUnauthorized());
    }
}
