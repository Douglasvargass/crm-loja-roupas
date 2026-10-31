package br.senai.crmloja.modules.crm.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AssistantPropertiesTest {

    @Test
    void variaveisEmBrancoUsamOsPadroesDaAnthropic() {
        // No Render, variável pedida e deixada em branco chega como "", não como ausente.
        var props = new AssistantProperties("", "sk-ant-x", "", "", "", 0, 0);
        assertThat(props.provider()).isEqualTo("anthropic");
        assertThat(props.enabled()).isTrue();
        assertThat(props.openAiCompatible()).isFalse();
        assertThat(props.baseUrl()).isNull();
        assertThat(props.model()).isEqualTo("claude-opus-5");
        assertThat(props.effort()).isEqualTo("medium");
        assertThat(props.maxToolRounds()).isEqualTo(8);
        assertThat(props.maxTokens()).isEqualTo(16000);
    }

    @Test
    void semChaveFicaDesligado() {
        assertThat(new AssistantProperties("", "", "", "", "", 0, 0).enabled()).isFalse();
        assertThat(new AssistantProperties("groq", "", "", "", "", 0, 0).enabled()).isFalse();
    }

    @Test
    void provedorCompativelComAnthropicETetoDeTokensSaoRespeitados() {
        var props = new AssistantProperties("anthropic", "sk-or-x", "https://openrouter.ai/api/",
                "anthropic/claude-haiku-5.5", "low", 3, 4000);
        assertThat(props.enabled()).isTrue();
        assertThat(props.baseUrl()).isEqualTo("https://openrouter.ai/api");   // barra final removida
        assertThat(props.model()).isEqualTo("anthropic/claude-haiku-5.5");
        assertThat(props.maxTokens()).isEqualTo(4000);
    }

    @Test
    void presetsGratisTemEnderecoEModeloPadrao() {
        var groq = new AssistantProperties("GROQ", "gsk_x", "", "", "", 0, 0);
        assertThat(groq.openAiCompatible()).isTrue();
        assertThat(groq.baseUrl()).isEqualTo("https://api.groq.com/openai/v1");
        assertThat(groq.model()).isEqualTo("openai/gpt-oss-120b");

        var gemini = new AssistantProperties("gemini", "AIza-x", "", "", "", 0, 0);
        assertThat(gemini.baseUrl()).isEqualTo("https://generativelanguage.googleapis.com/v1beta/openai");
        assertThat(gemini.model()).isEqualTo("gemini-2.5-flash");

        var openrouter = new AssistantProperties("openrouter", "sk-or-x", "", "qwen/qwen3-32b:free", "", 0, 0);
        assertThat(openrouter.baseUrl()).isEqualTo("https://openrouter.ai/api/v1");
        assertThat(openrouter.model()).isEqualTo("qwen/qwen3-32b:free");   // modelo informado vence o padrão
    }

    @Test
    void ollamaNaoPrecisaDeChaveEOpenaiGenericoPrecisaDeTudo() {
        assertThat(new AssistantProperties("ollama", "", "", "", "", 0, 0).enabled()).isTrue();
        assertThat(new AssistantProperties("openai", "k", "", "", "", 0, 0).enabled()).isFalse();
        assertThat(new AssistantProperties("openai", "k", "https://x/v1", "m", "", 0, 0).enabled()).isTrue();
    }

    @Test
    void provedorDesconhecidoDerrubaOBoot() {
        assertThatThrownBy(() -> new AssistantProperties("azure", "k", "", "", "", 0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ASSISTANT_PROVIDER");
    }
}
