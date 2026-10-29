# 06-painel-vendedor-metas · Design

## Banco

- `V6__crm_sales_target.sql` (reverso `U6`): tabela `sales_target` (`seller_id`, `period_month` = dia 1 do mês,
  `amount > 0`, únicos por vendedor e mês, autoria e datas) e índice `ix_sale_created_by_confirmed` para somar
  por vendedor.

## Back-end (`modules/crm/target/`)

| Arquivo | Papel |
| --- | --- |
| `SalesTarget`, `SalesTargetRepository` | Entidade e consulta por vendedor e mês |
| `Pace` | Conta pura: dias úteis (seg–sáb), ritmo, projeção, falta, por dia, situação (REQ-06 a REQ-08) |
| `TargetService` | `set` (REQ-01 a REQ-04, auditoria `sales_target_set`), `panel` (REQ-05, REQ-09, REQ-10; reaproveita `DashboardService.build(..., sellerId)` e devolve só os números do vendedor), `board` (REQ-11, um SQL com `left join` de meta e vendas) |
| `TargetController` | `GET /crm/targets`, `PUT /crm/targets/{sellerId}` (G), `GET /crm/my-panel` (V, sempre com o id da sessão) |
| `dto/TargetDtos` | `TargetRequest`, `TargetSaved`, `SellerPanel`, `TargetBoard`, `BoardRow` |
| `seed/MetasSeeder` | Metas de demonstração (perfis dev/demo), a partir do próprio histórico |
| `assistant/AssistantTools` | Ferramentas `meu_painel` (V) e `metas_vendedores` (G); as ferramentas passam a receber o id de quem pergunta |

## Front-end

- `MyPanelPage` (`/meu-painel`): cartão da meta com barra e frases, cartões, vendas por dia, mais vendidos, últimas vendas.
- `TargetsPage` (`/metas`): mês, totais da equipe, tabela com a meta editável na linha (botão Salvar só aparece quando o valor muda).
- `permissions.ts`: `myPanel` (só SELLER), `targets` (gerente e técnicos); `homeOf(SELLER) = /meu-painel`.
- `hooks.ts`: `useMyPanel`, `useTargets`, `useSetTarget`; vendas e estoque invalidam `myPanel` e `targets`.

## Testes

- `PaceTest` (unitário, 7): calendário de outubro, setembro e novembro de 2026, ritmo, faixas de situação, mês fechado, sem meta, último dia útil.
- `TargetIT` (integração, 8): REQ-01 a REQ-11, inclusive "vendedor não vê colega" e "gerente não tem Meu painel".
- `AssistantIT`: contagem de ferramentas por papel (11 para gerente, 12 para a equipe técnica, `meu_painel` só para vendedor).

## Decisões

- **Reaproveitar o dashboard filtrado por vendedor** em vez de repetir as consultas: mesmos números nas duas telas, por construção.
- **Sem parâmetro de vendedor em `/crm/my-panel`:** o isolamento não depende de validação, porque não existe como pedir o painel de outro.
- **Meta de mês fechado é imutável** (REQ-02) em vez de versionada: mais simples e suficiente; a auditoria guarda o caminho até o valor final.
