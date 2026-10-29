package br.senai.crmloja.core.auth;

import br.senai.crmloja.core.config.AppProperties;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

// Access token curto e sem estado: só o id do usuário. Papel e situação são lidos do banco a
// cada requisição, então inativar alguém ou mudar o papel vale na hora.
@Service
public class JwtService {

    private final SecretKey key;
    private final int ttlSeconds;

    public JwtService(AppProperties props) {
        this.key = Keys.hmacShaKeyFor(props.jwtSecret().getBytes(StandardCharsets.UTF_8));
        this.ttlSeconds = props.accessTokenTtlSeconds();
    }

    public String issue(Long userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(key)
                .compact();
    }

    public Optional<Long> parseUserId(String token) {
        try {
            var claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            // Token do "Ver como" é assinado com a mesma chave: nunca pode servir de login da pessoa vista.
            if (claims.get(TYP) != null) return Optional.empty();
            return Optional.of(Long.valueOf(claims.getSubject()));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    // ---------------------------------------------------------------- "Ver como" (spec crm/10)

    public record ViewAs(Long viewerId, Long targetId) {}

    private static final String TYP = "typ";
    private static final String VIEW_AS = "view_as";
    public static final int VIEW_AS_TTL_SECONDS = 30 * 60;

    public String issueViewAs(Long viewerId, Long targetId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(targetId.toString())
                .claim(TYP, VIEW_AS)
                .claim("imp", viewerId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(VIEW_AS_TTL_SECONDS)))
                .signWith(key)
                .compact();
    }

    public Optional<ViewAs> parseViewAs(String token) {
        try {
            var claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            if (!VIEW_AS.equals(claims.get(TYP))) return Optional.empty();
            return Optional.of(new ViewAs(Long.valueOf(claims.get("imp", String.class)), Long.valueOf(claims.getSubject())));
        } catch (JwtException | IllegalArgumentException | NullPointerException e) {
            return Optional.empty();
        }
    }
}
