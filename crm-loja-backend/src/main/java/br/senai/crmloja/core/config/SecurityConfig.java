package br.senai.crmloja.core.config;

import br.senai.crmloja.core.auth.JwtCookieFilter;
import br.senai.crmloja.core.auth.JwtService;
import br.senai.crmloja.core.identity.AppUserRepository;
import br.senai.crmloja.core.rbac.RbacService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.scrypt.SCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

// Sem sessão no servidor: cada requisição se autentica pelo cookie access_token.
// CSRF desligado porque os cookies são SameSite=Lax e a API só aceita JSON, e a origem do
// front é a mesma (proxy /api); CORS só libera as origens listadas em FRONTEND_ORIGINS.
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return SCryptPasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, JwtService jwt, AppUserRepository users, RbacService rbac,
                                    ObjectMapper json) throws Exception {
        http
            .csrf(c -> c.disable())
            .cors(c -> {})
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .httpBasic(b -> b.disable())
            .formLogin(f -> f.disable())
            .logout(l -> l.disable())
            // API exige sessão; o resto são as telas do front (servidas pelo SpaConfig no deploy único).
            .authorizeHttpRequests(a -> a
                .requestMatchers("/auth/login", "/auth/refresh", "/auth/logout", "/error").permitAll()
                .requestMatchers("/auth/**", "/crm/**", "/admin/**").authenticated()
                .anyRequest().permitAll())
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req, res, ex) -> write(res, json, HttpServletResponse.SC_UNAUTHORIZED,
                        "Sua sessão expirou. Entre novamente.", "unauthenticated"))
                .accessDeniedHandler((req, res, ex) -> write(res, json, HttpServletResponse.SC_FORBIDDEN,
                        "Seu perfil não tem permissão para esta ação.", "forbidden")))
            .addFilterBefore(new JwtCookieFilter(jwt, users, rbac, json), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(AppProperties props) {
        var cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(props.frontendOrigins());
        cfg.addAllowedMethod("*");
        cfg.addAllowedHeader("*");
        cfg.setAllowCredentials(true);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }

    private static void write(HttpServletResponse res, ObjectMapper json, int status, String msg, String code)
            throws java.io.IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        json.writeValue(res.getWriter(), Map.of("error", msg, "code", code));
    }
}
