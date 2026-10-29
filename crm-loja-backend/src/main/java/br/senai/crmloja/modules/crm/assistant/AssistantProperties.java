package br.senai.crmloja.modules.crm.assistant;

import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;

// Configuração do assistente. Sem chave ele fica desligado e o resto do sistema funciona normalmente.
//
// provider escolhe como falar com a IA:
//   anthropic  → API da Anthropic (padrão). baseUrl opcional para um provedor compatível (ex.: OpenRouter /api).
//   groq | gemini | openrouter | ollama → presets no formato "OpenAI-compatível" (chat/completions), que é o
//                 que os provedores com plano grátis usam; baseUrl e model têm padrão por preset.
//   openai     → qualquer outro provedor OpenAI-compatível: baseUrl e model obrigatórios.
@ConfigurationProperties("app.assistant")
public record AssistantProperties(String provider, String apiKey, String baseUrl, String model, String effort,
                                  int maxToolRounds, long maxTokens) {

    public AssistantProperties {
        provider = provider == null || provider.isBlank() ? "anthropic" : provider.trim().toLowerCase(Locale.ROOT);
        if (apiKey != null && apiKey.isBlank()) apiKey = null;
        if (baseUrl != null && baseUrl.isBlank()) baseUrl = null;
        if (baseUrl != null) baseUrl = baseUrl.replaceAll("/+$", "");
        if (model != null && model.isBlank()) model = null;
        if (effort == null || effort.isBlank()) effort = "medium";
        if (maxToolRounds <= 0) maxToolRounds = 8;
        if (maxTokens <= 0) maxTokens = 16000;

        switch (provider) {
            case "anthropic" -> { if (model == null) model = "claude-opus-5-5"; }
            case "groq" -> {
                if (baseUrl == null) baseUrl = "https://api.groq.com/openai/v1";
                if (model == null) model = "openai/gpt-oss-120b";
            }
            case "gemini" -> {
                if (baseUrl == null) baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai";
                if (model == null) model = "gemini-2.5-flash";
            }
            case "openrouter" -> {
                if (baseUrl == null) baseUrl = "https://openrouter.ai/api/v1";
                if (model == null) model = "meta-llama/llama-3.3-70b-instruct:free";
            }
            case "ollama" -> {
                if (baseUrl == null) baseUrl = "http://localhost:11434/v1";
                if (model == null) model = "llama3.1";
            }
            case "openai" -> { }
            default -> throw new IllegalArgumentException("ASSISTANT_PROVIDER inválido: " + provider
                    + " (use anthropic, groq, gemini, openrouter, ollama ou openai)");
        }
    }

    // Ollama roda na própria máquina e não pede chave; os outros só ligam com chave.
    public boolean enabled() {
        if ("ollama".equals(provider)) return true;
        if ("openai".equals(provider)) return apiKey != null && baseUrl != null && model != null;
        return apiKey != null;
    }

    public boolean openAiCompatible() { return !"anthropic".equals(provider); }
}
