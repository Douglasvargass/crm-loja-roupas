package br.senai.crmloja.modules.crm.request;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockRequestRepository extends JpaRepository<StockRequest, Long> {

    // Trava o chamado ao fechar: duas pessoas não resolvem (ou cancelam) o mesmo chamado ao mesmo tempo.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from StockRequest r where r.id = :id")
    Optional<StockRequest> findByIdForUpdate(@Param("id") Long id);

    // A peça chegou: atende os chamados abertos dela do mais antigo para o mais novo (RN35).
    List<StockRequest> findByProductIdAndStatusOrderByIdAsc(Long productId, StockRequest.Status status);

    // Sino do vendedor: chamados dele que fecharam e ele ainda não viu.
    List<StockRequest> findByOpenedByAndStatusNotAndRequesterSeenAtIsNull(Long openedBy, StockRequest.Status status);
}
