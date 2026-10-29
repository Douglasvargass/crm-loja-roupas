package br.senai.crmloja.modules.crm.assistant;

import br.senai.crmloja.core.auth.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Thalita (doc 10). Toda a equipe conversa; o que cada um consegue saber é decidido pelas ferramentas do papel
// (AssistantTools), não aqui. Cliente da loja não tem acesso.
@RestController
@RequestMapping("/crm/assistant")
@PreAuthorize("hasAnyRole('MANAGER','SELLER','STOCK_KEEPER')")
public class AssistantController {

    public record Status(boolean enabled, String provider, String model) {}

    public record AskRequest(String conversationId, @NotBlank(message = "Escreva sua pergunta.") String message) {}

    private final AssistantService service;

    public AssistantController(AssistantService service) { this.service = service; }

    // A tela pergunta antes de mostrar o chat: sem chave de IA configurada, a Thalita aparece desligada e o resto do sistema segue normal.
    @GetMapping("/status")
    public Status status() {
        boolean on = service.enabled();
        return new Status(on, on ? service.provider() : null, on ? service.model() : null);
    }

    // Quem pergunta vem da sessão (cookie), nunca do corpo da mensagem: ninguém pergunta "como se fosse" outra pessoa.
    @PostMapping("/chat")
    public AssistantService.Reply chat(@Valid @RequestBody AskRequest body, @AuthenticationPrincipal CurrentUser user) {
        return service.ask(body.conversationId(), body.message(), user);
    }
}
