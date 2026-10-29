package br.senai.crmloja.modules.crm.seed;

import br.senai.crmloja.core.identity.AppUser;
import br.senai.crmloja.core.identity.AppUserRepository;
import br.senai.crmloja.core.rbac.Module;
import br.senai.crmloja.core.rbac.ModuleRepository;
import br.senai.crmloja.core.rbac.ModuleRole;
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

// Cadastros de demonstração (perfis dev e demo). Duas partes:
//  1) A equipe: reconciliada em TODO boot. Garante login, nome e papel de cada pessoa, mesmo num banco que
//     já existia (o do Render, o .data local). Assim trocar o login ou o papel de alguém aqui vale na
//     próxima subida, sem precisar zerar o banco. A senha só é definida na criação da conta: uma senha
//     escolhida pela pessoa nunca é sobrescrita.
//  2) O catálogo (categorias, clientes, produtos): só quando o banco está vazio. Tudo passa pelos Services,
//     como aconteceria pela tela.
// Os produtos nascem com estoque zero de propósito: quem abre o estoque é o HistoricoVendasSeeder, que grava a
// carga inicial com data retrodatada. Se a entrada fosse criada aqui, ficaria com a data do boot — e o trigger
// trg_stock_movement_immutable não deixa corrigir depois.
// CPFs são números de teste válidos, não de pessoas reais.
@Component
@Profile({"dev", "demo"})
@Order(30)
public class DevDataSeeder implements ApplicationRunner {

    public static final String DEMO_PASSWORD = "loja12345";

    // Logins eram nome.sobrenome@loja.local; hoje são só nome.sobrenome. Contas antigas são renomeadas.
    static final String OLD_LOGIN_SUFFIX = "@loja.local";

    // Equipe real do projeto. ADMIN = super admin (passa em tudo) e, por cima disso, o papel DEVELOPER
    // para aparecer na tela com o rótulo certo. Os demais têm um papel só.
    public record Membro(String login, String nome, String papel, boolean superAdmin) {}

    public static final List<Membro> EQUIPE = List.of(
            new Membro("guilherme.dower", "Guilherme Dower", "MANAGER", false),
            new Membro("douglas.vargas", "Douglas Vargas", "DEVELOPER", true),
            new Membro("julia.alcantara", "Julia Alcantara", "MARKETING_DEV", false),
            new Membro("vitor.nunes", "Vitor Nunes", "SELLER", false),
            // Gestão de operação e compras: recebe os chamados de peça em falta e faz o pedido ao fornecedor.
            new Membro("vinicius.clemente", "Vinicius Clemente", "PURCHASING", false),
            new Membro("vinicius.muniz", "Vinicius Muniz", "SELLER", false));

    // Quem registra venda nos dados de demonstração (HistoricoVendasSeeder), em ordem de peso.
    public static List<String> vendedores() {
        return EQUIPE.stream().filter(m -> "SELLER".equals(m.papel())).map(Membro::login).toList();
    }

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    private final CategoryRepository categoryRepo;
    private final CategoryService categories;
    private final CustomerService customers;
    private final ProductService products;
    private final AppUserRepository users;
    private final ModuleRepository modules;
    private final ModuleRoleRepository roles;
    private final UserModuleAccessRepository accesses;
    private final PasswordEncoder encoder;
    private final TransactionTemplate tx;

    public DevDataSeeder(CategoryRepository categoryRepo, CategoryService categories, CustomerService customers,
                         ProductService products, AppUserRepository users,
                         ModuleRepository modules, ModuleRoleRepository roles, UserModuleAccessRepository accesses,
                         PasswordEncoder encoder, TransactionTemplate tx) {
        this.categoryRepo = categoryRepo;
        this.categories = categories;
        this.customers = customers;
        this.products = products;
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
                reconcileTeam();
                if (categoryRepo.count() > 0) return;
                seedCatalog();
                log.warn("\n\n  Dados de demonstração criados. Equipe (senha {}):\n    {}\n", DEMO_PASSWORD,
                        EQUIPE.stream().map(Membro::login).collect(java.util.stream.Collectors.joining("\n    ")));
            });
        } catch (RuntimeException e) {
            log.error("DevDataSeeder falhou; a aplicação segue no ar", e);
        }
    }

    // ---------------------------------------------------------------- equipe

    private void reconcileTeam() {
        Module crm = modules.findByCode("crm").orElseThrow();
        for (Membro m : EQUIPE) {
            AppUser u = users.findByLoginIgnoreCase(m.login())
                    .or(() -> users.findByLoginIgnoreCase(m.login() + OLD_LOGIN_SUFFIX).map(old -> {
                        old.setLogin(m.login());
                        log.info("Login de demonstração renomeado: {}{} -> {}", m.login(), OLD_LOGIN_SUFFIX, m.login());
                        return old;
                    }))
                    .orElseGet(() -> {
                        AppUser novo = new AppUser(m.login(), m.nome(), encoder.encode(DEMO_PASSWORD));
                        novo.setMustChangePassword(false);
                        return users.save(novo);
                    });

            if (!m.nome().equals(u.getFullName())) u.setFullName(m.nome());
            if (u.isSuperAdmin() != m.superAdmin()) u.setSuperAdmin(m.superAdmin());

            ModuleRole role = roles.findByModuleIdAndCode(crm.getId(), m.papel()).orElseThrow();
            accesses.findByUserIdAndModuleId(u.getId(), crm.getId()).ifPresentOrElse(a -> {
                if (!a.getModuleRoleId().equals(role.getId())) a.setModuleRoleId(role.getId());
                if (!a.isActive()) a.setActive(true);
            }, () -> accesses.save(new UserModuleAccess(u.getId(), crm.getId(), role.getId())));
        }
    }

    // --------------------------------------------------------------- catálogo

    private void seedCatalog() {
        Map<String, Long> cat = new HashMap<>();
        for (String[] c : List.of(
                new String[]{"Camisetas", "Camisetas e regatas"},
                new String[]{"Calças", "Jeans, sarja e moletom"},
                new String[]{"Vestidos", "Curtos, midi e longos"},
                new String[]{"Jaquetas", "Jaquetas e casacos"},
                new String[]{"Acessórios", "Cintos, bonés, meias"})) {
            cat.put(c[0], categories.create(new CategoryRequest(c[0], c[1], true)).id());
        }

        List.of(
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

        product("CAM-001-M-BR", "Camiseta Básica Branca M", cat.get("Camisetas"), "M", "Branco", "49.90", 5);
        product("CAM-002-G-BR", "Camiseta Básica Branca G", cat.get("Camisetas"), "G", "Branco", "49.90", 5);
        product("CAM-003-P-PT", "Camiseta Estampada Preta P", cat.get("Camisetas"), "P", "Preto", "69.90", 5);
        product("CAL-001-40-AZ", "Calça Jeans Skinny 40", cat.get("Calças"), "40", "Azul", "189.90", 4);
        product("CAL-002-42-PT", "Calça Sarja Preta 42", cat.get("Calças"), "42", "Preto", "159.90", 4);
        product("VES-001-M-VM", "Vestido Midi Floral M", cat.get("Vestidos"), "M", "Vermelho", "219.90", 3);
        product("VES-002-P-AZ", "Vestido Curto de Linho P", cat.get("Vestidos"), "P", "Azul", "179.90", 3);
        product("JAQ-001-M-PT", "Jaqueta Couro Sintético M", cat.get("Jaquetas"), "M", "Preto", "299.90", 2);
        product("JAQ-002-G-AZ", "Jaqueta Jeans G", cat.get("Jaquetas"), "G", "Azul", "249.90", 2);
        product("ACE-001-U-MR", "Cinto de Couro", cat.get("Acessórios"), "U", "Marrom", "79.90", 5);
        product("ACE-002-U-PT", "Boné Aba Curva", cat.get("Acessórios"), "U", "Preto", "59.90", 5);
        product("ACE-003-U-CZ", "Kit Meias 3 Pares", cat.get("Acessórios"), "U", "Cinza", "29.90", 10);
    }

    private Long customer(String name, String cpf, String phone, String email, String zip, String street,
                          String number, String district) {
        return customers.create(new CustomerRequest(name, cpf, phone, email, zip, street, number, null, district,
                "Florianópolis", "SC"), null).id();
    }

    // Estoque inicial zero: a carga de abertura é gravada retrodatada pelo HistoricoVendasSeeder.
    private Long product(String code, String name, Long categoryId, String size, String color, String price,
                         int min) {
        return products.create(new ProductRequest(code, name, null, categoryId, size, color, new BigDecimal(price),
                0, min), null).id();
    }
}
