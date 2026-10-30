package br.senai.crmloja.modules.crm.coupon;

import br.senai.crmloja.core.audit.AuditService;
import br.senai.crmloja.core.config.AppProperties;
import br.senai.crmloja.core.http.BusinessRuleException;
import br.senai.crmloja.core.http.InvalidInputException;
import br.senai.crmloja.core.http.NotFoundException;
import br.senai.crmloja.modules.crm.sale.Sale;
import br.senai.crmloja.modules.crm.sale.SaleStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Cupom de desconto (V14, RN61 a RN64). O gerente cria; o cliente usa no carrinho da loja online e o vendedor na
// venda do balcão. Aplicar só guarda a cópia da regra na venda; o uso conta na confirmação, com o cupom travado.
@Service
public class CouponService {

    public enum Kind { PERCENT, FIXED }

    public record Coupon(Long id, String code, Kind kind, BigDecimal value, BigDecimal minOrder, LocalDate validFrom,
                         LocalDate validUntil, Integer maxUses, int uses, boolean active, OffsetDateTime createdAt) {}

    public record CouponRequest(String code, Kind kind, BigDecimal value, BigDecimal minOrder, LocalDate validFrom,
                                LocalDate validUntil, Integer maxUses) {}

    private final NamedParameterJdbcTemplate jdbc;
    private final AuditService audit;
    private final AppProperties props;

    public CouponService(NamedParameterJdbcTemplate jdbc, AuditService audit, AppProperties props) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.props = props;
    }

    private static final RowMapper<Coupon> ROW = (rs, i) -> new Coupon(rs.getLong("id"), rs.getString("code"),
            Kind.valueOf(rs.getString("kind")), rs.getBigDecimal("value"), rs.getBigDecimal("min_order"),
            rs.getObject("valid_from", LocalDate.class), rs.getObject("valid_until", LocalDate.class),
            (Integer) rs.getObject("max_uses"), rs.getInt("uses"), rs.getBoolean("is_active"),
            rs.getObject("created_at", OffsetDateTime.class));

    // ------------------------------------------------------------------------------------------ gerente

    @Transactional(readOnly = true)
    public List<Coupon> list() {
        return jdbc.query("select * from coupon order by is_active desc, valid_until desc, code", ROW);
    }

    @Transactional
    public Coupon create(CouponRequest r, Long actorId) {
        String code = normalize(r.code());
        if (!code.matches("^[A-Z0-9_-]{3,30}$")) {
            throw new InvalidInputException("invalid_code", "Código do cupom: de 3 a 30 letras sem acento, números, - ou _.");
        }
        if (r.kind() == null) throw new InvalidInputException("invalid_kind", "Escolha desconto em % ou em R$.");
        BigDecimal value = r.value() == null ? BigDecimal.ZERO : r.value().setScale(2, RoundingMode.HALF_UP);
        if (value.signum() <= 0) throw new InvalidInputException("invalid_value", "O desconto precisa ser maior que zero.");
        if (r.kind() == Kind.PERCENT && value.compareTo(BigDecimal.valueOf(90)) > 0) {
            throw new InvalidInputException("invalid_value", "O desconto em % vai até 90%.");
        }
        BigDecimal min = r.minOrder() == null ? BigDecimal.ZERO : r.minOrder().setScale(2, RoundingMode.HALF_UP);
        if (min.signum() < 0) throw new InvalidInputException("invalid_min", "O valor mínimo não pode ser negativo.");
        LocalDate from = r.validFrom() == null ? today() : r.validFrom();
        if (r.validUntil() == null || r.validUntil().isBefore(from)) {
            throw new InvalidInputException("invalid_dates", "A data final precisa ser igual ou depois da inicial.");
        }
        if (r.maxUses() != null && r.maxUses() <= 0) {
            throw new InvalidInputException("invalid_max_uses", "O limite de usos precisa ser maior que zero (ou vazio para sem limite).");
        }
        var p = new MapSqlParameterSource().addValue("code", code).addValue("kind", r.kind().name()).addValue("value", value)
                .addValue("min", min).addValue("from", from).addValue("until", r.validUntil()).addValue("max", r.maxUses())
                .addValue("by", actorId);
        Long id;
        try {
            id = jdbc.queryForObject("""
                    insert into coupon (code, kind, value, min_order, valid_from, valid_until, max_uses, created_by)
                    values (:code, :kind, :value, :min, :from, :until, :max, :by) returning id""", p, Long.class);
        } catch (DuplicateKeyException e) {
            throw new BusinessRuleException("coupon_exists", "Já existe um cupom com o código " + code + ".");
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("code", code);
        payload.put("kind", r.kind().name());
        payload.put("value", value.toPlainString());
        audit.record(actorId, "crm", "coupon_created", "coupon", id, payload);
        return byId(id);
    }

    @Transactional
    public Coupon setActive(Long id, boolean active, Long actorId) {
        int n = jdbc.update("update coupon set is_active = :a where id = :id", Map.of("a", active, "id", id));
        if (n == 0) throw new NotFoundException("Cupom não encontrado.");
        audit.record(actorId, "crm", active ? "coupon_activated" : "coupon_deactivated", "coupon", id, Map.of());
        return byId(id);
    }

    // ------------------------------------------------------------------------------------- aplicar e usar

    // Aplica na venda em aberto (carrinho ou balcão). RN62: cupom ativo, dentro da validade, com uso disponível e
    // pedido no valor mínimo. Um cupom por venda: aplicar outro troca.
    public void apply(Sale sale, String rawCode) {
        if (sale.getStatus() != SaleStatus.DRAFT) {
            throw new BusinessRuleException("sale_not_draft", "Só dá para usar cupom antes de fechar o pedido.");
        }
        Coupon c = findUsable(normalize(rawCode));
        if (sale.getSubtotal().compareTo(c.minOrder()) < 0) {
            throw new BusinessRuleException("coupon_min_order",
                    "Este cupom vale para pedidos a partir de " + money(c.minOrder()) + ".");
        }
        sale.applyCoupon(c.id(), c.code(), c.kind().name(), c.value(), c.minOrder());
    }

    // Na confirmação (dentro da mesma transação): trava o cupom, confere de novo e conta o uso (RN63).
    public void redeem(Sale sale) {
        if (sale.getCouponId() == null) return;
        Coupon c;
        try {
            c = jdbc.queryForObject("select * from coupon where id = :id for update", Map.of("id", sale.getCouponId()), ROW);
        } catch (EmptyResultDataAccessException e) {
            throw new BusinessRuleException("coupon_invalid", "O cupom deste pedido não existe mais. Tire o cupom e confirme de novo.");
        }
        check(c);
        if (!sale.couponMinReached()) {
            throw new BusinessRuleException("coupon_min_order", "O pedido ficou abaixo de " + money(sale.getCouponMinOrder())
                    + ", o mínimo do cupom " + c.code() + ". Tire o cupom ou adicione peças.");
        }
        jdbc.update("update coupon set uses = uses + 1 where id = :id", Map.of("id", c.id()));
    }

    private Coupon findUsable(String code) {
        Coupon c;
        try {
            c = jdbc.queryForObject("select * from coupon where code = :code", Map.of("code", code), ROW);
        } catch (EmptyResultDataAccessException e) {
            throw new BusinessRuleException("coupon_not_found", "Cupom não encontrado. Confira o código.");
        }
        check(c);
        return c;
    }

    private void check(Coupon c) {
        LocalDate today = today();
        if (!c.active()) throw new BusinessRuleException("coupon_inactive", "O cupom " + c.code() + " foi desativado.");
        if (today.isBefore(c.validFrom())) throw new BusinessRuleException("coupon_not_started", "O cupom " + c.code() + " ainda não começou a valer.");
        if (today.isAfter(c.validUntil())) throw new BusinessRuleException("coupon_expired", "O cupom " + c.code() + " já venceu.");
        if (c.maxUses() != null && c.uses() >= c.maxUses()) throw new BusinessRuleException("coupon_exhausted", "O cupom " + c.code() + " já foi usado o máximo de vezes.");
    }

    private Coupon byId(Long id) {
        return jdbc.queryForObject("select * from coupon where id = :id", Map.of("id", id), ROW);
    }

    private LocalDate today() {
        return LocalDate.now(props.zone());
    }

    static String normalize(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }

    private static String money(BigDecimal v) {
        return "R$ " + String.format(Locale.forLanguageTag("pt-BR"), "%,.2f", v);
    }
}
