package br.senai.crmloja.modules.crm.category.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
        @NotBlank(message = "Informe o nome da categoria.")
        @Size(max = 60, message = "O nome deve ter no máximo 60 letras.")
        String name,

        @Size(max = 255, message = "A descrição deve ter no máximo 255 letras.")
        String description,

        Boolean active) {
}
