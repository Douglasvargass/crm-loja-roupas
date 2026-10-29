package br.senai.crmloja.modules.crm.assistant;

import br.senai.crmloja.core.http.BusinessRuleException;
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUseBlock;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Claude pela API da Anthropic (ou um provedor compatível com ela, como o OpenRouter em /api).
// Laço manual de tool use: pergunta → Claude pede ferramentas → executamos → devolvemos → até responder.
// A conversa cresce só por acréscimo: cada resposta volta inteira no próximo pedido, como a API exige
// para os blocos de raciocínio.
class AnthropicProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(AnthropicProvider.class);

    private final AssistantProperties props;
    private final AnthropicClient client;

    AnthropicProvider(AssistantProperties props) {
        this.props = props;
        // Provedores compatíveis autenticam com "Authorization: Bearer", não com x-api-key.
        this.client = props.baseUrl() == null
                ? AnthropicOkHttpClient.builder().apiKey(props.apiKey()).build()
                : AnthropicOkHttpClient.builder().baseUrl(props.baseUrl()).authToken(props.apiKey()).build();
    }

    @Override
    public String ask(List<Object> conversation, String systemPrompt, String question,
                      List<AssistantTools.ToolDef> tools, ToolRunner runner) {
        conversation.add(MessageParam.ofUser(question));
        List<Tool> available = tools.stream().map(AnthropicProvider::toTool).toList();
        try {
            for (int round = 0; round <= props.maxToolRounds(); round++) {
                var builder = MessageCreateParams.builder()
                        .model(props.model())
                        .maxTokens(props.maxTokens())
                        .system(systemPrompt)
                        .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.of(props.effort())).build())
                        .messages(conversation.stream().map(MessageParam.class::cast).toList());
                if (props.baseUrl() == null) {
                    // Se um classificador de segurança recusar por engano, a API tenta outro modelo sozinha.
                    // Recurso só da API da Anthropic: outros provedores não conhecem esse campo.
                    builder.putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
                            .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"));
                }
                available.forEach(builder::addTool);

                Message response = client.messages().create(builder.build());
                conversation.add(response.toParam());
                StopReason stop = response.stopReason().orElse(StopReason.END_TURN);

                if (StopReason.REFUSAL.equals(stop)) {
                    return "Não posso ajudar com esse pedido. Tente perguntar de outro jeito sobre vendas, estoque ou clientes.";
                }
                if (!StopReason.TOOL_USE.equals(stop)) {
                    String text = textOf(response);
                    if (StopReason.MAX_TOKENS.equals(stop)) text += "\n\n(Resposta cortada por tamanho. Peça um resumo menor.)";
                    return text.isBlank() ? "Não encontrei uma resposta. Pode reformular a pergunta?" : text;
                }

                // Todos os resultados voltam numa única mensagem do usuário, na mesma ordem dos pedidos.
                List<ContentBlockParam> results = new ArrayList<>();
                for (ContentBlock block : response.content()) {
                    block.toolUse().ifPresent(use -> results.add(runTool(use, runner)));
                }
                conversation.add(MessageParam.ofUserOfContentBlockParams(results));
            }
            return "A pergunta exigiu consultas demais. Tente algo mais específico (um período, um produto, um cliente).";
        } catch (RateLimitException e) {
            throw new BusinessRuleException("assistant_busy", "O assistente está muito requisitado. Tente de novo em instantes.");
        } catch (AnthropicServiceException e) {
            log.error("Erro da API do Claude: status {}", e.statusCode(), e);
            throw switch (e.statusCode()) {
                case 401, 403 -> new BusinessRuleException("assistant_auth", "A chave de API do assistente é inválida.");
                case 402 -> new BusinessRuleException("assistant_credits",
                        "A conta da chave do assistente está sem créditos. Adicione créditos no provedor ou use um modelo mais barato.");
                default -> new BusinessRuleException("assistant_error", "O assistente não conseguiu responder agora. Tente de novo.");
            };
        } catch (AnthropicIoException e) {
            log.error("Sem conexão com a API do Claude", e);
            throw new BusinessRuleException("assistant_offline", "Sem conexão com o serviço de IA. Verifique a internet do servidor.");
        }
    }

    private static ContentBlockParam runTool(ToolUseBlock use, ToolRunner runner) {
        Map<String, Object> args;
        try {
            args = use._input().convert(new TypeReference<Map<String, Object>>() {});
        } catch (RuntimeException e) {
            args = null;
        }
        var result = args == null ? new AssistantTools.Result("Argumentos inválidos.", true) : runner.run(use.name(), args);
        return ContentBlockParam.ofToolResult(ToolResultBlockParam.builder()
                .toolUseId(use.id())
                .content(result.content())
                .isError(result.error())
                .build());
    }

    private static Tool toTool(AssistantTools.ToolDef d) {
        var props = Tool.InputSchema.Properties.builder();
        d.properties().forEach((k, v) -> props.putAdditionalProperty(k, JsonValue.from(v)));
        return Tool.builder()
                .name(d.name())
                .description(d.description())
                .inputSchema(Tool.InputSchema.builder().properties(props.build()).required(d.required()).build())
                .build();
    }

    private static String textOf(Message m) {
        var sb = new StringBuilder();
        m.content().forEach(b -> b.text().ifPresent(t -> sb.append(t.text())));
        return sb.toString().trim();
    }
}
