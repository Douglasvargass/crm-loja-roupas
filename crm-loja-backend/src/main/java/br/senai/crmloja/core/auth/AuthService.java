package br.senai.crmloja.core.auth;

import br.senai.crmloja.core.config.AppProperties;
import br.senai.crmloja.core.http.InvalidInputException;
import br.senai.crmloja.core.http.UnauthorizedException;
import br.senai.crmloja.core.identity.AppUser;
import br.senai.crmloja.core.identity.AppUserRepository;
import br.senai.crmloja.core.identity.RefreshToken;
import br.senai.crmloja.core.identity.RefreshTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    public record Tokens(String access, String refresh, AppUser user) {}

    private static final String BAD_LOGIN = "Login ou senha incorretos";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AppUserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final AppProperties props;
    // Hash fixo para comparar quando o login não existe: a resposta demora o mesmo nos dois casos.
    private final String dummyHash;

    public AuthService(AppUserRepository users, RefreshTokenRepository refreshTokens, PasswordEncoder encoder,
                       JwtService jwt, AppProperties props) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.encoder = encoder;
        this.jwt = jwt;
        this.props = props;
        this.dummyHash = encoder.encode("dummy-password-for-timing");
    }

    @Transactional
    public Tokens login(String login, String password, String userAgent) {
        var found = users.findByLoginIgnoreCase(login == null ? "" : login.trim());
        boolean ok = encoder.matches(password == null ? "" : password,
                found.map(AppUser::getPasswordHash).orElse(dummyHash));
        // Mesma frase para senha errada, login inexistente, conta inativa ou bloqueada.
        if (found.isEmpty() || !ok || !found.get().isActive() || found.get().isBlocked()) {
            throw new UnauthorizedException("invalid_credentials", BAD_LOGIN);
        }
        AppUser user = found.get();
        user.setLastLoginAt(OffsetDateTime.now());
        return issuePair(user, OffsetDateTime.now(), userAgent);
    }

    // Rotação: cada refresh vale uma vez. Reapresentar um já trocado indica roubo, então a
    // cadeia inteira daquela sessão é revogada. noRollbackFor mantém a revogação gravada.
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public Tokens refresh(String rawRefresh, String userAgent) {
        if (rawRefresh == null) throw new UnauthorizedException("no_session", "Sessão expirada. Entre novamente.");
        OffsetDateTime now = OffsetDateTime.now();
        RefreshToken current = refreshTokens.findByTokenHash(sha256(rawRefresh))
                .orElseThrow(() -> new UnauthorizedException("no_session", "Sessão expirada. Entre novamente."));

        if (current.isRevoked()) {
            refreshTokens.revokeSession(current.getUserId(), current.getSessionStartedAt(), now);
            throw new UnauthorizedException("session_revoked", "Sessão encerrada por segurança. Entre novamente.");
        }
        if (current.getExpiresAt().isBefore(now)) {
            throw new UnauthorizedException("no_session", "Sessão expirada. Entre novamente.");
        }
        AppUser user = users.findById(current.getUserId())
                .filter(u -> u.isActive() && !u.isBlocked())
                .orElseThrow(() -> new UnauthorizedException("no_session", "Sessão expirada. Entre novamente."));

        Tokens next = issuePair(user, current.getSessionStartedAt(), userAgent);
        RefreshToken nextRow = refreshTokens.findByTokenHash(sha256(next.refresh())).orElseThrow();
        current.revoke(now, nextRow.getId());
        return next;
    }

    @Transactional
    public void logout(String rawRefresh) {
        if (rawRefresh == null) return;
        refreshTokens.findByTokenHash(sha256(rawRefresh))
                .filter(t -> !t.isRevoked())
                .ifPresent(t -> t.revoke(OffsetDateTime.now(), null));
    }

    // Troca a senha, derruba todas as sessões e devolve um par novo só para este navegador.
    @Transactional
    public Tokens changePassword(Long userId, String currentPassword, String newPassword, String userAgent) {
        AppUser user = users.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("no_session", "Sessão expirada. Entre novamente."));
        if (!encoder.matches(currentPassword == null ? "" : currentPassword, user.getPasswordHash())) {
            throw new InvalidInputException("wrong_password", "A senha atual não confere.");
        }
        validateNewPassword(newPassword);
        if (encoder.matches(newPassword, user.getPasswordHash())) {
            throw new InvalidInputException("same_password", "A nova senha precisa ser diferente da atual.");
        }
        user.setPasswordHash(encoder.encode(newPassword));
        user.setMustChangePassword(false);
        refreshTokens.revokeAllOfUser(userId, OffsetDateTime.now());
        return issuePair(user, OffsetDateTime.now(), userAgent);
    }

    public static void validateNewPassword(String password) {
        if (password == null || password.length() < 8) {
            throw new InvalidInputException("weak_password", "A senha precisa ter pelo menos 8 caracteres.");
        }
    }

    // Chamado só de dentro dos métodos @Transactional acima.
    private Tokens issuePair(AppUser user, OffsetDateTime sessionStartedAt, String userAgent) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        OffsetDateTime expires = OffsetDateTime.now().plusHours(props.refreshTokenTtlHours());
        String ua = userAgent == null ? null : userAgent.substring(0, Math.min(300, userAgent.length()));
        refreshTokens.saveAndFlush(new RefreshToken(user.getId(), sha256(raw), sessionStartedAt, expires, ua));
        return new Tokens(jwt.issue(user.getId()), raw, user);
    }

    static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
