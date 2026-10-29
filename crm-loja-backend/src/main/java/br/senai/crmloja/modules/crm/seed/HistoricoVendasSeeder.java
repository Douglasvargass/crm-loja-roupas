package br.senai.crmloja.modules.crm.seed;

import br.senai.crmloja.core.config.AppProperties;
import br.senai.crmloja.core.identity.AppUserRepository;
import br.senai.crmloja.modules.crm.customer.CustomerService;
import br.senai.crmloja.modules.crm.customer.dto.CustomerRequest;
import br.senai.crmloja.modules.crm.product.Product;
import br.senai.crmloja.modules.crm.product.ProductRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Gera 3 meses de histórico de vendas de uma loja de roupas de shopping.
 *
 * <p>Por que SQL direto e não os Services: {@code SaleService.confirm} grava {@code confirmed_at = now()}, e o
 * trigger {@code trg_stock_movement_immutable} recusa UPDATE em {@code stock_movement}. Ou seja, não existe
 * caminho pela aplicação que produza uma venda datada de agosto com a movimentação de estoque na mesma data.
 * INSERT, porém, o trigger permite — então o histórico é inserido já com as datas certas.
 *
 * <p>Como o SQL não passa pelas validações dos Services, este seeder assume as invariantes à mão:
 * {@code subtotal = unit_price * quantity}, {@code sale.total = soma dos subtotais},
 * {@code balance_after} acumulado em ordem cronológica e {@code product.stock_qty} fechando com o último saldo.
 * O modelo de demanda está documentado em {@code crm-loja-docs/docs/14-dados-de-demonstracao.md}.
 */
@Component
@Profile({"dev", "demo"})
@Order(40)
public class HistoricoVendasSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(HistoricoVendasSeeder.class);

    /** Semente fixa: o mesmo banco sai igual em toda execução, na máquina do aluno e na banca. */
    private static final long SEMENTE = 20_260_801L;

    private static final int DIAS = 89;
    private static final double VENDAS_POR_DIA = 20.0;

    /** Farol Shopping: lojas 10h–22h de segunda a sábado; 14h–21h domingo e feriado. */
    private static final int ABRE_SEMANA = 10;
    private static final int ABRE_DOMINGO = 14;

    /** Fluxo por hora. Semana tem pico no almoço e à noite; sábado concentra à tarde. */
    private static final int[] HORAS_SEMANA = {3, 5, 8, 8, 6, 6, 7, 9, 12, 13, 12, 7};
    private static final int[] HORAS_SABADO = {4, 8, 9, 9, 11, 12, 12, 11, 9, 8, 6, 4};
    private static final int[] HORAS_DOMINGO = {11, 15, 17, 17, 15, 14, 11};

    private static final String[] MOTIVOS_CANCELAMENTO = {
        "Cliente desistiu da compra", "Peça com defeito identificado na conferência",
        "Troca por outro tamanho", "Pagamento não autorizado", "Cliente encontrou tamanho errado"
    };

    private final ProductRepository produtos;
    private final CustomerService clientes;
    private final AppUserRepository usuarios;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final AppProperties props;

    public HistoricoVendasSeeder(ProductRepository produtos, CustomerService clientes, AppUserRepository usuarios,
                                 JdbcTemplate jdbc, TransactionTemplate tx, AppProperties props) {
        this.produtos = produtos;
        this.clientes = clientes;
        this.usuarios = usuarios;
        this.jdbc = jdbc;
        this.tx = tx;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            tx.executeWithoutResult(s -> {
                LocalDate hoje = LocalDate.now(props.zone());
                LocalDate inicio = hoje.minusDays(DIAS - 1L);
                OffsetDateTime corte = inicio.atStartOfDay(props.zone()).toOffsetDateTime();
                Long antigas = jdbc.queryForObject(
                        "select count(*) from sale where created_at < ?", Long.class, corte.plusDays(DIAS - 1L));
                if (antigas != null && antigas > 0) return;
                gerar(inicio, hoje);
            });
        } catch (RuntimeException e) {
            log.error("HistoricoVendasSeeder falhou; a aplicação segue no ar", e);
        }
    }

    // ---------------------------------------------------------------- geração

    private void gerar(LocalDate inicio, LocalDate fim) {
        Random rnd = new Random(SEMENTE);
        Long vendedor = usuarioId("vendedor@loja.local");
        Long gerente = usuarioId("gerente@loja.local");

        List<Catalogo> catalogo = carregarCatalogo();
        if (catalogo.isEmpty()) return;
        List<Long> compradores = criarClientes(rnd, inicio);

        Map<Long, Integer> saldo = new HashMap<>();
        Map<Long, Integer> vendidoNaSemana = new HashMap<>();
        catalogo.forEach(c -> saldo.put(c.id(), c.estoqueInicial()));

        List<ItemLinha> itens = new ArrayList<>();
        List<Movimento> movimentos = new ArrayList<>();
        List<Cancelamento> pendentes = new ArrayList<>();
        List<Cancelamento> efetivados = new ArrayList<>();
        int confirmadas = 0;

        // Carga inicial retrodatada: a loja não abre agosto com o estoque de hoje.
        OffsetDateTime abertura = instante(inicio, 8, 0, rnd);
        for (Catalogo c : catalogo) {
            int alvo = Math.max(c.minStock() * 4, (int) Math.ceil(VENDAS_POR_DIA * 7 * c.peso() / 100.0 * 2.5));
            int delta = Math.max(0, alvo - saldo.get(c.id()));
            if (delta > 0) {
                saldo.merge(c.id(), delta, Integer::sum);
                movimentos.add(new Movimento(c.id(), null, "ENTRY", delta, saldo.get(c.id()),
                        "Carga inicial da temporada", gerente, abertura));
            }
        }

        for (LocalDate dia = inicio; !dia.isAfter(fim); dia = dia.plusDays(1)) {
            boolean feriado = Feriados.ehFeriado(dia);

            // A agenda do dia é montada inteira e só então ordenada por horário. Isso importa: o saldo de cada
            // movimentação é acumulado na ordem em que ela entra na lista, e a tela ordena por data. Se as duas
            // ordens divergirem, o histórico mostra saldo pulando para trás.
            List<Evento> agenda = new ArrayList<>();

            if (dia.getDayOfWeek() == DayOfWeek.TUESDAY && !dia.equals(inicio)) {
                agenda.add(new Evento(instante(dia, 8, 30, rnd), Tipo.REPOSICAO, null));
            }
            for (Cancelamento c : new ArrayList<>(pendentes)) {
                if (!c.quando().toLocalDate().equals(dia)) continue;
                pendentes.remove(c);
                agenda.add(new Evento(c.quando(), Tipo.CANCELAMENTO, c));
            }
            int quantas = vendasDoDia(dia, feriado, rnd);
            for (int i = 0; i < quantas; i++) {
                agenda.add(new Evento(horario(dia, feriado, rnd), Tipo.VENDA, null));
            }
            agenda.sort(java.util.Comparator.comparing(Evento::quando));

            for (Evento e : agenda) {
                switch (e.tipo()) {
                    case REPOSICAO -> repor(catalogo, saldo, vendidoNaSemana, movimentos, gerente, e.quando(),
                            dia.isAfter(fim.minusDays(15)), rnd);
                    case CANCELAMENTO -> {
                        // RN19: cancelar venda confirmada devolve as peças ao estoque.
                        Cancelamento c = e.cancelamento();
                        for (ItemLinha i : c.itens()) {
                            saldo.merge(i.productId(), i.quantity(), Integer::sum);
                            movimentos.add(new Movimento(i.productId(), c.saleId(), "SALE_CANCELLATION",
                                    i.quantity(), saldo.get(i.productId()), null, gerente, c.quando()));
                        }
                        efetivados.add(c);
                    }
                    case VENDA -> {
                        List<ItemLinha> linhas = montarCarrinho(catalogo, saldo, dia, rnd);
                        if (linhas.isEmpty()) continue;

                        BigDecimal total = linhas.stream().map(ItemLinha::subtotal)
                                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
                        Long cliente = rnd.nextDouble() < 0.58
                                ? compradores.get(rnd.nextInt(compradores.size())) : null;
                        // A venda é aberta alguns minutos antes de ser confirmada, como no balcão.
                        OffsetDateTime aberta = e.quando().minusMinutes(2L + rnd.nextInt(12));
                        Long saleId = inserirVenda(cliente, total, e.quando(), aberta, vendedor);
                        confirmadas++;

                        for (ItemLinha l : linhas) {
                            itens.add(l.comVenda(saleId));
                            saldo.merge(l.productId(), -l.quantity(), Integer::sum);
                            vendidoNaSemana.merge(l.productId(), l.quantity(), Integer::sum);
                            movimentos.add(new Movimento(l.productId(), saleId, "SALE", -l.quantity(),
                                    saldo.get(l.productId()), null, vendedor, e.quando()));
                        }

                        // ~2,5% viram cancelamento (RN18: sempre com motivo). Nunca no mesmo dia: o
                        // cancelamento precisa cair depois da venda que o originou.
                        if (rnd.nextDouble() < 0.025) {
                            LocalDate quandoCancela = dia.plusDays(1L + rnd.nextInt(3));
                            if (!quandoCancela.isAfter(fim)) {
                                List<ItemLinha> doCancelamento = new ArrayList<>();
                                linhas.forEach(l -> doCancelamento.add(l.comVenda(saleId)));
                                pendentes.add(new Cancelamento(saleId,
                                        instante(quandoCancela, 11 + rnd.nextInt(9), -1, rnd),
                                        MOTIVOS_CANCELAMENTO[rnd.nextInt(MOTIVOS_CANCELAMENTO.length)],
                                        doCancelamento));
                            }
                        }
                    }
                }
            }
        }

        gravarItens(itens);
        gravarMovimentos(movimentos);
        gravarCancelamentos(efetivados, gerente);
        catalogo.forEach(c -> jdbc.update("update product set stock_qty = ?, updated_at = now() where id = ?",
                saldo.get(c.id()), c.id()));

        // Conta no banco, não na memória: o log não serve de nada se divergir do que foi realmente gravado.
        Long confirmadasNoBanco = jdbc.queryForObject(
                "select count(*) from sale where status = 'CONFIRMED'", Long.class);
        Long canceladasNoBanco = jdbc.queryForObject(
                "select count(*) from sale where status = 'CANCELLED'", Long.class);
        log.warn("\n\n  Histórico de demonstração: {} vendas confirmadas e {} canceladas entre {} e {},\n"
                + "  {} itens vendidos, {} movimentações de estoque, {} clientes na base.\n",
                confirmadasNoBanco, canceladasNoBanco, inicio, fim,
                itens.stream().mapToInt(ItemLinha::quantity).sum(), movimentos.size(), compradores.size());
        if (confirmadasNoBanco == null || confirmadasNoBanco + canceladasNoBanco != confirmadas) {
            log.error("Histórico inconsistente: {} vendas inseridas, {} no banco",
                    confirmadas, confirmadasNoBanco == null ? "?" : confirmadasNoBanco + canceladasNoBanco);
        }
    }

    /**
     * Reposição semanal: o fornecedor entrega terça de manhã, antes de abrir. O alvo é o giro da semana com
     * folga de segurança. Na virada para o verão a loja para de repor jaqueta — é daí que nascem, sozinhos,
     * os alertas de ruptura no dashboard.
     */
    private void repor(List<Catalogo> catalogo, Map<Long, Integer> saldo, Map<Long, Integer> vendidoNaSemana,
                       List<Movimento> movimentos, Long ator, OffsetDateTime quando, boolean fimDaTemporada,
                       Random rnd) {
        for (Catalogo c : catalogo) {
            if (fimDaTemporada && c.familia().equals("JAQ")) continue;
            int giro = vendidoNaSemana.getOrDefault(c.id(), 0);
            if (giro == 0) continue;
            int alvo = Math.max(c.minStock() * 3, (int) Math.ceil(giro * 2.2));
            int delta = alvo - saldo.get(c.id());
            if (delta <= 0) continue;
            saldo.merge(c.id(), delta, Integer::sum);
            movimentos.add(new Movimento(c.id(), null, "ENTRY", delta, saldo.get(c.id()),
                    "Reposição semanal", ator, quando));
        }
        vendidoNaSemana.clear();

        // Conferência de inventário de vez em quando acha divergência.
        if (rnd.nextDouble() < 0.22) {
            Catalogo c = catalogo.get(rnd.nextInt(catalogo.size()));
            if (saldo.get(c.id()) > 3) {
                saldo.merge(c.id(), -1, Integer::sum);
                movimentos.add(new Movimento(c.id(), null, "ADJUSTMENT", -1, saldo.get(c.id()),
                        rnd.nextBoolean() ? "Peça com defeito retirada da arara"
                                          : "Divergência na conferência de inventário", ator, quando));
            }
        }
    }

    // ------------------------------------------------------------- modelagem

    /** Movimento da loja no dia, antes do acaso: dia da semana × mês × data comemorativa × quinzena. */
    private int vendasDoDia(LocalDate d, boolean feriado, Random rnd) {
        double esperado = VENDAS_POR_DIA
                * pesoDoDia(d.getDayOfWeek(), feriado)
                * pesoDoMes(d.getMonthValue())
                * Feriados.pesoComercial(d)
                * (quinzena(d.getDayOfMonth()) ? 1.10 : 1.0)
                * (0.82 + rnd.nextDouble() * 0.36);
        return (int) Math.max(3, Math.round(esperado));
    }

    /** Sábado é o pico do shopping; segunda é o vale. Feriado roda em horário de domingo, com fluxo alto. */
    private double pesoDoDia(DayOfWeek d, boolean feriado) {
        if (feriado) return 1.05;
        return switch (d) {
            case MONDAY -> 0.70;
            case TUESDAY -> 0.76;
            case WEDNESDAY -> 0.84;
            case THURSDAY -> 0.94;
            case FRIDAY -> 1.20;
            case SATURDAY -> 1.72;
            case SUNDAY -> 0.92;
        };
    }

    /** Agosto fecha o inverno, setembro é o vale do trimestre, outubro puxa com Dia das Crianças e primavera. */
    private double pesoDoMes(int mes) {
        return switch (mes) {
            case 1 -> 1.05; case 2 -> 0.88; case 3 -> 0.95; case 4 -> 0.97;
            case 5 -> 1.08; case 6 -> 1.02; case 7 -> 1.00; case 8 -> 0.97;
            case 9 -> 0.93; case 10 -> 1.10; case 11 -> 1.25; default -> 1.45;
        };
    }

    /** Pagamento no dia 5 e adiantamento no dia 20: as duas janelas em que o shopping enche. */
    private boolean quinzena(int diaDoMes) {
        return (diaDoMes >= 5 && diaDoMes <= 9) || (diaDoMes >= 20 && diaDoMes <= 24);
    }

    private OffsetDateTime horario(LocalDate dia, boolean feriado, Random rnd) {
        boolean reduzido = feriado || dia.getDayOfWeek() == DayOfWeek.SUNDAY;
        int[] curva = reduzido ? HORAS_DOMINGO : dia.getDayOfWeek() == DayOfWeek.SATURDAY ? HORAS_SABADO : HORAS_SEMANA;
        int base = reduzido ? ABRE_DOMINGO : ABRE_SEMANA;
        return instante(dia, base + sortearIndice(curva, rnd), -1, rnd);
    }

    private OffsetDateTime instante(LocalDate dia, int hora, int minuto, Random rnd) {
        int m = minuto >= 0 ? minuto : rnd.nextInt(60);
        return dia.atTime(LocalTime.of(hora, m, rnd.nextInt(60))).atZone(props.zone()).toOffsetDateTime();
    }

    private int sortearIndice(int[] pesos, Random rnd) {
        int soma = 0;
        for (int p : pesos) soma += p;
        int alvo = rnd.nextInt(soma);
        for (int i = 0; i < pesos.length; i++) {
            alvo -= pesos[i];
            if (alvo < 0) return i;
        }
        return pesos.length - 1;
    }

    /** Monta o carrinho: 1 a 3 produtos distintos, respeitando o saldo e o UNIQUE (sale_id, product_id). */
    private List<ItemLinha> montarCarrinho(List<Catalogo> catalogo, Map<Long, Integer> saldo, LocalDate dia,
                                           Random rnd) {
        double sorteio = rnd.nextDouble();
        int linhas = sorteio < 0.62 ? 1 : sorteio < 0.91 ? 2 : 3;
        Set<Long> usados = new HashSet<>();
        List<ItemLinha> carrinho = new ArrayList<>();

        for (int i = 0; i < linhas; i++) {
            Catalogo c = sortearProduto(catalogo, saldo, usados, dia, rnd);
            if (c == null) break;
            usados.add(c.id());
            int disponivel = saldo.get(c.id());
            int qtd = Math.min(quantidade(c.preco(), rnd), disponivel);
            if (qtd <= 0) continue;
            BigDecimal subtotal = c.preco().multiply(BigDecimal.valueOf(qtd)).setScale(2, RoundingMode.HALF_UP);
            carrinho.add(new ItemLinha(null, c.id(), c.codigo(), c.nome(), c.preco(), qtd, subtotal));
        }
        return carrinho;
    }

    private Catalogo sortearProduto(List<Catalogo> catalogo, Map<Long, Integer> saldo, Set<Long> usados,
                                    LocalDate dia, Random rnd) {
        List<Catalogo> elegiveis = new ArrayList<>();
        List<Double> pesos = new ArrayList<>();
        double soma = 0;
        for (Catalogo c : catalogo) {
            if (usados.contains(c.id()) || saldo.get(c.id()) <= 0) continue;
            double p = c.peso() * sazonalidade(c.familia(), dia.getMonthValue()) * Feriados.pesoCategoria(c.familia(), dia);
            elegiveis.add(c);
            pesos.add(p);
            soma += p;
        }
        if (elegiveis.isEmpty()) return null;
        double alvo = rnd.nextDouble() * soma;
        for (int i = 0; i < elegiveis.size(); i++) {
            alvo -= pesos.get(i);
            if (alvo <= 0) return elegiveis.get(i);
        }
        return elegiveis.get(elegiveis.size() - 1);
    }

    /** Jaqueta é inverno, vestido é verão: a curva de cada família ao longo do ano. */
    private double sazonalidade(String familia, int mes) {
        double[] jaq = {0.4, 0.4, 0.7, 1.2, 1.6, 1.8, 1.8, 1.6, 1.0, 0.6, 0.5, 0.4};
        double[] ves = {1.5, 1.5, 1.3, 1.0, 0.8, 0.6, 0.6, 0.7, 1.0, 1.4, 1.5, 1.6};
        double[] cam = {1.3, 1.3, 1.2, 1.0, 0.9, 0.8, 0.8, 0.9, 1.0, 1.2, 1.3, 1.3};
        return switch (familia) {
            case "JAQ" -> jaq[mes - 1];
            case "VES" -> ves[mes - 1];
            case "CAM" -> cam[mes - 1];
            default -> 1.0;
        };
    }

    /** Peça barata sai em dupla ou trio; jaqueta e vestido saem no olho. */
    private int quantidade(BigDecimal preco, Random rnd) {
        double p = preco.doubleValue();
        double r = rnd.nextDouble();
        if (p <= 69.90) return r < 0.58 ? 1 : r < 0.88 ? 2 : 3;
        if (p <= 159.90) return r < 0.80 ? 1 : 2;
        return 1;
    }

    // ------------------------------------------------------------- persistência

    private Long inserirVenda(Long clienteId, BigDecimal total, OffsetDateTime confirmada, OffsetDateTime aberta,
                              Long ator) {
        return jdbc.queryForObject("""
                insert into sale (customer_id, status, total, confirmed_at, created_by, created_at, updated_at)
                values (?, 'CONFIRMED', ?, ?, ?, ?, ?)
                returning id
                """, Long.class, clienteId, total, confirmada, ator, aberta, confirmada);
    }

    private void gravarItens(List<ItemLinha> itens) {
        jdbc.batchUpdate("""
                insert into sale_item (sale_id, product_id, product_code, product_name, unit_price, quantity, subtotal)
                values (?, ?, ?, ?, ?, ?, ?)
                """, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                ItemLinha l = itens.get(i);
                ps.setLong(1, l.saleId());
                ps.setLong(2, l.productId());
                ps.setString(3, l.code());
                ps.setString(4, l.name());
                ps.setBigDecimal(5, l.unitPrice());
                ps.setInt(6, l.quantity());
                ps.setBigDecimal(7, l.subtotal());
            }

            @Override
            public int getBatchSize() {
                return itens.size();
            }
        });
    }

    private void gravarMovimentos(List<Movimento> movimentos) {
        // Inseridos na ordem cronológica em que foram gerados: o id acompanha a data.
        jdbc.batchUpdate("""
                insert into stock_movement
                       (product_id, sale_id, type, quantity, balance_after, reason, created_by, created_at)
                values (?, ?, cast(? as stock_movement_type), ?, ?, ?, ?, ?)
                """, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                Movimento m = movimentos.get(i);
                ps.setLong(1, m.productId());
                if (m.saleId() == null) ps.setNull(2, java.sql.Types.BIGINT); else ps.setLong(2, m.saleId());
                ps.setString(3, m.tipo());
                ps.setInt(4, m.quantidade());
                ps.setInt(5, m.saldo());
                ps.setString(6, m.motivo());
                ps.setLong(7, m.ator());
                ps.setObject(8, m.quando());
            }

            @Override
            public int getBatchSize() {
                return movimentos.size();
            }
        });
    }

    private void gravarCancelamentos(List<Cancelamento> cancelamentos, Long ator) {
        jdbc.batchUpdate("""
                update sale set status = 'CANCELLED', cancelled_at = ?, cancel_reason = ?, cancelled_by = ?,
                                updated_at = ?
                 where id = ?
                """, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                Cancelamento c = cancelamentos.get(i);
                ps.setObject(1, c.quando());
                ps.setString(2, c.motivo());
                ps.setLong(3, ator);
                ps.setObject(4, c.quando());
                ps.setLong(5, c.saleId());
            }

            @Override
            public int getBatchSize() {
                return cancelamentos.size();
            }
        });
    }

    // ------------------------------------------------------------- base de apoio

    private Long usuarioId(String login) {
        return usuarios.findByLoginIgnoreCase(login).orElseThrow().getId();
    }

    private List<Catalogo> carregarCatalogo() {
        // Peso de giro por produto: básico barato gira muito, peça de ticket alto gira pouco.
        Map<String, Double> giro = Map.ofEntries(
                Map.entry("CAM-001-M-BR", 14.0), Map.entry("CAM-002-G-BR", 11.0), Map.entry("CAM-003-P-PT", 10.0),
                Map.entry("CAL-001-40-AZ", 8.0), Map.entry("CAL-002-42-PT", 9.0),
                Map.entry("VES-001-M-VM", 6.0), Map.entry("VES-002-P-AZ", 7.0),
                Map.entry("JAQ-001-M-PT", 3.0), Map.entry("JAQ-002-G-AZ", 5.0),
                Map.entry("ACE-001-U-MR", 7.0), Map.entry("ACE-002-U-PT", 8.0), Map.entry("ACE-003-U-CZ", 12.0));

        List<Catalogo> catalogo = new ArrayList<>();
        for (Product p : produtos.findAll()) {
            String familia = p.getCode().length() >= 3 ? p.getCode().substring(0, 3) : "ACE";
            catalogo.add(new Catalogo(p.getId(), p.getCode(), p.getName(), p.getPrice(), familia,
                    giro.getOrDefault(p.getCode(), 6.0), p.getStockQty(), p.getMinStock()));
        }
        return catalogo;
    }

    /**
     * Clientes da região de Tubarão. O CPF é gerado com dígito verificador válido porque
     * {@code CustomerService.create} roda a validação de RN01 — número de teste, não de pessoa real.
     */
    private List<Long> criarClientes(Random rnd, LocalDate inicio) {
        String[] nomes = {
            "Alice Fernandes Duarte", "Bruno Kreuger Althoff", "Camila Búrigo Nunes", "Diego Vieira Zimmermann",
            "Eduarda Mendes Sartor", "Felipe Cardoso Búrigo", "Gabriela Remor Pacheco", "Henrique Dagostim Silva",
            "Isabela Mondardo Cruz", "Jonas Hobold Pereira", "Karina Nandi Guglielmi", "Leonardo Bitencourt Mello",
            "Marina Zanette Coelho", "Nathan Pizzolo Ramos", "Olívia Scussel Barbosa", "Patrícia Minatto Goulart",
            "Rafael Westphal Medeiros", "Sabrina Teixeira Bonfante", "Thiago Milanez Correa", "Valentina Boeing Rosa",
            "André Luiz Spillere", "Beatriz Colombo Feltrin", "Caio Nazário Pizzolo", "Daniela Savi Marcelino",
            "Emanuel Topanotti Lima", "Fabiana Crippa Rodrigues", "Gustavo Simon Bernardo", "Helena Darós Cechinel",
            "Igor Benedet Fontana", "Juliana Elias Possamai", "Kauã Peruchi Macagnan", "Larissa Bessa Tomaz",
            "Murilo Gava Vieira", "Natália Milak Souza", "Otávio Casagrande Reus", "Priscila Lemos Amboni",
            "Renan Steiner Policarpo", "Sofia Ghisi Marchetti", "Tomás Bortolotto Vieira", "Vitória Nunes Daufenbach",
            "Wagner Felisbino Costa", "Yasmin Rocha Bianchini", "Arthur Zilli Benincá", "Letícia Mariot Serafim",
            "Vinícius Hulse Scheffer"
        };
        String[][] cidades = {
            {"Tubarão", "88701000", "Avenida Marcolino Martins Cabral", "Centro"},
            {"Tubarão", "88704300", "Rua Lauro Müller", "Oficinas"},
            {"Tubarão", "88705000", "Avenida Expedicionário José Pedro Coelho", "Dehon"},
            {"Capivari de Baixo", "88745000", "Rua Ernani Cotrin", "Centro"},
            {"Laguna", "88790000", "Avenida Colombo Machado Salles", "Centro"},
            {"Braço do Norte", "88750000", "Rua Padre Pedro Fuss", "Centro"},
            {"Gravatal", "88735000", "Rua Nossa Senhora de Fátima", "Centro"},
            {"Jaguaruna", "88715000", "Avenida Nereu Ramos", "Centro"},
            {"Imbituba", "88780000", "Avenida Getúlio Vargas", "Centro"},
            {"Içara", "88820000", "Rua Coronel Marcos Rovaris", "Centro"}
        };

        List<Long> ids = new ArrayList<>();
        jdbc.queryForList("select id from customer order by id", Long.class).forEach(ids::add);

        Set<String> cpfs = new HashSet<>(jdbc.queryForList("select cpf from customer", String.class));
        List<Long> novos = new ArrayList<>();
        for (String nome : nomes) {
            String cpf = cpfValido(rnd, cpfs);
            String[] c = cidades[rnd.nextInt(cidades.length)];
            String email = email(nome);
            String fone = rnd.nextBoolean()
                    ? "489" + (10000000 + rnd.nextInt(89999999))
                    : "483" + (6000000 + rnd.nextInt(3999999));
            novos.add(clientes.create(new CustomerRequest(nome, cpf, fone, email, c[1], c[2],
                    String.valueOf(10 + rnd.nextInt(1990)), null, c[3], c[0], "SC"), null).id());
        }

        // O cadastro do cliente é anterior à primeira compra dele.
        for (Long id : novos) {
            OffsetDateTime quando = inicio.minusDays(rnd.nextInt(400))
                    .atTime(LocalTime.of(9 + rnd.nextInt(11), rnd.nextInt(60))).atZone(props.zone()).toOffsetDateTime();
            jdbc.update("update customer set created_at = ?, updated_at = ? where id = ?", quando, quando, id);
        }
        ids.addAll(novos);
        return ids;
    }

    private String email(String nome) {
        String[] partes = java.text.Normalizer.normalize(nome, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase().split(" ");
        return partes[0] + "." + partes[partes.length - 1] + "@email.com";
    }

    private String cpfValido(Random rnd, Set<String> usados) {
        while (true) {
            int[] n = new int[11];
            for (int i = 0; i < 9; i++) n[i] = rnd.nextInt(10);
            boolean todosIguais = true;
            for (int i = 1; i < 9; i++) if (n[i] != n[0]) todosIguais = false;
            if (todosIguais) continue;
            n[9] = digito(n, 9, 10);
            n[10] = digito(n, 10, 11);
            StringBuilder sb = new StringBuilder();
            for (int d : n) sb.append(d);
            String cpf = sb.toString();
            if (usados.add(cpf)) return cpf;
        }
    }

    private int digito(int[] n, int tamanho, int pesoInicial) {
        int soma = 0;
        for (int i = 0; i < tamanho; i++) soma += n[i] * (pesoInicial - i);
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }

    // ------------------------------------------------------------- estruturas

    private enum Tipo { REPOSICAO, CANCELAMENTO, VENDA }

    /** Um acontecimento do dia, com hora marcada. A agenda é ordenada por {@code quando} antes de ser aplicada. */
    private record Evento(OffsetDateTime quando, Tipo tipo, Cancelamento cancelamento) {}

    private record Catalogo(Long id, String codigo, String nome, BigDecimal preco, String familia, double peso,
                            int estoqueInicial, int minStock) {}

    private record ItemLinha(Long saleId, Long productId, String code, String name, BigDecimal unitPrice,
                             int quantity, BigDecimal subtotal) {
        ItemLinha comVenda(Long id) {
            return new ItemLinha(id, productId, code, name, unitPrice, quantity, subtotal);
        }
    }

    private record Movimento(Long productId, Long saleId, String tipo, int quantidade, int saldo, String motivo,
                             Long ator, OffsetDateTime quando) {}

    private record Cancelamento(Long saleId, OffsetDateTime quando, String motivo, List<ItemLinha> itens) {}
}
