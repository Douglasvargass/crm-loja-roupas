package br.senai.crmloja.modules.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.senai.crmloja.modules.crm.assistant.AssistantTools;
import br.senai.crmloja.support.IntegrationTest;
import com.anthropic.models.messages.Tool;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

// Sem ANTHROPIC_API_KEY: testa o modo desligado e as ferramentas que o Claude chamaria.
// A conversa com o modelo de verdade é testada à mão (docs/10-assistente-ia.md).
class AssistantIT extends IntegrationTest {

    @Autowired AssistantTools tools;

    @Test
    void semChaveOAssistenteFicaDesligadoComMensagemClara() throws Exception {
        Cookie seller = loginAs("SELLER");
        mvc.perform(get("/crm/assistant/status").cookie(seller))
                .andExpect(jsonPath("$.enabled").value(false));
        mvc.perform(post("/crm/assistant/chat").cookie(seller).contentType(APPLICATION_JSON)
                        .content("{\"message\":\"Quanto vendemos hoje?\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("assistant_disabled"));
    }

    @Test
    void ferramentasRespeitamOPapel() {
        var sellerTools = tools.toolsFor("SELLER").stream().map(Tool::name).toList();
        assertThat(sellerTools).contains("buscar_clientes", "buscar_vendas", "buscar_produtos")
                .doesNotContain("consultar_dashboard", "alertas_estoque", "movimentacoes_estoque");

        var stockTools = tools.toolsFor("STOCK_KEEPER").stream().map(Tool::name).toList();
        assertThat(stockTools).contains("alertas_estoque", "movimentacoes_estoque")
                .doesNotContain("buscar_clientes", "consultar_dashboard");

        assertThat(tools.toolsFor("MANAGER")).hasSize(10);

        // Mesmo se o modelo pedir uma ferramenta fora do papel, a execução recusa.
        var denied = tools.execute("consultar_dashboard", Map.of(), "SELLER");
        assertThat(denied.error()).isTrue();
    }

    @Test
    void ferramentasDevolvemDadosReaisEmJson() throws Exception {
        Cookie manager = loginAs("MANAGER");
        long category = createCategory(manager);
        long product = createProduct(manager, category, "77.00", 4);

        var detail = tools.execute("detalhe_produto", Map.of("produto_id", product), "MANAGER");
        assertThat(detail.error()).isFalse();
        assertThat(detail.content()).contains("\"stockQty\":4").contains("Estoque inicial");

        var dash = tools.execute("consultar_dashboard", Map.of("de", "2020-01-01", "ate", "2020-01-31"), "MANAGER");
        assertThat(dash.error()).isFalse();
        assertThat(dash.content()).contains("\"confirmedSales\"");
    }

    @Test
    void argumentoInvalidoViraErroLegivelParaOModelo() {
        var r = tools.execute("consultar_dashboard", Map.of("de", "ontem"), "MANAGER");
        assertThat(r.error()).isTrue();
        assertThat(r.content()).contains("yyyy-mm-dd");
        assertThat(tools.execute("detalhe_venda", Map.of("venda_id", 999999), "MANAGER").content())
                .contains("Venda não encontrada");
    }
}
