package br.senai.crmloja.modules.crm.category.dto;

import br.senai.crmloja.modules.crm.category.Category;

// productCount vai junto para a tela mostrar quantos produtos usam a categoria e explicar por que ela não pode ser excluída.
public record CategoryResponse(Long id, String name, String description, boolean active, long productCount) {

    public static CategoryResponse from(Category c, long productCount) {
        return new CategoryResponse(c.getId(), c.getName(), c.getDescription(), c.isActive(), productCount);
    }
}
