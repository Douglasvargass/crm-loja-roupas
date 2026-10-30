## 5. Regras de negócio

As regras ficam no Service e, sempre que possível, também no banco (CHECK, UNIQUE, trigger). Quebra de regra devolve `409` com um `code` estável, para a tela escolher a frase; validação de campo devolve `400`. O estoque só muda junto com uma movimentação, e a venda só mexe no estoque ao ser confirmada ou ao ser cancelada depois de confirmada.

| ID | Regra | Garantia no banco | Resposta |
| --- | --- | --- | --- |
| RN01 | CPF obrigatório, único, 11 dígitos, dígito verificador válido | `ux_customer_cpf`, `ck_customer_cpf_digits` | 400 `invalid_cpf` · 409 `cpf_taken` |
| RN02 | Cliente com vendas não é excluído; só inativado | `fk_sale_customer` RESTRICT | 409 `customer_has_sales` |
| RN03 | Nome de categoria único, sem diferenciar maiúsculas | `ux_category_name_ci` | 409 `category_name_taken` |
| RN04 | Categoria com produtos não é excluída | `fk_product_category` RESTRICT | 409 `category_has_products` |
| RN05 | Código de produto único | `ux_product_code` | 409 `product_code_taken` |
| RN06 | Preço > 0; estoque e estoque mínimo ≥ 0 | `ck_product_*` | 400 |
| RN07 | Produto com vendas ou movimentações além da inicial não é excluído; só inativado | FKs RESTRICT | 409 `product_has_history` |
| RN08 | Estoque nunca é editado direto; toda mudança grava `stock_movement` com saldo após | Trigger de imutabilidade | — |
| RN09 | Nenhuma movimentação deixa saldo negativo | `ck_product_stock`, `ck_stock_movement_balance` | 409 `negative_stock` |
| RN10 | Estoque baixo: `0 < stock_qty ≤ min_stock`; sem estoque: `stock_qty = 0` | — | — |
| RN11 | Venda nasce `DRAFT`; cliente é opcional | Default da coluna | — |
| RN12 | Item copia código, nome e preço do produto na inclusão | Colunas próprias em `sale_item` | — |
| RN13 | Subtotal = quantidade × preço; total = soma, sempre calculado no servidor | `ck_sale_item_subtotal` | — |
| RN14 | Só venda `DRAFT` aceita incluir, alterar ou remover itens | — | 409 `sale_not_draft` |
| RN15 | Confirmar exige ≥ 1 item e estoque para **todos**; tudo ou nada | Transação + travas | 409 `sale_empty` · `insufficient_stock` · `product_inactive` |
| RN16 | Confirmar só a partir de `DRAFT` (sem baixa duplicada) | `ux_stock_movement_sale_item` | 409 `sale_not_draft` |
| RN17 | Confirmação grava uma movimentação `SALE` por item, ligada à venda | `ck_stock_movement_sale` | — |
| RN18 | Cancelar exige motivo; a partir de `DRAFT` ou `CONFIRMED` | `ck_sale_cancelled` | 400 `reason_required` · 409 `sale_already_cancelled` |
| RN19 | Cancelar venda confirmada grava `SALE_CANCELLATION` por item e devolve o estoque | `ux_stock_movement_sale_item` | — |
| RN20 | Venda nunca é apagada | FKs RESTRICT, sem rota de DELETE | — |
| RN21 | Dashboard conta e soma só vendas `CONFIRMED` no período; canceladas à parte | — | — |
| RN22 | Produto inativo ou sem estoque não aparece na busca da tela de venda | — | — |
| RN23 | Confirmar, cancelar, ajustar estoque, inativar e mudar papel gravam `audit_event` na mesma transação | Trigger de imutabilidade | — |
| RN24 | Cada rota exige papel (seção 6); o front só esconde, quem barra é o back-end. Os papéis técnicos (`DEVELOPER`, `MARKETING_DEV`) valem por gerente + vendedor + estoquista; Operações e Compras (`PURCHASING`) vale por estoquista e recebe os chamados | — | 401 sem sessão · 403 sem papel |
| RN25 | O gerente não muda o próprio papel e a loja nunca fica sem gerente ativo | Contagem dentro da transação | 409 `self_role_change` · `last_admin` |
| RN26 | Meta é por vendedor e mês, maior que zero, uma por mês (spec crm/06) | `ux_sales_target_seller_month`, `ck_sales_target_amount` | 400 |
| RN27 | Meta só no mês atual ou até 12 meses à frente; mês fechado não muda | — | 409 `month_closed` · `month_too_far` |
| RN28 | Só vendedor(a) ativo(a) recebe meta | — | 409 `not_a_seller` |
| RN29 | Vendido = vendas `CONFIRMED` que o vendedor **abriu**, pela data de confirmação no fuso da loja; canceladas saem | — | — |
| RN30 | Dia útil = segunda a sábado. Ritmo = vendido ÷ dias úteis passados; projeção = ritmo × dias úteis do mês; por dia = falta ÷ dias úteis restantes. Situação: batida, no ritmo (projeção ≥ meta), atenção (80–100%), abaixo do ritmo (< 80%), não bateu (mês fechado) | — | — |
| RN31 | O vendedor vê só os próprios números (Meu painel, sem parâmetro de vendedor); o gerente vê todos; toda mudança de meta grava `audit_event` com o valor anterior | `audit_event` imutável | 403 |
| RN32 | Chamado de peça em falta: descrição de 3 a 200 letras, quantidade de 1 a 99; abre quem atende (gerente, vendedor) | `ck_stock_request_*` | 400 · 403 |
| RN33 | Peça ativa com estoque suficiente não abre chamado | — | 409 `product_in_stock` |
| RN34 | Resolver: estoque ou gerente; cancelar: esses ou quem abriu; sempre com nota; fechado não reabre | `ck_stock_request_closed` | 409 `request_closed` |
| RN35 | Entrada de estoque resolve os chamados abertos da peça, do mais antigo ao mais novo, enquanto a quantidade cobrir | Mesma transação da entrada | — |
| RN36 | A Thalita só grava uma coisa: chamado, depois de a pessoa confirmar o resumo | — | — |
| RN37 | Cliente só acessa a loja (`/loja/**`), sempre resolvido pela sessão; pedido, carrinho ou dado de outro cliente: 404 | `ux_customer_user` | 403 · 404 |
| RN38 | Pedido online = venda com canal ONLINE; pagar confirma pela mesma regra tudo-ou-nada do balcão | `ck_sale_channel`, `ux_payment_sale` | 409 `insufficient_stock` |
| RN39 | Preço que mudou com a peça no carrinho: vale o novo; o carrinho é atualizado e o cliente confere antes de pagar | — | 409 `price_changed` |
| RN40 | Pagamento simulado; cartão final 0002 é recusado; só os 4 últimos dígitos são guardados | `ck_payment_card` | 409 `payment_declined` |
| RN41 | Cancelar pedido online pago estorna o pagamento (status REFUNDED) e devolve o estoque | `ck_payment_refund` | — |
| RN42 | O cliente muda contato e endereço; nome e CPF só a loja muda | — | — |
| RN43 | Reposição: média por dia = vendido (confirmado) nos últimos 30 dias ÷ 30; dias até acabar = estoque ÷ média; sem venda, sem previsão | `Coverage` | — |
| RN44 | Quanto pedir = venda prevista nos dias escolhidos (7 a 90) + estoque mínimo + chamados abertos da peça − estoque − o que está a caminho; nunca negativo | `Coverage` | 400 `invalid_horizon` |
| RN45 | "A caminho" = soma dos pedidos de compra abertos da peça; pedido recebido ou cancelado deixa de contar | — | — |
| RN46 | Situação: zerado; acaba em até 7 dias ou no mínimo; até 21 dias; tranquilo; parado (com estoque e sem venda em 30 dias) | `Coverage` | — |
| RN47 | Pedido de compra: fornecedor, data de hoje a 180 dias, 1 a 30 peças de 1 a 9999 un.; registra, muda data e cancela só quem compra (G, Operações e Compras), sempre com motivo para mudar ou cancelar; nada se apaga | `ck_purchase_order_closed` | 400 · 403 · 409 `order_closed` |
| RN49 | Pedido de compra: pedido feito → em trânsito (quem compra) → em conferência (quem recebe) → entrada feita com o nº da nota fiscal; data só muda antes de chegar; cancela até a entrada | `ck_purchase_order_steps` | 400 `invoice_required` · 409 `wrong_step` |
| RN50 | Dashboard: o bloco Estoque é de gerente, Operações e Compras e estoquista; vendas e faturamento continuam só do gerente | `/crm/dashboard` G | 403 |
| RN51 | Cliente pede peça que não está na vitrine (Thalita com confirmação ou botão da vitrine); vira chamado ligado a ele; cada cliente vê só os seus | — | 409 `product_in_stock` |
| RN52 | Conferência do pedido de compra: contado peça por peça; chegou a menos → a nota volta ao fornecedor (nada entra, pedido volta a feito); chegou a mais → o extra entra com a nota de bonificação; nunca entrada "mais ou menos" para ajustar depois | `ck_purchase_order_item_received` | 409 `short_delivery` · 400 `bonus_invoice_required` |
| RN53 | Cliente com acesso à loja online não se exclui; inativa | `fk_customer_user` | 409 `customer_has_login` |
| RN54 | Resetar senha (gerente): senha provisória, troca obrigatória, sessões encerradas; ninguém reseta a própria por aí | — | 409 `self_reset` |
| RN55 | Previsão de vendas: só vendas confirmadas, pela data da confirmação no fuso da loja, dos dois canais; o dia de hoje entra pela previsão, não pelo parcial; precisa de pelo menos 14 dias com venda | — | 409 `not_enough_history` |
| RN56 | Previsão do dia = nível atual (média dos últimos 28 dias sem os outros efeitos) × dia da semana (ou feriado) × semana do salário × data comercial × mês do ano | — | — |
| RN57 | Dia da semana aprendido nas últimas 12 semanas sem feriados e datas comerciais; salário (dias 5–9 e 20–24) aprendido contra os outros dias, de 0,8 a 1,4; feriado aprendido com 2 ou mais feriados, senão vale como domingo | — | — |
| RN58 | Datas comerciais (Mães, Namorados, Pais, Cliente, Crianças, Black Friday, Natal) sobem aos poucos nos dias antes; aumento aprendido quando o histórico tem a janela inteira, senão estimativa padrão marcada; mês do ano só com 1 ano de histórico | — | — |
| RN59 | Faixa provável (~80%) pelo erro diário dos últimos 56 dias; acerto = erro médio por semana refazendo a previsão 28 dias atrás (precisa de 56 dias) | — | — |
| RN60 | Meta sugerida = previsão do mês da loja (mês atual: vendido até ontem + previsão) × parte do vendedor no faturamento da loja nos últimos 90 dias × (1 + crescimento de −20% a +50%), arredondada para a centena; sem venda em 90 dias, sem sugestão; aplicar passa pela regra e auditoria da meta | `/crm/targets/suggestions` G | 400 `invalid_growth` · 409 `month_closed` |
| RN61 | Cupom em % (até 90%) ou em R$, com pedido mínimo, validade e limite de usos; um cupom por venda; a venda guarda a cópia da regra e o total líquido (peças − desconto) é sempre recalculado no servidor | `ck_coupon_*`, `ck_sale_discount` | 400 `invalid_code` · `invalid_value` |
| RN62 | Aplicar exige cupom ativo, dentro da validade, com uso disponível e pedido no mínimo; o código vale em maiúsculas ou minúsculas | — | 409 `coupon_not_found` · `coupon_inactive` · `coupon_expired` · `coupon_exhausted` · `coupon_min_order` |
| RN63 | O uso do cupom só conta na confirmação, com o cupom travado e conferido de novo; se não vale mais, nada é baixado do estoque | `FOR UPDATE` | 409 (mesmos códigos) |
| RN64 | Cupom não se apaga: o gerente desliga; o painel soma o faturamento líquido, com o desconto rateado entre as peças | — | — |
| RN65 | Cliente cria a própria conta pelo site (Sou cliente → Criar conta): mesmos dados do cadastro do balcão, senha de 8+ caracteres e aceite do uso dos dados; o login é o e-mail | `ux_app_user_email` | 400 `terms_required` · `weak_password` · 409 `email_in_use` |
| RN66 | CPF que já é cliente do balcão só vira conta com o mesmo e-mail do cadastro; CPF com conta não cria outra | — | 409 `cpf_registered` · `cpf_has_account` |
| RN67 | Esqueci minha senha: resposta sempre igual (não revela se o e-mail tem conta); até 3 pedidos por hora; o banco guarda só o hash do link | `password_reset_token` | 400 `invalid_email` |
| RN68 | O link vale 30 minutos e uma vez só; trocar a senha invalida os outros links e encerra as sessões abertas | — | 409 `invalid_reset_link` |
| RN69 | Galeria do produto: até 8 imagens de até 2 MB (JPG, PNG ou WEBP, conferido pelos primeiros bytes do arquivo); a primeira é a capa; só o gerente envia, troca a capa e exclui; as imagens são públicas, como numa vitrine | `ck_product_image_*` | 400 `invalid_image` · `image_too_large` · 409 `too_many_images` |
| RN48 | Receber dá entrada de todas as peças na mesma transação (tudo ou nada) e fecha os chamados que a entrada cobre (RN35); aberto com data vencida = atrasado | Mesma transação da entrada | 409 `order_closed` |

### Ciclo de vida da venda

&#91;embedded content: ciclo de vida da venda · 3 status\]

| Status atual (tela · código) | Ação | Novo status | Efeito no estoque |
| --- | --- | --- | --- |
| Aberta · `DRAFT` | Incluir, alterar ou remover item | `DRAFT` | Nenhum |
| Aberta · `DRAFT` | Confirmar | `CONFIRMED` | Baixa de cada item (`SALE`) |
| Aberta · `DRAFT` | Cancelar | `CANCELLED` | Nenhum |
| Confirmada · `CONFIRMED` | Cancelar | `CANCELLED` | Devolução de cada item (`SALE_CANCELLATION`) |
| Confirmada · `CONFIRMED` | Confirmar de novo | recusado, 409 | Nenhum |
| Cancelada · `CANCELLED` | Qualquer ação | recusado, 409 | Nenhum |

### Lógica da confirmação (pseudocódigo)

```java
@Service
public class SaleService {   // sem Lombok: construtor explícito (omitido aqui)

    private final SaleRepository sales;
    private final ProductRepository products;
    private final StockService stock;
    private final AuditService audit;

    // Confirma a venda e baixa o estoque. Tudo ou nada: qualquer exceção desfaz a transação inteira.
    @Transactional
    public SaleResponse confirm(Long saleId, Long actorId) {
        Sale sale = sales.findByIdForUpdate(saleId)                   // trava a linha da venda
            .orElseThrow(() -> new NotFoundException("Venda não encontrada."));

        if (sale.getStatus() != SaleStatus.DRAFT)                     // segunda confirmação para aqui
            throw new BusinessRuleException("sale_not_draft", "Esta venda já foi confirmada ou cancelada.");
        if (sale.getItems().isEmpty())
            throw new BusinessRuleException("sale_empty", "Adicione ao menos um produto antes de confirmar.");

        // Trava os produtos em ordem de id: duas vendas simultâneas não se bloqueiam mutuamente.
        List<Long> ids = sale.getItems().stream().map(i -> i.getProduct().getId()).sorted().toList();
        Map<Long, Product> locked = products.findAllByIdInForUpdate(ids).stream()
            .collect(Collectors.toMap(Product::getId, p -> p));

        // 1º valida todos os itens; só depois mexe em estoque.
        for (SaleItem item : sale.getItems()) {
            Product p = locked.get(item.getProduct().getId());
            if (!p.isActive())
                throw new BusinessRuleException("product_inactive", "O produto " + p.getName() + " está inativo.");
            if (p.getStockQty() < item.getQuantity())
                throw new BusinessRuleException("insufficient_stock", "Estoque insuficiente para " + p.getName()
                    + ": disponível " + p.getStockQty() + ", pedido " + item.getQuantity() + ".");
        }
        // 2º baixa e registra a movimentação ligada à venda.
        for (SaleItem item : sale.getItems()) {
            stock.move(locked.get(item.getProduct().getId()), -item.getQuantity(),
                       StockMovementType.SALE, sale, null, actorId);
        }
        sale.recalculateTotal();                  // total sempre calculado no servidor
        sale.markConfirmed(OffsetDateTime.now()); // CONFIRMED + confirmed_at
        audit.record(actorId, "crm", "sale_confirmed", "sale", sale.getId(),
                     Map.of("total", sale.getTotal(), "items", sale.getItems().size()));
        return SaleResponse.from(sale);
    }
}
```

```java
public interface SaleRepository extends JpaRepository<Sale, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Sale s where s.id = :id")
    Optional<Sale> findByIdForUpdate(@Param("id") Long id);
}

public interface ProductRepository extends JpaRepository<Product, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id in :ids order by p.id")
    List<Product> findAllByIdInForUpdate(@Param("ids") List<Long> ids);
}
```

`StockService.move` calcula o saldo novo, recusa saldo negativo (`negative_stock`), atualiza `product.stock_qty` e grava a `stock_movement` com `balance_after`, tudo no mesmo `@Transactional` de quem chamou. O cancelamento é simétrico: trava a venda, recusa se já estiver `CANCELLED`, devolve o estoque só se estava `CONFIRMED`, grava motivo, data, quem cancelou e o `audit_event`. Mesmo que o Service falhe em barrar uma segunda baixa, o índice `ux_stock_movement_sale_item` barra no banco.
