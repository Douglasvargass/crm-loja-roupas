package br.senai.crmloja.modules.crm.assistant;

import br.senai.crmloja.core.http.BusinessRuleException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

// Provedores no formato "OpenAI-compatível" (POST {baseUrl}/chat/completions com function calling):
// Groq, Google Gemini, OpenRouter, Ollama e outros. É o formato dos planos grátis, por isso não usa SDK:
// um JSON simples por rodada, guardado na conversa como Map para voltar igual no pedido seguinte.
class OpenAiCompatibleProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleProvider.class);

    private final AssistantProperties props;
    private final RestClient http;
    private final ObjectMapper json;

    OpenAiCompatibleProvider(AssistantProperties props, RestClient.Builder builder, ObjectMapper json) {
        this.props = props;
        this.json = json;
        var b = builder.baseUrl(props.baseUrl()).defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        if (props.apiKey() != null) b = b.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + props.apiKey());
        // O OpenRouter usa estes cabeçalhos para identificar o app no painel (opcionais nos demais).
        b = b.defaultHeader("HTTP-Referer", "https://github.com/Douglasvargass/crm-loja-roupas")
                .defaultHeader("X-Title", "Orden");
        this.http = b.build();
    }

    @Override
    public String ask(List<Object> conversation, String systemPrompt, String question,
                      List<AssistantTools.ToolDef> tools, ToolRunner runner) {
        conversation.add(message("user", question));
        List<Map<String, Object>> toolSpecs = tools.stream().map(OpenAiCompatibleProvider::toTool).toList();

        for (int round = 0; round <= props.maxToolRounds(); round++) {
            List<Object> messages = new ArrayList<>();
            // O system vai na frente em todo pedido e fora da conversa guardada (ele muda com a data e quem pergunta).
            messages.add(message("system", systemPrompt));
            messages.addAll(conversation);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", props.model());
            body.put("messages", messages);
            body.put("max_tokens", props.maxTokens());
            body.put("temperature", 0.2);
            if (!toolSpecs.isEmpty()) {
                body.put("tools", toolSpecs);
                body.put("tool_choice", "auto");
            }

            JsonNode choice = call(body).path("choices").path(0);
            JsonNode msg = choice.path("message");
            String finish = choice.path("finish_reason").asText("stop");

            JsonNode toolCalls = msg.path("tool_calls");
            if (toolCalls.isArray() && !toolCalls.isEmpty()) {
                conversation.add(assistantMessage(msg));
                for (JsonNode call : toolCalls) {
                    String id = call.path("id").asText();
                    String name = call.path("function").path("name").asText();
                    AssistantTools.Result result = runTool(name, call.path("function").path("arguments").asText(""), runner);
                    Map<String, Object> toolMsg = message("tool", result.content());
                    toolMsg.put("tool_call_id", id);
                    toolMsg.put("name", name);
                    conversation.add(toolMsg);
                }
                continue;
            }

            String text = msg.path("content").asText("").trim();
            conversation.add(message("assistant", text));
            if ("content_filter".equals(finish)) {
                return "Não posso ajudar com esse pedido. Tente perguntar de outro jeito sobre vendas, estoque ou clientes.";
            }
            if ("length".equals(finish)) text += "\n\n(Resposta cortada por tamanho. Peça um resumo menor.)";
            return text.isBlank() ? "Não encontrei uma resposta. Pode reformular a pergunta?" : text;
        }
        return "A pergunta exigiu consultas demais. Tente algo mais específico (um período, um produto, um cliente).";
    }

    private JsonNode call(Map<String, Object> body) {
        try {
            String raw = http.post().uri("/chat/completions").body(body).retrieve().body(String.class);
            JsonNode root = json.readTree(raw == null ? "{}" : raw);
            if (root.path("choices").isEmpty()) {
                log.error("Resposta sem choices do provedor {}: {}", props.provider(), abbreviate(raw));
                throw new BusinessRuleException("assistant_error", "O assistente não conseguiu responder agora. Tente de novo.");
            }
            return root;
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            log.error("Erro do provedor {} ({}): status {} {}", props.provider(), props.model(), status,
                    abbreviate(e.getResponseBodyAsString()));
            throw switch (status) {
                case 401, 403 -> new BusinessRuleException("assistant_auth", "A chave de API do assistente é inválida.");
                case 402 -> new BusinessRuleException("assistant_credits",
                        "A conta da chave do assistente está sem créditos. Adicione créditos no provedor ou use um modelo mais barato.");
                case 429 -> new BusinessRuleException("assistant_busy",
                        "O limite gratuito do provedor foi atingido por enquanto. Tente de novo em um minuto.");
                case 404 -> new BusinessRuleException("assistant_model",
                        "O modelo '" + props.model() + "' não existe neste provedor. Confira ASSISTANT_MODEL.");
                default -> new BusinessRuleException("assistant_error", "O assistente não conseguiu responder agora. Tente de novo.");
            };
        } catch (ResourceAccessException e) {
            log.error("Sem conexão com o provedor {}", props.provider(), e);
            throw new BusinessRuleException("assistant_offline", "Sem conexão com o serviço de IA. Verifique a internet do servidor.");
        } catch (java.io.IOException e) {
            throw new BusinessRuleException("assistant_error", "O assistente devolveu uma resposta que não consegui ler.");
        }
    }

    private AssistantTools.Result runTool(String name, String arguments, ToolRunner runner) {
        Map<String, Object> args;
        try {
            args = arguments.isBlank() ? Map.of() : json.readValue(arguments, new TypeReference<Map<String, Object>>() {});
        } catch (java.io.IOException e) {
            return new AssistantTools.Result("Argumentos inválidos (JSON malformado).", true);
        }
        return runner.run(name, args);
    }

    // Guarda só o que a API aceita de volta: role, content e tool_calls (campos extras como "reasoning" ficam de fora).
    private Map<String, Object> assistantMessage(JsonNode msg) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("role", "assistant");
        String content = msg.path("content").isTextual() ? msg.path("content").asText() : null;
        if (content != null && !content.isBlank()) m.put("content", content);
        List<Map<String, Object>> calls = new ArrayList<>();
        for (JsonNode call : msg.path("tool_calls")) {
            Map<String, Object> fn = new LinkedHashMap<>();
            fn.put("name", call.path("function").path("name").asText());
            fn.put("arguments", call.path("function").path("arguments").asText("{}"));
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("id", call.path("id").asText());
            c.put("type", "function");
            c.put("function", fn);
            calls.add(c);
        }
        m.put("tool_calls", calls);
        return m;
    }

    private static Map<String, Object> message(String role, String content) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("role", role);
        m.put("content", content);
        return m;
    }

    private static Map<String, Object> toTool(AssistantTools.ToolDef d) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        params.put("properties", d.properties());
        params.put("required", d.required());
        Map<String, Object> fn = new LinkedHashMap<>();
        fn.put("name", d.name());
        fn.put("description", d.description());
        fn.put("parameters", params);
        return Map.of("type", "function", "function", fn);
    }

    private static String abbreviate(String s) {
        if (s == null) return "";
        return s.length() > 500 ? s.substring(0, 500) + "…" : s;
    }
}
