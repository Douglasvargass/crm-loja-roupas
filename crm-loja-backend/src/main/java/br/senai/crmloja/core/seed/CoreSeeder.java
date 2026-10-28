package br.senai.crmloja.core.seed;

import br.senai.crmloja.core.config.AppProperties;
import br.senai.crmloja.core.identity.AppUser;
import br.senai.crmloja.core.identity.AppUserRepository;
import br.senai.crmloja.core.users.PasswordGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

// Reconciliador idempotente: garante o super admin. Falha de seed é logada e o sistema continua no ar.
@Component
@Order(10)
public class CoreSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CoreSeeder.class);

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final AppProperties props;
    private final TransactionTemplate tx;

    public CoreSeeder(AppUserRepository users, PasswordEncoder encoder, AppProperties props, TransactionTemplate tx) {
        this.users = users;
        this.encoder = encoder;
        this.props = props;
        this.tx = tx;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            tx.executeWithoutResult(s -> seed());
        } catch (RuntimeException e) {
            log.error("CoreSeeder falhou; a aplicação segue no ar", e);
        }
    }

    private void seed() {
        if (users.findByLoginIgnoreCase(props.superAdminLogin()).isPresent()) return;

        boolean fixed = props.superAdminPassword() != null && !props.superAdminPassword().isBlank();
        String password = fixed ? props.superAdminPassword() : PasswordGenerator.generate(24);
        var admin = new AppUser(props.superAdminLogin(), "Administrador", encoder.encode(password));
        admin.setSuperAdmin(true);
        // Senha gerada aparece uma vez só: troca obrigatória no primeiro acesso.
        admin.setMustChangePassword(!fixed);
        users.save(admin);
        if (fixed) {
            log.info("Super admin criado: {} (senha definida em SUPER_ADMIN_PASSWORD)", props.superAdminLogin());
        } else {
            log.warn("\n\n  Super admin criado: {}  senha: {}\n  Anote agora: ela não será mostrada de novo.\n",
                    props.superAdminLogin(), password);
        }
    }
}
