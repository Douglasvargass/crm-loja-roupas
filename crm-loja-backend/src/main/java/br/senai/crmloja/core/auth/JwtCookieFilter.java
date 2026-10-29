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
import java.util.Set;
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
            String role = rbac.effectiveRoleOf(u.getId()).orElse(null);
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

            // "Ver como" (spec crm/10): cookie assinado, emitido para ESTE usuário (imp), que precisa poder usar o
            // recurso. A requisição passa a ser da pessoa vista, com o papel dela, e só leitura. Cookie inválido,
            // vencido ou de outra pessoa é ignorado: a pessoa volta a ser ela mesma.
            var target = AuthCookies.read(req, AuthCookies.VIEW_AS)
                    .flatMap(jwt::parseViewAs)
                    .filter(v -> v.viewerId().equals(u.getId()) && canViewAs(u.isSuperAdmin(), role))
                    .flatMap(v -> users.findById(v.targetId()))
                    .filter(t -> t.isActive() && !t.isBlocked() && !t.getId().equals(u.getId()));
            if (target.isPresent()) {
                var t = target.get();
                if (!READ_METHODS.contains(req.getMethod()) && !VIEW_AS_EXEMPT.contains(req.getRequestURI())) {
                    res.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    res.setCharacterEncoding("UTF-8");
                    json.writeValue(res.getWriter(), Map.of("error", "Você está vendo como " + t.getFullName()
                            + ": é só para olhar. Saia do \"Ver como\" para fazer alterações.", "code", "read_only_view_as"));
                    return;
                }
                String targetRole = rbac.roleCodeOf(t.getId(), "crm").orElse(null);
                // Super admin da pessoa vista NÃO vale aqui: o "Ver como" mostra exatamente o papel dela no crm.
                var seen = new CurrentUser(t.getId(), t.getLogin(), t.getFullName(), false, false, targetRole,
                        u.getId(), u.getFullName());
                List<GrantedAuthority> seenAuth = new ArrayList<>();
                RbacService.grantedRoles(targetRole).forEach(r -> seenAuth.add(new SimpleGrantedAuthority("ROLE_" + r)));
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(seen, null, seenAuth));
                chain.doFilter(req, res);
                return;
            }

            // Super admin passa em tudo; os demais recebem o próprio papel e os que ele implica
            // (papel técnico vale por gerente, vendedor e estoquista).
            List<GrantedAuthority> authorities = new ArrayList<>();
            if (u.isSuperAdmin()) {
                RbacService.BASE_ROLES.forEach(r -> authorities.add(new SimpleGrantedAuthority("ROLE_" + r)));
            }
            RbacService.grantedRoles(role).forEach(r -> authorities.add(new SimpleGrantedAuthority("ROLE_" + r)));
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(principal, null, authorities));
        }
        chain.doFilter(req, res);
    }

    private static final Set<String> READ_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    // Durante o "Ver como" só estas alterações passam: sair dele, sair do sistema e renovar a sessão.
    private static final Set<String> VIEW_AS_EXEMPT = Set.of("/auth/view-as", "/auth/logout", "/auth/refresh");

    // Quem pode usar o "Ver como": gerente (e quem vale por ele) ou super admin.
    public static boolean canViewAs(boolean superAdmin, String role) {
        return superAdmin || RbacService.grantedRoles(role).contains("MANAGER");
    }
}
