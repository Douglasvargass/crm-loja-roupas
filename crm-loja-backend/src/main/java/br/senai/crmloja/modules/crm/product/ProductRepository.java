package br.senai.crmloja.modules.crm.product;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    Optional<Product> findByCodeIgnoreCase(String code);

    // Trava a linha do produto até o fim da transação: duas vendas ao mesmo tempo não conseguem baixar o mesmo saldo.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") Long id);

    // Trava todos os produtos da venda sempre na mesma ordem (por id), para duas confirmações simultâneas não se travarem uma à outra.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id in :ids order by p.id")
    List<Product> findAllByIdInForUpdate(@Param("ids") List<Long> ids);

    @Query("select p from Product p join fetch p.category where p.active = true and p.stockQty > 0 "
         + "and p.stockQty <= p.minStock order by p.stockQty, p.name")
    // Estoque baixo (RN10): ativo, com saldo, e saldo menor ou igual ao mínimo. Zerado tem lista própria.
    List<Product> findLowStock();

    @Query("select p from Product p join fetch p.category where p.active = true and p.stockQty = 0 order by p.name")
    List<Product> findOutOfStock();
}
