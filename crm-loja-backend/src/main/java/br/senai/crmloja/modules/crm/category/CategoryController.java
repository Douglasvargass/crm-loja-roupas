package br.senai.crmloja.modules.crm.category;

import br.senai.crmloja.modules.crm.category.dto.CategoryRequest;
import br.senai.crmloja.modules.crm.category.dto.CategoryResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// HU07 e HU08. Cadastrar, alterar e excluir é do gerente; listar é de todos, porque vendedor e estoquista
// precisam das categorias para filtrar produtos. A regra (nome único, vínculo com produto) fica no CategoryService.
@RestController
@RequestMapping("/crm/categories")
@PreAuthorize("hasRole('MANAGER')")
public class CategoryController {

    private final CategoryService service;

    public CategoryController(CategoryService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("hasAnyRole('MANAGER','SELLER','STOCK_KEEPER')")
    public List<CategoryResponse> list() {
        return service.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse create(@Valid @RequestBody CategoryRequest body) {
        return service.create(body);
    }

    @PutMapping("/{id}")
    public CategoryResponse update(@PathVariable Long id, @Valid @RequestBody CategoryRequest body) {
        return service.update(id, body);
    }

    // 204 sem corpo quando apaga; com produtos vinculados o Service responde 409 com a frase para a tela.
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
