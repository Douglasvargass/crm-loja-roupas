package br.senai.crmloja.core.users;

import br.senai.crmloja.core.auth.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/users")
@PreAuthorize("hasRole('MANAGER')")
public class UserAdminController {

    public record CreateUserRequest(
            // Login curto no padrão nome.sobrenome (e-mail completo também vale, para contas antigas).
            @NotBlank(message = "Informe o login.")
            @Pattern(regexp = "^[a-z0-9]+([._-][a-z0-9]+)*(@[a-z0-9.-]+\\.[a-z]{2,})?$",
                     message = "Use o padrão nome.sobrenome, só letras minúsculas, números e ponto.")
            String login,
            @NotBlank(message = "Informe o nome.") @Size(min = 3, max = 120, message = "Nome entre 3 e 120 letras.") String fullName,
            @NotBlank(message = "Escolha o papel.") String role) {}

    public record UpdateUserRequest(String role, Boolean active) {}

    private final UserAdminService service;

    public UserAdminController(UserAdminService service) { this.service = service; }

    @GetMapping
    public List<UserAdminService.UserRow> list(@RequestParam(defaultValue = "crm") String module) {
        return service.list(module);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserAdminService.CreatedUser create(@RequestParam(defaultValue = "crm") String module,
                                               @Valid @RequestBody CreateUserRequest body,
                                               @AuthenticationPrincipal CurrentUser actor) {
        return service.create(module, body.login(), body.fullName(), body.role(), actor.id());
    }

    @PostMapping("/{id}/reset-password")
    public UserAdminService.CreatedUser resetPassword(@RequestParam(defaultValue = "crm") String module, @PathVariable Long id,
                                                      @AuthenticationPrincipal CurrentUser actor) {
        return service.resetPassword(module, id, actor.id());
    }

    @PatchMapping("/{id}")
    public UserAdminService.UserRow update(@RequestParam(defaultValue = "crm") String module, @PathVariable Long id,
                                           @RequestBody UpdateUserRequest body,
                                           @AuthenticationPrincipal CurrentUser actor) {
        return service.update(module, id, body.role(), body.active(), actor.id());
    }
}
