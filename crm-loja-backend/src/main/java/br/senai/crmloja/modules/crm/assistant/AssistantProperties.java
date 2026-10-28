package br.senai.crmloja.modules.crm.assistant;

import org.springframework.boot.context.properties.ConfigurationProperties;

// Sem ANTHROPIC_API_KEY o assistente fica desligado e o resto do sistema funciona normalmente.
@ConfigurationProperties("app.assistant")
// baseUrl preenchido = provedor compatível com a API da Anthropic (ex.: OpenRouter).
public record AssistantProperties(String apiKey, String baseUrl, String model, String effort, int maxToolRounds,
                                  long maxTokens) {

    public AssistantProperties {
        if (baseUrl != null && baseUrl.isBlank()) baseUrl = null;
        if (model == null || model.isBlank()) model = "claude-opus-5-5";
        if (effort == null || effort.isBlank()) effort = "medium";
        if (maxToolRounds <= 0) maxToolRounds = 8;
        if (maxTokens <= 0) maxTokens = 16000;
    }

    public boolean enabled() { return apiKey != null && !apiKey.isBlank(); }
}
