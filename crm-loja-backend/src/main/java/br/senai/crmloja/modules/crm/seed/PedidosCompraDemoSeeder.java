package br.senai.crmloja.modules.crm.seed;

import br.senai.crmloja.core.config.AppProperties;
import br.senai.crmloja.core.identity.AppUser;
import br.senai.crmloja.core.identity.AppUserRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
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

// Pedidos de compra de demonstração (perfis dev e demo, spec crm/11), depois do histórico de vendas (@Order 40) e
// das metas (@Order 50). Só roda com a tabela vazia: nunca mexe num pedido já registrado (nem nos da equipe).
//
// Escolhe as peças que acabam primeiro (menor estoque ÷ venda dos últimos 30 dias) e cria 4 pedidos do Vinicius
// Clemente, para o painel mostrar as etapas (V11): um em trânsito com nota fiscal (chega em 3 dias), um pedido feito
// (chega em 10), um pedido feito 2 dias atrasado e um que chegou hoje e está em conferência.
// Grava direto no banco (e não pelo PurchaseOrderService) porque o pedido atrasado tem data no passado, que a tela
// recusa de propósito.
@Component
@Profile({"dev", "demo"})
@Order(55)
public class PedidosCompraDemoSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PedidosCompraDemoSeeder.class);

    private record Plano(String fornecedor, int diasAteChegar, int diasDesdePedido, int primeiraPeca, int pecas,
                         String obs, String etapa, String nf) {}

    private static final List<Plano> PLANOS = List.of(
            new Plano("Malharia Vale do Sul", 3, 4, 0, 2, "Pedido nº 4471 no fornecedor", "IN_TRANSIT", "000.184.552"),
            new Plano("Confecções Litoral SC", 10, 2, 2, 3, null, "OPEN", null),
            new Plano("Jeans Tubarão", -2, 12, 5, 1, "Fornecedor confirmou por WhatsApp", "OPEN", null),
            new Plano("Acessórios Laguna", 0, 6, 4, 1, null, "CHECKING", "000.031.907"));

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final AppUserRepository users;
    private final AppProperties props;

    public PedidosCompraDemoSeeder(JdbcTemplate jdbc, TransactionTemplate tx, AppUserRepository users,
                                   AppProperties props) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.users = users;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            tx.executeWithoutResult(s -> seed());
        } catch (RuntimeException e) {
            log.error("PedidosCompraDemoSeeder falhou; a aplicação segue no ar", e);
        }
    }

    private void seed() {
        Integer existentes = jdbc.queryForObject("select count(*) from purchase_order", Integer.class);
        if (existentes != null && existentes > 0) return;
        LocalDate hoje = LocalDate.now(props.zone());
        OffsetDateTime desde = hoje.minusDays(29).atStartOfDay(props.zone()).toOffsetDateTime();

        // As peças que vendem e acabam primeiro; a quantidade cobre ~30 dias de venda, em múltiplos de 5.
        List<long[]> pecas = jdbc.query("""
                select p.id, s.sold
                  from product p
                  join (select si.product_id, sum(si.quantity) as sold
                          from sale_item si join sale sa on sa.id = si.sale_id
                         where sa.status = 'CONFIRMED' and sa.confirmed_at >= ?
                         group by si.product_id) s on s.product_id = p.id
                 where p.is_active
                 order by p.stock_qty::numeric / s.sold, p.id
                 limit 6""", (rs, i) -> new long[] {rs.getLong(1), rs.getLong(2)}, desde);
        if (pecas.size() < 6) return;   // sem histórico de vendas ainda: não há o que repor

        Long clemente = users.findByLoginIgnoreCase("vinicius.clemente").map(AppUser::getId).orElse(null);
        for (Plano pl : PLANOS) {
            OffsetDateTime criado = hoje.minusDays(pl.diasDesdePedido()).atTime(10, 0).atZone(props.zone()).toOffsetDateTime();
            // Em trânsito: saiu do fornecedor ontem. Em conferência: chegou hoje de manhã (CHECK do V11 exige as datas).
            OffsetDateTime enviado = pl.etapa().equals("OPEN") ? null : hoje.minusDays(1).atTime(15, 0).atZone(props.zone()).toOffsetDateTime();
            OffsetDateTime chegou = pl.etapa().equals("CHECKING") ? hoje.atTime(9, 30).atZone(props.zone()).toOffsetDateTime() : null;
            Long id = jdbc.queryForObject("""
                    insert into purchase_order (supplier, expected_date, note, created_by, created_at, updated_at, status,
                                                invoice_number, shipped_at, arrived_at)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) returning id""", Long.class,
                    pl.fornecedor(), hoje.plusDays(pl.diasAteChegar()), pl.obs(), clemente, criado, criado, pl.etapa(),
                    pl.nf(), enviado, chegou);
            for (int i = pl.primeiraPeca(); i < pl.primeiraPeca() + pl.pecas(); i++) {
                long qtd = Math.max(5, (pecas.get(i)[1] + 4) / 5 * 5);
                jdbc.update("insert into purchase_order_item (order_id, product_id, quantity) values (?, ?, ?)",
                        id, pecas.get(i)[0], (int) Math.min(qtd, 9999));
            }
        }
        log.warn("Pedidos de compra de demonstração: {} pedidos criados (em trânsito, feitos, atrasado e em conferência)", PLANOS.size());
    }
}
