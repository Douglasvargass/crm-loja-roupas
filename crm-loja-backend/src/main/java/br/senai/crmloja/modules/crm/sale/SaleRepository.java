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

    // Trava a venda ao confirmar ou cancelar: o mesmo clique duas vezes não baixa (nem devolve) o estoque em dobro.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Sale s where s.id = :id")
    Optional<Sale> findByIdForUpdate(@Param("id") Long id);

    // Usado para decidir se o cliente pode ser excluído (sem vendas) ou só inativado (RN02).
    long countByCustomer_Id(Long customerId);

    // HU06: todas as vendas do cliente, da mais nova para a mais antiga, inclusive canceladas.
    @Query("select s from Sale s where s.customer.id = :customerId order by s.createdAt desc, s.id desc")
    List<Sale> findHistoryOf(@Param("customerId") Long customerId);

    @Query("""
        select new br.senai.crmloja.modules.crm.sale.dto.PurchaseSummary(count(s), sum(s.total), max(s.confirmedAt))
          from Sale s
         where s.customer.id = :customerId and s.status = :status""")
    // Resumo da ficha do cliente: quantas compras, quanto gastou e a última compra. Só conta venda confirmada.
    PurchaseSummary purchaseSummary(@Param("customerId") Long customerId, @Param("status") SaleStatus status);

    default PurchaseSummary purchaseSummary(Long customerId) {
        return purchaseSummary(customerId, SaleStatus.CONFIRMED);
    }
}
