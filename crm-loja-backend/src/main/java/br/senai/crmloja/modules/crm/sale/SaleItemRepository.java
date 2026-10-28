package br.senai.crmloja.modules.crm.sale;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SaleItemRepository extends JpaRepository<SaleItem, Long> {

    long countByProduct_Id(Long productId);

    // Status vai como parâmetro: um literal de enum no JPQL vira cast para um tipo "SaleStatus"
    // que não existe no Postgres (o tipo real é sale_status).
    @Query("""
        select coalesce(sum(i.quantity), 0) from SaleItem i
         where i.product.id = :productId and i.sale.status = :status""")
    long totalSoldOf(@Param("productId") Long productId, @Param("status") SaleStatus status);

    default long totalSoldOf(Long productId) {
        return totalSoldOf(productId, SaleStatus.CONFIRMED);
    }
}
