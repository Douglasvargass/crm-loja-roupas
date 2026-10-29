package br.senai.crmloja.modules.crm.customer;

import br.senai.crmloja.core.auth.CurrentUser;
import br.senai.crmloja.core.http.PageResponse;
import br.senai.crmloja.modules.crm.customer.dto.CustomerDetailResponse;
import br.senai.crmloja.modules.crm.customer.dto.CustomerRequest;
import br.senai.crmloja.modules.crm.customer.dto.CustomerResponse;
import br.senai.crmloja.modules.crm.sale.SaleService;
import br.senai.crmloja.modules.crm.sale.dto.SaleSummary;
import jakarta.validation.Valid;
import java.util.List;
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

// HU01 a HU06. Vendedor e gerente atendem o cliente; excluir e inativar é só do gerente. As regras (CPF válido
// e único, vínculo com vendas) ficam no CustomerService; aqui só entram e saem os dados.
@RestController
@RequestMapping("/crm/customers")
@PreAuthorize("hasAnyRole('MANAGER','SELLER')")
public class CustomerController {

    private final CustomerService service;
    private final SaleService sales;

    public CustomerController(CustomerService service, SaleService sales) {
        this.service = service;
        this.sales = sales;
    }

    // HU02: busca por parte do nome (sem acento) ou pelo CPF, com ou sem pontuação.
    @GetMapping
    public PageResponse<CustomerResponse> list(@RequestParam(required = false) String search,
                                               @RequestParam(required = false) Boolean active,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "10") int size) {
        return service.search(search, active, page, size);
    }

    @GetMapping("/{id}")
    public CustomerDetailResponse detail(@PathVariable Long id) {
        return service.detail(id);
    }

    // HU06: histórico de compras. O find() antes garante 404 para cliente que não existe, em vez de lista vazia.
    @GetMapping("/{id}/sales")
    public List<SaleSummary> history(@PathVariable Long id) {
        service.find(id);
        return sales.historyOf(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerResponse create(@Valid @RequestBody CustomerRequest body, @AuthenticationPrincipal CurrentUser user) {
        return service.create(body, user.id());
    }

    @PutMapping("/{id}")
    public CustomerResponse update(@PathVariable Long id, @Valid @RequestBody CustomerRequest body,
                                   @AuthenticationPrincipal CurrentUser user) {
        return service.update(id, body, user.id());
    }

    // HU05: sem vendas apaga; com vendas o Service responde 409 e a tela oferece inativar.
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('MANAGER')")
    public CustomerResponse deactivate(@PathVariable Long id, @AuthenticationPrincipal CurrentUser user) {
        return service.setActive(id, false, user.id());
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasRole('MANAGER')")
    public CustomerResponse activate(@PathVariable Long id, @AuthenticationPrincipal CurrentUser user) {
        return service.setActive(id, true, user.id());
    }
}
