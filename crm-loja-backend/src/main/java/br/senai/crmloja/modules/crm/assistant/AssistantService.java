package br.senai.crmloja.modules.crm.assistant;

import br.senai.crmloja.core.auth.CurrentUser;
import br.senai.crmloja.core.config.AppProperties;
import br.senai.crmloja.core.http.BusinessRuleException;
import br.senai.crmloja.core.http.InvalidInputException;
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.AnthropicIoException;
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
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

// Assistente da loja: o Claude responde perguntas consultando os dados reais pelas AssistantTools.
// Laço manual de tool use: pergunta → Claude pede ferramentas → executamos → devolvemos → até responder.
// A conversa fica na memória do servidor (some ao reiniciar) e cresce só por acréscimo: cada resposta
// do Claude volta inteira no próximo pedido, como a API exige para os blocos de raciocínio.
@Service
public class AssistantService {

    private static final Logger log = LoggerFactory.getLogger(AssistantService.class);
    private static final Duration IDLE_TTL = Duration.ofHours(2);
    private static final int MAX_QUESTION = 2000;

    public record Reply(String conversationId, String answer, List<String> toolsUsed) {}

    private record Conversation(Long userId, String role, List<MessageParam> messages, Instant[] lastUse) {}

    private final AssistantProperties props;
    private final AssistantTools tools;
    private final AppProperties app;
    private final AnthropicClient client;
    private final Map<String, Conversation> conversations = new ConcurrentHashMap<>();

    public AssistantService(AssistantProperties props, AssistantTools tools, AppProperties app) {
        this.props = props;
        this.tools = tools;
        this.app = app;
        this.client = props.enabled() ? buildClient(props) : null;
    }

    // Provedores compatíveis (ex.: OpenRouter) autenticam com "Authorization: Bearer", não com x-api-key.
    private static AnthropicClient buildClient(AssistantProperties props) {
        if (props.baseUrl() == null) return AnthropicOkHttpClient.builder().apiKey(props.apiKey()).build();
        return AnthropicOkHttpClient.builder().baseUrl(props.baseUrl()).authToken(props.apiKey()).build();
    }

    public boolean enabled() { return client != null; }

    public String model() { return props.model(); }

    public Reply ask(String conversationId, String question, CurrentUser user) {
        if (client == null) {
            throw new BusinessRuleException("assistant_disabled",
                    "O assistente está desligado: configure a variável ANTHROPIC_API_KEY no back-end.");
        }
        if (question == null || question.isBlank()) throw new InvalidInputException("empty_question", "Escreva sua pergunta.");
        if (question.length() > MAX_QUESTION) {
            throw new InvalidInputException("question_too_long", "Pergunta muito longa (máximo " + MAX_QUESTION + " caracteres).");
        }
        String role = user.superAdmin() && user.role() == null ? "MANAGER" : user.role();
        if (role == null) throw new BusinessRuleException("no_role", "Sua conta não tem papel no CRM.");

        evictIdle();
        Conversation conv = conversationId == null ? null : conversations.get(conversationId);
        // Conversa de outra pessoa ou com papel diferente: começa outra, nunca reaproveita.
        if (conv == null || !conv.userId().equals(user.id()) || !conv.role().equals(role)) {
            conversationId = UUID.randomUUID().toString();
            conv = new Conversation(user.id(), role, new ArrayList<>(), new Instant[]{Instant.now()});
            conversations.put(conversationId, conv);
        }

        // Uma pergunta por vez na mesma conversa: a lista de mensagens não aceita escrita concorrente.
        synchronized (conv) {
            conv.lastUse()[0] = Instant.now();
            int rollback = conv.messages().size();
            conv.messages().add(MessageParam.ofUser(question.trim()));
            try {
                List<String> used = new ArrayList<>();
                return new Reply(conversationId, runLoop(conv, user, used), used);
            } catch (RuntimeException e) {
                // Pergunta que falhou sai do histórico, para a próxima não herdar uma conversa quebrada.
                conv.messages().subList(rollback, conv.messages().size()).clear();
                throw translate(e);
            }
        }
    }

    // used: nomes das ferramentas consultadas nesta pergunta (a tela mostra "consultei: …").
    private String runLoop(Conversation conv, CurrentUser user, List<String> used) {
        List<Tool> available = tools.toolsFor(conv.role());
        for (int round = 0; round <= props.maxToolRounds(); round++) {
            var builder = MessageCreateParams.builder()
                    .model(props.model())
                    .maxTokens(props.maxTokens())
                    .system(systemPrompt(conv.role(), user))
                    .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.of(props.effort())).build())
                    .messages(conv.messages());
            if (props.baseUrl() == null) {
                // Se um classificador de segurança recusar por engano, a API tenta outro modelo sozinha.
                // Recurso só da API da Anthropic: outros provedores não conhecem esse campo.
                builder.putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
                        .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"));
            }
            available.forEach(builder::addTool);

            Message response = client.messages().create(builder.build());
            conv.messages().add(response.toParam());
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
                block.toolUse().ifPresent(use -> {
                    if (!used.contains(use.name())) used.add(use.name());
                    results.add(runTool(use, conv.role()));
                });
            }
            conv.messages().add(MessageParam.ofUserOfContentBlockParams(results));
        }
        return "A pergunta exigiu consultas demais. Tente algo mais específico (um período, um produto, um cliente).";
    }

    private ContentBlockParam runTool(ToolUseBlock use, String role) {
        Map<String, Object> args;
        try {
            args = use._input().convert(new TypeReference<Map<String, Object>>() {});
        } catch (RuntimeException e) {
            args = null;
        }
        var result = args == null
                ? new AssistantTools.Result("Argumentos inválidos.", true)
                : tools.execute(use.name(), args, role);
        log.info("assistente: ferramenta {} {}", use.name(), result.error() ? "com erro" : "ok");
        return ContentBlockParam.ofToolResult(ToolResultBlockParam.builder()
                .toolUseId(use.id())
                .content(result.content())
                .isError(result.error())
                .build());
    }

    private static String textOf(Message m) {
        var sb = new StringBuilder();
        m.content().forEach(b -> b.text().ifPresent(t -> sb.append(t.text())));
        return sb.toString().trim();
    }

    private String systemPrompt(String role, CurrentUser user) {
        return AssistantPrompt.SYSTEM
                + "\n\n<contexto>\nHoje: " + LocalDate.now(app.zone()) + " (fuso " + app.timeZone() + ").\n"
                + "Quem pergunta: " + user.fullName() + ", papel " + role + ".\n</contexto>";
    }

    private void evictIdle() {
        Instant limit = Instant.now().minus(IDLE_TTL);
        conversations.values().removeIf(c -> c.lastUse()[0].isBefore(limit));
    }

    private RuntimeException translate(RuntimeException e) {
        if (e instanceof BusinessRuleException || e instanceof InvalidInputException) return e;
        if (e instanceof RateLimitException) {
            return new BusinessRuleException("assistant_busy", "O assistente está muito requisitado. Tente de novo em instantes.");
        }
        if (e instanceof AnthropicServiceException se) {
            log.error("Erro da API do Claude: status {}", se.statusCode(), e);
            if (se.statusCode() == 401) {
                return new BusinessRuleException("assistant_auth", "A chave ANTHROPIC_API_KEY do servidor é inválida.");
            }
            if (se.statusCode() == 402) {
                return new BusinessRuleException("assistant_credits",
                        "A conta da chave do assistente está sem créditos. Adicione créditos no provedor ou use um modelo mais barato.");
            }
            return new BusinessRuleException("assistant_error", "O assistente não conseguiu responder agora. Tente de novo.");
        }
        if (e instanceof AnthropicIoException) {
            log.error("Sem conexão com a API do Claude", e);
            return new BusinessRuleException("assistant_offline", "Sem conexão com o serviço de IA. Verifique a internet do servidor.");
        }
        return e;
    }
}
