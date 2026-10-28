package br.senai.crmloja.core.auth;

import br.senai.crmloja.core.http.UnauthorizedException;
import br.senai.crmloja.core.identity.AppUser;
import br.senai.crmloja.core.rbac.RbacService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    public record LoginRequest(@NotBlank(message = "Informe o login.") String login,
                               @NotBlank(message = "Informe a senha.") String password) {}

    public record ChangePasswordRequest(@NotBlank(message = "Informe a senha atual.") String currentPassword,
                                        @NotBlank(message = "Informe a nova senha.") String newPassword) {}

    // Papel efetivo: super admin enxerga o sistema como gerente.
    public record MeResponse(Long id, String login, String fullName, boolean superAdmin,
                             boolean mustChangePassword, String role) {
        static MeResponse of(CurrentUser u) {
            return new MeResponse(u.id(), u.login(), u.fullName(), u.superAdmin(), u.mustChangePassword(),
                    u.superAdmin() && u.role() == null ? "MANAGER" : u.role());
        }
    }

    private final AuthService auth;
    private final AuthCookies cookies;
    private final RbacService rbac;

    public AuthController(AuthService auth, AuthCookies cookies, RbacService rbac) {
        this.auth = auth;
        this.cookies = cookies;
        this.rbac = rbac;
    }

    @PostMapping("/login")
    public MeResponse login(@Valid @RequestBody LoginRequest body, HttpServletRequest req, HttpServletResponse res) {
        var tokens = auth.login(body.login(), body.password(), req.getHeader(HttpHeaders.USER_AGENT));
        cookies.write(res, tokens.access(), tokens.refresh());
        return me(tokens.user());
    }

    @PostMapping("/refresh")
    public MeResponse refresh(HttpServletRequest req, HttpServletResponse res) {
        try {
            var tokens = auth.refresh(AuthCookies.read(req, AuthCookies.REFRESH).orElse(null),
                    req.getHeader(HttpHeaders.USER_AGENT));
            cookies.write(res, tokens.access(), tokens.refresh());
            return me(tokens.user());
        } catch (UnauthorizedException e) {
            cookies.clear(res);
            throw e;
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest req, HttpServletResponse res) {
        auth.logout(AuthCookies.read(req, AuthCookies.REFRESH).orElse(null));
        cookies.clear(res);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/change-password")
    public MeResponse changePassword(@AuthenticationPrincipal CurrentUser user,
                                     @Valid @RequestBody ChangePasswordRequest body,
                                     HttpServletRequest req, HttpServletResponse res) {
        var tokens = auth.changePassword(user.id(), body.currentPassword(), body.newPassword(),
                req.getHeader(HttpHeaders.USER_AGENT));
        cookies.write(res, tokens.access(), tokens.refresh());
        return me(tokens.user());
    }

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal CurrentUser user) {
        return MeResponse.of(user);
    }

    private MeResponse me(AppUser u) {
        String role = rbac.roleCodeOf(u.getId(), "crm").orElse(null);
        return MeResponse.of(new CurrentUser(u.getId(), u.getLogin(), u.getFullName(), u.isSuperAdmin(),
                u.isMustChangePassword(), role));
    }
}
