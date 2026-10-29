package br.senai.crmloja.modules.crm.request;

import br.senai.crmloja.core.auth.CurrentUser;
import br.senai.crmloja.core.http.PageResponse;
import br.senai.crmloja.modules.crm.request.StockRequest.Status;
import br.senai.crmloja.modules.crm.request.StockRequest.Via;
import br.senai.crmloja.modules.crm.request.StockRequestService.Alerts;
import br.senai.crmloja.modules.crm.request.StockRequestService.Input;
import br.senai.crmloja.modules.crm.request.StockRequestService.Row;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// Spec crm/09. Abrir: quem atende (gerente, vendedor). Resolver: quem cuida do estoque (gerente, estoquista).
// Cancelar: os dois, e o vendedor só o que abriu (conferido no Service).
@RestController
@RequestMapping("/crm/requests")
@PreAuthorize("hasAnyRole('MANAGER','SELLER','STOCK_KEEPER')")
public class StockRequestController {

    public record OpenRequest(
            Long productId,
            @NotBlank(message = "Descreva a peça que o cliente queria.") @Size(max = 200, message = "Descrição muito longa.")
            String description,
            @Size(max = 10, message = "Tamanho muito longo.") String size,
            @Size(max = 30, message = "Cor muito longa.") String color,
            @Min(value = 1, message = "A quantidade deve ser de 1 a 99.") @Max(value = 99, message = "A quantidade deve ser de 1 a 99.")
            Integer quantity,
            Long customerId) {}

    public record CloseRequest(@NotBlank(message = "Escreva uma nota.") @Size(max = 255, message = "Nota muito longa.") String note) {}

    private final StockRequestService service;

    public StockRequestController(StockRequestService service) { this.service = service; }

    // Super admin sem papel no crm vê como gerente (mesma regra do /auth/me).
    static String role(CurrentUser u) {
        return u.superAdmin() && u.role() == null ? "MANAGER" : u.role();
    }

    @GetMapping
    public PageResponse<Row> list(@RequestParam(required = false) Status status,
                                  @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
                                  @AuthenticationPrincipal CurrentUser me) {
        return service.search(status, me.id(), role(me), page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('MANAGER','SELLER')")
    public Row open(@Valid @RequestBody OpenRequest body, @AuthenticationPrincipal CurrentUser me) {
        return service.open(new Input(body.productId(), body.description(), body.size(), body.color(), body.quantity(),
                body.customerId()), Via.SCREEN, me.id());
    }

    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasAnyRole('MANAGER','STOCK_KEEPER')")
    public Row resolve(@PathVariable Long id, @Valid @RequestBody CloseRequest body, @AuthenticationPrincipal CurrentUser me) {
        return service.close(id, Status.RESOLVED, body.note(), me.id(), role(me));
    }

    @PostMapping("/{id}/cancel")
    public Row cancel(@PathVariable Long id, @Valid @RequestBody CloseRequest body, @AuthenticationPrincipal CurrentUser me) {
        return service.close(id, Status.CANCELLED, body.note(), me.id(), role(me));
    }

    @GetMapping("/alerts")
    public Alerts alerts(@AuthenticationPrincipal CurrentUser me) {
        return service.alerts(me.id(), role(me));
    }

    @PostMapping("/alerts/seen")
    public Map<String, Integer> seen(@AuthenticationPrincipal CurrentUser me) {
        return Map.of("marked", service.markSeen(me.id()));
    }
}
