## 8. Plano de testes

São 38 casos. Os marcados **(auto)** são testes de integração: sobem um Postgres 16 real com Testcontainers, rodam **todas** as migrations Flyway do zero, fazem login de verdade e chamam a API com MockMvc. Assim a suíte testa também as migrations, os CHECKs e os triggers. Os demais são roteiro manual antes da apresentação.

```java
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
public abstract class IntegrationTest {

    // Banco descartável e real: o mesmo Postgres 16 do docker-compose e do CI, nunca o de desenvolvimento.
    @Container @ServiceConnection
    static PostgreSQLContainer<?> db = new PostgreSQLContainer<>("postgres:16");

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
