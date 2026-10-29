package br.senai.crmloja.modules.crm.purchase;

import br.senai.crmloja.core.audit.AuditService;
import br.senai.crmloja.core.config.AppProperties;
import br.senai.crmloja.core.http.BusinessRuleException;
import br.senai.crmloja.core.http.InvalidInputException;
import br.senai.crmloja.core.http.NotFoundException;
import br.senai.crmloja.core.http.PageResponse;
import br.senai.crmloja.modules.crm.stock.StockService;
import jakarta.persistence.EntityManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Pedidos de compra ao fornecedor (spec crm/11). Quem compra (Operações e Compras, gerente) registra, avisa que saiu do
// fornecedor, muda a data e cancela; quem recebe a mercadoria (estoque, gerente) marca que chegou e, depois de conferir,
// dá entrada com o número da nota fiscal. Etapas (V11): pedido feito → em trânsito → em conferência → recebido.
//
// Feito com SQL direto (sem entidade JPA): o pedido e os itens são gravados juntos e a leitura sempre precisa do nome
// das peças e das pessoas, o mesmo caminho do StockRequestService.
@Service
public class PurchaseOrderService {

    // OPEN = pedido feito; IN_TRANSIT = saiu do fornecedor; CHECKING = chegou e está em conferência. Os três contam como
    // "a caminho" na Reposição. RECEIVED e CANCELLED fecham o pedido.
    public enum Status {
        OPEN, IN_TRANSIT, CHECKING, RECEIVED, CANCELLED;

        public boolean isOpen() { return this == OPEN || this == IN_TRANSIT || this == CHECKING; }
    }

    // Mesma lista em SQL, para as consultas (aqui e no StockPlanningService).
    public static final String OPEN_SQL = "('OPEN', 'IN_TRANSIT', 'CHECKING')";

    public record ItemInput(Long productId, Integer quantity) {}

    public record Input(String supplier, LocalDate expectedDate, String note, List<ItemInput> items) {}

    // receivedQuantity: contado na conferência (nulo até a entrada).
    public record Item(Long productId, String productCode, String productName, String size, String color,
                       int quantity, int stockNow, Integer receivedQuantity) {}

    public record ReceivedItem(Long productId, Integer receivedQuantity) {}

    // late = ainda não chegou (pedido feito ou em trânsito) e a data prometida já passou (calculado na leitura).
    public record Order(Long id, String supplier, LocalDate expectedDate, Status status, boolean late, String note,
                        int rescheduleCount, String createdByName, OffsetDateTime createdAt, String closingNote,
                        String closedByName, OffsetDateTime closedAt, String invoiceNumber, OffsetDateTime shippedAt,
                        OffsetDateTime arrivedAt, String bonusInvoiceNumber, int returnCount, int totalUnits,
                        List<Item> items) {}

    // Resposta do recebimento: o pedido, quantos chamados de peça em falta a entrada fechou e o extra da bonificação.
    public record Received(Order order, int requestsResolved, int bonusUnits) {}

    static final int MAX_ITEMS = 30;
    static final int MAX_DAYS_AHEAD = 180;

    private final NamedParameterJdbcTemplate jdbc;
    private final StockService stock;
    private final AuditService audit;
    private final AppProperties props;
    private final EntityManager em;

    public PurchaseOrderService(NamedParameterJdbcTemplate jdbc, StockService stock, AuditService audit,
                                AppProperties props, EntityManager em) {
        this.jdbc = jdbc;
        this.stock = stock;
        this.audit = audit;
        this.props = props;
        this.em = em;
    }

    // ------------------------------------------------------------------------------------------- registrar

    // PC-01: fornecedor, data prevista (hoje até 180 dias) e de 1 a 30 peças diferentes, cada uma de 1 a 9999 un.
    @Transactional
    public Order create(Input in, Long actorId) {
        String supplier = clean(in.supplier());
        if (supplier.length() < 2 || supplier.length() > 80) {
            throw new InvalidInputException("invalid_supplier", "Informe o fornecedor (de 2 a 80 letras).");
        }
        LocalDate expected = checkDate(in.expectedDate());
        String note = optional(in.note(), "Observação");
        List<ItemInput> items = in.items() == null ? List.of() : in.items();
        if (items.isEmpty()) throw new InvalidInputException("items_required", "Inclua ao menos uma peça no pedido.");
        if (items.size() > MAX_ITEMS) {
            throw new InvalidInputException("too_many_items", "No máximo " + MAX_ITEMS + " peças diferentes por pedido.");
        }
        Set<Long> seen = new HashSet<>();
        for (ItemInput it : items) {
            if (it.productId() == null) throw new InvalidInputException("product_required", "Escolha a peça de cada linha.");
            if (!seen.add(it.productId())) {
                throw new InvalidInputException("duplicated_product", "A mesma peça aparece duas vezes: junte numa linha só.");
            }
            if (it.quantity() == null || it.quantity() < 1 || it.quantity() > 9999) {
                throw new InvalidInputException("invalid_quantity", "A quantidade de cada peça deve ser de 1 a 9999.");
            }
            Boolean active = jdbc.query("select is_active from product where id = :id",
                    new MapSqlParameterSource("id", it.productId()), rs -> rs.next() ? rs.getBoolean(1) : null);
            if (active == null) throw new NotFoundException("Produto não encontrado.");
            if (!active) {
                throw new BusinessRuleException("product_inactive", "Uma das peças está desativada: reative o produto antes de pedir.");
            }
        }
        var key = new GeneratedKeyHolder();
        jdbc.update("""
                insert into purchase_order (supplier, expected_date, note, created_by)
                values (:supplier, :expected, :note, :actor)""",
                new MapSqlParameterSource().addValue("supplier", supplier).addValue("expected", expected)
                        .addValue("note", note).addValue("actor", actorId), key, new String[] {"id"});
        long id = key.getKey().longValue();
        for (ItemInput it : items) {
            jdbc.update("insert into purchase_order_item (order_id, product_id, quantity) values (:o, :p, :q)",
                    new MapSqlParameterSource().addValue("o", id).addValue("p", it.productId()).addValue("q", it.quantity()));
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("supplier", supplier);
        payload.put("expectedDate", expected.toString());
        payload.put("items", items.size());
        audit.record(actorId, "crm", "purchase_order_created", "purchase_order", id, payload);
        return get(id);
    }

    // ------------------------------------------------------------------------------------------- receber

    // PC-06: o fornecedor despachou. Pode já informar a nota fiscal (fica guardada para a entrada).
    @Transactional
    public Order ship(Long id, String invoiceNumber, Long actorId) {
        Order o = lock(id, Set.of(Status.OPEN), "Este pedido já saiu do fornecedor ou já chegou.");
        String nf = invoice(invoiceNumber, o.invoiceNumber(), false);
        jdbc.update("""
                update purchase_order set status = 'IN_TRANSIT', shipped_at = now(), invoice_number = :nf, updated_at = now()
                 where id = :id""", new MapSqlParameterSource().addValue("nf", nf).addValue("id", id));
        Map<String, Object> payload = new HashMap<>();
        if (nf != null) payload.put("invoiceNumber", nf);
        audit.record(actorId, "crm", "purchase_order_shipped", "purchase_order", id, payload);
        return get(id);
    }

    // PC-07: a mercadoria chegou na loja e vai ser conferida antes de entrar no estoque.
    @Transactional
    public Order arrive(Long id, Long actorId) {
        lock(id, Set.of(Status.OPEN, Status.IN_TRANSIT), "Este pedido já está em conferência.");
        jdbc.update("update purchase_order set status = 'CHECKING', arrived_at = now(), updated_at = now() where id = :id",
                new MapSqlParameterSource("id", id));
        audit.record(actorId, "crm", "purchase_order_arrived", "purchase_order", id, Map.of());
        return get(id);
    }

    // PC-03 e PC-08 (conferência, regra de 29/10/2026): o que chegou é contado peça por peça.
    //  - chegou a menos em qualquer peça → NÃO dá entrada: a nota volta ao fornecedor (returnDelivery);
    //  - chegou a mais → o pedido entra com a nota fiscal e o extra entra com a NOTA DE BONIFICAÇÃO (obrigatória);
    //  - bateu → entrada normal.
    // Cada peça entra pelo caminho de sempre (StockService.entry), que também fecha os chamados de peça em falta que
    // a entrada cobre. Tudo na mesma transação: ou entra tudo, ou nada. A nota fiscal é obrigatória (a do envio vale).
    @Transactional
    public Received receive(Long id, String invoiceNumber, String bonusInvoiceNumber, String note,
                            List<ReceivedItem> counted, Long actorId) {
        Order o = lock(id, Set.of(Status.OPEN, Status.IN_TRANSIT, Status.CHECKING), "Este pedido já foi recebido ou cancelado.");
        String nf = invoice(invoiceNumber, o.invoiceNumber(), true);
        String n = optional(note, "Observação");
        Map<Long, Integer> received = new HashMap<>();
        for (ReceivedItem r : counted == null ? List.<ReceivedItem>of() : counted) {
            if (r.productId() == null || r.receivedQuantity() == null) continue;
            if (r.receivedQuantity() < 0 || r.receivedQuantity() > 99999) {
                throw new InvalidInputException("invalid_quantity", "Quantidade conferida inválida.");
            }
            received.put(r.productId(), r.receivedQuantity());
        }
        List<String> faltou = new ArrayList<>();
        int bonusUnits = 0;
        for (Item it : o.items()) {
            int got = received.getOrDefault(it.productId(), it.quantity());   // sem contagem informada = bateu
            if (got < it.quantity()) faltou.add(it.productName() + " (" + got + " de " + it.quantity() + ")");
            bonusUnits += Math.max(0, got - it.quantity());
        }
        if (!faltou.isEmpty()) {
            throw new BusinessRuleException("short_delivery", "Chegou menos do que o pedido: " + String.join(", ", faltou)
                    + ". Devolva a nota ao fornecedor (botão \"Devolver a nota\"); nada entra no estoque até ele corrigir.");
        }
        String bonusNf = clean(bonusInvoiceNumber);
        if (bonusNf.length() > 30) throw new InvalidInputException("invalid_invoice", "Nº da nota de bonificação: no máximo 30 caracteres.");
        if (bonusUnits > 0 && bonusNf.isEmpty()) {
            throw new InvalidInputException("bonus_invoice_required", "Chegou " + bonusUnits
                    + " un. a mais do que o pedido: informe o número da nota de bonificação para o extra entrar.");
        }
        if (bonusUnits == 0) bonusNf = "";   // sem extra, não guarda nota de bonificação
        int openBefore = openRequests(o.items());
        String reason = "Pedido de compra nº " + id + " · " + o.supplier() + " · NF " + nf;
        String bonusReason = "Bonificação · pedido de compra nº " + id + " · " + o.supplier() + " · NF " + bonusNf;
        for (Item it : o.items()) {
            int got = received.getOrDefault(it.productId(), it.quantity());
            stock.entry(it.productId(), it.quantity(), reason.length() > 255 ? reason.substring(0, 255) : reason, actorId);
            if (got > it.quantity()) {
                stock.entry(it.productId(), got - it.quantity(),
                        bonusReason.length() > 255 ? bonusReason.substring(0, 255) : bonusReason, actorId);
            }
            jdbc.update("update purchase_order_item set received_quantity = :q where order_id = :o and product_id = :p",
                    new MapSqlParameterSource().addValue("q", got).addValue("o", id).addValue("p", it.productId()));
        }
        // A entrada muda o produto e os chamados pelo JPA, que só grava no fim da transação. Daqui em diante a leitura é
        // por SQL direto (contagem de chamados, pedido com o estoque novo): grava antes, ou ela veria o valor antigo.
        em.flush();
        close(id, Status.RECEIVED, n, actorId);
        jdbc.update("update purchase_order set invoice_number = :nf, bonus_invoice_number = :bnf where id = :id",
                new MapSqlParameterSource().addValue("nf", nf).addValue("bnf", bonusNf.isEmpty() ? null : bonusNf)
                        .addValue("id", id));
        int resolved = openBefore - openRequests(o.items());
        Map<String, Object> payload = new HashMap<>();
        payload.put("units", o.totalUnits());
        payload.put("bonusUnits", bonusUnits);
        payload.put("requestsResolved", resolved);
        payload.put("invoiceNumber", nf);
        if (!bonusNf.isEmpty()) payload.put("bonusInvoiceNumber", bonusNf);
        if (n != null) payload.put("note", n);
        audit.record(actorId, "crm", "purchase_order_received", "purchase_order", id, payload);
        return new Received(get(id), resolved, bonusUnits);
    }

    // PC-08: a nota volta ao fornecedor (chegou a menos, peça errada, avaria). Nada entra no estoque; o pedido volta a
    // "pedido feito" (o fornecedor corrige e envia de novo), as datas de envio e chegada e a nota saem, e o motivo e as
    // quantidades contadas ficam na auditoria.
    @Transactional
    public Order returnDelivery(Long id, String note, List<ReceivedItem> counted, Long actorId) {
        lock(id, Set.of(Status.CHECKING, Status.IN_TRANSIT), "Só dá para devolver a nota de um pedido em conferência ou em trânsito.");
        String n = required(note, "Conte por que a nota voltou (de 3 a 255 letras).");
        Map<String, Object> payload = new HashMap<>();
        payload.put("note", n);
        if (counted != null) {
            Map<String, Integer> q = new LinkedHashMap<>();
            for (ReceivedItem r : counted) if (r.productId() != null && r.receivedQuantity() != null) q.put(String.valueOf(r.productId()), r.receivedQuantity());
            payload.put("counted", q);
        }
        jdbc.update("""
                update purchase_order set status = 'OPEN', shipped_at = null, arrived_at = null, invoice_number = null,
                       return_count = return_count + 1, updated_at = now()
                 where id = :id""", new MapSqlParameterSource("id", id));
        audit.record(actorId, "crm", "purchase_order_returned", "purchase_order", id, payload);
        return get(id);
    }

    // ------------------------------------------------------------------------------------------- mudar data e cancelar

    // PC-04: o fornecedor avisou outra data. Motivo obrigatório; a data antiga e a nova ficam na auditoria.
    @Transactional
    public Order reschedule(Long id, LocalDate newDate, String note, Long actorId) {
        Order o = lock(id, Set.of(Status.OPEN, Status.IN_TRANSIT), "A mercadoria já chegou: não há data para mudar.");
        LocalDate d = checkDate(newDate);
        String n = required(note, "Conte por que a data mudou (de 3 a 255 letras).");
        if (d.equals(o.expectedDate())) {
            throw new InvalidInputException("same_date", "A data nova é igual à atual.");
        }
        jdbc.update("""
                update purchase_order set expected_date = :d, reschedule_count = reschedule_count + 1, updated_at = now()
                 where id = :id""", new MapSqlParameterSource().addValue("d", d).addValue("id", id));
        audit.record(actorId, "crm", "purchase_order_rescheduled", "purchase_order", id,
                Map.of("from", o.expectedDate().toString(), "to", d.toString(), "note", n));
        return get(id);
    }

    // PC-05: pedido desistido ou recusado pelo fornecedor. Não some: fica na lista de cancelados com o motivo.
    @Transactional
    public Order cancel(Long id, String note, Long actorId) {
        lock(id, Set.of(Status.OPEN, Status.IN_TRANSIT, Status.CHECKING), "Este pedido já foi recebido ou cancelado.");
        String n = required(note, "Informe o motivo do cancelamento (de 3 a 255 letras).");
        close(id, Status.CANCELLED, n, actorId);
        audit.record(actorId, "crm", "purchase_order_cancelled", "purchase_order", id, Map.of("note", n));
        return get(id);
    }

    // ------------------------------------------------------------------------------------------- consultar

    // Abertos primeiro (os atrasados no topo, depois pela data prevista); os fechados, do mais recente ao mais antigo.
    @Transactional(readOnly = true)
    public PageResponse<Order> search(Status status, boolean onlyLate, int page, int size) {
        int p = Math.max(page, 0);
        int s = Math.min(Math.max(size, 1), 100);
        var params = new MapSqlParameterSource().addValue("limit", s).addValue("offset", p * s)
                .addValue("today", today());
        String where = " where 1 = 1";
        if (status != null) {
            where += " and o.status = :status";
            params.addValue("status", status.name());
        }
        if (onlyLate) where += " and o.status in ('OPEN', 'IN_TRANSIT') and o.expected_date < :today";
        Long total = jdbc.queryForObject("select count(*) from purchase_order o" + where, params, Long.class);
        List<Order> rows = withItems(jdbc.query(SELECT + where
                + " order by o.status in " + OPEN_SQL + " desc,"
                + " case when o.status in " + OPEN_SQL + " then o.expected_date end asc,"
                + " coalesce(o.closed_at, o.created_at) desc, o.id desc"
                + " limit :limit offset :offset", params, (rs, i) -> map(rs)));
        return PageResponse.of(new PageImpl<>(rows, PageRequest.of(p, s), total == null ? 0 : total), x -> x);
    }

    @Transactional(readOnly = true)
    public Order get(Long id) {
        var params = new MapSqlParameterSource("id", id).addValue("today", today());
        List<Order> l = jdbc.query(SELECT + " where o.id = :id", params, (rs, i) -> map(rs));
        if (l.isEmpty()) throw new NotFoundException("Pedido de compra não encontrado.");
        return withItems(l).get(0);
    }

    // ------------------------------------------------------------------------------------------- apoio

    // Trava o pedido e confere a etapa. Pedido fechado é sempre order_closed; etapa errada de um pedido aberto é
    // wrong_step (ex.: mudar a data de algo que já chegou).
    private Order lock(Long id, Set<Status> allowed, String wrongStepMessage) {
        String st = jdbc.query("select status from purchase_order where id = :id for update",
                new MapSqlParameterSource("id", id), rs -> rs.next() ? rs.getString(1) : null);
        if (st == null) throw new NotFoundException("Pedido de compra não encontrado.");
        Status s = Status.valueOf(st);
        if (!s.isOpen()) throw new BusinessRuleException("order_closed", "Este pedido já foi recebido ou cancelado.");
        if (!allowed.contains(s)) throw new BusinessRuleException("wrong_step", wrongStepMessage);
        return get(id);
    }

    // Nota fiscal: a nova vale; senão, a já guardada. required = sem nenhuma das duas, recusa.
    private static String invoice(String given, String current, boolean required) {
        String g = clean(given);
        if (g.length() > 30) throw new InvalidInputException("invalid_invoice", "Nº da nota fiscal: no máximo 30 caracteres.");
        String nf = g.isEmpty() ? current : g;
        if (required && (nf == null || nf.isBlank())) {
            throw new InvalidInputException("invoice_required", "Informe o número da nota fiscal para dar entrada.");
        }
        return nf;
    }

    private void close(Long id, Status to, String note, Long actorId) {
        jdbc.update("""
                update purchase_order set status = :st, closing_note = :note, closed_by = :actor, closed_at = now(),
                       updated_at = now()
                 where id = :id""",
                new MapSqlParameterSource().addValue("st", to.name()).addValue("note", note)
                        .addValue("actor", actorId).addValue("id", id));
    }

    private int openRequests(List<Item> items) {
        if (items.isEmpty()) return 0;
        Integer n = jdbc.queryForObject("select count(*) from stock_request where status = 'OPEN' and product_id in (:ids)",
                new MapSqlParameterSource("ids", items.stream().map(Item::productId).toList()), Integer.class);
        return n == null ? 0 : n;
    }

    private LocalDate today() { return LocalDate.now(props.zone()); }

    private LocalDate checkDate(LocalDate d) {
        if (d == null) throw new InvalidInputException("date_required", "Informe quando o pedido deve chegar.");
        LocalDate t = today();
        if (d.isBefore(t)) throw new InvalidInputException("date_in_past", "A data prevista não pode ser antes de hoje.");
        if (d.isAfter(t.plusDays(MAX_DAYS_AHEAD))) {
            throw new InvalidInputException("date_too_far", "A data prevista deve ser em até " + MAX_DAYS_AHEAD + " dias.");
        }
        return d;
    }

    private static String clean(String v) { return v == null ? "" : v.trim().replaceAll("\\s+", " "); }

    private static String optional(String v, String label) {
        String t = clean(v);
        if (t.isEmpty()) return null;
        if (t.length() > 255) throw new InvalidInputException("invalid_note", label + ": no máximo 255 letras.");
        return t;
    }

    private static String required(String v, String message) {
        String t = clean(v);
        if (t.length() < 3 || t.length() > 255) throw new InvalidInputException("note_required", message);
        return t;
    }

    private List<Order> withItems(List<Order> orders) {
        if (orders.isEmpty()) return orders;
        Map<Long, List<Item>> byOrder = new LinkedHashMap<>();
        orders.forEach(o -> byOrder.put(o.id(), new ArrayList<>()));
        jdbc.query("""
                select i.order_id, i.product_id, p.code, p.name, p.size, p.color, i.quantity, p.stock_qty, i.received_quantity
                  from purchase_order_item i
                  join product p on p.id = i.product_id
                 where i.order_id in (:ids)
                 order by i.id""", new MapSqlParameterSource("ids", byOrder.keySet()), rs -> {
            byOrder.get(rs.getLong("order_id")).add(new Item(rs.getLong("product_id"), rs.getString("code"),
                    rs.getString("name"), rs.getString("size"), rs.getString("color"), rs.getInt("quantity"),
                    rs.getInt("stock_qty"), (Integer) rs.getObject("received_quantity", Integer.class)));
        });
        return orders.stream().map(o -> {
            List<Item> its = byOrder.get(o.id());
            return new Order(o.id(), o.supplier(), o.expectedDate(), o.status(), o.late(), o.note(), o.rescheduleCount(),
                    o.createdByName(), o.createdAt(), o.closingNote(), o.closedByName(), o.closedAt(),
                    o.invoiceNumber(), o.shippedAt(), o.arrivedAt(), o.bonusInvoiceNumber(), o.returnCount(),
                    its.stream().mapToInt(Item::quantity).sum(), List.copyOf(its));
        }).toList();
    }

    private static final String SELECT = """
            select o.id, o.supplier, o.expected_date, o.status, o.note, o.reschedule_count, uc.full_name as created_by_name,
                   o.created_at, o.closing_note, ux.full_name as closed_by_name, o.closed_at,
                   o.invoice_number, o.shipped_at, o.arrived_at, o.bonus_invoice_number, o.return_count,
                   (o.status in ('OPEN', 'IN_TRANSIT') and o.expected_date < :today) as late
              from purchase_order o
              left join app_user uc on uc.id = o.created_by
              left join app_user ux on ux.id = o.closed_by""";

    private static Order map(ResultSet rs) throws SQLException {
        return new Order(rs.getLong("id"), rs.getString("supplier"), rs.getObject("expected_date", LocalDate.class),
                Status.valueOf(rs.getString("status")), rs.getBoolean("late"), rs.getString("note"),
                rs.getInt("reschedule_count"), rs.getString("created_by_name"),
                rs.getObject("created_at", OffsetDateTime.class), rs.getString("closing_note"),
                rs.getString("closed_by_name"), rs.getObject("closed_at", OffsetDateTime.class),
                rs.getString("invoice_number"), rs.getObject("shipped_at", OffsetDateTime.class),
                rs.getObject("arrived_at", OffsetDateTime.class), rs.getString("bonus_invoice_number"),
                rs.getInt("return_count"), 0, List.of());
    }
}
