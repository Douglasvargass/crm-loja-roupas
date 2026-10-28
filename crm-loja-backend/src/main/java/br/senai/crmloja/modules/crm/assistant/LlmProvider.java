package br.senai.crmloja.modules.crm.assistant;

import java.util.List;
import java.util.Map;

// Fronteira entre o assistente e a API de IA. Cada provedor guarda a conversa no formato que a sua API
// exige (por isso List<Object>): o Service só acrescenta perguntas e, se algo falhar, corta a lista de
// volta ao tamanho anterior.
interface LlmProvider {

    @FunctionalInterface
    interface ToolRunner {
        AssistantTools.Result run(String name, Map<String, Object> args);
    }

    // Acrescenta a pergunta à conversa, roda as rodadas de ferramentas e devolve a resposta final em texto.
    // Erros de API devem virar BusinessRuleException com um code estável (assistant_auth, assistant_busy…).
    String ask(List<Object> conversation, String systemPrompt, String question,
               List<AssistantTools.ToolDef> tools, ToolRunner runner);
}
