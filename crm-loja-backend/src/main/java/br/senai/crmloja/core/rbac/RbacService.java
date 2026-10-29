package br.senai.crmloja.core.rbac;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RbacService {

    // Papéis "de balcão": cada um abre só a sua parte do sistema.
    public static final List<String> BASE_ROLES = List.of("MANAGER", "SELLER", "STOCK_KEEPER");

    // Papéis da equipe técnica (quem constrói e mantém o sistema): enxergam e fazem tudo que o gerente
    // faz, em todas as áreas. O rótulo na tela é o que muda; a permissão é a soma dos papéis de balcão.
    public static final Set<String> FULL_ACCESS_ROLES = Set.of("DEVELOPER", "MARKETING_DEV");

    // Papéis que valem por um papel de balcão. Operações e Compras recebe os chamados de peça em falta e faz o pedido
    // ao fornecedor: enxerga e faz tudo o que o estoquista faz (estoque, produtos, resolver chamado), e não vende.
    private static final Map<String, String> IMPLIES = Map.of("PURCHASING", "STOCK_KEEPER");

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

    // Papel de quem entra: o do crm (equipe) ou, se não houver, o do módulo loja (cliente, spec crm/07).
    @Transactional(readOnly = true)
    public Optional<String> effectiveRoleOf(Long userId) {
        return roleCodeOf(userId, "crm").or(() -> roleCodeOf(userId, "loja"));
    }

    // Papéis que valem para quem tem este papel. É o que o @PreAuthorize e as ferramentas da Thalita
    // conferem: um papel técnico "vale por" gerente, vendedor e estoquista ao mesmo tempo.
    public static Set<String> grantedRoles(String roleCode) {
        if (roleCode == null) return Set.of();
        if (FULL_ACCESS_ROLES.contains(roleCode)) {
            return Set.of("MANAGER", "SELLER", "STOCK_KEEPER", roleCode);
        }
        if (IMPLIES.containsKey(roleCode)) return Set.of(roleCode, IMPLIES.get(roleCode));
        return Set.of(roleCode);
    }
}
