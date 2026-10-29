package br.senai.crmloja.modules.crm.stock;

import br.senai.crmloja.core.auth.CurrentUser;
import br.senai.crmloja.core.http.PageResponse;
import br.senai.crmloja.modules.crm.stock.dto.StockAlertsResponse;
import br.senai.crmloja.modules.crm.stock.dto.StockMovementResponse;
import br.senai.crmloja.modules.crm.stock.dto.StockRequests.AdjustmentRequest;
import br.senai.crmloja.modules.crm.stock.dto.StockRequests.EntryRequest;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/crm/stock")
@PreAuthorize("hasAnyRole('MANAGER','STOCK_KEEPER')")
public class StockController {

    private final StockService service;

    public StockController(StockService service) { this.service = service; }

    @GetMapping("/movements")
    public PageResponse<StockMovementResponse> movements(
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) StockMovementType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.history(productId, type, from, to, page, size);
    }

    // HU17: entrada de mercadoria. Também fecha sozinha os chamados de peça em falta que ela cobre (spec crm/09).
    @PostMapping("/entries")
    @ResponseStatus(HttpStatus.CREATED)
    public StockMovementResponse entry(@Valid @RequestBody EntryRequest body, @AuthenticationPrincipal CurrentUser user) {
        return service.entry(body.productId(), body.quantity(), body.reason(), user.id());
    }

    // HU18: ajuste (perda, avaria, inventário), para mais ou para menos, com motivo obrigatório e registro na auditoria.
    @PostMapping("/adjustments")
    @ResponseStatus(HttpStatus.CREATED)
    public StockMovementResponse adjust(@Valid @RequestBody AdjustmentRequest body,
                                        @AuthenticationPrincipal CurrentUser user) {
        return service.adjust(body.productId(), body.quantity(), body.reason(), user.id());
    }

    // HU19: listas de estoque baixo e zerado, usadas pela tela de estoque e pelo aviso de pendências ao entrar.
    @GetMapping("/alerts")
    public StockAlertsResponse alerts() {
        return service.alerts();
    }
}
