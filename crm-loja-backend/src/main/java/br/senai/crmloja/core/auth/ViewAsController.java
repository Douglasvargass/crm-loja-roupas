package br.senai.crmloja.core.auth;

import br.senai.crmloja.core.audit.AuditService;
import br.senai.crmloja.core.http.BusinessRuleException;
import br.senai.crmloja.core.http.NotFoundException;
import br.senai.crmloja.core.users.UserAdminService;
import br.senai.crmloja.core.users.UserAdminService.UserRow;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * "Ver como" (spec crm/10): o gerente vê o sistema exatamente como outra pessoa da equipe (menu, telas, números),
 * para conferir o que ela enxerga ou demonstrar sem saber a senha dela. É só leitura (o JwtCookieFilter barra
 * qualquer alteração), dura no máximo 30 minutos, e entrar e sair ficam na auditoria.
 */
@RestController
@RequestMapping("/auth/view-as")
public class ViewAsController {

    public record StartRequest(@NotNull(message = "Escolha a pessoa.") Long userId) {}

    private final UserAdminService users;
    private final JwtService jwt;
    private final AuthCookies cookies;
    private final AuditService audit;

    public ViewAsController(UserAdminService users, JwtService jwt, AuthCookies cookies, AuditService audit) {
        this.users = users;
        this.jwt = jwt;
        this.cookies = cookies;
        this.audit = audit;
    }

    // Quem pode ser vista: contas ativas com papel no crm, menos a própria.
    @GetMapping("/targets")
    @PreAuthorize("hasRole('MANAGER')")
    public List<UserRow> targets(@AuthenticationPrincipal CurrentUser me) {
        return users.list("crm").stream().filter(u -> u.active() && !u.id().equals(me.id())).toList();
    }

    // A auditoria (AuditService) só grava dentro de uma transação.
    @Transactional
    @PostMapping
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<Void> start(@Valid @RequestBody StartRequest body, @AuthenticationPrincipal CurrentUser me,
                                      HttpServletResponse res) {
        if (me.viewingAs()) {
            throw new BusinessRuleException("already_viewing_as", "Saia do \"Ver como\" atual antes de escolher outra pessoa.");
        }
        UserRow target = targets(me).stream().filter(u -> u.id().equals(body.userId())).findFirst()
                .orElseThrow(() -> new NotFoundException("Esta pessoa não pode ser vista agora. Escolha outra."));
        cookies.writeViewAs(res, jwt.issueViewAs(me.id(), target.id()), JwtService.VIEW_AS_TTL_SECONDS);
        audit.record(me.id(), "crm", "view_as_started", "app_user", target.id(), Map.of("role", String.valueOf(target.role())));
        return ResponseEntity.noContent().build();
    }

    // Sair: qualquer um logado (no "Ver como", quem chama tem o papel da pessoa vista). Repetir não faz mal.
    @Transactional
    @DeleteMapping
    public ResponseEntity<Void> stop(@AuthenticationPrincipal CurrentUser me, HttpServletResponse res) {
        if (me != null && me.viewingAs()) {
            audit.record(me.viewerId(), "crm", "view_as_ended", "app_user", me.id(), Map.of());
        }
        cookies.clearViewAs(res);
        return ResponseEntity.noContent().build();
    }
}
