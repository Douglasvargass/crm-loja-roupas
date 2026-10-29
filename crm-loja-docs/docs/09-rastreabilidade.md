## 10. Rastreabilidade: história → tela → API → teste

Situação em 27/10/2026. **Feito** = implementado e coberto por teste automático ou pelo roteiro manual; **Fora** = não implementado (prioridade Could).

| HU | História | Tela (rota) | API | Testes | Situação |
| --- | --- | --- | --- | --- | --- |
| HU01 | Cadastrar cliente | `/clientes` → Novo cliente | `POST /crm/customers` | CT01, CT14–CT17 (CustomerIT) | Feito |
| HU02 | Listar e pesquisar clientes | `/clientes` | `GET /crm/customers?search=` | CT02 (CustomerIT) | Feito |
| HU03 | Ver detalhes do cliente | `/clientes/:id` | `GET /crm/customers/{id}` | SaleIT (resumo de compras) | Feito |
| HU04 | Editar cliente | `/clientes/:id` → Editar | `PUT /crm/customers/{id}` | CT16 (CPF de outro cliente) | Feito |
| HU05 | Excluir ou inativar cliente | `/clientes/:id` → Excluir | `DELETE` · `PATCH …/deactivate` | CT29, exclusão sem vendas | Feito |
| HU06 | Histórico de compras | `/clientes/:id` | `GET /crm/customers/{id}/sales` | CT09 (SaleIT) | Feito |
| HU07 | Cadastrar categoria | `/categorias` | `POST /crm/categories` | RN03 (CatalogStockIT) | Feito |
| HU08 | Listar, editar e excluir categoria | `/categorias` | `GET · PUT · DELETE /crm/categories` | CT28, categoria inativa | Feito |
| HU09 | Cadastrar produto | `/produtos` → Novo produto | `POST /crm/products` | CT04, CT18–CT20 | Feito |
| HU10 | Listar e pesquisar produtos | `/produtos` | `GET /crm/products?name=&categoryId=&size=&code=` | filtros combinados | Feito |
| HU11 | Ver detalhes do produto | `/produtos/:id` | `GET /crm/products/{id}` | CT04–CT06 | Feito |
| HU12 | Editar produto | `/produtos/:id` → Editar | `PUT /crm/products/{id}` | CT30 | Feito |
| HU13 | Excluir ou inativar produto | `/produtos/:id` → Excluir | `DELETE` · `PATCH …/deactivate` | exclusão com só estoque inicial; com histórico → 409 | Feito |
| HU14 | Ativos e indisponíveis | `/produtos` (etiquetas e filtro) | `availability=` | filtros combinados | Feito |
| HU15 | Filtrar por categoria | `/produtos` | `categoryId=` | filtros combinados | Feito |
| HU16 | Ver saldo | `/estoque` | `GET /crm/products` | CT05 | Feito |
| HU17 | Registrar entrada | `/estoque` → Registrar entrada | `POST /crm/stock/entries` | CT05, CT21 | Feito |
| HU18 | Registrar ajuste | `/estoque` → Registrar ajuste | `POST /crm/stock/adjustments` | CT06, CT27 | Feito |
| HU19 | Estoque baixo e zerado | `/estoque` (Precisa repor) | `GET /crm/stock/alerts` | filtros combinados | Feito |
| HU20 | Histórico de movimentações | `/estoque` (Histórico) | `GET /crm/stock/movements` | CT04–CT06, CT38 | Feito |
| HU21 | Abrir venda e vincular cliente | `/vendas/nova` | `POST /crm/sales` | CT07, cliente inativo | Feito |
| HU22 | Adicionar e remover itens | `/vendas/:id` (aberta) | `POST · PUT · DELETE /crm/sales/{id}/items` | CT07, CT22, produto indisponível | Feito |
| HU23 | Confirmar venda | `/vendas/:id` → Confirmar | `POST /crm/sales/{id}/confirm` | CT08, CT23–CT25, CT31 | Feito |
| HU24 | Cancelar venda | `/vendas/:id` → Cancelar | `POST /crm/sales/{id}/cancel` | CT11, CT26, CT33 | Feito |
| HU25 | Consultar vendas | `/vendas` | `GET /crm/sales?id=&customerId=&from=&to=&status=` | consulta por número e status | Feito |
| HU26 | Ver detalhes da venda | `/vendas/:id` | `GET /crm/sales/{id}` | CT30 | Feito |
| HU27 | Indicadores gerais | `/` | `GET /crm/dashboard` | CT10, CT12 | Feito |
| HU28 | Vendas e faturamento por período | `/` (Hoje, 7, 30 dias, Este mês, Personalizado; lista Vendedor(a)) | `from=&to=&sellerId=` | virada de período 23:59/00:00; filtro por vendedor (DashboardIT) | Feito |
| HU29 | Mais vendidos | `/` | `topProducts` | CT10 | Feito |
| HU30 | Alertas de estoque | `/` (com atalho para entrada) | `lowStock`, `outOfStock` | CT10 | Feito |
| HU31 | Navegação e feedback | menu lateral, avisos de 4 s, confirmações | — | roteiro manual | Feito |
| HU32 | Exportar CSV | Clientes, Produtos e Vendas → Exportar planilha (gerente) | `GET /crm/export/{customers,products,sales}.csv` | — | Feito |
| HU33 | Entrar no sistema | `/login` | `POST /auth/login` · `/refresh` · `/logout` | CT32, CT36, login inválido | Feito |
| HU34 | Trocar a senha | `/trocar-senha` (obrigatória no 1º acesso; link no topo) | `POST /auth/change-password` | CT35 | Feito |
| HU35 | Gerenciar usuários e papéis | `/usuarios` | `GET · POST · PATCH /admin/users` | CT37, criação com senha provisória, login curto `nome.sobrenome`, papéis técnicos (DashboardIT) | Feito |
| HU36 | Tirar dúvidas com a Thalita (assistente de IA) | botão flutuante (todas as telas) | `GET /crm/assistant/status` · `POST /crm/assistant/chat` | AssistantIT (sem chave); seção 9 do roteiro manual (com chave) | Feito |
| HU37 | Meu painel: minhas vendas e minha meta | `/meu-painel` (tela inicial do vendedor) | `GET /crm/my-panel` | TargetIT, PaceTest; seção 10 do roteiro | Feito (spec 06) |
| HU38 | Definir a meta de cada vendedor | `/metas` | `PUT /crm/targets/{sellerId}` | TargetIT (validações, mês fechado, auditoria) | Feito (spec 06) |
| HU39 | Quadro de metas da equipe | `/metas` | `GET /crm/targets` | TargetIT | Feito (spec 06) |
| HU46 | Agente de vendas da demonstração (um dia por vez, clima pela IA) | Dashboard → Diário do agente | `GET /crm/demo-agent` | HumorDoDiaTest, DemoAgentIT | Feito (spec 08) |
| HU47 | Abrir chamado de peça em falta (tela ou Thalita) | `/chamados` → Novo chamado; ficha do produto zerado | `POST /crm/requests` | StockRequestIT | Feito (spec 09) |
| HU48 | Resolver ou cancelar chamado; peça que chega resolve sozinha | `/chamados` | `POST /crm/requests/{id}/resolve` · `/cancel`; `POST /crm/stock/entries` | StockRequestIT | Feito (spec 09) |
| HU49 | Sino de avisos | topo de todas as telas | `GET /crm/requests/alerts` | StockRequestIT | Feito (spec 09) |
| HU50 | "Ver como" (só leitura) | botão no topo; faixa "Vendo como…" | `/auth/view-as` | ViewAsIT | Feito (spec 10) |
| HU51 | Ver o que vai faltar e quanto pedir | `/reposicao` | `GET /crm/stock/planning` | CoverageTest, ReplenishmentIT | Feito (spec 11) |
| HU52 | Registrar pedido de compra | `/pedidos-compra` → Novo pedido; Reposição → Pedir | `POST /crm/purchase-orders` | ReplenishmentIT | Feito (spec 11) |
| HU53 | Receber o pedido (entrada + fecha chamados) | `/pedidos-compra` → Receber | `POST /crm/purchase-orders/{id}/receive` | ReplenishmentIT | Feito (spec 11) |
| HU55 | Etapas do pedido e nota fiscal | `/pedidos-compra` → Saiu do fornecedor / Chegou na loja / Dar entrada | `/ship` · `/arrive` · `/receive` | ReplenishmentIT (`etapasDoPedidoAteAEntrada`) | Feito (spec 11) |
| HU56 | Estoque no Dashboard | `/` (bloco Estoque) | `GET /crm/stock/planning`, `GET /crm/purchase-orders` | ReplenishmentIT; roteiro seção 18 | Feito (spec 11) |
| HU57 | Thalita do cliente e pedido de peça | Loja: Thalita, "Não achou? Peça à loja", Meus pedidos | `/loja/assistant/**`, `/loja/requests` | StoreIT (`clientePedePeca…`) | Feito (spec 07) |
| HU58 | Previsão de vendas com sazonalidade | `/previsao` | `GET /crm/forecast?months=` | ForecastModelTest (8), ForecastIT (`gerenteVePrevisao…`, `mesesForaDoLimite…`) | Feito (spec 12) |
| HU59 | Meta sugerida pela previsão | `/metas` (coluna Sugestão, Usar, Aplicar a todos) | `GET /crm/targets/suggestions?month=&growth=` | ForecastIT (`metaSugerida…`, `sugestaoRecusa…`) | Feito (spec 12) |
| HU54 | Mudar data ou cancelar o pedido, com motivo | `/pedidos-compra` → Mudar data / Cancelar | `/reschedule` · `/cancel` | ReplenishmentIT | Feito (spec 11) |
| HU40–HU45 | Espaço do cliente: conta e troca de senha, vitrine, carrinho, pagamento simulado, meus pedidos, meus dados | `/loja/...` | `/loja/**` | StoreIT | Feito (spec 07) |

**HU36 · Assistente de IA (extra).** Como membro da equipe, quero perguntar em linguagem natural sobre vendas, estoque, clientes e uso do sistema, para decidir sem procurar em várias telas.

- As respostas usam dados consultados no banco no momento; nada é inventado.
- Cada perfil só obtém o que a tela já mostraria a ele.
- O assistente só consulta; pedidos de ação são respondidos com o caminho na tela.
- Sem `ANTHROPIC_API_KEY`, o assistente aparece desligado e o resto do sistema funciona.

### Decisões tomadas na implementação

- **Postgres embutido no lugar do Testcontainers.** O Docker do laboratório nem sempre está ligado; o zonky sobe o mesmo Postgres 16 sem Docker, nos testes e no modo `spring-boot:test-run`.
- **V5: exclusão do produto só com estoque inicial.** O trigger do V4 recusava qualquer DELETE em `stock_movement`, o que tornava a HU13 impossível. O V5 libera uma única exceção: apagar a movimentação `ENTRY` do produto liberado por `set_config('crm.product_purge', id, true)` na mesma transação.
- **Busca sem acento com `translate()`.** A extensão `unaccent` exige superusuário; `translate()` resolve o caso da HU02 em qualquer banco.
- **Enum no JPQL vai como parâmetro.** Um literal de enum vira cast para um tipo `SaleStatus` que não existe no Postgres (o tipo real é `sale_status`).
- **Sem Lombok.** Menos configuração no VS Code dos alunos; entidades e DTOs (records) ficam explícitos.
