package br.senai.crmloja.modules.crm.category.dto;

import br.senai.crmloja.modules.crm.category.Category;

public record CategoryResponse(Long id, String name, String description, boolean active, long productCount) {

    public static CategoryResponse from(Category c, long productCount) {
        return new CategoryResponse(c.getId(), c.getName(), c.getDescription(), c.isActive(), productCount);
    }
}
