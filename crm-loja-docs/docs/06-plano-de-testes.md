## 8. Plano de testes

São 38 casos. Os marcados **(auto)** são testes de integração: sobem um Postgres 16 real **embutido** (biblioteca zonky, sem Docker), rodam **todas** as migrations Flyway do zero, fazem login de verdade e chamam a API com MockMvc. Assim a suíte testa também as migrations, os CHECKs e os triggers. Os demais são roteiro manual antes da apresentação.

```java
@SpringBootTest
@AutoConfigureMockMvc
public abstract class IntegrationTest {

    // Banco descartável e real: um Postgres 16 embutido, nunca o de desenvolvimento. Sem Docker, porque
    // o Docker do laboratório nem sempre está ligado.
    private static final EmbeddedPostgres PG = EmbeddedPostgres.builder().start();

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> PG.getJdbcUrl("postgres", "postgres"));
    }

    @Autowired protected MockMvc mvc;
    @Autowired protected TestUsers users;   // cria usuários por papel com senha conhecida

    protected Cookie loginAs(String role) throws Exception {
        String login = users.ensure(role);
        MvcResult r = mvc.perform(post("/auth/login").contentType(APPLICATION_JSON)
                .content("{\"login\":\"" + login + "\",\"password\":\"" + TestUsers.PASSWORD + "\"}"))
            .andExpect(status().isOk()).andReturn();
        return r.getResponse().getCookie("access_token");
    }
}
```

| ID | Tipo | Cenário | Resultado esperado | Obtido |
| --- | --- | --- | --- | --- |
| CT01 | Válido (auto) | Cadastrar cliente completo | 201, aparece na busca |  |
| CT02 | Válido (auto) | Buscar por parte do nome e por CPF com pontuação | Encontrado nos dois casos |  |
| CT03 | Válido | Cadastrar categoria "Camisetas" | Salva |  |
| CT04 | Válido (auto) | Produto com estoque inicial 10 | `stock_qty` 10 e movimentação `ENTRY` |  |
| CT05 | Válido (auto) | Entrada de 5 unidades | Saldo 15, `balance_after` 15 |  |
| CT06 | Válido (auto) | Ajuste −2 com motivo | Saldo 13, movimentação `ADJUSTMENT` e `audit_event` |  |
| CT07 | Válido (auto) | Venda com 2 produtos (3 + 2 un.) | Subtotais e total corretos, calculados no servidor |  |
| CT08 | Válido (auto) | Confirmar a venda do CT07 | `CONFIRMED`; estoques −3 e −2; 2 movimentações `SALE`; `audit_event` |  |
| CT09 | Válido (auto) | Histórico do cliente após CT08 | Venda listada |  |
| CT10 | Válido (auto) | Dashboard após CT08 | Vendas +1, faturamento + total, produtos no top |  |
| CT11 | Válido (auto) | Cancelar a venda confirmada com motivo | `CANCELLED`; estoque volta; 2 `SALE_CANCELLATION` |  |
| CT12 | Válido (auto) | Dashboard após CT11 | Vendas −1, faturamento − total, canceladas +1 |  |
| CT13 | Válido | Fechar e reabrir a aplicação | Dados continuam lá |  |
| CT14 | Inválido (auto) | Cliente sem nome | 400, nada salvo |  |
| CT15 | Inválido (auto) | CPF 111.111.111-11 | 400 `invalid_cpf` |  |
| CT16 | Inválido (auto) | CPF já cadastrado | 409 `cpf_taken` |  |
| CT17 | Inválido (auto) | E-mail "joao@" | 400 |  |
| CT18 | Inválido (auto) | Produto com preço 0 ou −10 | 400 |  |
| CT19 | Inválido (auto) | Estoque inicial −1 | 400 |  |
| CT20 | Inválido (auto) | Código de produto duplicado | 409 `product_code_taken` |  |
| CT21 | Inválido (auto) | Entrada com quantidade 0 | 400 |  |
| CT22 | Exceção (auto) | Vender 20 com estoque 13 | 409 `insufficient_stock`, estoque intacto |  |
| CT23 | Exceção (auto) | Item A ok e item B sem estoque | Nada baixado, nem o A |  |
| CT24 | Exceção (auto) | Confirmar duas vezes | Segunda 409 `sale_not_draft`; uma baixa só |  |
| CT25 | Exceção (auto) | Duas confirmações simultâneas (2 threads) | Uma 200, outra 409; uma baixa só |  |
| CT26 | Exceção (auto) | Cancelar duas vezes | Segundo 409; um estorno só |  |
| CT27 | Exceção (auto) | Ajuste −20 com saldo 13 | 409 `negative_stock`; saldo 13 |  |
| CT28 | Exceção (auto) | Excluir categoria com produtos | 409 `category_has_products` |  |
| CT29 | Exceção (auto) | Excluir cliente com vendas | 409 `customer_has_sales` |  |
| CT30 | Exceção (auto) | Mudar preço e abrir venda antiga | Venda mantém nome e preço originais |  |
| CT31 | Exceção (auto) | Confirmar venda sem itens | 409 `sale_empty` |  |
| CT32 | Acesso (auto) | Chamar `/crm/customers` sem cookie | 401 |  |
| CT33 | Acesso (auto) | Vendedor cancelar venda | 403 |  |
| CT34 | Acesso (auto) | Estoquista abrir o dashboard | 403 |  |
| CT35 | Acesso (auto) | Usuário com troca de senha pendente chamar o crm | 403 até trocar |  |
| CT36 | Acesso (auto) | Reusar um refresh token já trocado | 401 e a sessão inteira revogada |  |
| CT37 | Acesso (auto) | Último gerente rebaixar o próprio papel | 409 `self_role_change` |  |
| CT38 | Banco (auto) | `UPDATE` ou `DELETE` direto em `stock_movement` | Banco recusa (trigger) |  |

Cobrir também a virada de período no dashboard: venda confirmada às 23:59 do último dia entra; às 00:00 do dia seguinte, não.

### Casos das funções que vieram depois do MVP (29/10/2026)

| ID | Tipo | Cenário | Resultado esperado | Onde |
| --- | --- | --- | --- | --- |
| CT39 | Válido (auto) | Gerente define a meta do mês de um vendedor | Salva; mudança na auditoria (de → para) | TargetIT |
| CT40 | Inválido (auto) | Meta em mês fechado, valor 0 ou para quem não é vendedor ativo | 400 / 409, nada salvo | TargetIT |
| CT41 | Acesso (auto) | Vendedor abre Meu painel | Vê só os próprios números | TargetIT |
| CT42 | Válido (auto) | Ritmo e projeção por dia útil (segunda a sábado) | Conta igual à da spec 06 | PaceTest (7) |
| CT43 | Válido (auto) | Cliente monta carrinho e paga com Pix | Venda com canal Online, estoque baixa | StoreIT |
| CT44 | Exceção (auto) | Cartão final 0002, preço que mudou no carrinho, peça que acabou | 409 `payment_declined` / `price_changed` / `insufficient_stock`, nada baixado | StoreIT |
| CT45 | Acesso (auto) | Cliente abre pedido de outro cliente ou rota da equipe | 404 / 403 | StoreIT |
| CT46 | Válido (auto) | Vendedor abre chamado de peça zerada; entrada da peça resolve | Chamado resolvido e aviso no sino | StockRequestIT |
| CT47 | Exceção (auto) | Chamado de peça com estoque; fechar chamado já fechado | 409 `product_in_stock` / `request_closed` | StockRequestIT |
| CT48 | Acesso (auto) | Gerente usa "Ver como" e tenta alterar algo | Vê como a pessoa; alteração 403 `read_only_view_as` | ViewAsIT |
| CT49 | Válido (auto) | Reposição: vende 15 de 20 em 30 dias | Acaba em 10 dias, pedir 12; com pedido a caminho, pedir 0 | ReplenishmentIT, CoverageTest (7) |
| CT50 | Inválido (auto) | Pedido de compra com data passada, peça repetida ou sem peças | 400, nada salvo | ReplenishmentIT |
| CT51 | Exceção (auto) | Receber pedido duas vezes; receber dá entrada e fecha chamado | Segunda 409 `order_closed`; estoque sobe uma vez só | ReplenishmentIT |
| CT52 | Acesso (auto) | Estoquista tenta registrar pedido; vendedor abre a Reposição | 403 | ReplenishmentIT |
| CT53 | Válido (auto) | Thalita: cada papel recebe só as ferramentas que a tela dele permite | Gerente 15, equipe técnica 16, vendedor sem estoque/faturamento | AssistantIT |
| CT54 | Válido (auto) | Thalita conta a história e recusa assunto fora da loja | Texto pronto, sem inventar | ThalitaPersonaTest |

### Onde estão os testes automáticos

Rodada completa em 29/10/2026 com `./mvnw verify`: **106 testes unitários e 97 de integração, todos passando** (rodada final, depois da revisão de 29/10).

| Classe | Testes | O que cobre |
| --- | --- | --- |
| `CustomerIT` | 9 | Clientes: cadastro, busca, CPF, exclusão com vínculo |
| `CatalogStockIT` | 12 | Categorias, produtos e estoque (entrada, ajuste, histórico, alertas) |
| `SaleIT` | 12 | Vendas: itens, total, confirmação, estoque, cancelamento, concorrência |
| `DashboardIT` | 10 | Indicadores, período, filtros de vendedor e canal |
| `AuthIT` | 8 | Login, troca de senha, sessão, papéis |
| `ViewAsIT` | 5 | "Ver como" só leitura |
| `TargetIT` | 8 | Metas e Meu painel |
| `StoreIT` | 12 | Espaço do cliente, Prévia da loja, Thalita do cliente, pedido de peça, cliente com login e reset de senha |
| `StockRequestIT` | 7 | Chamados de peça em falta |
| `ReplenishmentIT` | 8 | Reposição, pedidos de compra, etapas e conferência (devolver a nota, bonificação) |
| `DemoAgentIT` | 2 | Agente de vendas da demonstração |
| `AssistantIT` | 4 | Ferramentas da Thalita por papel |
| `CpfTest` | 12 | Dígito verificador do CPF |
| `PaceTest` | 7 | Ritmo e projeção da meta |
| `CoverageTest` | 8 | Conta da Reposição, caso a caso (inclui peça nova) |
| `HumorDoDiaTest` | 6 | Clima do dia do agente |
| `ThalitaPersonaTest` | 63 | Apresentação, história e recusas da Thalita |
| `AssistantPropertiesTest` · `OpenAiCompatibleProviderTest` | 6 · 4 | Configuração e conexão com a IA |
