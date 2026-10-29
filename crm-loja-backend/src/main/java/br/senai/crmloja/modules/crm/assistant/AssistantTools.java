package br.senai.crmloja.modules.crm.assistant;

import br.senai.crmloja.core.http.BusinessRuleException;
import br.senai.crmloja.core.http.InvalidInputException;
import br.senai.crmloja.core.rbac.RbacService;
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
import br.senai.crmloja.modules.crm.request.StockRequest;
import br.senai.crmloja.modules.crm.request.StockRequestService;
import br.senai.crmloja.modules.crm.target.TargetService;
import br.senai.crmloja.modules.crm.forecast.ForecastService;
import br.senai.crmloja.modules.crm.planning.Coverage;
import br.senai.crmloja.modules.crm.planning.StockPlanningService;
import br.senai.crmloja.modules.crm.purchase.PurchaseOrderService;
import br.senai.crmloja.modules.crm.store.StoreService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

// Ferramentas que o assistente pode chamar. Todas só LEEM dados, pelos mesmos Services da API,
// e cada uma exige os mesmos papéis da rota equivalente: o assistente nunca mostra a alguém
// algo que a tela não mostraria. A definição é neutra (JSON Schema); cada LlmProvider converte.
@Component
public class AssistantTools {

    // Definição neutra de uma ferramenta: nome, descrição e parâmetros em JSON Schema.
    public record ToolDef(String name, String description, Map<String, Object> properties, List<String> required) {}

    // `me` e `papel` são de quem pergunta: as ferramentas "meu/minha" (ex.: meu_painel) e a visibilidade dos
    // chamados usam só eles, nunca um argumento vindo do modelo.
    @FunctionalInterface
    private interface Run { Object apply(Map<String, Object> args, Long me, String papel); }

    private record Def(String name, String description, Set<String> roles, Map<String, Object> properties,
                       List<String> required, Run run) {}

    private static final Set<String> ALL = Set.of("MANAGER", "SELLER", "STOCK_KEEPER");
    private static final Set<String> MANAGER = Set.of("MANAGER");
    private static final Set<String> SALES = Set.of("MANAGER", "SELLER");
    private static final Set<String> STOCK = Set.of("MANAGER", "STOCK_KEEPER");
    private static final Set<String> SELLER_ONLY = Set.of("SELLER");
    // Cliente da loja (Thalita do cliente): nenhum papel da equipe recebe estas, e o cliente não recebe nenhuma da equipe.
    private static final Set<String> CUSTOMER = Set.of("CUSTOMER");

    private final Map<String, Def> defs = new LinkedHashMap<>();
    private final ObjectMapper json;

    public AssistantTools(DashboardService dashboard, ProductService products, CategoryService categories,
                          CustomerService customers, SaleService sales, StockService stock, TargetService targets,
                          StockRequestService requests, StockPlanningService planning, PurchaseOrderService purchases,
                          StoreService store, ForecastService forecast, ObjectMapper json) {
        this.json = json;

        add(new Def("consultar_dashboard",
                "Indicadores da loja num período: total de clientes e produtos, quantidade e valor das vendas "
                        + "confirmadas, ticket médio, canceladas, comparação com o período anterior, 5 mais vendidos, "
                        + "faturamento por dia, vendas por categoria, estoque baixo e zerado. Sem datas = mês atual até hoje. "
                        + "ATENÇÃO: previousPeriod é a janela de mesma duração logo antes do período (não o mês anterior "
                        + "inteiro); cite suas datas (previousPeriod.from/to). Para o mês anterior fechado, chame de novo "
                        + "com de = primeiro dia e ate = último dia daquele mês. "
                        + "Para ver as vendas de UM vendedor, passe vendedor_id: a resposta traz a lista `sellers` "
                        + "(id e nome de cada vendedor); sem vendedor_id, os números são da loja inteira.",
                MANAGER,
                Map.of("de", date("Data inicial (yyyy-mm-dd)"), "ate", date("Data final (yyyy-mm-dd), inclusive"),
                        "vendedor_id", integer("Id do vendedor (ver `sellers` numa resposta anterior)")),
                List.of(),
                (a, me, papel) -> dashboard.build(dateArg(a, "de"), dateArg(a, "ate"), longArg(a, "vendedor_id"))));

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
                (a, me, papel) -> products.search(strArg(a, "nome"), longArg(a, "categoria_id"), strArg(a, "tamanho"),
                        strArg(a, "codigo"), enumArg(a, "situacao", AvailabilityFilter.class),
                        intArg(a, "pagina", 0), 20)));

        add(new Def("detalhe_produto",
                "Ficha de um produto: dados, últimas 10 movimentações de estoque e total vendido.",
                ALL, Map.of("produto_id", integer("Id do produto")), List.of("produto_id"),
                (a, me, papel) -> products.detail(requiredLong(a, "produto_id"))));

        add(new Def("listar_categorias", "Lista as categorias com a quantidade de produtos de cada uma.",
                ALL, Map.of(), List.of(), (a, me, papel) -> categories.list()));

        add(new Def("buscar_clientes",
                "Busca clientes por parte do nome ou do CPF. Devolve até 20 por página.",
                SALES,
                Map.of("busca", str("Nome ou CPF (com ou sem pontuação)"),
                        "ativo", Map.of("type", "boolean", "description", "true só ativos, false só inativos; omita para todos"),
                        "pagina", integer("Página, começa em 0")),
                List.of(),
                (a, me, papel) -> customers.search(strArg(a, "busca"), boolArg(a, "ativo"), intArg(a, "pagina", 0), 20)));

        add(new Def("detalhe_cliente",
                "Ficha do cliente com resumo de compras confirmadas e o histórico de todas as vendas dele.",
                SALES, Map.of("cliente_id", integer("Id do cliente")), List.of("cliente_id"),
                (a, me, papel) -> {
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
                (a, me, papel) -> sales.search(longArg(a, "venda_id"), longArg(a, "cliente_id"), dateArg(a, "de"),
                        dateArg(a, "ate"), enumArg(a, "status", SaleStatus.class), intArg(a, "pagina", 0), 20)));

        add(new Def("detalhe_venda",
                "Detalhe de uma venda: itens com preço praticado, total, cliente, datas e motivo do cancelamento.",
                SALES, Map.of("venda_id", integer("Número da venda")), List.of("venda_id"),
                (a, me, papel) -> sales.detail(requiredLong(a, "venda_id"))));

        add(new Def("alertas_estoque", "Produtos ativos com estoque baixo (até o mínimo) e sem estoque.",
                STOCK, Map.of(), List.of(), (a, me, papel) -> stock.alerts()));

        add(new Def("movimentacoes_estoque",
                "Histórico de entradas, ajustes, vendas e estornos de estoque, com saldo após cada movimento.",
                STOCK,
                Map.of("produto_id", integer("Id do produto"),
                        "tipo", enumOf("Tipo", "ENTRY", "ADJUSTMENT", "SALE", "SALE_CANCELLATION"),
                        "de", date("Data inicial (yyyy-mm-dd)"),
                        "ate", date("Data final (yyyy-mm-dd), inclusive"),
                        "pagina", integer("Página, começa em 0")),
                List.of(),
                (a, me, papel) -> stock.history(longArg(a, "produto_id"), enumArg(a, "tipo", StockMovementType.class),
                        dateArg(a, "de"), dateArg(a, "ate"), intArg(a, "pagina", 0), 30)));

        // Spec crm/06. O painel é sempre de quem pergunta: não há argumento para escolher outro vendedor.
        add(new Def("meu_painel",
                "Painel de quem pergunta (vendedor): meta do mês, quanto já vendeu, % da meta, quanto falta, média por "
                        + "dia útil (segunda a sábado), projeção de fechamento, quanto precisa vender por dia útil daqui "
                        + "para a frente, vendas de hoje, os mais vendidos e as últimas vendas. Só os números da própria "
                        + "pessoa, nunca da loja nem de outros vendedores. Sem mes = mês atual. status: ACHIEVED bateu, "
                        + "ON_TRACK no ritmo, ATTENTION atenção (projeção entre 80% e 100%), BEHIND abaixo do ritmo, "
                        + "MISSED não bateu (mês fechado), NO_TARGET sem meta, NOT_STARTED mês sem dia útil ainda.",
                SELLER_ONLY, Map.of("mes", str("Mês no formato aaaa-mm (ex.: 2026-10)")), List.of(),
                (a, me, papel) -> {
                    if (me == null) throw new IllegalArgumentException("Não consegui identificar quem está perguntando.");
                    return targets.panel(me, targets.parseMonth(strArg(a, "mes")));
                }));

        add(new Def("metas_vendedores",
                "Quadro de metas do mês para o gerente: cada vendedor ativo com meta, realizado, % da meta, projeção, "
                        + "quanto falta e situação (mesmos status de meu_painel). Sem mes = mês atual.",
                MANAGER, Map.of("mes", str("Mês no formato aaaa-mm (ex.: 2026-10)")), List.of(),
                (a, me, papel) -> targets.board(targets.parseMonth(strArg(a, "mes")))));

        // Spec crm/12. A mesma conta da tela Previsão de vendas; os dias saem da resposta para caber na conversa.
        add(new Def("previsao_vendas",
                "Previsão de faturamento e de número de vendas da loja: próximos 7 e 30 dias, fechamento do mês atual "
                        + "(realized = o que já vendeu até ontem) e os próximos `meses` meses, cada um com a faixa provável "
                        + "(low a high, ~80%). model explica os fatores: weekday (dia da semana), payday (semana do salário), "
                        + "holiday (feriado), events (datas comerciais; learned=false é estimativa padrão do varejo), "
                        + "monthSeasonality (só com 1 ano de histórico) e accuracy (erro médio por semana nas últimas 4). "
                        + "Diga sempre que é previsão, cite a faixa e o que pesou.",
                MANAGER, Map.of("meses", integer("Quantos meses à frente, de 1 a 12 (padrão 3)")), List.of(),
                (a, me, papel) -> {
                    var f = forecast.forecast(a.get("meses") == null ? null : intArg(a, "meses", 3));
                    return Map.of("hoje", f.today(), "historicoDesde", f.historyFrom(), "diasDeHistorico", f.historyDays(),
                            "periodos", f.periods(), "modelo", f.model());
                }));

        add(new Def("sugestao_metas",
                "Meta sugerida para cada vendedor num mês: previsão do mês da loja × parte do vendedor no faturamento da "
                        + "loja nos últimos 90 dias × (1 + crescimento%), arredondada para a centena. suggested=null quando o "
                        + "vendedor não vendeu nos 90 dias (reason explica). currentTarget é a meta já definida. Você NÃO "
                        + "grava metas: oriente o gerente a usar o botão Usar ou Aplicar a todos na tela Metas.",
                MANAGER,
                Map.of("mes", str("Mês no formato aaaa-mm (padrão: mês atual)"),
                        "crescimento", integer("Crescimento em %, de -20 a 50 (padrão 0)")),
                List.of(),
                (a, me, papel) -> forecast.suggestTargets(targets.parseMonth(strArg(a, "mes")),
                        a.get("crescimento") == null ? null : intArg(a, "crescimento", 0))));

        // Spec crm/09. A ÚNICA ferramenta que grava: só cria um chamado (que pode ser cancelado), e só depois de a
        // pessoa confirmar o resumo. Sem confirmado=true, devolve erro e nada é gravado.
        add(new Def("abrir_chamado",
                "Abre um chamado de peça em falta: o cliente queria uma peça que acabou ou que a loja não tem; o estoque "
                        + "e o gerente são avisados, e quem abriu é avisado quando resolver. ANTES de chamar com "
                        + "confirmado=true, mostre o resumo (peça, tamanho, cor, quantidade, cliente) e pergunte "
                        + "\"Posso abrir o chamado?\". Se o produto tiver estoque, o sistema recusa: oriente a vender.",
                SALES,
                Map.of("descricao", str("O que o cliente queria, ex.: Calça jeans skinny 44 azul"),
                        "produto_id", integer("Id do produto, se ele existir no catálogo (use buscar_produtos)"),
                        "cliente_id", integer("Id do cliente, se identificado (use buscar_clientes)"),
                        "tamanho", str("Tamanho, ex.: 44"),
                        "cor", str("Cor"),
                        "quantidade", integer("Quantidade, de 1 a 99 (padrão 1)"),
                        "confirmado", Map.of("type", "boolean", "description", "true só depois de a pessoa confirmar o resumo")),
                List.of("descricao", "confirmado"),
                (a, me, papel) -> {
                    if (!Boolean.TRUE.equals(boolArg(a, "confirmado"))) {
                        throw new IllegalArgumentException("Ainda não confirmado: mostre o resumo e pergunte \"Posso abrir o chamado?\".");
                    }
                    if (me == null) throw new IllegalArgumentException("Não consegui identificar quem está pedindo.");
                    return requests.open(new StockRequestService.Input(longArg(a, "produto_id"), strArg(a, "descricao"),
                            strArg(a, "tamanho"), strArg(a, "cor"), (int) (long) longOr(a, "quantidade", 1L),
                            longArg(a, "cliente_id")), StockRequest.Via.ASSISTANT, me);
                }));

        add(new Def("listar_chamados",
                "Chamados de peça em falta. Gerente e estoquista veem todos; vendedor vê os que ele abriu. "
                        + "status: OPEN aberto, RESOLVED resolvido, CANCELLED cancelado; sem status = todos.",
                ALL,
                Map.of("status", enumOf("Situação", "OPEN", "RESOLVED", "CANCELLED"),
                        "pagina", integer("Página, começa em 0")),
                List.of(),
                (a, me, papel) -> requests.search(enumArg(a, "status", StockRequest.Status.class), me, papel,
                        intArg(a, "pagina", 0), 20)));

        // Spec crm/11. A resposta traz o resumo e só as peças que pedem atenção (até 20), para caber na conversa.
        add(new Def("previsao_estoque",
                "O que vai faltar: para cada peça, média de vendas por dia (últimos 30 dias), em quantos dias acaba "
                        + "(daysLeft) e em que data (runOutDate), o que já está a caminho em pedido de compra (incoming, "
                        + "nextArrival), chamados abertos e quanto pedir (suggestedQty) para cobrir `dias` dias. "
                        + "status: OUT zerado, CRITICAL acaba em até 7 dias ou está no mínimo, ATTENTION até 21 dias, "
                        + "STALE parado (tem estoque e não vendeu em 30 dias). covered = o pedido a caminho já resolve; "
                        + "arrivesAfterRunOut = o pedido chega depois de acabar. Passe produto_id para ver uma peça só.",
                STOCK,
                Map.of("dias", integer("Quantos dias o pedido deve cobrir, de 7 a 90 (padrão 30)"),
                        "produto_id", integer("Id de uma peça (opcional)")),
                List.of(),
                (a, me, papel) -> {
                    var plan = planning.build(a.get("dias") == null ? null : intArg(a, "dias", 30));
                    Long only = longArg(a, "produto_id");
                    var items = plan.items().stream()
                            .filter(i -> only != null ? i.productId().equals(only)
                                    : i.status() != Coverage.Status.OK)
                            .limit(only != null ? 1 : 20).toList();
                    return Map.of("hoje", plan.today(), "diasCobertos", plan.horizonDays(), "resumo", plan.summary(),
                            "pecas", items);
                }));

        add(new Def("pedidos_compra",
                "Pedidos de compra ao fornecedor: fornecedor, data prevista, peças, quantidades e nº da nota fiscal. status: "
                        + "OPEN pedido feito, IN_TRANSIT em trânsito (saiu do fornecedor), CHECKING em conferência (chegou na "
                        + "loja), RECEIVED entrada feita, CANCELLED cancelado; sem status = todos. atrasados=true traz só os "
                        + "que ainda não chegaram com a data já vencida (late).",
                STOCK,
                Map.of("status", enumOf("Situação", "OPEN", "IN_TRANSIT", "CHECKING", "RECEIVED", "CANCELLED"),
                        "atrasados", Map.of("type", "boolean", "description", "true = só os atrasados"),
                        "pagina", integer("Página, começa em 0")),
                List.of(),
                (a, me, papel) -> purchases.search(enumArg(a, "status", PurchaseOrderService.Status.class),
                        Boolean.TRUE.equals(boolArg(a, "atrasados")), intArg(a, "pagina", 0), 20)));

        // ---------------------------------------------------------------------------- Thalita do cliente
        // Tudo pelo cliente da SESSÃO (me): nenhuma ferramenta aceita id de cliente vindo do modelo.
        add(new Def("vitrine",
                "Peças à venda agora na loja online: nome, categoria, tamanho, cor, preço e se são as últimas unidades "
                        + "(lastUnits). Só aparece o que tem em estoque; a quantidade exata não é informada ao cliente.",
                CUSTOMER,
                Map.of("nome", str("Parte do nome, ex.: camiseta"), "categoria_id", integer("Id da categoria (use categorias_loja)"),
                        "tamanho", str("Tamanho exato, ex.: M ou 40"), "pagina", integer("Página, começa em 0")),
                List.of(),
                (a, me, papel) -> store.catalog(strArg(a, "nome"), longArg(a, "categoria_id"), strArg(a, "tamanho"),
                        intArg(a, "pagina", 0))));

        add(new Def("categorias_loja", "Categorias da loja online (id e nome).", CUSTOMER, Map.of(), List.of(),
                (a, me, papel) -> store.categories()));

        add(new Def("meus_pedidos",
                "Os pedidos de quem pergunta (online e na loja): número, data, situação, total, forma de pagamento e se "
                        + "foi estornado.",
                CUSTOMER, Map.of(), List.of(), (a, me, papel) -> store.orders(me)));

        add(new Def("detalhe_meu_pedido", "Itens, preços e pagamento de UM pedido de quem pergunta.", CUSTOMER,
                Map.of("pedido_id", integer("Número do pedido")), List.of("pedido_id"),
                (a, me, papel) -> store.order(me, requiredLong(a, "pedido_id"))));

        add(new Def("minhas_solicitacoes",
                "Peças que quem pergunta pediu à loja e a situação: OPEN aberta, RESOLVED resolvida (a peça chegou), "
                        + "CANCELLED cancelada, com a nota da loja.",
                CUSTOMER, Map.of(), List.of(), (a, me, papel) -> requests.search(null, me, "CUSTOMER", 0, 20)));

        // A única que grava para o cliente: uma solicitação de peça, só depois de ele confirmar o resumo.
        add(new Def("pedir_peca",
                "Pede à loja uma peça que não está na vitrine (acabou ou a loja não tem). O gerente e o estoque são "
                        + "avisados e o cliente acompanha em Meus pedidos. ANTES de chamar com confirmado=true, mostre o "
                        + "resumo (peça, tamanho, cor, quantidade) e pergunte \"Posso fazer o pedido da peça?\". Se a peça "
                        + "estiver na vitrine com estoque, o sistema recusa: oriente a comprar pela vitrine.",
                CUSTOMER,
                Map.of("descricao", str("A peça que o cliente procura, ex.: Vestido longo verde"),
                        "tamanho", str("Tamanho"), "cor", str("Cor"),
                        "quantidade", integer("Quantidade, de 1 a 99 (padrão 1)"),
                        "confirmado", Map.of("type", "boolean", "description", "true só depois de o cliente confirmar o resumo")),
                List.of("descricao", "confirmado"),
                (a, me, papel) -> {
                    if (!Boolean.TRUE.equals(boolArg(a, "confirmado"))) {
                        throw new IllegalArgumentException("Ainda não confirmado: mostre o resumo e pergunte \"Posso fazer o pedido da peça?\".");
                    }
                    if (me == null) throw new IllegalArgumentException("Não consegui identificar quem está pedindo.");
                    return requests.open(new StockRequestService.Input(null, strArg(a, "descricao"), strArg(a, "tamanho"),
                            strArg(a, "cor"), (int) (long) longOr(a, "quantidade", 1L), store.customerIdOf(me)),
                            StockRequest.Via.ASSISTANT, me);
                }));
    }

    // Ferramentas que o papel pode usar (um papel técnico vale por gerente, vendedor e estoquista).
    public List<ToolDef> definitionsFor(String role) {
        return defs.values().stream()
                .filter(d -> allowed(d, role))
                .map(d -> new ToolDef(d.name(), d.description(), d.properties(), d.required()))
                .toList();
    }

    public record Result(String content, boolean error) {}

    public Result execute(String name, Map<String, Object> args, String role) {
        return execute(name, args, role, null);
    }

    // Executa e devolve JSON. Erro vira texto para o modelo explicar à pessoa, nunca uma exceção.
    public Result execute(String name, Map<String, Object> args, String role, Long userId) {
        Def d = defs.get(name);
        if (d == null) return new Result("Ferramenta desconhecida: " + name, true);
        if (!allowed(d, role)) {
            return new Result("O perfil de quem pergunta não tem acesso a esta informação.", true);
        }
        try {
            return new Result(json.writeValueAsString(d.run().apply(args == null ? Map.of() : args, userId, role)), false);
        } catch (NotFoundException | InvalidInputException | BusinessRuleException | IllegalArgumentException e) {
            return new Result(e.getMessage(), true);
        } catch (Exception e) {
            return new Result("Falha ao consultar os dados: " + e.getClass().getSimpleName(), true);
        }
    }

    private void add(Def d) { defs.put(d.name(), d); }

    private static boolean allowed(Def d, String role) {
        return RbacService.grantedRoles(role).stream().anyMatch(d.roles()::contains);
    }

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

    private static Long longOr(Map<String, Object> a, String k, Long def) {
        Long v = longArg(a, k);
        return v == null ? def : v;
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
