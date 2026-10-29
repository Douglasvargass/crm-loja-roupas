package br.senai.crmloja.modules.crm.store;

import br.senai.crmloja.core.auth.CurrentUser;
import br.senai.crmloja.modules.crm.assistant.AssistantService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Talita do cliente (pedido do Douglas, 29/10/2026): a mesma assistente, com outra rota e outras ferramentas. O papel
// CUSTOMER vem da sessão; o AssistantService escolhe o texto de instruções do cliente e o AssistantTools só entrega as
// ferramentas do cliente (vitrine, meus pedidos, minhas solicitações, pedir peça). Nada da equipe aparece aqui.
@RestController
@RequestMapping("/loja/assistant")
@PreAuthorize("hasRole('CUSTOMER')")
public class StoreAssistantController {

    public record Status(boolean enabled) {}

    public record AskRequest(String conversationId, @NotBlank(message = "Escreva sua pergunta.") String message) {}

    private final AssistantService service;

    public StoreAssistantController(AssistantService service) { this.service = service; }

    // Sem provedor e modelo: o cliente não precisa saber qual IA a loja usa.
    @GetMapping("/status")
    public Status status() {
        return new Status(service.enabled());
    }

    @PostMapping("/chat")
    public AssistantService.Reply chat(@Valid @RequestBody AskRequest body, @AuthenticationPrincipal CurrentUser user) {
        return service.ask(body.conversationId(), body.message(), user);
    }
}
