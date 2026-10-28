package br.senai.crmloja.core.users;

import br.senai.crmloja.core.audit.AuditService;
import br.senai.crmloja.core.http.BusinessRuleException;
import br.senai.crmloja.core.http.InvalidInputException;
import br.senai.crmloja.core.http.NotFoundException;
import br.senai.crmloja.core.identity.AppUser;
import br.senai.crmloja.core.identity.AppUserRepository;
import br.senai.crmloja.core.identity.RefreshTokenRepository;
import br.senai.crmloja.core.rbac.Module;
import br.senai.crmloja.core.rbac.ModuleRepository;
import br.senai.crmloja.core.rbac.ModuleRole;
import br.senai.crmloja.core.rbac.ModuleRoleRepository;
import br.senai.crmloja.core.rbac.UserModuleAccess;
import br.senai.crmloja.core.rbac.UserModuleAccessRepository;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAdminService {

    public record UserRow(Long id, String login, String fullName, String role, boolean active,
                          boolean mustChangePassword, OffsetDateTime lastLoginAt) {}

    public record CreatedUser(UserRow user, String provisionalPassword) {}

    private final AppUserRepository users;
    private final ModuleRepository modules;
    private final ModuleRoleRepository roles;
    private final UserModuleAccessRepository accesses;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder encoder;
    private final AuditService audit;

    public UserAdminService(AppUserRepository users, ModuleRepository modules, ModuleRoleRepository roles,
                            UserModuleAccessRepository accesses, RefreshTokenRepository refreshTokens,
                            PasswordEncoder encoder, AuditService audit) {
        this.users = users;
        this.modules = modules;
        this.roles = roles;
        this.accesses = accesses;
        this.refreshTokens = refreshTokens;
        this.encoder = encoder;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<UserRow> list(String moduleCode) {
        Module module = module(moduleCode);
        Map<Long, String> roleCodes = roles.findByModuleId(module.getId()).stream()
                .collect(Collectors.toMap(ModuleRole::getId, ModuleRole::getCode));
        Map<Long, AppUser> byId = users.findAll().stream().collect(Collectors.toMap(AppUser::getId, Function.identity()));
        return accesses.findByModuleId(module.getId()).stream()
                .map(a -> {
                    AppUser u = byId.get(a.getUserId());
                    return new UserRow(u.getId(), u.getLogin(), u.getFullName(), roleCodes.get(a.getModuleRoleId()),
                            u.isActive() && a.isActive(), u.isMustChangePassword(), u.getLastLoginAt());
                })
                .sorted(Comparator.comparing(UserRow::fullName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional
    public CreatedUser create(String moduleCode, String login, String fullName, String roleCode, Long actorId) {
        Module module = moduleForUpdate(moduleCode);
        ModuleRole role = role(module, roleCode);
        String normalized = login.trim().toLowerCase();
        if (users.findByLoginIgnoreCase(normalized).isPresent()) {
            throw new BusinessRuleException("login_taken", "Já existe uma conta com este login.");
        }
        String password = PasswordGenerator.generate(10);
        AppUser user = users.save(new AppUser(normalized, fullName.trim(), encoder.encode(password)));
        accesses.save(new UserModuleAccess(user.getId(), module.getId(), role.getId()));
        audit.record(actorId, moduleCode, "user_created", "app_user", user.getId(), Map.of("role", role.getCode()));
        return new CreatedUser(new UserRow(user.getId(), user.getLogin(), user.getFullName(), role.getCode(),
                true, true, null), password);
    }

    // Muda papel e/ou situação. A trava no módulo serializa a regra "sempre um gerente ativo".
    @Transactional
    public UserRow update(String moduleCode, Long userId, String roleCode, Boolean active, Long actorId) {
        Module module = moduleForUpdate(moduleCode);
        AppUser user = users.findById(userId).orElseThrow(() -> new NotFoundException("Usuário não encontrado."));
        UserModuleAccess access = accesses.findByUserIdAndModuleId(userId, module.getId())
                .orElseThrow(() -> new NotFoundException("Usuário não tem acesso a este módulo."));
        ModuleRole currentRole = roles.findById(access.getModuleRoleId()).orElseThrow();

        boolean changesRole = roleCode != null && !roleCode.equals(currentRole.getCode());
        boolean changesActive = active != null && active != (user.isActive() && access.isActive());
        if (userId.equals(actorId) && (changesRole || changesActive)) {
            throw new BusinessRuleException("self_role_change",
                    "Você não pode mudar o próprio papel nem desativar a própria conta.");
        }

        Map<String, Object> payload = new HashMap<>();
        if (changesRole) {
            ModuleRole newRole = role(module, roleCode);
            access.setModuleRoleId(newRole.getId());
            payload.put("from", currentRole.getCode());
            payload.put("to", newRole.getCode());
        }
        if (changesActive) {
            user.setActive(active);
            access.setActive(active);
            payload.put("active", active);
            if (!active) refreshTokens.revokeAllOfUser(userId, OffsetDateTime.now());
        }
        if (payload.isEmpty()) return toRow(user, access, currentRole.getCode());

        users.flush();
        if (accesses.countActiveAdmins(module.getId()) == 0) {
            throw new BusinessRuleException("last_admin", "A loja precisa ter pelo menos um gerente ativo.");
        }
        audit.record(actorId, moduleCode, changesRole ? "user_role_changed" : "user_status_changed",
                "app_user", userId, payload);
        return toRow(user, access, roles.findById(access.getModuleRoleId()).orElseThrow().getCode());
    }

    private UserRow toRow(AppUser u, UserModuleAccess a, String role) {
        return new UserRow(u.getId(), u.getLogin(), u.getFullName(), role, u.isActive() && a.isActive(),
                u.isMustChangePassword(), u.getLastLoginAt());
    }

    private Module module(String code) {
        return modules.findByCode(code).orElseThrow(() -> new NotFoundException("Módulo não encontrado."));
    }

    private Module moduleForUpdate(String code) {
        return modules.findByCodeForUpdate(code).orElseThrow(() -> new NotFoundException("Módulo não encontrado."));
    }

    private ModuleRole role(Module module, String code) {
        return roles.findByModuleIdAndCode(module.getId(), code)
                .orElseThrow(() -> new InvalidInputException("invalid_role", "Papel inválido."));
    }
}
