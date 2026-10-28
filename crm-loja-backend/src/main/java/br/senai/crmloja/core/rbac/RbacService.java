package br.senai.crmloja.core.rbac;

import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RbacService {

    private final ModuleRepository modules;
    private final ModuleRoleRepository roles;
    private final UserModuleAccessRepository accesses;

    public RbacService(ModuleRepository modules, ModuleRoleRepository roles, UserModuleAccessRepository accesses) {
        this.modules = modules;
        this.roles = roles;
        this.accesses = accesses;
    }

    // Papel ativo do usuário no módulo (ex.: "SELLER"), ou vazio se não tiver acesso.
    @Transactional(readOnly = true)
    public Optional<String> roleCodeOf(Long userId, String moduleCode) {
        return modules.findByCode(moduleCode)
                .filter(Module::isActive)
                .flatMap(m -> accesses.findByUserIdAndModuleId(userId, m.getId()))
                .filter(UserModuleAccess::isActive)
                .flatMap(a -> roles.findById(a.getModuleRoleId()))
                .map(ModuleRole::getCode);
    }
}
