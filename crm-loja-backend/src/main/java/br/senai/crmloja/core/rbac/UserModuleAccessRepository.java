package br.senai.crmloja.core.rbac;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserModuleAccessRepository extends JpaRepository<UserModuleAccess, Long> {
    Optional<UserModuleAccess> findByUserIdAndModuleId(Long userId, Long moduleId);
    List<UserModuleAccess> findByUserId(Long userId);
    List<UserModuleAccess> findByModuleId(Long moduleId);

    // Super admin não conta: a regra é a loja ter pelo menos um gerente "de verdade" ativo.
    @Query("""
        select count(a) from UserModuleAccess a, AppUser u, ModuleRole r
         where a.userId = u.id and a.moduleRoleId = r.id
           and a.moduleId = :moduleId and r.admin = true
           and a.active = true and u.active = true""")
    long countActiveAdmins(@Param("moduleId") Long moduleId);
}
