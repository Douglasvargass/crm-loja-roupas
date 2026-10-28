package br.senai.crmloja.core.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.ZoneId;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

// Fail-fast: sem JWT_SECRET (ou com valor inválido) a aplicação não sobe,
// em vez de falhar só no primeiro login.
@Validated
@ConfigurationProperties("app")
public record AppProperties(
        @NotBlank @Size(min = 32, message = "JWT_SECRET precisa de pelo menos 32 caracteres") String jwtSecret,
        @Positive int accessTokenTtlSeconds,
        @Positive int refreshTokenTtlHours,
        List<String> frontendOrigins,
        @NotBlank String superAdminLogin,
        String superAdminPassword,
        boolean cookieSecure,
        @NotBlank String timeZone) {

    public ZoneId zone() { return ZoneId.of(timeZone); }
}
