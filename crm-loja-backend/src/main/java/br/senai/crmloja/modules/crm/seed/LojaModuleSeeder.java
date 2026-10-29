package br.senai.crmloja.modules.crm.seed;

import br.senai.crmloja.core.rbac.Module;
import br.senai.crmloja.core.rbac.ModuleRepository;
import br.senai.crmloja.core.rbac.ModuleRole;
import br.senai.crmloja.core.rbac.ModuleRoleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

// Módulo "loja" (espaço do cliente, spec crm/07) com o papel CUSTOMER. Separado do crm de propósito: o cliente não
// tem papel nenhum no crm, então nenhuma rota da equipe (/crm/**, /admin/**) o aceita. Roda sempre, como o do crm.
@Component
@Order(21)
public class LojaModuleSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LojaModuleSeeder.class);

    private final ModuleRepository modules;
    private final ModuleRoleRepository roles;
    private final TransactionTemplate tx;

    public LojaModuleSeeder(ModuleRepository modules, ModuleRoleRepository roles, TransactionTemplate tx) {
        this.modules = modules;
        this.roles = roles;
        this.tx = tx;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            tx.executeWithoutResult(s -> seed());
        } catch (RuntimeException e) {
            log.error("LojaModuleSeeder falhou; a aplicação segue no ar", e);
        }
    }

    void seed() {
        Module loja = modules.findByCode("loja").orElseGet(() -> modules.save(new Module("loja", "Loja online")));
        if (!loja.isActive()) loja.setActive(true);
        roles.findByModuleIdAndCode(loja.getId(), "CUSTOMER")
                .orElseGet(() -> roles.save(new ModuleRole(loja.getId(), "CUSTOMER", "Cliente", false)));
    }
}
