package br.senai.crmloja.support;

import br.senai.crmloja.core.identity.AppUser;
import br.senai.crmloja.core.identity.AppUserRepository;
import br.senai.crmloja.core.rbac.ModuleRepository;
import br.senai.crmloja.core.rbac.ModuleRoleRepository;
import br.senai.crmloja.core.rbac.UserModuleAccess;
import br.senai.crmloja.core.rbac.UserModuleAccessRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// Usuários de teste por papel (MANAGER, SELLER, STOCK_KEEPER), com senha conhecida e sem troca pendente.
@Component
public class TestUsers {

    public static final String PASSWORD = "senha-de-teste-123";

    private final AppUserRepository users;
    private final ModuleRepository modules;
    private final ModuleRoleRepository roles;
    private final UserModuleAccessRepository accesses;
    private final PasswordEncoder encoder;

    public TestUsers(AppUserRepository users, ModuleRepository modules, ModuleRoleRepository roles,
                     UserModuleAccessRepository accesses, PasswordEncoder encoder) {
        this.users = users;
        this.modules = modules;
        this.roles = roles;
        this.accesses = accesses;
        this.encoder = encoder;
    }

    @Transactional
    public String ensure(String role) {
        String login = "test-" + role.toLowerCase() + "@loja.local";
        if (users.findByLoginIgnoreCase(login).isEmpty()) create(login, role, false);
        return login;
    }

    // Conta nova e exclusiva, para testes que mudam senha, papel ou situação.
    @Transactional
    public AppUser create(String login, String role, boolean mustChangePassword) {
        AppUser u = new AppUser(login, "Teste " + role, encoder.encode(PASSWORD));
        u.setMustChangePassword(mustChangePassword);
        users.save(u);
        var crm = modules.findByCode("crm").orElseThrow();
        var r = roles.findByModuleIdAndCode(crm.getId(), role).orElseThrow();
        accesses.save(new UserModuleAccess(u.getId(), crm.getId(), r.getId()));
        return u;
    }
}
