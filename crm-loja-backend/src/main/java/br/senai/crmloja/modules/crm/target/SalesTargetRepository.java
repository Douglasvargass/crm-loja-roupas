package br.senai.crmloja.modules.crm.target;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SalesTargetRepository extends JpaRepository<SalesTarget, Long> {
    // Uma meta por vendedor e mês (índice único do V6); o mês é sempre o dia 1.
    Optional<SalesTarget> findBySellerIdAndPeriodMonth(Long sellerId, LocalDate periodMonth);
}
