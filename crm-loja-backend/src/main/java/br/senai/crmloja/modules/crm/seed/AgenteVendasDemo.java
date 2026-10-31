package br.senai.crmloja.modules.crm.seed;

import br.senai.crmloja.core.config.AppProperties;
import br.senai.crmloja.core.identity.AppUser;
import br.senai.crmloja.core.identity.AppUserRepository;
import br.senai.crmloja.modules.crm.assistant.AssistantService;
import br.senai.crmloja.modules.crm.seed.HistoricoVendasSeeder.Catalogo;
import br.senai.crmloja.modules.crm.seed.HistoricoVendasSeeder.ItemLinha;
import br.senai.crmloja.modules.crm.seed.HistoricoVendasSeeder.Movimento;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Agente de vendas da demonstração (spec crm/08). Todo dia que passou e ainda não tem vendas simuladas, ele:
 * <ol>
 *   <li>olha como a loja vendeu nos últimos 14 dias;</li>
 *   <li>pede à IA (se houver provedor configurado, de graça no Groq ou no Gemini) o "clima" do dia: mais ou menos
 *       movimento, uma categoria em destaque e uma frase com o motivo;</li>
 *   <li>gera as vendas com o MESMO motor do histórico ({@link HistoricoVendasSeeder}): horário do shopping, peso do
 *       dia da semana, feriados, peso de cada vendedor, reposição de terça. Sem IA, o dia sai no padrão.</li>
 * </ol>
 * A IA nunca grava nada: ela só escolhe números que este código limita ({@link HumorDoDia}).
 *
 * <p>Só fecha dias inteiros que já passaram (nunca hoje), e pula o dia se houve uso real do sistema depois dele:
 * inventar vendas "no passado" depois de uma venda de verdade quebraria a sequência do saldo no histórico de estoque.
 *
 * <p>Roda na subida (o Render grátis dorme e acorda) e de hora em hora enquanto estiver acordado. Perfis dev e demo.
 */
@Component
@Profile({"dev", "demo"})
@Order(60)
public class AgenteVendasDemo implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AgenteVendasDemo.class);

    // Se o servidor ficar parado muito tempo, fecha no máximo isto por vez (o resto na próxima rodada).
    static final int MAX_DIAS_POR_RODADA = 45;
    // Fora da estação de frio a loja não repõe jaqueta (mesma regra do histórico).
    private static final Set<Integer> MESES_SEM_JAQUETA = Set.of(10, 11, 12, 1, 2);

    private static final String SISTEMA = """
            Você simula o movimento de uma loja de roupas de shopping em Tubarão/SC, para dados de demonstração de um \
            sistema escolar. Para o dia pedido, escolha o "clima" do dia com base no dia da semana, na data e no \
            movimento recente. Seja realista e varie: a maioria dos dias é comum (fator entre 0.9 e 1.1); dias \
            especiais (chuva forte, promoção, evento no shopping, véspera de data comemorativa, pagamento) podem ir \
            de 0.6 a 1.4. Responda SÓ com um JSON, sem texto antes ou depois, neste formato:
            {"fator": 1.0, "categoria_destaque": "Camisetas|Calças|Vestidos|Jaquetas|Acessórios|null", \
            "fator_categoria": 1.0, "motivo": "uma frase curta em português explicando o dia"}""";

    private final HistoricoVendasSeeder motor;
    private final AssistantService ia;
    private final AppUserRepository usuarios;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final AppProperties props;
    private final ObjectMapper json;
    private final ReentrantLock rodando = new ReentrantLock();

    public AgenteVendasDemo(HistoricoVendasSeeder motor, AssistantService ia, AppUserRepository usuarios,
                            JdbcTemplate jdbc, TransactionTemplate tx, AppProperties props, ObjectMapper json) {
        this.motor = motor;
        this.ia = ia;
        this.usuarios = usuarios;
        this.jdbc = jdbc;
        this.tx = tx;
        this.props = props;
        this.json = json;
    }

    // Em segundo plano: com IA, cada dia leva alguns segundos, e o site não pode esperar isso para abrir.
    @Override
    public void run(ApplicationArguments args) {
        Thread t = new Thread(this::colocarEmDia, "agente-vendas-demo");
        t.setDaemon(true);
        t.start();
    }

    // De hora em hora, no minuto 5: se o servidor passou da meia-noite acordado, o dia anterior é fechado.
    @Scheduled(cron = "0 5 * * * *", zone = "America/Sao_Paulo")
    public void deHoraEmHora() {
        colocarEmDia();
    }

    /** Fecha todos os dias que faltam até ontem. Uma rodada por vez; cada dia na sua própria transação. */
    public void colocarEmDia() {
        if (!rodando.tryLock()) return;
        try {
            LocalDate ontem = LocalDate.now(props.zone()).minusDays(1);
            LocalDate ultimo = ultimoDiaCoberto();
            if (ultimo == null) return;   // banco sem histórico ainda: nada a continuar
            int feitos = 0;
            for (LocalDate dia = ultimo.plusDays(1); !dia.isAfter(ontem) && feitos < MAX_DIAS_POR_RODADA;
                 dia = dia.plusDays(1)) {
                LocalDate d = dia;
                try {
                    tx.executeWithoutResult(s -> fecharDia(d));
                } catch (RuntimeException e) {
                    // Um dia com erro não trava os outros nem derruba a aplicação; volta a tentar na próxima rodada.
                    log.error("Agente de vendas: falha ao fechar {}", d, e);
                    return;
                }
                feitos++;
            }
            if (feitos > 0) log.warn("Agente de vendas: {} dia(s) fechado(s) até {}", feitos, ontem);
        } finally {
            rodando.unlock();
        }
    }

    // Último dia do diário; sem diário, o dia da última venda (o histórico de 3 meses termina no dia da subida).
    private LocalDate ultimoDiaCoberto() {
        LocalDate doDiario = jdbc.queryForObject("select max(day) from demo_agent_day", LocalDate.class);
        if (doDiario != null) return doDiario;
        OffsetDateTime ultimaVenda = jdbc.queryForObject("select max(created_at) from sale", OffsetDateTime.class);
        return ultimaVenda == null ? null : ultimaVenda.atZoneSameInstant(props.zone()).toLocalDate();
    }

    void fecharDia(LocalDate dia) {
        OffsetDateTime inicioDoDiaSeguinte = dia.plusDays(1).atStartOfDay(props.zone()).toOffsetDateTime();
        Boolean usoReal = jdbc.queryForObject("""
                select exists (select 1 from stock_movement where created_at >= ?)
                    or exists (select 1 from sale where created_at >= ? or confirmed_at >= ? or cancelled_at >= ?)""",
                Boolean.class, inicioDoDiaSeguinte, inicioDoDiaSeguinte, inicioDoDiaSeguinte, inicioDoDiaSeguinte);
        if (Boolean.TRUE.equals(usoReal)) {
            registrar(dia, "SKIPPED", "NONE", null, "O sistema foi usado depois deste dia: o agente não inventa um "
                    + "passado que contradiga o estoque de hoje.", 0, BigDecimal.ZERO);
            return;
        }

        // A IA vem ANTES da trava: é uma chamada HTTP que pode levar mais de um minuto, e o lock abaixo
        // segura todos os produtos — enquanto ela pensasse, nenhuma venda de verdade conseguiria confirmar.
        HumorDoDia humor = humorDoDia(dia);

        // Trava o catálogo: uma venda de verdade feita agora espera o agente terminar, e o saldo não se perde.
        jdbc.queryForList("select id from product order by id for update", Long.class);
        Set<Long> ativos = new HashSet<>(jdbc.queryForList("select id from product where is_active", Long.class));
        List<Catalogo> catalogo = motor.carregarCatalogo().stream().filter(c -> ativos.contains(c.id())).toList();
        List<Long> vendedores = vendedoresAtivos();
        if (catalogo.isEmpty() || vendedores.isEmpty()) {
            registrar(dia, "SKIPPED", "NONE", null, "Sem produtos ativos ou sem vendedores ativos para simular.",
                    0, BigDecimal.ZERO);
            return;
        }

        List<Catalogo> comDestaque = catalogo.stream().map(c -> humor.familiaDestaque() != null
                && humor.familiaDestaque().equals(c.familia())
                ? new Catalogo(c.id(), c.codigo(), c.nome(), c.preco(), c.familia(), c.peso() * humor.fatorDestaque(),
                        c.estoqueInicial(), c.minStock())
                : c).toList();

        Map<Long, Integer> saldo = new HashMap<>();
        catalogo.forEach(c -> saldo.put(c.id(), c.estoqueInicial()));
        Map<Long, Integer> vendidoNaSemana = vendidoNosUltimos7Dias(dia);
        List<Long> compradores = jdbc.queryForList("select id from customer where is_active order by id", Long.class);
        Long gerente = usuarios.findByLoginIgnoreCase("guilherme.dower").map(AppUser::getId).orElse(vendedores.get(0));

        // Semente pelo dia: rodar de novo o mesmo dia (num banco recriado) dá o mesmo resultado.
        Random rnd = new Random(HistoricoVendasSeeder.SEMENTE ^ dia.toEpochDay());
        Random rndVendedor = new Random(HistoricoVendasSeeder.SEMENTE + 7 + dia.toEpochDay());
        boolean feriado = Feriados.ehFeriado(dia);

        List<ItemLinha> itens = new ArrayList<>();
        List<Movimento> movimentos = new ArrayList<>();
        List<OffsetDateTime> horarios = new ArrayList<>();
        int quantas = (int) Math.max(1, Math.round(motor.vendasDoDia(dia, feriado, rnd) * humor.fator()));
        for (int i = 0; i < quantas; i++) horarios.add(motor.horario(dia, feriado, rnd));
        horarios.sort(Comparator.naturalOrder());

        // Reposição de terça: antes de a loja abrir, com o giro real dos últimos 7 dias.
        if (dia.getDayOfWeek() == DayOfWeek.TUESDAY) {
            motor.repor(catalogo, saldo, vendidoNaSemana, movimentos, gerente, motor.instante(dia, 8, 30, rnd),
                    MESES_SEM_JAQUETA.contains(dia.getMonthValue()), rnd);
        }

        int vendas = 0;
        BigDecimal faturamento = BigDecimal.ZERO;
        for (OffsetDateTime quando : horarios) {
            List<ItemLinha> linhas = motor.montarCarrinho(comDestaque, saldo, dia, rnd);
            if (linhas.isEmpty()) continue;
            BigDecimal total = linhas.stream().map(ItemLinha::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);
            Long cliente = !compradores.isEmpty() && rnd.nextDouble() < 0.58
                    ? compradores.get(rnd.nextInt(compradores.size())) : null;
            Long vendedor = motor.sortearVendedor(vendedores, rndVendedor);
            Long saleId = motor.inserirVenda(cliente, total, quando, quando.minusMinutes(2L + rnd.nextInt(12)), vendedor);
            for (ItemLinha l : linhas) {
                itens.add(l.comVenda(saleId));
                saldo.merge(l.productId(), -l.quantity(), Integer::sum);
                movimentos.add(new Movimento(l.productId(), saleId, "SALE", -l.quantity(), saldo.get(l.productId()),
                        null, vendedor, quando));
            }
            vendas++;
            faturamento = faturamento.add(total);
        }

        motor.gravarItens(itens);
        motor.gravarMovimentos(movimentos);
        for (Catalogo c : catalogo) {
            if (!Objects.equals(saldo.get(c.id()), c.estoqueInicial())) {
                jdbc.update("update product set stock_qty = ?, updated_at = now() where id = ?", saldo.get(c.id()), c.id());
            }
        }
        registrar(dia, "GENERATED", humor.fonte(), humor, humor.motivo(), vendas, faturamento);
    }

    // Mesma ordem e pesos do histórico (5 : 4 : 3); quem foi desativado sai da escala.
    private List<Long> vendedoresAtivos() {
        List<Long> ids = new ArrayList<>();
        for (String login : DevDataSeeder.vendedores()) {
            usuarios.findByLoginIgnoreCase(login).filter(AppUser::isActive).map(AppUser::getId).ifPresent(ids::add);
        }
        return ids;
    }

    private Map<Long, Integer> vendidoNosUltimos7Dias(LocalDate dia) {
        Map<Long, Integer> giro = new HashMap<>();
        jdbc.query("""
                select product_id, sum(-quantity) as qtd from stock_movement
                 where type = 'SALE' and created_at >= ? and created_at < ?
                 group by product_id""",
                rs -> { giro.put(rs.getLong("product_id"), rs.getInt("qtd")); },
                dia.minusDays(7).atStartOfDay(props.zone()).toOffsetDateTime(),
                dia.atStartOfDay(props.zone()).toOffsetDateTime());
        return giro;
    }

    // ------------------------------------------------------------------------------------------------ IA

    private HumorDoDia humorDoDia(LocalDate dia) {
        return ia.completeOnce(SISTEMA, contexto(dia))
                .flatMap(r -> HumorDoDia.daResposta(r, json))
                .orElseGet(() -> HumorDoDia.padrao(ia.enabled()
                        ? "Dia comum: a IA não respondeu a tempo, então o agente seguiu o padrão da loja."
                        : "Dia comum: IA desligada, o agente seguiu o padrão da loja."));
    }

    // O que a IA recebe: só números agregados da loja, nenhum nome ou CPF de cliente.
    private String contexto(LocalDate dia) {
        StringBuilder sb = new StringBuilder();
        Locale ptBr = Locale.forLanguageTag("pt-BR");
        sb.append("Dia a simular: ").append(dia).append(" (")
                .append(dia.getDayOfWeek().getDisplayName(TextStyle.FULL, ptBr)).append(")");
        if (Feriados.ehFeriado(dia)) sb.append(", feriado (shopping abre das 14h às 21h)");
        double comercial = Feriados.pesoComercial(dia);
        if (comercial > 1.0) sb.append(", perto de uma data comemorativa (movimento esperado ×")
                .append(String.format(Locale.ROOT, "%.2f", comercial)).append(")");
        sb.append(".\nVendas confirmadas nos últimos 14 dias (dia: quantidade, faturamento):\n");
        jdbc.query("""
                select (confirmed_at at time zone ?)::date as dia, count(*) as qtd, sum(total) as fat
                  from sale where status = 'CONFIRMED' and confirmed_at >= ? and confirmed_at < ?
                 group by 1 order by 1""",
                rs -> {
                    sb.append("- ").append(rs.getObject("dia", LocalDate.class)).append(": ").append(rs.getLong("qtd"))
                            .append(" vendas, R$ ").append(rs.getBigDecimal("fat")).append('\n');
                },
                props.timeZone(), dia.minusDays(14).atStartOfDay(props.zone()).toOffsetDateTime(),
                dia.atStartOfDay(props.zone()).toOffsetDateTime());
        sb.append("Peças vendidas por categoria nesses 14 dias: ");
        List<String> partes = new ArrayList<>();
        jdbc.query("""
                select c.name, sum(si.quantity) as qtd
                  from sale_item si join sale s on s.id = si.sale_id
                  join product p on p.id = si.product_id join category c on c.id = p.category_id
                 where s.status = 'CONFIRMED' and s.confirmed_at >= ? and s.confirmed_at < ?
                 group by c.name order by qtd desc""",
                rs -> { partes.add(rs.getString("name") + " " + rs.getLong("qtd")); },
                dia.minusDays(14).atStartOfDay(props.zone()).toOffsetDateTime(),
                dia.atStartOfDay(props.zone()).toOffsetDateTime());
        sb.append(partes.isEmpty() ? "sem dados" : String.join(", ", partes)).append('.');
        return sb.toString();
    }

    private void registrar(LocalDate dia, String status, String fonte, HumorDoDia humor, String motivo, int vendas,
                           BigDecimal faturamento) {
        jdbc.update("""
                insert into demo_agent_day (day, status, source, demand_factor, highlight_family, highlight_factor,
                                            reason, sales_created, revenue)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                dia, status, fonte,
                humor == null ? null : BigDecimal.valueOf(humor.fator()),
                humor == null ? null : humor.familiaDestaque(),
                humor == null || humor.familiaDestaque() == null ? null : BigDecimal.valueOf(humor.fatorDestaque()),
                HumorDoDia.motivo(motivo), vendas, faturamento.setScale(2, RoundingMode.HALF_UP));
    }
}
