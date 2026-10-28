package br.senai.crmloja.modules.crm.customer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// CPF, telefone e CEP podem vir com ou sem máscara; o Service guarda só os dígitos.
public record CustomerRequest(
        @NotBlank(message = "Informe o nome completo do cliente.")
        @Size(min = 3, max = 120, message = "O nome deve ter entre 3 e 120 letras.")
        String fullName,

        @NotBlank(message = "Informe o CPF do cliente.")
        String cpf,

        @NotBlank(message = "Informe o telefone do cliente.")
        String phone,

        @NotBlank(message = "Informe o e-mail do cliente.")
        @Size(max = 120, message = "E-mail muito longo.")
        @Pattern(regexp = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", message = "E-mail inválido.")
        String email,

        @NotBlank(message = "Informe o CEP.")
        String zipCode,

        @NotBlank(message = "Informe a rua.")
        @Size(max = 120, message = "Rua muito longa.")
        String street,

        @NotBlank(message = "Informe o número.")
        @Size(max = 10, message = "Número muito longo.")
        String number,

        @Size(max = 60, message = "Complemento muito longo.")
        String complement,

        @NotBlank(message = "Informe o bairro.")
        @Size(max = 60, message = "Bairro muito longo.")
        String district,

        @NotBlank(message = "Informe a cidade.")
        @Size(max = 60, message = "Cidade muito longa.")
        String city,

        @NotBlank(message = "Informe a UF.")
        @Pattern(regexp = "^[A-Za-z]{2}$", message = "UF inválida.")
        String state) {
}
