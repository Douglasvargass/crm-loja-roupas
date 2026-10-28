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

@RestController
@RequestMapping("/crm/assistant")
@PreAuthorize("hasAnyRole('MANAGER','SELLER','STOCK_KEEPER')")
public class AssistantController {

    public record Status(boolean enabled, String model) {}

    public record AskRequest(String conversationId, @NotBlank(message = "Escreva sua pergunta.") String message) {}

    private final AssistantService service;

    public AssistantController(AssistantService service) { this.service = service; }

    @GetMapping("/status")
    public Status status() {
        return new Status(service.enabled(), service.enabled() ? service.model() : null);
    }

    @PostMapping("/chat")
    public AssistantService.Reply chat(@Valid @RequestBody AskRequest body, @AuthenticationPrincipal CurrentUser user) {
        return service.ask(body.conversationId(), body.message(), user);
    }
}
