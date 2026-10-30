package br.senai.crmloja.modules.crm.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import br.senai.crmloja.core.http.BusinessRuleException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

// Simula um provedor OpenAI-compatível (Groq, Gemini, OpenRouter…) sem rede: confere o formato do
// pedido, o laço de ferramentas e a tradução dos erros HTTP.
class OpenAiCompatibleProviderTest {

    private static final String BASE = "https://ia.exemplo/v1";

    private final ObjectMapper json = new ObjectMapper();
    private final List<AssistantTools.ToolDef> tools = List.of(new AssistantTools.ToolDef(
            "alertas_estoque", "Produtos com estoque baixo e zerado.", Map.of(), List.of()));

    private MockRestServiceServer server;
    private OpenAiCompatibleProvider provider;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new OpenAiCompatibleProvider(
                new AssistantProperties("groq", "gsk_teste", BASE, "modelo-x", "", 3, 500), builder, json);
    }

    @Test
    void chamaAFerramentaEDevolveARespostaFinal() {
        // 1ª rodada: o modelo pede a ferramenta.
        server.expect(requestTo(BASE + "/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer gsk_teste"))
                .andExpect(jsonPath("$.model").value("modelo-x"))
                .andExpect(jsonPath("$.max_tokens").value(500))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[1].role").value("user"))
                .andExpect(jsonPath("$.messages[1].content").value("O que preciso repor?"))
                .andExpect(jsonPath("$.tools[0].type").value("function"))
                .andExpect(jsonPath("$.tools[0].function.name").value("alertas_estoque"))
                .andExpect(jsonPath("$.tools[0].function.parameters.type").value("object"))
                .andExpect(jsonPath("$.tool_choice").value("auto"))
                .andRespond(withSuccess("""
                        {"choices":[{"finish_reason":"tool_calls","message":{"role":"assistant","content":null,
                          "reasoning":"vou consultar",
                          "tool_calls":[{"id":"call_1","type":"function",
                            "function":{"name":"alertas_estoque","arguments":"{}"}}]}}]}""", MediaType.APPLICATION_JSON));
        // 2ª rodada: recebe o resultado (role tool) e responde em texto.
        server.expect(requestTo(BASE + "/chat/completions"))
                .andExpect(jsonPath("$.messages[2].role").value("assistant"))
                .andExpect(jsonPath("$.messages[2].tool_calls[0].id").value("call_1"))
                .andExpect(jsonPath("$.messages[2].reasoning").doesNotExist())
                .andExpect(jsonPath("$.messages[3].role").value("tool"))
                .andExpect(jsonPath("$.messages[3].tool_call_id").value("call_1"))
                .andExpect(jsonPath("$.messages[3].content").value("{\"lowStock\":[\"Calça 40\"]}"))
                .andRespond(withSuccess("""
                        {"choices":[{"finish_reason":"stop","message":{"role":"assistant",
                          "content":"Precisa repor: Calça 40."}}]}""", MediaType.APPLICATION_JSON));

        List<Object> conversation = new ArrayList<>();
        List<String> used = new ArrayList<>();
        String answer = provider.ask(conversation, "Você é o assistente.", "O que preciso repor?", tools, (name, args) -> {
            used.add(name);
            return new AssistantTools.Result("{\"lowStock\":[\"Calça 40\"]}", false);
        });

        server.verify();
        assertThat(answer).isEqualTo("Precisa repor: Calça 40.");
        assertThat(used).containsExactly("alertas_estoque");
        // Conversa guardada: pergunta, pedido de ferramenta, resultado, resposta (sem o system, que muda a cada pedido).
        assertThat(conversation).hasSize(4);
    }

    @Test
    void respostaCortadaAvisaEArgumentoInvalidoViraErroParaOModelo() {
        server.expect(requestTo(BASE + "/chat/completions")).andRespond(withSuccess("""
                {"choices":[{"finish_reason":"tool_calls","message":{"role":"assistant",
                  "tool_calls":[{"id":"c1","type":"function","function":{"name":"alertas_estoque","arguments":"{nao é json"}}]}}]}""",
                MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/chat/completions"))
                .andExpect(jsonPath("$.messages[3].content").value("Argumentos inválidos (JSON malformado)."))
                .andRespond(withSuccess("""
                        {"choices":[{"finish_reason":"length","message":{"role":"assistant","content":"Lista longa"}}]}""",
                        MediaType.APPLICATION_JSON));

        String answer = provider.ask(new ArrayList<>(), "sys", "repor?", tools, (n, a) -> new AssistantTools.Result("ok", false));
        assertThat(answer).startsWith("Lista longa").contains("cortada por tamanho");
    }

    @Test
    void errosHttpViramMensagensClaras() {
        // O servidor simulado exige todas as expectativas antes do primeiro pedido, em ordem.
        server.expect(requestTo(BASE + "/chat/completions"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("{\"error\":\"bad key\"}"));
        server.expect(requestTo(BASE + "/chat/completions"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).body("{\"error\":\"rate limit\"}"));
        server.expect(requestTo(BASE + "/chat/completions"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).body("{\"error\":\"no such model\"}"));

        for (String expected : List.of("assistant_auth", "assistant_busy", "assistant_model")) {
            assertThatThrownBy(() -> provider.ask(new ArrayList<>(), "sys", "oi", tools, (n, a) -> null))
                    .isInstanceOf(BusinessRuleException.class)
                    .extracting(e -> ((BusinessRuleException) e).getCode()).isEqualTo(expected);
        }
        server.verify();
    }

    @Test
    void semChaveNaoMandaAuthorization() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer local = MockRestServiceServer.bindTo(builder).build();
        var ollama = new OpenAiCompatibleProvider(
                new AssistantProperties("ollama", "", "", "", "", 1, 100), builder, json);
        local.expect(requestTo("http://localhost:11434/v1/chat/completions"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(request -> assertThat(request.getHeaders().containsKey("Authorization")).isFalse())
                .andRespond(withSuccess("""
                        {"choices":[{"finish_reason":"stop","message":{"role":"assistant","content":"Olá"}}]}""",
                        MediaType.APPLICATION_JSON));
        assertThat(ollama.ask(new ArrayList<>(), "sys", "oi", List.of(), (n, a) -> null)).isEqualTo("Olá");
        local.verify();
    }

    @Test
    void limitePorMinutoEsperaOTempoPedidoETentaDeNovo() {
        server.expect(requestTo(BASE + "/chat/completions"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).body(
                        "{\"error\":{\"message\":\"Rate limit reached on tokens per minute (TPM): Limit 8000. Please try again in 20ms.\"}}"));
        server.expect(requestTo(BASE + "/chat/completions"))
                .andRespond(withSuccess("""
                        {"choices":[{"finish_reason":"stop","message":{"role":"assistant","content":"Vendemos R$ 10."}}]}""",
                        MediaType.APPLICATION_JSON));
        String answer = provider.ask(new ArrayList<>(), "sys", "Quanto vendemos?", tools, (n, a) -> null);
        assertThat(answer).isEqualTo("Vendemos R$ 10.");
        server.verify();
    }

    @Test
    void limiteDoDiaNaoEsperaEAvisa() {
        server.expect(requestTo(BASE + "/chat/completions"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).body(
                        "{\"error\":{\"message\":\"Rate limit reached on tokens per day (TPD). Please try again in 7m12.5s.\"}}"));
        assertThatThrownBy(() -> provider.ask(new ArrayList<>(), "sys", "Oi?", tools, (n, a) -> null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("limite gratuito de hoje");
    }
}
