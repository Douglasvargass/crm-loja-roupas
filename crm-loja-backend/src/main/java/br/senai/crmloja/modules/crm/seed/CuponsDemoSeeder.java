package br.senai.crmloja.modules.crm.seed;

import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

// Cupons de demonstração (perfis dev e demo). Idempotente: só cria o código que ainda não existe.
@Component
@Profile({"dev", "demo"})
@Order(56)
public class CuponsDemoSeeder implements ApplicationRunner {

    private final JdbcTemplate jdbc;

    public CuponsDemoSeeder(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void run(ApplicationArguments args) {
        LocalDate today = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        cupom("BEMVINDO10", "PERCENT", "10", "0", today.minusDays(30), today.plusYears(1), null);
        cupom("ORDEN50", "FIXED", "50", "300", today.minusDays(30), today.plusMonths(6), 100);
    }

    private void cupom(String code, String kind, String value, String min, LocalDate from, LocalDate until, Integer max) {
        jdbc.update("""
                insert into coupon (code, kind, value, min_order, valid_from, valid_until, max_uses)
                select ?, ?, ?::numeric, ?::numeric, ?, ?, ? where not exists (select 1 from coupon where code = ?)""",
                code, kind, value, min, from, until, max, code);
    }
}
