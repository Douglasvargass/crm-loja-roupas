# Como contribuir

Guia para quem vai testar, corrigir ou evoluir o CRM. Os comandos de instalação e execução estão no [guia de comandos](crm-loja-docs/docs/11-guia-de-comandos.md).

## Fluxo de trabalho

1. **Combine antes:** abra (ou pegue) uma *Issue* descrevendo o que vai mudar e por quê.
2. **Branch novo a partir do `main`:**

   ```bash
   git checkout main && git pull
   git checkout -b fix/total-da-venda
   ```

   Prefixos: `feat/` (funcionalidade), `fix/` (correção), `docs/` (documentação), `test/` (testes), `refactor/` (melhoria sem mudar comportamento).
3. **Faça a mudança** seguindo as regras abaixo.
4. **Rode as checagens.** O CI roda as mesmas no GitHub:

   ```bash
   cd crm-loja-backend && ./mvnw verify
   cd ../crm-loja-frontend && npx pnpm lint && npx pnpm typecheck && npx pnpm build
   ```

5. **Commit** com mensagem em português, no imperativo, dizendo o quê e, se não for óbvio, o porquê:

   ```bash
   git add <arquivos>
   git commit -m "Impede venda de produto inativo na confirmação (RN15)"
   ```

6. **Push e Pull Request:**

   ```bash
   git push -u origin fix/total-da-venda
   gh pr create --fill        # ou pelo site do GitHub
   ```

   No PR, diga o que mudou, como testar e quais HUs/RNs foram afetadas. Espere o CI ficar verde e uma revisão antes do merge.

## Regras do projeto

- **Código em inglês** (classes, tabelas, colunas, variáveis); **tela, mensagens, commits e documentação em português**.
- **Regra de negócio só no Service** do back-end. O front esconde botões, mas quem barra é a API.
- **O banco muda só por migration nova** (`V6__...sql`). Nunca edite uma migration já publicada.
- **Nunca apague histórico:** venda se cancela; cliente e produto com vínculo se inativam.
- **Estoque só muda pelo `StockService.move`**, que grava a movimentação junto com o saldo.
- **Toda mudança de comportamento tem teste.** Bug corrigido ganha um teste que falhava antes.
- **Mudou rota ou DTO?** Atualize `crm-loja-docs/specs/api-contract.md` e `crm-loja-frontend/src/data/dto.ts` na mesma mudança.
- **Mudou regra de negócio?** Atualize `crm-loja-docs/docs/04-regras-de-negocio.md` e o texto do assistente (`AssistantPrompt.java`).
- **Nada de segredo no Git:** chaves (`ANTHROPIC_API_KEY`, `JWT_SECRET`) só em variáveis de ambiente ou `.env`.
- Comentário explica o **porquê**, não o quê.

## Onde mexer

| Quero mudar… | Back-end (`crm-loja-backend/src/main/java/br/senai/crmloja/`) | Front-end (`crm-loja-frontend/src/`) |
| --- | --- | --- |
| Uma regra (ex.: limite de desconto) | `modules/crm/<área>/<Área>Service.java` | — |
| Uma validação de campo | `modules/crm/<área>/dto/*Request.java` | o formulário em `presentation/pages/...` |
| Uma tela | — | `presentation/pages/...` |
| Texto ou tradução de status | — | `data/adapters.ts` |
| Cores e tema | — | `presentation/styles/tokens.css` |
| Quem pode o quê | `@PreAuthorize` no Controller | `domain/permissions.ts` |
| Tabelas e colunas | `src/main/resources/db/migration/V<n>__*.sql` + a entidade | `data/dto.ts` |
| Indicadores do dashboard | `modules/crm/dashboard/DashboardService.java` | `presentation/pages/DashboardPage.tsx` |
| O que o assistente sabe ou consulta | `modules/crm/assistant/AssistantPrompt.java` / `AssistantTools.java` | `presentation/pages/AssistantPage.tsx` |
| Dados de demonstração | `modules/crm/seed/DevDataSeeder.java` | — |

## Receita: nova funcionalidade de ponta a ponta

Exemplo: **HU32, exportar clientes em CSV.**

1. **Back-end, Service:** em `CustomerService`, um método que monta o CSV a partir da mesma busca da lista.
2. **Back-end, Controller:** em `CustomerController`, uma rota `GET /crm/customers/export` com `@PreAuthorize("hasRole('MANAGER')")`, devolvendo `text/csv` com `Content-Disposition: attachment`.
3. **Teste:** em `CustomerIT`, um caso que chama a rota como gerente (200 e cabeçalho CSV) e como vendedor (403).
4. **Contrato:** uma linha nova em `crm-loja-docs/specs/api-contract.md`.
5. **Front:** em `CustomersPage.tsx`, um botão "Exportar CSV", visível só quando `can.users(role)` (gerente), que abre `/api/crm/customers/export?search=...` (o cookie vai junto).
6. **Rastreabilidade:** em `crm-loja-docs/docs/09-rastreabilidade.md`, mude a HU32 para "Feito".
7. Rode as checagens, faça o commit e abra o PR.

## Receita: corrigir um bug

1. Reproduza e anote os passos na Issue.
2. Escreva primeiro um teste que **falha** mostrando o bug (ex.: em `SaleIT`).
3. Corrija no Service. O teste passa.
4. `./mvnw verify` inteiro, para garantir que nada mais quebrou.

## Estrutura de testes

- `src/test/java/.../support/IntegrationTest.java`: base com Postgres embutido, MockMvc e atalhos (`loginAs`, `createProduct`, `createCustomer`…).
- `TestData`: gera CPFs válidos e códigos únicos. Os testes compartilham o banco, então **use sempre dados únicos** e não dependa da ordem dos testes.
- Nomes de teste em português, descrevendo o comportamento: `vendaAcimaDoEstoqueERecusada`.
