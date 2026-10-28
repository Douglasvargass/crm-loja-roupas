package br.senai.crmloja.core.auth;

import br.senai.crmloja.core.config.AppProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

// Tokens só em cookie httpOnly: o JavaScript da página nunca lê o token (protege contra XSS).
// O refresh vai só para /auth, então não trafega em toda chamada da API.
@Component
public class AuthCookies {

    public static final String ACCESS = "access_token";
    public static final String REFRESH = "refresh_token";

    private final AppProperties props;

    public AuthCookies(AppProperties props) { this.props = props; }

    public void write(HttpServletResponse res, String access, String refresh) {
        res.addHeader(HttpHeaders.SET_COOKIE, cookie(ACCESS, access, "/",
                Duration.ofSeconds(props.accessTokenTtlSeconds())).toString());
        res.addHeader(HttpHeaders.SET_COOKIE, cookie(REFRESH, refresh, "/auth",
                Duration.ofHours(props.refreshTokenTtlHours())).toString());
    }

    public void clear(HttpServletResponse res) {
        res.addHeader(HttpHeaders.SET_COOKIE, cookie(ACCESS, "", "/", Duration.ZERO).toString());
        res.addHeader(HttpHeaders.SET_COOKIE, cookie(REFRESH, "", "/auth", Duration.ZERO).toString());
    }

    public static Optional<String> read(HttpServletRequest req, String name) {
        if (req.getCookies() == null) return Optional.empty();
        return Arrays.stream(req.getCookies())
                .filter(c -> name.equals(c.getName()))
                .map(Cookie::getValue)
                .filter(v -> !v.isBlank())
                .findFirst();
    }

    // SameSite=Lax + API sem CORS aberto: um site de terceiros não consegue disparar POST autenticado.
    private ResponseCookie cookie(String name, String value, String path, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(props.cookieSecure())
                .sameSite("Lax")
                .path(path)
                .maxAge(maxAge)
                .build();
    }
}
