package br.senai.crmloja.core.identity;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    // Login sem diferenciar maiúscula: "Douglas.Vargas" e "douglas.vargas" são a mesma pessoa (o índice único do V1 também é sem caixa).
    Optional<AppUser> findByLoginIgnoreCase(String login);
}
