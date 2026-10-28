package br.senai.crmloja.modules.crm.stock.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class StockRequests {

    private StockRequests() {}

    // HU17: entrada sempre positiva; o motivo (ex.: nota do fornecedor) é opcional.
    public record EntryRequest(
            @NotNull(message = "Escolha o produto.") Long productId,
            @NotNull(message = "Informe a quantidade.") @Min(value = 1, message = "A quantidade deve ser maior que zero.")
            Integer quantity,
            @Size(max = 255, message = "Observação muito longa.") String reason) {}

    // HU18: ajuste com sinal (+ ou −) e motivo obrigatório.
    public record AdjustmentRequest(
            @NotNull(message = "Escolha o produto.") Long productId,
            @NotNull(message = "Informe a quantidade.") Integer quantity,
            @NotBlank(message = "Informe o motivo do ajuste.") @Size(max = 255, message = "Motivo muito longo.")
            String reason) {}
}
