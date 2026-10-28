package br.senai.crmloja.core.rbac;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ModuleRepository extends JpaRepository<Module, Long> {
    Optional<Module> findByCode(String code);

    // Serializa mudanças de papel no módulo: a checagem "último gerente" não pode correr em paralelo.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Module m where m.code = :code")
    Optional<Module> findByCodeForUpdate(@Param("code") String code);
}
