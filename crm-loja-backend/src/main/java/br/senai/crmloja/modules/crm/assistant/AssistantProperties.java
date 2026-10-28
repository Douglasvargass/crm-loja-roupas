package br.senai.crmloja.modules.crm.assistant;

import org.springframework.boot.context.properties.ConfigurationProperties;

// Sem ANTHROPIC_API_KEY o assistente fica desligado e o resto do sistema funciona normalmente.
@ConfigurationProperties("app.assistant")
public record AssistantProperties(String apiKey, String model, String effort, int maxToolRounds) {

    public AssistantProperties {
        if (model == null || model.isBlank()) model = "claude-opus-5-5";
        if (effort == null || effort.isBlank()) effort = "medium";
        if (maxToolRounds <= 0) maxToolRounds = 8;
    }

    public boolean enabled() { return apiKey != null && !apiKey.isBlank(); }
}
