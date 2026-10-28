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
| HU28 | Vendas e faturamento por período | `/` (Hoje, 7, 30 dias, Este mês, Personalizado) | `from=&to=` | virada de período 23:59/00:00 | Feito |
| HU29 | Mais vendidos | `/` | `topProducts` | CT10 | Feito |
| HU30 | Alertas de estoque | `/` (com atalho para entrada) | `lowStock`, `outOfStock` | CT10 | Feito |
| HU31 | Navegação e feedback | menu lateral, avisos de 4 s, confirmações | — | roteiro manual | Feito |
| HU32 | Exportar CSV | — | — | — | Fora (Could) |
| HU33 | Entrar no sistema | `/login` | `POST /auth/login` · `/refresh` · `/logout` | CT32, CT36, login inválido | Feito |
| HU34 | Trocar a senha | `/trocar-senha` (obrigatória no 1º acesso; link no topo) | `POST /auth/change-password` | CT35 | Feito |
| HU35 | Gerenciar usuários e papéis | `/usuarios` | `GET · POST · PATCH /admin/users` | CT37, criação com senha provisória | Feito |
| HU36 | Tirar dúvidas com o assistente de IA | `/assistente` | `GET /crm/assistant/status` · `POST /crm/assistant/chat` | AssistantIT (sem chave); seção 9 do roteiro manual (com chave) | Feito |

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
