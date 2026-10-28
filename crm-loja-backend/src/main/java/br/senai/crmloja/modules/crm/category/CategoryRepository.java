package br.senai.crmloja.modules.crm.category;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findByNameIgnoreCase(String name);

    // [id, quantidade de produtos] numa consulta só, para a lista não fazer N+1.
    @Query("select p.category.id, count(p) from Product p group by p.category.id")
    List<Object[]> countProductsByCategory();
}
