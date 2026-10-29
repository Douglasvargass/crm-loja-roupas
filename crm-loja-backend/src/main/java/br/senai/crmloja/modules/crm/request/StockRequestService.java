package br.senai.crmloja.modules.crm.request;

import br.senai.crmloja.core.audit.AuditService;
import br.senai.crmloja.core.config.AppProperties;
import br.senai.crmloja.core.http.BusinessRuleException;
import br.senai.crmloja.core.http.InvalidInputException;
import br.senai.crmloja.core.http.NotFoundException;
import br.senai.crmloja.core.http.PageResponse;
import br.senai.crmloja.core.rbac.RbacService;
import br.senai.crmloja.modules.crm.customer.CustomerRepository;
import br.senai.crmloja.modules.crm.product.Product;
import br.senai.crmloja.modules.crm.product.ProductRepository;
import br.senai.crmloja.modules.crm.request.StockRequest.Status;
import br.senai.crmloja.modules.crm.request.StockRequest.Via;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Chamados de peça em falta (spec crm/09). Quem atende (gerente, vendedor) abre; quem cuida do estoque (estoquista,
// gerente) resolve. A entrada de estoque da peça resolve sozinha os chamados mais antigos que ela cobre, e quem abriu
// recebe o aviso no sino.
@Service
public class StockRequestService {

    public record Input(Long productId, String description, String size, String color, Integer quantity,
                        Long customerId) {}

    public record Row(Long id, Status status, String description, String size, String color, int quantity,
                      Long productId, String productCode, String productName, Integer productStock,
                      Long customerId, String customerName, Via openedVia, Long openedById, String openedByName,
                      OffsetDateTime createdAt, String closingNote, String closedByName, OffsetDateTime closedAt) {}

    // Sino: para quem cuida do estoque, os chamados abertos; para quem abriu, os que fecharam e ele ainda não viu.
    public record Alerts(String kind, long count, List<Row> items) {}

    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final StockRequestRepository requests;
    private final ProductRepository products;
    private final CustomerRepository customers;
    private final AuditService audit;
    private final NamedParameterJdbcTemplate jdbc;
    private final AppProperties props;

    public StockRequestService(StockRequestRepository requests, ProductRepository products,
                               CustomerRepository customers, AuditService audit, NamedParameterJdbcTemplate jdbc,
                               AppProperties props) {
        this.requests = requests;
        this.products = products;
        this.customers = customers;
        this.audit = audit;
        this.jdbc = jdbc;
        this.props = props;
    }

    // Gerente e estoquista (e a equipe técnica, que vale pelos dois) veem todos; vendedor vê os que abriu.
    public static boolean seesAll(String role) {
        var g = RbacService.grantedRoles(role);
        return g.contains("MANAGER") || g.contains("STOCK_KEEPER");
    }

    // ------------------------------------------------------------------------------------------- abrir

    // REQ-01 a REQ-03.
    @Transactional
    public Row open(Input in, Via via, Long actorId) {
        String description = in.description() == null ? "" : in.description().trim().replaceAll("\\s+", " ");
        if (description.length() < 3 || description.length() > 200) {
            throw new InvalidInputException("invalid_description", "Descreva a peça que o cliente queria (de 3 a 200 letras).");
        }
        int quantity = in.quantity() == null ? 1 : in.quantity();
        if (quantity < 1 || quantity > 99) {
            throw new InvalidInputException("invalid_quantity", "A quantidade deve ser de 1 a 99.");
        }
        String size = blankToNull(in.size(), 10, "Tamanho");
        String color = blankToNull(in.color(), 30, "Cor");
        if (in.productId() != null) {
            Product p = products.findById(in.productId()).orElseThrow(() -> new NotFoundException("Produto não encontrado."));
            // Se a peça está na prateleira, o caminho é vender, não abrir chamado.
            if (p.isActive() && p.getStockQty() >= quantity) {
                throw new BusinessRuleException("product_in_stock", "O produto " + p.getName() + " tem " + p.getStockQty()
                        + " em estoque: dá para vender agora, sem chamado.");
            }
        }
        if (in.customerId() != null && customers.findById(in.customerId()).isEmpty()) {
            throw new NotFoundException("Cliente não encontrado.");
        }
        StockRequest r = requests.saveAndFlush(new StockRequest(in.productId(), description, size, color, quantity,
                in.customerId(), via, actorId));
        Map<String, Object> payload = new HashMap<>();
        payload.put("productId", in.productId());
        payload.put("quantity", quantity);
        payload.put("via", via.name());
        audit.record(actorId, "crm", "stock_request_opened", "stock_request", r.getId(), payload);
        return row(r.getId());
    }

    // ------------------------------------------------------------------------------------------- fechar

    // REQ-05 e REQ-06. Resolver: quem cuida do estoque. Cancelar: quem cuida do estoque ou quem abriu.
    @Transactional
    public Row close(Long id, Status to, String note, Long actorId, String role) {
        if (to == Status.OPEN) throw new InvalidInputException("invalid_status", "Escolha resolver ou cancelar.");
        String n = note == null ? "" : note.trim();
        if (n.length() < 3 || n.length() > 255) {
            throw new InvalidInputException("note_required", to == Status.RESOLVED
                    ? "Conte como foi resolvido (de 3 a 255 letras)." : "Informe o motivo do cancelamento (de 3 a 255 letras).");
        }
        StockRequest r = requests.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Chamado não encontrado."));
        boolean all = seesAll(role);
        boolean mine = actorId != null && actorId.equals(r.getOpenedBy());
        if (!all && !mine) throw new NotFoundException("Chamado não encontrado.");   // não revela chamado de outro
        if (to == Status.RESOLVED && !all) {
            throw new BusinessRuleException("forbidden_resolve", "Quem resolve o chamado é o estoque ou o gerente. Você pode cancelar o seu.");
        }
        if (r.getStatus() != Status.OPEN) {
            throw new BusinessRuleException("request_closed", "Este chamado já foi resolvido ou cancelado.");
        }
        r.close(to, n, actorId, OffsetDateTime.now());
        requests.flush();
        audit.record(actorId, "crm", to == Status.RESOLVED ? "stock_request_resolved" : "stock_request_cancelled",
                "stock_request", id, Map.of("note", n));
        return row(id);
    }

    // REQ-04: chamado pela entrada de estoque, dentro da mesma transação. Atende os chamados abertos da peça, do mais
    // antigo para o mais novo, enquanto a quantidade que entrou cobrir o pedido de cada um.
    @Transactional
    public int onStockEntry(Long productId, int quantity, Long actorId) {
        int restante = quantity;
        int resolvidos = 0;
        OffsetDateTime agora = OffsetDateTime.now();
        String nota = "A peça chegou: entrada de " + quantity + " un. em " + agora.atZoneSameInstant(props.zone()).format(DIA) + ".";
        for (StockRequest r : requests.findByProductIdAndStatusOrderByIdAsc(productId, Status.OPEN)) {
            if (r.getQuantity() > restante) break;
            restante -= r.getQuantity();
            r.close(Status.RESOLVED, nota, actorId, agora);
            audit.record(actorId, "crm", "stock_request_resolved", "stock_request", r.getId(), Map.of("by", "stock_entry"));
            resolvidos++;
        }
        return resolvidos;
    }

    // ------------------------------------------------------------------------------------------- consultar

    @Transactional(readOnly = true)
    public PageResponse<Row> search(Status status, Long actorId, String role, int page, int size) {
        int p = Math.max(page, 0);
        int s = Math.min(Math.max(size, 1), 100);
        var params = new MapSqlParameterSource().addValue("limit", s).addValue("offset", p * s);
        String where = " where 1 = 1";
        if (status != null) {
            where += " and r.status = :status";
            params.addValue("status", status.name());
        }
        if (!seesAll(role)) {
            where += " and r.opened_by = :me";
            params.addValue("me", actorId);
        }
        Long total = jdbc.queryForObject("select count(*) from stock_request r" + where, params, Long.class);
        List<Row> rows = jdbc.query(SELECT + where + " order by r.status = 'OPEN' desc, r.created_at desc, r.id desc"
                + " limit :limit offset :offset", params, (rs, i) -> map(rs));
        return PageResponse.of(new PageImpl<>(rows, PageRequest.of(p, s), total == null ? 0 : total), x -> x);
    }

    @Transactional(readOnly = true)
    public Alerts alerts(Long actorId, String role) {
        var params = new MapSqlParameterSource().addValue("me", actorId);
        if (seesAll(role)) {
            Long n = jdbc.queryForObject("select count(*) from stock_request where status = 'OPEN'", params, Long.class);
            List<Row> items = jdbc.query(SELECT + " where r.status = 'OPEN' order by r.created_at desc limit 5",
                    params, (rs, i) -> map(rs));
            return new Alerts("OPEN_REQUESTS", n == null ? 0 : n, items);
        }
        String mine = " where r.opened_by = :me and r.status <> 'OPEN' and r.requester_seen_at is null";
        Long n = jdbc.queryForObject("select count(*) from stock_request r" + mine, params, Long.class);
        List<Row> items = jdbc.query(SELECT + mine + " order by r.closed_at desc limit 5", params, (rs, i) -> map(rs));
        return new Alerts("MY_UPDATES", n == null ? 0 : n, items);
    }

    // Quem abriu viu os avisos de fechamento: o sino zera para ele.
    @Transactional
    public int markSeen(Long actorId) {
        var list = requests.findByOpenedByAndStatusNotAndRequesterSeenAtIsNull(actorId, Status.OPEN);
        OffsetDateTime agora = OffsetDateTime.now();
        list.forEach(r -> r.markSeen(agora));
        return list.size();
    }

    private Row row(Long id) {
        return jdbc.queryForObject(SELECT + " where r.id = :id", new MapSqlParameterSource("id", id), (rs, i) -> map(rs));
    }

    private static final String SELECT = """
            select r.id, r.status, r.description, r.size, r.color, r.quantity, r.product_id, p.code as product_code,
                   p.name as product_name, p.stock_qty as product_stock, r.customer_id, c.full_name as customer_name,
                   r.opened_via, r.opened_by, uo.full_name as opened_by_name, r.created_at, r.closing_note,
                   uc.full_name as closed_by_name, r.closed_at
              from stock_request r
              left join product p   on p.id = r.product_id
              left join customer c  on c.id = r.customer_id
              left join app_user uo on uo.id = r.opened_by
              left join app_user uc on uc.id = r.closed_by""";

    private static Row map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Row(rs.getLong("id"), Status.valueOf(rs.getString("status")), rs.getString("description"),
                rs.getString("size"), rs.getString("color"), rs.getInt("quantity"),
                (Long) rs.getObject("product_id", Long.class), rs.getString("product_code"), rs.getString("product_name"),
                (Integer) rs.getObject("product_stock", Integer.class),
                (Long) rs.getObject("customer_id", Long.class), rs.getString("customer_name"),
                Via.valueOf(rs.getString("opened_via")), (Long) rs.getObject("opened_by", Long.class),
                rs.getString("opened_by_name"), rs.getObject("created_at", OffsetDateTime.class),
                rs.getString("closing_note"), rs.getString("closed_by_name"), rs.getObject("closed_at", OffsetDateTime.class));
    }

    private static String blankToNull(String v, int max, String label) {
        if (v == null || v.isBlank()) return null;
        String t = v.trim();
        if (t.length() > max) throw new InvalidInputException("invalid_field", label + ": no máximo " + max + " letras.");
        return t;
    }
}
