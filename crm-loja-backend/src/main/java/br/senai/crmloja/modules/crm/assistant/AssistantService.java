package br.senai.crmloja.modules.crm.assistant;

import br.senai.crmloja.core.auth.CurrentUser;
import br.senai.crmloja.core.config.AppProperties;
import br.senai.crmloja.core.http.BusinessRuleException;
import br.senai.crmloja.core.http.InvalidInputException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.web.client.RestClient;

// Assistente da loja: a IA responde perguntas consultando os dados reais pelas AssistantTools.
// Quem fala com a API é um LlmProvider (Anthropic ou OpenAI-compatível); aqui ficam as regras que não
// dependem do provedor: papel de quem pergunta, conversas na memória, rollback quando a pergunta falha.
@Service
public class AssistantService {

    private static final Logger log = LoggerFactory.getLogger(AssistantService.class);
    private static final Duration IDLE_TTL = Duration.ofHours(2);
    private static final int MAX_QUESTION = 2000;

    public record Reply(String conversationId, String answer, List<String> toolsUsed) {}

    private record Conversation(Long userId, String role, List<Object> messages, Instant[] lastUse) {}

    private final AssistantProperties props;
    private final AssistantTools tools;
    private final AppProperties app;
    private final LlmProvider provider;
    private final Map<String, Conversation> conversations = new ConcurrentHashMap<>();

    public AssistantService(AssistantProperties props, AssistantTools tools, AppProperties app,
                            RestClient.Builder restClientBuilder, ObjectMapper json) {
        this.props = props;
        this.tools = tools;
        this.app = app;
        if (!props.enabled()) {
            this.provider = null;
        } else if (props.openAiCompatible()) {
            this.provider = new OpenAiCompatibleProvider(props, restClientBuilder, json);
        } else {
            this.provider = new AnthropicProvider(props);
        }
        if (provider != null) log.info("Assistente ligado: provedor {} · modelo {}", props.provider(), props.model());
    }

    public boolean enabled() { return provider != null; }

    public String provider() { return props.provider(); }

    public String model() { return props.model(); }

    public Reply ask(String conversationId, String question, CurrentUser user) {
        if (provider == null) {
            throw new BusinessRuleException("assistant_disabled",
                    "O assistente está desligado: configure ASSISTANT_PROVIDER e ASSISTANT_API_KEY no back-end.");
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
            List<String> used = new ArrayList<>();   // a tela mostra "consultei: …"
            String finalRole = role;
            LlmProvider.ToolRunner runner = (name, args) -> {
                if (!used.contains(name)) used.add(name);
                var result = tools.execute(name, args, finalRole);
                log.info("assistente: ferramenta {} {}", name, result.error() ? "com erro" : "ok");
                return result;
            };
            try {
                String answer = provider.ask(conv.messages(), systemPrompt(role, user), question.trim(),
                        tools.definitionsFor(role), runner);
                return new Reply(conversationId, answer, used);
            } catch (RuntimeException e) {
                // Pergunta que falhou sai do histórico, para a próxima não herdar uma conversa quebrada.
                conv.messages().subList(rollback, conv.messages().size()).clear();
                if (!(e instanceof BusinessRuleException) && !(e instanceof InvalidInputException)) {
                    log.error("Falha inesperada no assistente ({})", props.provider(), e);
                }
                throw e;
            }
        }
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
}
