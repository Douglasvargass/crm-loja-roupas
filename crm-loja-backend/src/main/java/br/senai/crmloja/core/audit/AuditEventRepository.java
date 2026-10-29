package br.senai.crmloja.core.audit;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

// A auditoria só cresce: nada aqui apaga ou altera evento (o banco também recusa, ver V2).
public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {
    // Linha do tempo de um registro (ex.: tudo que aconteceu com a venda 42), na ordem em que aconteceu.
    List<AuditEvent> findByEntityTypeAndEntityIdOrderByIdAsc(String entityType, Long entityId);
}
