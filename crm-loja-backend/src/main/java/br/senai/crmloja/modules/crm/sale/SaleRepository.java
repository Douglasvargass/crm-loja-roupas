package br.senai.crmloja.modules.crm.sale;

import br.senai.crmloja.modules.crm.sale.dto.PurchaseSummary;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SaleRepository extends JpaRepository<Sale, Long>, JpaSpecificationExecutor<Sale> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Sale s where s.id = :id")
    Optional<Sale> findByIdForUpdate(@Param("id") Long id);

    long countByCustomer_Id(Long customerId);

    @Query("select s from Sale s where s.customer.id = :customerId order by s.createdAt desc, s.id desc")
    List<Sale> findHistoryOf(@Param("customerId") Long customerId);

    @Query("""
        select new br.senai.crmloja.modules.crm.sale.dto.PurchaseSummary(count(s), sum(s.total), max(s.confirmedAt))
          from Sale s
         where s.customer.id = :customerId and s.status = :status""")
    PurchaseSummary purchaseSummary(@Param("customerId") Long customerId, @Param("status") SaleStatus status);

    default PurchaseSummary purchaseSummary(Long customerId) {
        return purchaseSummary(customerId, SaleStatus.CONFIRMED);
    }
}
