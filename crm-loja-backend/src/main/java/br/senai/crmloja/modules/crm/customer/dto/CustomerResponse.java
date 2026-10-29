package br.senai.crmloja.modules.crm.customer.dto;

import br.senai.crmloja.modules.crm.customer.Customer;
import java.time.OffsetDateTime;

// O que a tela recebe de um cliente. A entidade nunca sai direto pela API: assim um campo novo no banco não vaza sem querer.
public record CustomerResponse(Long id, String fullName, String cpf, String phone, String email,
                               String zipCode, String street, String number, String complement,
                               String district, String city, String state, boolean active,
                               OffsetDateTime createdAt) {

    public static CustomerResponse from(Customer c) {
        return new CustomerResponse(c.getId(), c.getFullName(), c.getCpf(), c.getPhone(), c.getEmail(),
                c.getZipCode(), c.getStreet(), c.getNumber(), c.getComplement(), c.getDistrict(),
                c.getCity(), c.getState(), c.isActive(), c.getCreatedAt());
    }
}
