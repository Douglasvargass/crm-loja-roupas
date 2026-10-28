package br.senai.crmloja.core.rbac;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModuleRoleRepository extends JpaRepository<ModuleRole, Long> {
    List<ModuleRole> findByModuleId(Long moduleId);
    Optional<ModuleRole> findByModuleIdAndCode(Long moduleId, String code);
}
