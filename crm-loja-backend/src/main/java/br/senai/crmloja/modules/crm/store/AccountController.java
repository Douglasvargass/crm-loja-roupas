package br.senai.crmloja.modules.crm.store;

import br.senai.crmloja.modules.crm.store.AccountService.Signup;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// Sem login: criar conta de cliente, pedir o link de recuperação e trocar a senha pelo link.
@RestController
public class AccountController {

    public record EmailBody(String email) {}

    public record ResetBody(String token, String password) {}

    private final AccountService service;
    private final String publicUrl;

    public AccountController(AccountService service, @Value("${app.public-url:}") String publicUrl) {
        this.service = service;
        this.publicUrl = publicUrl;
    }

    @PostMapping("/loja/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> signup(@RequestBody Signup body) {
        return Map.of("login", service.signup(body));
    }

    @PostMapping("/auth/forgot-password")
    public Map<String, String> forgot(@RequestBody EmailBody body, HttpServletRequest req) {
        service.forgotPassword(body == null ? null : body.email(), baseUrl(req));
        return Map.of("message", "Se este e-mail tiver conta, enviamos um link para criar uma senha nova. Confira também a caixa de spam.");
    }

    @PostMapping("/auth/reset-password")
    public Map<String, String> reset(@RequestBody ResetBody body) {
        service.resetPassword(body == null ? null : body.token(), body == null ? null : body.password());
        return Map.of("message", "Senha alterada. Entre com a senha nova.");
    }

    // Endereço das telas para o link do e-mail: PUBLIC_URL; senão a página de onde veio o pedido; senão este servidor.
    private String baseUrl(HttpServletRequest req) {
        if (publicUrl != null && !publicUrl.isBlank()) return publicUrl.replaceAll("/+$", "");
        String referer = req.getHeader("Referer");
        if (referer != null) {
            try {
                URI u = URI.create(referer);
                if (u.getScheme() != null && u.getHost() != null) {
                    return u.getScheme() + "://" + u.getHost() + (u.getPort() > 0 ? ":" + u.getPort() : "");
                }
            } catch (IllegalArgumentException ignored) {
                // Referer inválido: cai para o servidor
            }
        }
        return req.getScheme() + "://" + req.getServerName() + (req.getServerPort() == 80 || req.getServerPort() == 443 ? "" : ":" + req.getServerPort());
    }
}
