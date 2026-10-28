package br.senai.crmloja.modules.crm.seed;

import br.senai.crmloja.core.rbac.Module;
import br.senai.crmloja.core.rbac.ModuleRepository;
import br.senai.crmloja.core.rbac.ModuleRole;
import br.senai.crmloja.core.rbac.ModuleRoleRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

// Módulo e papéis são dados, não migration: este reconciliador garante que existam e corrige nome
// ou flag divergente em todo boot. Nunca remove papel (pode estar em uso).
@Component
@Order(20)
public class CrmModuleSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CrmModuleSeeder.class);

    private record RoleDef(String code, String name, boolean admin) {}

    private static final List<RoleDef> ROLES = List.of(
            new RoleDef("MANAGER", "Gerente", true),
            new RoleDef("SELLER", "Vendedor(a)", false),
            new RoleDef("STOCK_KEEPER", "Estoquista", false));

    private final ModuleRepository modules;
    private final ModuleRoleRepository roles;
    private final TransactionTemplate tx;

    public CrmModuleSeeder(ModuleRepository modules, ModuleRoleRepository roles, TransactionTemplate tx) {
        this.modules = modules;
        this.roles = roles;
        this.tx = tx;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            tx.executeWithoutResult(s -> seed());
        } catch (RuntimeException e) {
            log.error("CrmModuleSeeder falhou; a aplicação segue no ar", e);
        }
    }

    void seed() {
        Module crm = modules.findByCode("crm").orElseGet(() -> modules.save(new Module("crm", "CRM Loja de Roupas")));
        if (!crm.isActive()) crm.setActive(true);
        for (RoleDef def : ROLES) {
            roles.findByModuleIdAndCode(crm.getId(), def.code()).ifPresentOrElse(r -> {
                if (!r.getName().equals(def.name())) r.setName(def.name());
                if (r.isAdmin() != def.admin()) r.setAdmin(def.admin());
            }, () -> roles.save(new ModuleRole(crm.getId(), def.code(), def.name(), def.admin())));
        }
    }
}
