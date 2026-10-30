package br.senai.crmloja.modules.crm.sale;

import br.senai.crmloja.core.auth.CurrentUser;
import br.senai.crmloja.core.http.PageResponse;
import br.senai.crmloja.modules.crm.sale.dto.SaleRequests.AddItemRequest;
import br.senai.crmloja.modules.crm.sale.dto.SaleRequests.CancelRequest;
import br.senai.crmloja.modules.crm.sale.dto.SaleRequests.CreateSaleRequest;
import br.senai.crmloja.modules.crm.sale.dto.SaleRequests.UpdateItemRequest;
import br.senai.crmloja.modules.crm.sale.dto.SaleResponse;
import br.senai.crmloja.modules.crm.sale.dto.SaleSummary;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// Venda não tem DELETE (RN20): cancela-se com motivo e ela continua no histórico.
@RestController
@RequestMapping("/crm/sales")
@PreAuthorize("hasAnyRole('MANAGER','SELLER')")
public class SaleController {

    private final SaleService service;

    public SaleController(SaleService service) { this.service = service; }

    @GetMapping
    public PageResponse<SaleSummary> list(
            @RequestParam(required = false) Long id,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) SaleStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return service.search(id, customerId, from, to, status, page, size);
    }

    @GetMapping("/{id}")
    public SaleResponse detail(@PathVariable Long id) {
        return service.detail(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SaleResponse open(@RequestBody(required = false) CreateSaleRequest body,
                             @AuthenticationPrincipal CurrentUser user) {
        return service.open(body == null ? null : body.customerId(), user.id());
    }

    @PostMapping("/{id}/items")
    public SaleResponse addItem(@PathVariable Long id, @Valid @RequestBody AddItemRequest body) {
        return service.addItem(id, body.productId(), body.quantity());
    }

    @PutMapping("/{id}/items/{itemId}")
    public SaleResponse updateItem(@PathVariable Long id, @PathVariable Long itemId,
                                   @Valid @RequestBody UpdateItemRequest body) {
        return service.updateItem(id, itemId, body.quantity());
    }

    @DeleteMapping("/{id}/items/{itemId}")
    public SaleResponse removeItem(@PathVariable Long id, @PathVariable Long itemId) {
        return service.removeItem(id, itemId);
    }

    public record CouponBody(String code) {}

    @PostMapping("/{id}/coupon")
    public SaleResponse applyCoupon(@PathVariable Long id, @RequestBody CouponBody body) {
        return service.applyCoupon(id, body == null ? null : body.code());
    }

    @DeleteMapping("/{id}/coupon")
    public SaleResponse removeCoupon(@PathVariable Long id) {
        return service.removeCoupon(id);
    }

    @PostMapping("/{id}/confirm")
    public SaleResponse confirm(@PathVariable Long id, @AuthenticationPrincipal CurrentUser user) {
        return service.confirm(id, user.id());
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('MANAGER')")
    public SaleResponse cancel(@PathVariable Long id, @Valid @RequestBody(required = false) CancelRequest body,
                               @AuthenticationPrincipal CurrentUser user) {
        return service.cancel(id, body == null ? null : body.reason(), user.id());
    }
}
