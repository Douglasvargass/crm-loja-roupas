package br.senai.crmloja.core.identity;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// Guarda só o hash do refresh token: se o banco vazar, os tokens não servem para entrar.
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("update RefreshToken t set t.revokedAt = :now where t.userId = :userId "
         + "and t.sessionStartedAt = :session and t.revokedAt is null")
    // Sair: revoga a sessão inteira deste navegador (todos os tokens da mesma rotação), não só o último.
    int revokeSession(@Param("userId") Long userId, @Param("session") OffsetDateTime session,
                      @Param("now") OffsetDateTime now);

    // Trocar a senha ou resetar a conta derruba todas as sessões abertas da pessoa, em qualquer navegador.
    @Modifying
    @Query("update RefreshToken t set t.revokedAt = :now where t.userId = :userId and t.revokedAt is null")
    int revokeAllOfUser(@Param("userId") Long userId, @Param("now") OffsetDateTime now);
}
