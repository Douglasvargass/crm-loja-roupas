package br.senai.crmloja.modules.crm.store;

import br.senai.crmloja.core.config.AppProperties;
import br.senai.crmloja.modules.crm.store.AccountService.Signup;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
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
    private final List<String> origensConhecidas;

    public AccountController(AccountService service, @Value("${app.public-url:}") String publicUrl,
                             AppProperties props) {
        this.service = service;
        this.publicUrl = publicUrl;
        this.origensConhecidas = props.frontendOrigins() == null ? List.of() : props.frontendOrigins();
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

    // Endereço das telas para o link do e-mail: PUBLIC_URL; senão a página de onde veio o pedido, desde que
    // seja uma origem conhecida; senão este servidor.
    //
    // O Referer é escolhido por quem chama, e /auth/forgot-password é público. Aceitá-lo sem conferir
    // permitia pedir a recuperação da senha de alguém com um Referer forjado: a vítima receberia um e-mail
    // legítimo da loja cujo link levaria o token de verdade para o site de quem pediu. Por isso ele só vale
    // quando bate com uma das origens que a própria aplicação já reconhece (as mesmas do CORS).
    private String baseUrl(HttpServletRequest req) {
        if (publicUrl != null && !publicUrl.isBlank()) return publicUrl.replaceAll("/+$", "");
        String referer = req.getHeader("Referer");
        if (referer != null) {
            try {
                URI u = URI.create(referer);
                if (u.getScheme() != null && u.getHost() != null) {
                    String origem = u.getScheme() + "://" + u.getHost() + (u.getPort() > 0 ? ":" + u.getPort() : "");
                    boolean conhecida = origensConhecidas.stream()
                            .anyMatch(o -> o != null && o.replaceAll("/+$", "").equalsIgnoreCase(origem));
                    if (conhecida) return origem;
                }
            } catch (IllegalArgumentException ignored) {
                // Referer inválido: cai para o servidor
            }
        }
        return req.getScheme() + "://" + req.getServerName() + (req.getServerPort() == 80 || req.getServerPort() == 443 ? "" : ":" + req.getServerPort());
    }
}
