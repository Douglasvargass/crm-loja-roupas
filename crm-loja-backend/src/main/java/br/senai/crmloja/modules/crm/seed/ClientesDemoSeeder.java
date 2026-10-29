package br.senai.crmloja.modules.crm.seed;

import br.senai.crmloja.core.identity.AppUser;
import br.senai.crmloja.core.identity.AppUserRepository;
import br.senai.crmloja.core.rbac.ModuleRepository;
import br.senai.crmloja.core.rbac.ModuleRoleRepository;
import br.senai.crmloja.core.rbac.UserModuleAccess;
import br.senai.crmloja.core.rbac.UserModuleAccessRepository;
import br.senai.crmloja.modules.crm.customer.CustomerRepository;
import br.senai.crmloja.modules.crm.customer.CustomerService;
import br.senai.crmloja.modules.crm.customer.dto.CustomerRequest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

// Clientes de demonstração com acesso ao espaço do cliente (spec crm/07, REQ-11): os professores da banca.
// Senha cliente12345 com troca obrigatória no primeiro acesso. Conferido em todo boot; uma senha que o professor já
// trocou nunca é mexida. CPFs são números de teste válidos, não de pessoas reais.
@Component
@Profile({"dev", "demo"})
@Order(35)
public class ClientesDemoSeeder implements ApplicationRunner {

    public static final String SENHA_INICIAL = "cliente12345";

    record Cliente(String login, String nome, String cpf, String email) {}

    static final List<Cliente> CLIENTES = List.of(
            new Cliente("vitor.freitas", "Vitor Freitas", "48151623446", "vitor.freitas@email.com"),
            new Cliente("matheus.carvalho", "Matheus Carvalho", "27182818205", "matheus.carvalho@email.com"));

    private static final Logger log = LoggerFactory.getLogger(ClientesDemoSeeder.class);

    private final AppUserRepository users;
    private final ModuleRepository modules;
    private final ModuleRoleRepository roles;
    private final UserModuleAccessRepository accesses;
    private final CustomerRepository customers;
    private final CustomerService customerService;
    private final PasswordEncoder encoder;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public ClientesDemoSeeder(AppUserRepository users, ModuleRepository modules, ModuleRoleRepository roles,
                              UserModuleAccessRepository accesses, CustomerRepository customers,
                              CustomerService customerService, PasswordEncoder encoder, JdbcTemplate jdbc,
                              TransactionTemplate tx) {
        this.users = users;
        this.modules = modules;
        this.roles = roles;
        this.accesses = accesses;
        this.customers = customers;
        this.customerService = customerService;
        this.encoder = encoder;
        this.jdbc = jdbc;
        this.tx = tx;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            tx.executeWithoutResult(s -> CLIENTES.forEach(this::garantir));
        } catch (RuntimeException e) {
            log.error("ClientesDemoSeeder falhou; a aplicação segue no ar", e);
        }
    }

    private void garantir(Cliente c) {
        var loja = modules.findByCode("loja").orElseThrow();
        var papel = roles.findByModuleIdAndCode(loja.getId(), "CUSTOMER").orElseThrow();

        AppUser u = users.findByLoginIgnoreCase(c.login()).orElseGet(() -> {
            AppUser novo = new AppUser(c.login(), c.nome(), encoder.encode(SENHA_INICIAL));
            novo.setMustChangePassword(true);
            log.warn("Cliente de demonstração criado: {} (senha {}, troca obrigatória)", c.login(), SENHA_INICIAL);
            return users.save(novo);
        });
        if (accesses.findByUserIdAndModuleId(u.getId(), loja.getId()).isEmpty()) {
            accesses.save(new UserModuleAccess(u.getId(), loja.getId(), papel.getId()));
        }

        Long customerId = customers.findByCpf(c.cpf()).map(x -> x.getId()).orElseGet(() -> customerService.create(
                new CustomerRequest(c.nome(), c.cpf(), "48991230000", c.email(), "88701000",
                        "Avenida Marcolino Martins Cabral", "1000", null, "Centro", "Tubarão", "SC"), null).id());
        jdbc.update("update customer set user_id = ? where id = ? and user_id is null", u.getId(), customerId);
    }
}
