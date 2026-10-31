package br.senai.crmloja.modules.crm.store;

import br.senai.crmloja.core.audit.AuditService;
import br.senai.crmloja.core.http.BusinessRuleException;
import br.senai.crmloja.core.http.InvalidInputException;
import br.senai.crmloja.core.http.NotFoundException;
import br.senai.crmloja.core.http.PageResponse;
import br.senai.crmloja.modules.crm.customer.Customer;
import br.senai.crmloja.modules.crm.customer.CustomerRepository;
import br.senai.crmloja.modules.crm.customer.CustomerService;
import br.senai.crmloja.modules.crm.customer.dto.CustomerRequest;
import br.senai.crmloja.modules.crm.customer.dto.CustomerResponse;
import br.senai.crmloja.modules.crm.product.ProductService;
import br.senai.crmloja.modules.crm.product.ProductService.AvailabilityFilter;
import br.senai.crmloja.modules.crm.sale.Sale;
import br.senai.crmloja.modules.crm.sale.SaleItem;
import br.senai.crmloja.modules.crm.sale.SaleRepository;
import br.senai.crmloja.modules.crm.sale.SaleService;
import br.senai.crmloja.modules.crm.sale.dto.SaleResponse;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Espaço do cliente (spec crm/07). O cliente vê a vitrine, monta o carrinho, paga (simulado) e acompanha os pedidos.
 * Tudo é resolvido pelo cliente da SESSÃO (customer.user_id): nenhuma rota aceita id de cliente vindo de fora, então
 * um cliente nunca alcança o carrinho, o pedido ou os dados de outro. O pedido é a mesma venda do balcão, com canal
 * ONLINE, e passa pela mesma confirmação tudo-ou-nada que baixa o estoque.
 */
@Service
public class StoreService {

    public record ShopProduct(Long id, String name, String description, String category, String size, String color,
                              BigDecimal price, boolean lastUnits) {}

    public record Category(Long id, String name) {}

    public record CartItem(Long itemId, Long productId, String code, String name, BigDecimal unitPrice, int quantity,
                           BigDecimal subtotal, boolean available) {}

    // subtotal = soma das peças; discount = cupom; total = o que o cliente paga.
    public record Cart(Long saleId, List<CartItem> items, BigDecimal total, int count, BigDecimal subtotal, BigDecimal discount,
                       String couponCode) {}

    public record Order(Long id, String status, String channel, OffsetDateTime createdAt, OffsetDateTime confirmedAt,
                        BigDecimal total, int items, String paymentMethod, String paymentStatus, String cardLast4) {}

    public record OrderDetail(SaleResponse sale, String paymentMethod, String paymentStatus, String cardLast4) {}

    public record MyData(String phone, String email, String zipCode, String street, String number, String complement,
                         String district, String city, String state) {}

    /** Preço mudou com a peça no carrinho: o carrinho é atualizado (fica gravado) e o cliente confere de novo. */
    public static class PriceChangedException extends BusinessRuleException {
        public PriceChangedException(String msg) { super("price_changed", msg); }
    }

    /** Cartão recusado (simulado): nada muda, mas a tentativa fica na auditoria. */
    public static class PaymentDeclinedException extends BusinessRuleException {
        public PaymentDeclinedException() { super("payment_declined", "Pagamento não autorizado. Tente outro cartão ou Pix."); }
    }

    // Cartão de teste que sempre recusa, para demonstrar o erro (decisão 3 da spec 07).
    static final String DECLINED_SUFFIX = "0002";

    private final CustomerRepository customers;
    private final CustomerService customerService;
    private final ProductService productService;
    private final SaleRepository sales;
    private final SaleService saleService;
    private final AuditService audit;
    private final NamedParameterJdbcTemplate jdbc;

    public StoreService(CustomerRepository customers, CustomerService customerService,
                        ProductService productService, SaleRepository sales, SaleService saleService,
                        AuditService audit, NamedParameterJdbcTemplate jdbc) {
        this.customers = customers;
        this.customerService = customerService;
        this.productService = productService;
        this.sales = sales;
        this.saleService = saleService;
        this.audit = audit;
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------------------------------- vitrine

    // REQ-04: só ativo com estoque; sem estoque exato, só o aviso "últimas unidades".
    @Transactional(readOnly = true)
    public PageResponse<ShopProduct> catalog(String name, Long categoryId, String size, int page) {
        var p = productService.search(name, categoryId, size, null, AvailabilityFilter.AVAILABLE, page, 12);
        return new PageResponse<>(p.content().stream().map(x -> new ShopProduct(x.id(), x.name(), x.description(),
                x.categoryName(), x.size(), x.color(), x.price(), x.lowStock())).toList(),
                p.page(), p.size(), p.totalElements(), p.totalPages());
    }

    @Transactional(readOnly = true)
    public List<Category> categories() {
        return jdbc.query("select id, name from category where is_active order by name", Map.of(),
                (rs, i) -> new Category(rs.getLong("id"), rs.getString("name")));
    }

    // ------------------------------------------------------------------------------------------- carrinho

    @Transactional(readOnly = true)
    public Cart cart(Long userId) {
        return openCartId(me(userId)).map(this::cartOf).orElse(new Cart(null, List.of(), BigDecimal.ZERO.setScale(2), 0,
                BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2), null));
    }

    // REQ-05: o mesmo produto de novo soma na linha; estoque conferido na hora (sem reservar).
    @Transactional
    public Cart add(Long userId, Long productId, int quantity) {
        Customer c = me(userId);
        Long saleId = openCartId(c).orElseGet(() -> saleService.open(c.getId(), userId, true).id());
        saleService.addItem(saleId, productId, quantity, true);
        return cartOf(saleId);
    }

    @Transactional
    public Cart update(Long userId, Long itemId, int quantity) {
        Long saleId = openCartId(me(userId)).orElseThrow(() -> new NotFoundException("Seu carrinho está vazio."));
        if (quantity < 1) saleService.removeItem(saleId, itemId, true);
        else saleService.updateItem(saleId, itemId, quantity, true);
        return cartOf(saleId);
    }

    @Transactional
    public Cart remove(Long userId, Long itemId) {
        Long saleId = openCartId(me(userId)).orElseThrow(() -> new NotFoundException("Seu carrinho está vazio."));
        saleService.removeItem(saleId, itemId, true);
        return cartOf(saleId);
    }

    // ------------------------------------------------------------------------------------------- pagamento

    // Cupom no carrinho (spec crm/07 + RN61 a RN64).
    @Transactional
    public Cart applyCoupon(Long userId, String code) {
        Long saleId = openCartId(me(userId)).orElseThrow(() -> new BusinessRuleException("cart_empty", "Seu carrinho está vazio."));
        saleService.applyCoupon(saleId, code, true);
        return cartOf(saleId);
    }

    @Transactional
    public Cart removeCoupon(Long userId) {
        Long saleId = openCartId(me(userId)).orElseThrow(() -> new BusinessRuleException("cart_empty", "Seu carrinho está vazio."));
        saleService.removeCoupon(saleId, true);
        return cartOf(saleId);
    }

    // REQ-06 e REQ-07. noRollbackFor: o preço atualizado e a tentativa recusada ficam gravados mesmo com o 409.
    @Transactional(noRollbackFor = {PriceChangedException.class, PaymentDeclinedException.class})
    public OrderDetail checkout(Long userId, String method, String cardNumber) {
        Customer c = me(userId);
        Long saleId = openCartId(c).orElseThrow(() -> new BusinessRuleException("cart_empty", "Seu carrinho está vazio."));
        Sale sale = sales.findByIdForUpdate(saleId).orElseThrow();
        if (sale.getItems().isEmpty()) throw new BusinessRuleException("cart_empty", "Seu carrinho está vazio.");

        String m = method == null ? "" : method.trim().toUpperCase();
        if (!m.equals("PIX") && !m.equals("CARD")) {
            throw new InvalidInputException("invalid_method", "Escolha Pix ou cartão de crédito.");
        }
        String last4 = null;
        if (m.equals("CARD")) {
            String digits = cardNumber == null ? "" : cardNumber.replaceAll("\\D", "");
            if (digits.length() < 13 || digits.length() > 19) {
                throw new InvalidInputException("invalid_card", "Número de cartão inválido (de 13 a 19 dígitos).");
            }
            last4 = digits.substring(digits.length() - 4);
        }

        // Decisão 1 da spec 07: se o preço mudou com a peça no carrinho, vale o novo, e o cliente confere antes.
        List<String> mudaram = new ArrayList<>();
        for (SaleItem i : sale.getItems()) {
            BigDecimal atual = i.getProduct().getPrice();
            if (atual.compareTo(i.getUnitPrice()) != 0) {
                i.reprice(atual);
                mudaram.add(i.getProductName());
            }
        }
        if (!mudaram.isEmpty()) {
            sale.recalculateTotal();
            sales.flush();
            throw new PriceChangedException("O preço de " + String.join(", ", mudaram)
                    + " mudou e o carrinho foi atualizado. Confira o novo total e pague de novo.");
        }

        if (last4 != null && last4.equals(DECLINED_SUFFIX)) {
            audit.record(userId, "loja", "payment_declined", "sale", saleId, Map.of("method", m, "card_last4", last4));
            throw new PaymentDeclinedException();
        }

        // A mesma confirmação do balcão: trava os produtos, confere estoque de todos, baixa tudo ou nada.
        saleService.confirm(saleId, userId, true);
        var params = new MapSqlParameterSource().addValue("sale", saleId).addValue("method", m)
                .addValue("amount", sale.getTotal()).addValue("last4", last4);
        jdbc.update("insert into payment (sale_id, method, amount, card_last4) values (:sale, :method, :amount, :last4)", params);
        audit.record(userId, "loja", "online_order_paid", "sale", saleId, Map.of("method", m, "total", sale.getTotal()));
        return order(userId, saleId);
    }

    // ------------------------------------------------------------------------------------------- pedidos

    // REQ-08: os pedidos do cliente (online e balcão), menos o carrinho aberto.
    @Transactional(readOnly = true)
    public List<Order> orders(Long userId) {
        return jdbc.query("""
                select s.id, s.status::text as status, s.channel, s.created_at, s.confirmed_at, s.total,
                       (select coalesce(sum(i.quantity), 0) from sale_item i where i.sale_id = s.id) as items,
                       p.method, p.status as pay_status, p.card_last4
                  from sale s left join payment p on p.sale_id = s.id
                 where s.customer_id = :c and not (s.status = 'DRAFT' and s.channel = 'ONLINE')
                 order by s.created_at desc, s.id desc""",
                Map.of("c", me(userId).getId()),
                (rs, i) -> new Order(rs.getLong("id"), rs.getString("status"), rs.getString("channel"),
                        rs.getObject("created_at", OffsetDateTime.class), rs.getObject("confirmed_at", OffsetDateTime.class),
                        rs.getBigDecimal("total"), rs.getInt("items"), rs.getString("method"),
                        rs.getString("pay_status"), rs.getString("card_last4")));
    }

    // Pedido de outro cliente: 404, sem revelar que ele existe.
    @Transactional(readOnly = true)
    public OrderDetail order(Long userId, Long saleId) {
        Customer c = me(userId);
        Sale s = sales.findById(saleId).filter(x -> x.getCustomer() != null && x.getCustomer().getId().equals(c.getId()))
                .orElseThrow(() -> new NotFoundException("Pedido não encontrado."));
        var pay = jdbc.queryForList("select method, status, card_last4 from payment where sale_id = :s", Map.of("s", saleId));
        Map<String, Object> p = pay.isEmpty() ? Map.of() : pay.get(0);
        return new OrderDetail(SaleResponse.from(s), (String) p.get("method"), (String) p.get("status"),
                p.get("card_last4") == null ? null : p.get("card_last4").toString().trim());
    }

    // ------------------------------------------------------------------------------------------- meus dados

    @Transactional(readOnly = true)
    public CustomerResponse myData(Long userId) {
        return customerService.detail(me(userId).getId()).customer();
    }

    // REQ-09: o cliente muda contato e endereço; nome e CPF só a loja muda (são a identidade de quem comprou).
    @Transactional
    public CustomerResponse updateMyData(Long userId, MyData d) {
        Customer c = me(userId);
        return customerService.update(c.getId(), new CustomerRequest(c.getFullName(), c.getCpf(), d.phone(), d.email(),
                d.zipCode(), d.street(), d.number(), d.complement(), d.district(), d.city(), d.state()), userId);
    }

    // Id do cadastro do cliente da sessão: usado para abrir a solicitação de peça já com o cliente certo (nunca um id
    // vindo da tela ou da IA).
    @Transactional(readOnly = true)
    public Long customerIdOf(Long userId) {
        return me(userId).getId();
    }

    // ------------------------------------------------------------------------------------------- apoio

    private Customer me(Long userId) {
        List<Long> ids = jdbc.queryForList("select id from customer where user_id = :u", Map.of("u", userId), Long.class);
        if (ids.isEmpty()) {
            throw new BusinessRuleException("no_customer", "Sua conta não está ligada a um cadastro de cliente. Fale com a loja.");
        }
        Customer c = customers.findById(ids.get(0)).orElseThrow();
        if (!c.isActive()) throw new BusinessRuleException("customer_inactive", "Seu cadastro está inativo. Fale com a loja.");
        return c;
    }

    private Optional<Long> openCartId(Customer c) {
        return jdbc.queryForList("""
                select id from sale where customer_id = :c and status = 'DRAFT' and channel = 'ONLINE'
                 order by id desc limit 1""", Map.of("c", c.getId()), Long.class).stream().findFirst();
    }

    private Cart cartOf(Long saleId) {
        SaleResponse s = saleService.detail(saleId);
        List<CartItem> items = s.items().stream().map(i -> new CartItem(i.id(), i.productId(), i.productCode(),
                i.productName(), i.unitPrice(), i.quantity(), i.subtotal(), i.availableStock() >= i.quantity())).toList();
        return new Cart(saleId, items, s.total(), items.stream().mapToInt(CartItem::quantity).sum(), s.subtotal(), s.discount(),
                s.couponCode());
    }
}
