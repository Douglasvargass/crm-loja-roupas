package br.senai.crmloja.modules.crm.category;

import br.senai.crmloja.core.http.BusinessRuleException;
import br.senai.crmloja.core.http.NotFoundException;
import br.senai.crmloja.core.text.TextSearch;
import br.senai.crmloja.modules.crm.category.dto.CategoryRequest;
import br.senai.crmloja.modules.crm.category.dto.CategoryResponse;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {

    private final CategoryRepository categories;

    public CategoryService(CategoryRepository categories) { this.categories = categories; }

    @Transactional(readOnly = true)
    public List<CategoryResponse> list() {
        Map<Long, Long> counts = productCounts();
        return categories.findAll().stream()
                .sorted(Comparator.comparing(Category::getName, String.CASE_INSENSITIVE_ORDER))
                .map(c -> CategoryResponse.from(c, counts.getOrDefault(c.getId(), 0L)))
                .toList();
    }

    @Transactional
    public CategoryResponse create(CategoryRequest req) {
        String name = req.name().trim();
        ensureNameFree(name, null);
        Category c = new Category(name, blankToNull(req.description()));
        if (req.active() != null) c.setActive(req.active());
        return CategoryResponse.from(categories.save(c), 0);
    }

    @Transactional
    public CategoryResponse update(Long id, CategoryRequest req) {
        Category c = find(id);
        String name = req.name().trim();
        ensureNameFree(name, id);
        c.setName(name);
        c.setDescription(blankToNull(req.description()));
        if (req.active() != null) c.setActive(req.active());
        return CategoryResponse.from(c, productCounts().getOrDefault(id, 0L));
    }

    // RN04: categoria com produtos fica; a FK RESTRICT garante o mesmo no banco.
    @Transactional
    public void delete(Long id) {
        Category c = find(id);
        long count = productCounts().getOrDefault(id, 0L);
        if (count > 0) {
            throw new BusinessRuleException("category_has_products",
                    "Categoria possui " + count + " produto(s) vinculados e não pode ser excluída.");
        }
        categories.delete(c);
    }

    public Category find(Long id) {
        return categories.findById(id).orElseThrow(() -> new NotFoundException("Categoria não encontrada."));
    }

    private void ensureNameFree(String name, Long selfId) {
        categories.findByNameIgnoreCase(name)
                .filter(other -> !other.getId().equals(selfId))
                .ifPresent(other -> {
                    throw new BusinessRuleException("category_name_taken", "Já existe uma categoria com este nome.");
                });
    }

    private Map<Long, Long> productCounts() {
        return categories.countProductsByCategory().stream()
                .collect(Collectors.toMap(r -> (Long) r[0], r -> (Long) r[1]));
    }

    private static String blankToNull(String s) {
        return TextSearch.isBlank(s) ? null : s.trim();
    }
}
