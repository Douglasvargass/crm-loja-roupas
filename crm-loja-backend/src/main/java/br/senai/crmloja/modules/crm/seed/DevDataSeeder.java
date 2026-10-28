package br.senai.crmloja.modules.crm.seed;

import br.senai.crmloja.core.identity.AppUser;
import br.senai.crmloja.core.identity.AppUserRepository;
import br.senai.crmloja.core.rbac.ModuleRepository;
import br.senai.crmloja.core.rbac.ModuleRoleRepository;
import br.senai.crmloja.core.rbac.UserModuleAccess;
import br.senai.crmloja.core.rbac.UserModuleAccessRepository;
import br.senai.crmloja.modules.crm.category.CategoryRepository;
import br.senai.crmloja.modules.crm.category.CategoryService;
import br.senai.crmloja.modules.crm.category.dto.CategoryRequest;
import br.senai.crmloja.modules.crm.customer.CustomerService;
import br.senai.crmloja.modules.crm.customer.dto.CustomerRequest;
import br.senai.crmloja.modules.crm.product.ProductService;
import br.senai.crmloja.modules.crm.product.dto.ProductRequest;
import br.senai.crmloja.modules.crm.sale.SaleService;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

// Dados de demonstração (perfis dev e demo, só com o catálogo vazio). Tudo passa pelos Services para gerar
// as movimentações ENTRY e SALE corretas, como aconteceria pela tela.
// CPFs são números de teste válidos, não de pessoas reais.
@Component
@Profile({"dev", "demo"})
@Order(30)
public class DevDataSeeder implements ApplicationRunner {

    public static final String DEMO_PASSWORD = "loja12345";

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    private final CategoryRepository categoryRepo;
    private final CategoryService categories;
    private final CustomerService customers;
    private final ProductService products;
    private final SaleService sales;
    private final AppUserRepository users;
    private final ModuleRepository modules;
    private final ModuleRoleRepository roles;
    private final UserModuleAccessRepository accesses;
    private final PasswordEncoder encoder;
    private final TransactionTemplate tx;

    public DevDataSeeder(CategoryRepository categoryRepo, CategoryService categories, CustomerService customers,
                         ProductService products, SaleService sales, AppUserRepository users,
                         ModuleRepository modules, ModuleRoleRepository roles, UserModuleAccessRepository accesses,
                         PasswordEncoder encoder, TransactionTemplate tx) {
        this.categoryRepo = categoryRepo;
        this.categories = categories;
        this.customers = customers;
        this.products = products;
        this.sales = sales;
        this.users = users;
        this.modules = modules;
        this.roles = roles;
        this.accesses = accesses;
        this.encoder = encoder;
        this.tx = tx;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            tx.executeWithoutResult(s -> {
                if (categoryRepo.count() > 0) return;
                seed();
                log.warn("\n\n  Dados de demonstração criados. Logins (senha {}):\n"
                        + "    gerente@loja.local · vendedor@loja.local · estoquista@loja.local\n", DEMO_PASSWORD);
            });
        } catch (RuntimeException e) {
            log.error("DevDataSeeder falhou; a aplicação segue no ar", e);
        }
    }

    private void seed() {
        Long manager = user("gerente@loja.local", "Gabriela Gerente", "MANAGER");
        user("vendedor@loja.local", "Vitor Vendedor", "SELLER");
        user("estoquista@loja.local", "Elisa Estoquista", "STOCK_KEEPER");

        Map<String, Long> cat = new HashMap<>();
        for (String[] c : List.of(
                new String[]{"Camisetas", "Camisetas e regatas"},
                new String[]{"Calças", "Jeans, sarja e moletom"},
                new String[]{"Vestidos", "Curtos, midi e longos"},
                new String[]{"Jaquetas", "Jaquetas e casacos"},
                new String[]{"Acessórios", "Cintos, bonés, meias"})) {
            cat.put(c[0], categories.create(new CategoryRequest(c[0], c[1], true)).id());
        }

        List<Long> cust = List.of(
                customer("Ana Paula Souza", "52998224725", "48991234567", "ana.souza@email.com",
                        "88010000", "Rua Felipe Schmidt", "120", "Centro"),
                customer("João Pedro Lima", "11144477735", "48998765432", "joao.lima@email.com",
                        "88015200", "Avenida Mauro Ramos", "455", "Centro"),
                customer("Mariana Costa", "39053344705", "48996541230", "mariana.costa@email.com",
                        "88036000", "Rua Lauro Linhares", "980", "Trindade"),
                customer("Carlos Eduardo Rocha", "87748248800", "4832245566", "carlos.rocha@email.com",
                        "88040000", "Rua Deputado Antônio Edu Vieira", "77", "Pantanal"),
                customer("Fernanda Oliveira", "71428793860", "48999887766", "fernanda.o@email.com",
                        "88062000", "Avenida das Rendeiras", "1500", "Lagoa da Conceição"),
                customer("Lucas Martins", "34608514300", "48991112233", "lucas.martins@email.com",
                        "88058000", "Rua das Gaivotas", "33", "Ingleses"));

        Map<String, Long> p = new HashMap<>();
        p.put("CAM-001", product("CAM-001-M-BR", "Camiseta Básica Branca M", cat.get("Camisetas"), "M", "Branco", "49.90", 30, 5));
        product("CAM-002-G-BR", "Camiseta Básica Branca G", cat.get("Camisetas"), "G", "Branco", "49.90", 3, 5);
        p.put("CAM-003", product("CAM-003-P-PT", "Camiseta Estampada Preta P", cat.get("Camisetas"), "P", "Preto", "69.90", 20, 5));
        product("CAL-001-40-AZ", "Calça Jeans Skinny 40", cat.get("Calças"), "40", "Azul", "189.90", 2, 4);
        p.put("CAL-002", product("CAL-002-42-PT", "Calça Sarja Preta 42", cat.get("Calças"), "42", "Preto", "159.90", 15, 4));
        p.put("VES-001", product("VES-001-M-VM", "Vestido Midi Floral M", cat.get("Vestidos"), "M", "Vermelho", "219.90", 10, 3));
        product("VES-002-P-AZ", "Vestido Curto de Linho P", cat.get("Vestidos"), "P", "Azul", "179.90", 8, 3);
        product("JAQ-001-M-PT", "Jaqueta Couro Sintético M", cat.get("Jaquetas"), "M", "Preto", "299.90", 0, 2);
        p.put("JAQ-002", product("JAQ-002-G-AZ", "Jaqueta Jeans G", cat.get("Jaquetas"), "G", "Azul", "249.90", 6, 2));
        p.put("ACE-001", product("ACE-001-U-MR", "Cinto de Couro", cat.get("Acessórios"), "U", "Marrom", "79.90", 25, 5));
        p.put("ACE-002", product("ACE-002-U-PT", "Boné Aba Curva", cat.get("Acessórios"), "U", "Preto", "59.90", 18, 5));
        p.put("ACE-003", product("ACE-003-U-CZ", "Kit Meias 3 Pares", cat.get("Acessórios"), "U", "Cinza", "29.90", 40, 10));

        sale(cust.get(0), manager, Map.of(p.get("CAM-001"), 2, p.get("CAL-002"), 1));
        sale(cust.get(1), manager, Map.of(p.get("VES-001"), 1, p.get("ACE-001"), 1));
        sale(null, manager, Map.of(p.get("ACE-003"), 3));
        sale(cust.get(2), manager, Map.of(p.get("CAM-003"), 2, p.get("ACE-002"), 1));
        Long cancelled = sale(cust.get(0), manager, Map.of(p.get("JAQ-002"), 1));
        sales.cancel(cancelled, "Cliente desistiu da compra", manager);
    }

    private Long user(String login, String name, String roleCode) {
        AppUser u = new AppUser(login, name, encoder.encode(DEMO_PASSWORD));
        u.setMustChangePassword(false);
        users.save(u);
        var crm = modules.findByCode("crm").orElseThrow();
        var role = roles.findByModuleIdAndCode(crm.getId(), roleCode).orElseThrow();
        accesses.save(new UserModuleAccess(u.getId(), crm.getId(), role.getId()));
        return u.getId();
    }

    private Long customer(String name, String cpf, String phone, String email, String zip, String street,
                          String number, String district) {
        return customers.create(new CustomerRequest(name, cpf, phone, email, zip, street, number, null, district,
                "Florianópolis", "SC"), null).id();
    }

    private Long product(String code, String name, Long categoryId, String size, String color, String price,
                         int stock, int min) {
        return products.create(new ProductRequest(code, name, null, categoryId, size, color, new BigDecimal(price),
                stock, min), null).id();
    }

    private Long sale(Long customerId, Long actor, Map<Long, Integer> items) {
        Long id = sales.open(customerId, actor).id();
        items.forEach((productId, qty) -> sales.addItem(id, productId, qty));
        sales.confirm(id, actor);
        return id;
    }
}
