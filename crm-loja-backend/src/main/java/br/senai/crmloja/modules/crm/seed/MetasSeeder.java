package br.senai.crmloja.modules.crm.seed;

import br.senai.crmloja.core.config.AppProperties;
import br.senai.crmloja.core.identity.AppUser;
import br.senai.crmloja.core.identity.AppUserRepository;
import br.senai.crmloja.modules.crm.target.SalesTarget;
import br.senai.crmloja.modules.crm.target.SalesTargetRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

// Metas de demonstração (perfis dev e demo), depois do histórico de vendas (@Order 40). Para cada vendedor da
// equipe, nos últimos 3 meses e no mês atual, cria a meta que ainda não existir. Nunca altera uma meta já gravada.
//
// As metas saem do próprio histórico, para a demonstração ter os dois casos: o 1º vendedor bate a meta e o 2º fica
// abaixo (com um 3º vendedor, ele fica perto). Mês passado: meta = realizado × fator. Mês atual: meta = realizado do mês anterior ×
// fator (o mês atual ainda está em andamento). Arredondado para R$ 500.
@Component
@Profile({"dev", "demo"})
@Order(50)
public class MetasSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MetasSeeder.class);

    // Mesma ordem de DevDataSeeder.vendedores(): bate, fica abaixo, fica perto.
    private static final double[] FATORES = {0.92, 1.12, 1.00};
    private static final BigDecimal ARREDONDA = BigDecimal.valueOf(500);

    private final AppUserRepository users;
    private final SalesTargetRepository targets;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final AppProperties props;

    public MetasSeeder(AppUserRepository users, SalesTargetRepository targets, JdbcTemplate jdbc,
                       TransactionTemplate tx, AppProperties props) {
        this.users = users;
        this.targets = targets;
        this.jdbc = jdbc;
        this.tx = tx;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            tx.executeWithoutResult(s -> seed());
        } catch (RuntimeException e) {
            log.error("MetasSeeder falhou; a aplicação segue no ar", e);
        }
    }

    private void seed() {
        YearMonth atual = YearMonth.now(props.zone());
        Long gerente = users.findByLoginIgnoreCase("guilherme.dower").map(AppUser::getId).orElse(null);
        List<String> vendedores = DevDataSeeder.vendedores();
        int criadas = 0;
        for (int i = 0; i < vendedores.size(); i++) {
            Long id = users.findByLoginIgnoreCase(vendedores.get(i)).map(AppUser::getId).orElse(null);
            if (id == null) continue;
            double fator = FATORES[Math.min(i, FATORES.length - 1)];
            for (YearMonth mes = atual.minusMonths(3); !mes.isAfter(atual); mes = mes.plusMonths(1)) {
                LocalDate primeiro = mes.atDay(1);
                if (targets.findBySellerIdAndPeriodMonth(id, primeiro).isPresent()) continue;
                BigDecimal base = realizado(id, mes.equals(atual) ? mes.minusMonths(1) : mes);
                if (base.signum() <= 0) continue;   // sem vendas para servir de base: fica sem meta
                targets.save(new SalesTarget(id, primeiro, arredonda(base.multiply(BigDecimal.valueOf(fator))), gerente));
                criadas++;
            }
        }
        if (criadas > 0) log.warn("Metas de demonstração: {} metas criadas para {} vendedores", criadas, vendedores.size());
    }

    private BigDecimal realizado(Long vendedor, YearMonth mes) {
        BigDecimal v = jdbc.queryForObject("""
                select coalesce(sum(total), 0) from sale
                 where created_by = ? and status = 'CONFIRMED' and confirmed_at >= ? and confirmed_at < ?""",
                BigDecimal.class, vendedor,
                mes.atDay(1).atStartOfDay(props.zone()).toOffsetDateTime(),
                mes.plusMonths(1).atDay(1).atStartOfDay(props.zone()).toOffsetDateTime());
        return v == null ? BigDecimal.ZERO : v;
    }

    private static BigDecimal arredonda(BigDecimal v) {
        BigDecimal n = v.divide(ARREDONDA, 0, RoundingMode.HALF_UP).max(BigDecimal.ONE);
        return n.multiply(ARREDONDA).setScale(2, RoundingMode.HALF_UP);
    }
}
