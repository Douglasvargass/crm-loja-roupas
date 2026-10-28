package br.senai.crmloja.modules.crm.product;

import br.senai.crmloja.core.auth.CurrentUser;
import br.senai.crmloja.core.http.PageResponse;
import br.senai.crmloja.modules.crm.product.ProductService.AvailabilityFilter;
import br.senai.crmloja.modules.crm.product.dto.ProductDetailResponse;
import br.senai.crmloja.modules.crm.product.dto.ProductRequest;
import br.senai.crmloja.modules.crm.product.dto.ProductResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/crm/products")
@PreAuthorize("hasAnyRole('MANAGER','STOCK_KEEPER')")
public class ProductController {

    private final ProductService service;

    public ProductController(ProductService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("hasAnyRole('MANAGER','SELLER','STOCK_KEEPER')")
    public PageResponse<ProductResponse> list(@RequestParam(required = false) String name,
                                              @RequestParam(required = false) Long categoryId,
                                              @RequestParam(required = false) String size,
                                              @RequestParam(required = false) String code,
                                              @RequestParam(required = false) AvailabilityFilter availability,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(name = "pageSize", defaultValue = "10") int pageSize) {
        return service.search(name, categoryId, size, code, availability, page, pageSize);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('MANAGER','SELLER','STOCK_KEEPER')")
    public ProductDetailResponse detail(@PathVariable Long id) {
        return service.detail(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(@Valid @RequestBody ProductRequest body, @AuthenticationPrincipal CurrentUser user) {
        return service.create(body, user.id());
    }

    @PutMapping("/{id}")
    public ProductResponse update(@PathVariable Long id, @Valid @RequestBody ProductRequest body,
                                  @AuthenticationPrincipal CurrentUser user) {
        return service.update(id, body, user.id());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('MANAGER')")
    public ProductResponse deactivate(@PathVariable Long id, @AuthenticationPrincipal CurrentUser user) {
        return service.setActive(id, false, user.id());
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasRole('MANAGER')")
    public ProductResponse activate(@PathVariable Long id, @AuthenticationPrincipal CurrentUser user) {
        return service.setActive(id, true, user.id());
    }
}
