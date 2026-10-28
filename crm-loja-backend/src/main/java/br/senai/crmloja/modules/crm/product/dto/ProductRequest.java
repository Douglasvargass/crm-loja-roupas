package br.senai.crmloja.modules.crm.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

// initialStock só é lido no cadastro; na edição o estoque muda só por entrada, ajuste ou venda.
public record ProductRequest(
        @NotBlank(message = "Informe o código do produto.")
        @Size(max = 30, message = "O código deve ter no máximo 30 caracteres.")
        String code,

        @NotBlank(message = "Informe o nome do produto.")
        @Size(max = 120, message = "O nome deve ter no máximo 120 letras.")
        String name,

        @Size(max = 500, message = "A descrição deve ter no máximo 500 letras.")
        String description,

        @NotNull(message = "Escolha a categoria.")
        Long categoryId,

        @NotBlank(message = "Informe o tamanho.")
        @Size(max = 10, message = "O tamanho deve ter no máximo 10 caracteres.")
        String size,

        @NotBlank(message = "Informe a cor.")
        @Size(max = 30, message = "A cor deve ter no máximo 30 letras.")
        String color,

        @NotNull(message = "Informe o preço de venda.")
        @DecimalMin(value = "0.01", message = "O preço deve ser maior que zero.")
        @Digits(integer = 10, fraction = 2, message = "Preço inválido.")
        BigDecimal price,

        @Min(value = 0, message = "O estoque inicial não pode ser negativo.")
        Integer initialStock,

        @NotNull(message = "Informe o estoque mínimo.")
        @Min(value = 0, message = "O estoque mínimo não pode ser negativo.")
        Integer minStock) {
}
