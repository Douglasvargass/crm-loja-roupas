package br.senai.crmloja.modules.crm.coupon;

import br.senai.crmloja.core.auth.CurrentUser;
import br.senai.crmloja.modules.crm.coupon.CouponService.Coupon;
import br.senai.crmloja.modules.crm.coupon.CouponService.CouponRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// Cupons de desconto: só o gerente (e a equipe técnica) cria e liga ou desliga. Nada se apaga (RN64).
@RestController
@RequestMapping("/crm/coupons")
@PreAuthorize("hasRole('MANAGER')")
public class CouponController {

    private final CouponService service;

    public CouponController(CouponService service) { this.service = service; }

    @GetMapping
    public List<Coupon> list() { return service.list(); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Coupon create(@RequestBody CouponRequest body, @AuthenticationPrincipal CurrentUser me) {
        return service.create(body, me.id());
    }

    @PatchMapping("/{id}/active")
    public Coupon setActive(@PathVariable Long id, @RequestParam boolean value, @AuthenticationPrincipal CurrentUser me) {
        return service.setActive(id, value, me.id());
    }
}
