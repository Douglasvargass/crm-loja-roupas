package br.senai.crmloja.modules.crm.purchase;

import br.senai.crmloja.core.auth.CurrentUser;
import br.senai.crmloja.core.http.PageResponse;
import br.senai.crmloja.modules.crm.purchase.PurchaseOrderService.Input;
import br.senai.crmloja.modules.crm.purchase.PurchaseOrderService.ItemInput;
import br.senai.crmloja.modules.crm.purchase.PurchaseOrderService.Order;
import br.senai.crmloja.modules.crm.purchase.PurchaseOrderService.Received;
import br.senai.crmloja.modules.crm.purchase.PurchaseOrderService.ReceivedItem;
import br.senai.crmloja.modules.crm.purchase.PurchaseOrderService.Status;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
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

// Spec crm/11. Ver e receber: quem cuida do estoque (gerente, estoquista, Operações e Compras). Registrar, mudar a
// data e cancelar: quem compra (gerente e Operações e Compras); o estoquista só recebe a mercadoria.
@RestController
@RequestMapping("/crm/purchase-orders")
@PreAuthorize("hasAnyRole('MANAGER','STOCK_KEEPER')")
public class PurchaseOrderController {

    public record ItemBody(@NotNull(message = "Escolha a peça.") Long productId,
                           @NotNull(message = "Informe a quantidade.") Integer quantity) {}

    public record CreateBody(
            @NotBlank(message = "Informe o fornecedor.") @Size(max = 80, message = "Fornecedor muito longo.") String supplier,
            @NotNull(message = "Informe quando o pedido deve chegar.") LocalDate expectedDate,
            @Size(max = 255, message = "Observação muito longa.") String note,
            @NotEmpty(message = "Inclua ao menos uma peça no pedido.") List<@Valid ItemBody> items) {}

    public record CountedItem(Long productId, Integer receivedQuantity) {}

    // items = o que foi contado na conferência (peça ausente = bateu com o pedido); bonusInvoiceNumber só se veio a mais.
    public record ReceiveBody(@Size(max = 30, message = "Nº da nota fiscal muito longo.") String invoiceNumber,
                              @Size(max = 30, message = "Nº da nota de bonificação muito longo.") String bonusInvoiceNumber,
                              @Size(max = 255, message = "Observação muito longa.") String note,
                              List<CountedItem> items) {}

    public record ReturnBody(@NotBlank(message = "Conte por que a nota voltou.") @Size(max = 255, message = "Motivo muito longo.")
                             String note, List<CountedItem> items) {}

    public record ShipBody(@Size(max = 30, message = "Nº da nota fiscal muito longo.") String invoiceNumber) {}

    public record RescheduleBody(@NotNull(message = "Informe a nova data.") LocalDate expectedDate,
                                 @NotBlank(message = "Conte por que a data mudou.") @Size(max = 255, message = "Motivo muito longo.")
                                 String note) {}

    public record CancelBody(@NotBlank(message = "Informe o motivo.") @Size(max = 255, message = "Motivo muito longo.")
                             String note) {}

    private final PurchaseOrderService service;

    public PurchaseOrderController(PurchaseOrderService service) { this.service = service; }

    @GetMapping
    public PageResponse<Order> list(@RequestParam(required = false) Status status,
                                    @RequestParam(defaultValue = "false") boolean late,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size) {
        return service.search(status, late, page, size);
    }

    @GetMapping("/{id}")
    public Order get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('MANAGER','PURCHASING')")
    public Order create(@Valid @RequestBody CreateBody body, @AuthenticationPrincipal CurrentUser me) {
        return service.create(new Input(body.supplier(), body.expectedDate(), body.note(),
                body.items().stream().map(i -> new ItemInput(i.productId(), i.quantity())).toList()), me.id());
    }

    // Saiu do fornecedor: quem compra avisa (pode já informar a nota fiscal).
    @PostMapping("/{id}/ship")
    @PreAuthorize("hasAnyRole('MANAGER','PURCHASING')")
    public Order ship(@PathVariable Long id, @Valid @RequestBody(required = false) ShipBody body,
                      @AuthenticationPrincipal CurrentUser me) {
        return service.ship(id, body == null ? null : body.invoiceNumber(), me.id());
    }

    // Chegou na loja: quem recebe a mercadoria marca e começa a conferência.
    @PostMapping("/{id}/arrive")
    public Order arrive(@PathVariable Long id, @AuthenticationPrincipal CurrentUser me) {
        return service.arrive(id, me.id());
    }

    // Conferido: entrada no estoque com o nº da nota fiscal.
    @PostMapping("/{id}/receive")
    public Received receive(@PathVariable Long id, @Valid @RequestBody(required = false) ReceiveBody body,
                            @AuthenticationPrincipal CurrentUser me) {
        return service.receive(id, body == null ? null : body.invoiceNumber(), body == null ? null : body.bonusInvoiceNumber(),
                body == null ? null : body.note(), counted(body == null ? null : body.items()), me.id());
    }

    // A nota volta ao fornecedor: quem recebe a mercadoria decide, com o motivo.
    @PostMapping("/{id}/return")
    public Order returnDelivery(@PathVariable Long id, @Valid @RequestBody ReturnBody body, @AuthenticationPrincipal CurrentUser me) {
        return service.returnDelivery(id, body.note(), counted(body.items()), me.id());
    }

    private static List<ReceivedItem> counted(List<CountedItem> items) {
        return items == null ? null : items.stream().map(i -> new ReceivedItem(i.productId(), i.receivedQuantity())).toList();
    }

    @PostMapping("/{id}/reschedule")
    @PreAuthorize("hasAnyRole('MANAGER','PURCHASING')")
    public Order reschedule(@PathVariable Long id, @Valid @RequestBody RescheduleBody body,
                            @AuthenticationPrincipal CurrentUser me) {
        return service.reschedule(id, body.expectedDate(), body.note(), me.id());
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('MANAGER','PURCHASING')")
    public Order cancel(@PathVariable Long id, @Valid @RequestBody CancelBody body, @AuthenticationPrincipal CurrentUser me) {
        return service.cancel(id, body.note(), me.id());
    }
}
