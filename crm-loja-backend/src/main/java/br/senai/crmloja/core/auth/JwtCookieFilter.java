package br.senai.crmloja.core.auth;

import br.senai.crmloja.core.identity.AppUserRepository;
import br.senai.crmloja.core.rbac.RbacService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

// Lê o cookie access_token, carrega usuário e papel do banco e monta o principal.
// Token inválido ou ausente: segue anônimo e o Spring Security devolve 401 nas rotas protegidas.
// Não é @Component de propósito: só o SecurityConfig o registra, para não rodar duas vezes.
public class JwtCookieFilter extends OncePerRequestFilter {

    public static final List<String> ROLES = List.of("MANAGER", "SELLER", "STOCK_KEEPER");

    private final JwtService jwt;
    private final AppUserRepository users;
    private final RbacService rbac;
    private final ObjectMapper json;

    public JwtCookieFilter(JwtService jwt, AppUserRepository users, RbacService rbac, ObjectMapper json) {
        this.jwt = jwt;
        this.users = users;
        this.rbac = rbac;
        this.json = json;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        var user = AuthCookies.read(req, AuthCookies.ACCESS)
                .flatMap(jwt::parseUserId)
                .flatMap(users::findById)
                .filter(u -> u.isActive() && !u.isBlocked());

        if (user.isPresent()) {
            var u = user.get();
            String role = rbac.roleCodeOf(u.getId(), "crm").orElse(null);
            var principal = new CurrentUser(u.getId(), u.getLogin(), u.getFullName(), u.isSuperAdmin(),
                    u.isMustChangePassword(), role);

            // Troca de senha pendente: só as rotas /auth/* ficam liberadas.
            if (u.isMustChangePassword() && !req.getRequestURI().startsWith("/auth/")) {
                res.setStatus(HttpServletResponse.SC_FORBIDDEN);
                res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                res.setCharacterEncoding("UTF-8");
                json.writeValue(res.getWriter(), Map.of(
                        "error", "Troque sua senha antes de continuar.", "code", "password_change_required"));
                return;
            }

            List<GrantedAuthority> authorities = new ArrayList<>();
            if (u.isSuperAdmin()) {
                ROLES.forEach(r -> authorities.add(new SimpleGrantedAuthority("ROLE_" + r)));
            } else if (role != null) {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
            }
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(principal, null, authorities));
        }
        chain.doFilter(req, res);
    }
}
