package br.senai.crmloja.modules.crm.stock;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long>,
        JpaSpecificationExecutor<StockMovement> {

    // Ordena por data, não por id: histórico carregado de uma vez (carga inicial, importação) tem id
    // crescente mas data antiga, e ordenar por id mostraria o movimento mais velho como o mais recente.
    @EntityGraph(attributePaths = {"product", "sale"})
    List<StockMovement> findByProduct_IdOrderByCreatedAtDescIdDesc(Long productId, Pageable pageable);

    // Só tipo e motivo: carregar as entidades as deixaria presas ao produto que vai ser apagado.
    @Query("select m.type as type, m.reason as reason from StockMovement m where m.product.id = :productId")
    List<MovementKind> kindsOf(@Param("productId") Long productId);

    interface MovementKind {
        StockMovementType getType();
        String getReason();
    }

    long countByProduct_Id(Long productId);
}
