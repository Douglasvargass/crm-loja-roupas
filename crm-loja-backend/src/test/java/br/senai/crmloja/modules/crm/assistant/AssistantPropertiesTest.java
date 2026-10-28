package br.senai.crmloja.modules.crm.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AssistantPropertiesTest {

    @Test
    void variaveisEmBrancoUsamOsPadroesDaAnthropic() {
        // No Render, variável pedida e deixada em branco chega como "", não como ausente.
        var props = new AssistantProperties("sk-ant-x", "", "", "", 0, 0);
        assertThat(props.baseUrl()).isNull();
        assertThat(props.model()).isEqualTo("claude-opus-5-5");
        assertThat(props.effort()).isEqualTo("medium");
        assertThat(props.maxToolRounds()).isEqualTo(8);
        assertThat(props.maxTokens()).isEqualTo(16000);
    }

    @Test
    void provedorCompativelETetoDeTokensSaoRespeitados() {
        var props = new AssistantProperties("sk-or-x", "https://openrouter.ai/api",
                "anthropic/claude-haiku-5.5", "low", 3, 4000);
        assertThat(props.enabled()).isTrue();
        assertThat(props.baseUrl()).isEqualTo("https://openrouter.ai/api");
        assertThat(props.model()).isEqualTo("anthropic/claude-haiku-5.5");
        assertThat(props.maxTokens()).isEqualTo(4000);
    }
}
