package br.senai.crmloja.modules.crm.sale.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class SaleRequests {

    private SaleRequests() {}

    public record CreateSaleRequest(Long customerId) {}

    public record AddItemRequest(
            @NotNull(message = "Escolha o produto.") Long productId,
            @NotNull(message = "Informe a quantidade.") @Min(value = 1, message = "A quantidade deve ser maior que zero.")
            Integer quantity) {}

    public record UpdateItemRequest(
            @NotNull(message = "Informe a quantidade.") @Min(value = 1, message = "A quantidade deve ser maior que zero.")
            Integer quantity) {}

    // A obrigatoriedade do motivo é checada no Service para devolver o code reason_required.
    public record CancelRequest(@Size(max = 255, message = "Motivo muito longo.") String reason) {}
}
