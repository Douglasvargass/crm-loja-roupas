package br.senai.crmloja.modules.crm.assistant;

import br.senai.crmloja.core.http.InvalidInputException;
import br.senai.crmloja.core.http.NotFoundException;
import br.senai.crmloja.modules.crm.category.CategoryService;
import br.senai.crmloja.modules.crm.customer.CustomerService;
import br.senai.crmloja.modules.crm.dashboard.DashboardService;
import br.senai.crmloja.modules.crm.product.ProductService;
import br.senai.crmloja.modules.crm.product.ProductService.AvailabilityFilter;
import br.senai.crmloja.modules.crm.sale.SaleService;
import br.senai.crmloja.modules.crm.sale.SaleStatus;
import br.senai.crmloja.modules.crm.stock.StockMovementType;
import br.senai.crmloja.modules.crm.stock.StockService;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.Tool;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.springframework.stereotype.Component;

// Ferramentas que o assistente pode chamar. Todas só LEEM dados, pelos mesmos Services da API,
// e cada uma exige os mesmos papéis da rota equivalente: o assistente nunca mostra a alguém
// algo que a tela não mostraria.
@Component
public class AssistantTools {

    private record Def(String name, String description, Set<String> roles, Map<String, Object> properties,
                       List<String> required, Function<Map<String, Object>, Object> run) {}

    private static final Set<String> ALL = Set.of("MANAGER", "SELLER", "STOCK_KEEPER");
    private static final Set<String> MANAGER = Set.of("MANAGER");
    private static final Set<String> SALES = Set.of("MANAGER", "SELLER");
    private static final Set<String> STOCK = Set.of("MANAGER", "STOCK_KEEPER");

    private final Map<String, Def> defs = new LinkedHashMap<>();
    private final ObjectMapper json;

    public AssistantTools(DashboardService dashboard, ProductService products, CategoryService categories,
                          CustomerService customers, SaleService sales, StockService stock, ObjectMapper json) {
        this.json = json;

        add(new Def("consultar_dashboard",
                "Indicadores da loja num período: total de clientes e produtos, quantidade e valor das vendas "
                        + "confirmadas, ticket médio, canceladas, comparação com o período anterior, 5 mais vendidos, "
                        + "faturamento por dia, vendas por categoria, estoque baixo e zerado. Sem datas = mês atual.",
                MANAGER,
                Map.of("de", date("Data inicial (yyyy-mm-dd)"), "ate", date("Data final (yyyy-mm-dd), inclusive")),
                List.of(),
                a -> dashboard.build(dateArg(a, "de"), dateArg(a, "ate"))));

        add(new Def("buscar_produtos",
                "Busca produtos do catálogo com preço, tamanho, cor, categoria, estoque atual e situação. "
                        + "Filtros combináveis; devolve até 20 por página.",
                ALL,
                Map.of("nome", str("Parte do nome, sem diferenciar acentos"),
                        "codigo", str("Parte do código, ex.: CAM-001"),
                        "categoria_id", integer("Id da categoria (use listar_categorias)"),
                        "tamanho", str("Tamanho exato, ex.: M ou 40"),
                        "situacao", enumOf("Filtro de estoque", "ALL", "AVAILABLE", "OUT_OF_STOCK", "LOW_STOCK", "INACTIVE"),
                        "pagina", integer("Página, começa em 0")),
                List.of(),
                a -> products.search(strArg(a, "nome"), longArg(a, "categoria_id"), strArg(a, "tamanho"),
                        strArg(a, "codigo"), enumArg(a, "situacao", AvailabilityFilter.class),
                        intArg(a, "pagina", 0), 20)));

        add(new Def("detalhe_produto",
                "Ficha de um produto: dados, últimas 10 movimentações de estoque e total vendido.",
                ALL, Map.of("produto_id", integer("Id do produto")), List.of("produto_id"),
                a -> products.detail(requiredLong(a, "produto_id"))));

        add(new Def("listar_categorias", "Lista as categorias com a quantidade de produtos de cada uma.",
                ALL, Map.of(), List.of(), a -> categories.list()));

        add(new Def("buscar_clientes",
                "Busca clientes por parte do nome ou do CPF. Devolve até 20 por página.",
                SALES,
                Map.of("busca", str("Nome ou CPF (com ou sem pontuação)"),
                        "ativo", Map.of("type", "boolean", "description", "true só ativos, false só inativos; omita para todos"),
                        "pagina", integer("Página, começa em 0")),
                List.of(),
                a -> customers.search(strArg(a, "busca"), boolArg(a, "ativo"), intArg(a, "pagina", 0), 20)));

        add(new Def("detalhe_cliente",
                "Ficha do cliente com resumo de compras confirmadas e o histórico de todas as vendas dele.",
                SALES, Map.of("cliente_id", integer("Id do cliente")), List.of("cliente_id"),
                a -> {
                    long id = requiredLong(a, "cliente_id");
                    return Map.of("cliente", customers.detail(id), "historico", sales.historyOf(id));
                }));

        add(new Def("buscar_vendas",
                "Consulta vendas por número, cliente, período (data de abertura) e status. Devolve até 20 por página, "
                        + "da mais recente para a mais antiga.",
                SALES,
                Map.of("venda_id", integer("Número da venda"),
                        "cliente_id", integer("Id do cliente"),
                        "de", date("Data inicial (yyyy-mm-dd)"),
                        "ate", date("Data final (yyyy-mm-dd), inclusive"),
                        "status", enumOf("Status da venda", "DRAFT", "CONFIRMED", "CANCELLED"),
                        "pagina", integer("Página, começa em 0")),
                List.of(),
                a -> sales.search(longArg(a, "venda_id"), longArg(a, "cliente_id"), dateArg(a, "de"),
                        dateArg(a, "ate"), enumArg(a, "status", SaleStatus.class), intArg(a, "pagina", 0), 20)));

        add(new Def("detalhe_venda",
                "Detalhe de uma venda: itens com preço praticado, total, cliente, datas e motivo do cancelamento.",
                SALES, Map.of("venda_id", integer("Número da venda")), List.of("venda_id"),
                a -> sales.detail(requiredLong(a, "venda_id"))));

        add(new Def("alertas_estoque", "Produtos ativos com estoque baixo (até o mínimo) e sem estoque.",
                STOCK, Map.of(), List.of(), a -> stock.alerts()));

        add(new Def("movimentacoes_estoque",
                "Histórico de entradas, ajustes, vendas e estornos de estoque, com saldo após cada movimento.",
                STOCK,
                Map.of("produto_id", integer("Id do produto"),
                        "tipo", enumOf("Tipo", "ENTRY", "ADJUSTMENT", "SALE", "SALE_CANCELLATION"),
                        "de", date("Data inicial (yyyy-mm-dd)"),
                        "ate", date("Data final (yyyy-mm-dd), inclusive"),
                        "pagina", integer("Página, começa em 0")),
                List.of(),
                a -> stock.history(longArg(a, "produto_id"), enumArg(a, "tipo", StockMovementType.class),
                        dateArg(a, "de"), dateArg(a, "ate"), intArg(a, "pagina", 0), 30)));
    }

    // Ferramentas que o papel pode usar, já no formato da API do Claude.
    public List<Tool> toolsFor(String role) {
        List<Tool> tools = new ArrayList<>();
        for (Def d : defs.values()) {
            if (!d.roles().contains(role)) continue;
            var props = Tool.InputSchema.Properties.builder();
            d.properties().forEach((k, v) -> props.putAdditionalProperty(k, JsonValue.from(v)));
            tools.add(Tool.builder()
                    .name(d.name())
                    .description(d.description())
                    .inputSchema(Tool.InputSchema.builder().properties(props.build()).required(d.required()).build())
                    .build());
        }
        return tools;
    }

    public record Result(String content, boolean error) {}

    // Executa e devolve JSON. Erro vira texto para o modelo explicar à pessoa, nunca uma exceção.
    public Result execute(String name, Map<String, Object> args, String role) {
        Def d = defs.get(name);
        if (d == null) return new Result("Ferramenta desconhecida: " + name, true);
        if (!d.roles().contains(role)) {
            return new Result("O perfil de quem pergunta não tem acesso a esta informação.", true);
        }
        try {
            return new Result(json.writeValueAsString(d.run().apply(args == null ? Map.of() : args)), false);
        } catch (NotFoundException | InvalidInputException | IllegalArgumentException e) {
            return new Result(e.getMessage(), true);
        } catch (Exception e) {
            return new Result("Falha ao consultar os dados: " + e.getClass().getSimpleName(), true);
        }
    }

    private void add(Def d) { defs.put(d.name(), d); }

    /* ---- esquema e leitura de argumentos ---- */

    private static Map<String, Object> str(String desc) { return Map.of("type", "string", "description", desc); }
    private static Map<String, Object> integer(String desc) { return Map.of("type", "integer", "description", desc); }
    private static Map<String, Object> date(String desc) {
        return Map.of("type", "string", "format", "date", "description", desc);
    }
    private static Map<String, Object> enumOf(String desc, String... values) {
        return Map.of("type", "string", "enum", List.of(values), "description", desc);
    }

    private static String strArg(Map<String, Object> a, String k) {
        Object v = a.get(k);
        return v == null || v.toString().isBlank() ? null : v.toString();
    }

    private static Long longArg(Map<String, Object> a, String k) {
        Object v = a.get(k);
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try {
            return Long.valueOf(v.toString().trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("O campo " + k + " deve ser um número.");
        }
    }

    private static long requiredLong(Map<String, Object> a, String k) {
        Long v = longArg(a, k);
        if (v == null) throw new IllegalArgumentException("Informe " + k + ".");
        return v;
    }

    private static int intArg(Map<String, Object> a, String k, int def) {
        Long v = longArg(a, k);
        return v == null ? def : v.intValue();
    }

    private static Boolean boolArg(Map<String, Object> a, String k) {
        Object v = a.get(k);
        return v == null ? null : Boolean.valueOf(v.toString());
    }

    private static LocalDate dateArg(Map<String, Object> a, String k) {
        String v = strArg(a, k);
        try {
            return v == null ? null : LocalDate.parse(v);
        } catch (Exception e) {
            throw new IllegalArgumentException("Data inválida em " + k + ": use yyyy-mm-dd.");
        }
    }

    private static <E extends Enum<E>> E enumArg(Map<String, Object> a, String k, Class<E> type) {
        String v = strArg(a, k);
        try {
            return v == null ? null : Enum.valueOf(type, v.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Valor inválido em " + k + ": " + v);
        }
    }
}
