# 29/10/2026 · Papéis da equipe, logins curtos e filtro por vendedor

Resumo do que mudou nesta entrega e por quê. (A entrega do MVP em 27/10/2026 está rastreada em
`docs/09-rastreabilidade.md`; as specs `01` a `05` em `specs/crm/` foram todas concluídas nela.)

## O que mudou

| Mudança | Onde | Por quê |
| --- | --- | --- |
| Login passa a ser `nome.sobrenome` (sem `@loja.local`); senha de demonstração continua `loja12345` | `DevDataSeeder`, `UserAdminController` (validação), `LoginPage`, `UsersPage` | Mais simples de digitar na apresentação; e-mail completo continua aceito para contas antigas |
| A equipe é **reconciliada em todo boot** | `DevDataSeeder.reconcileTeam` | Trocar login, papel ou senha de demonstração aqui vale no banco do Render e no `.data` local sem zerar nada. A senha só é definida na criação da conta; uma senha escolhida pela pessoa nunca é sobrescrita |
| Dois papéis técnicos: `DEVELOPER` (Engenheiro(a) de Software) e `MARKETING_DEV` (Marketing e Desenvolvimento) | `CrmModuleSeeder`, `RbacService.grantedRoles`, `JwtCookieFilter`, `AssistantTools`, `permissions.ts`, `adapters.ts` | A equipe que constrói o sistema precisa ver tudo; o rótulo na tela diz o que cada um faz. Na permissão valem por gerente + vendedor + estoquista. Não contam como "gerente ativo" na RN25 |
| Equipe (atualizado no fim do dia: Vinicius Clemente passou a Operações e Compras, papel `PURCHASING`): Guilherme gerente; Douglas Engenheiro de Software (e super admin); Julia Marketing e Desenvolvimento; Vitor, Vinicius Clemente e Vinicius Muniz vendedores | `DevDataSeeder.EQUIPE` | Pedido do dono do projeto |
| Filtro **Vendedor(a)** no dashboard (`sellerId`) | `DashboardService`, `DashboardController`, `DashboardResponse` (`sellers`, `selected.sellerId/sellerName`), `DashboardPage` | Saber quanto cada vendedor vendeu. O vendedor é quem abriu a venda (`sale.created_by`). Vale para cartões e para os três gráficos |
| Talita consulta por vendedor (`vendedor_id`) | `AssistantTools`, `AssistantPrompt` | "Quanto o Vitor vendeu este mês?" |
| Histórico de demonstração creditado aos 3 vendedores (pesos 5 : 4 : 3) | `HistoricoVendasSeeder` | Sorteio **separado** do principal, para a sequência de vendas continuar igual à documentada. Banco antigo (tudo num vendedor só) é redistribuído uma vez na subida |

## Decisões

- **Permissão por implicação, não por cópia.** Em vez de repetir `hasRole('MANAGER','DEVELOPER',…)` em cada rota, o `JwtCookieFilter` dá ao papel técnico as autoridades de gerente, vendedor e estoquista. As rotas não mudaram.
- **`is_admin = false` nos papéis técnicos.** A regra "a loja nunca fica sem gerente ativo" continua falando do gerente de verdade.
- **Movimentações de estoque antigas não mudam de autor.** O trigger de imutabilidade não deixa, e o dono da venda fica em `sale.created_by`, que é o que o filtro usa.

## Testes

- `DashboardIT.filtrarPorVendedorMostraSoAsVendasDele`: dois vendedores, filtro por um só, lista `sellers`, 404 para id inexistente.
- `DashboardIT.equipeTecnicaVeODashboardEOsUsuarios`: `DEVELOPER` e `MARKETING_DEV` abrem dashboard, usuários e alertas de estoque.
- `AuthIT.loginCurtoNoPadraoNomeSobrenome`: `ana.silva-1` aceito; `Ana.Silva`, `ana silva`, `ana..silva`, `.ana` recusados (400).
- `AssistantIT.ferramentasRespeitamOPapel`: papéis técnicos recebem as 10 ferramentas.

Total: 70 testes automáticos. Roteiro manual (doc 12) ganhou os itens de login curto, papéis técnicos e filtro por vendedor.

## Documentação alinhada ao código na mesma entrega

Postgres embutido no lugar de Testcontainers (docs 01, 06, 07, CLAUDE.md), cookie `SameSite=Lax` (ADR-0003,
ADR-0004, doc 08), workflows reais do CI (`backend.yml`, `frontend.yml`, `docker.yml`, `pages.yml`), seeds
atuais (doc 07), contagem de testes (70) e de histórias (36), variáveis `ASSISTANT_*` no Render (doc 13),
specs marcadas como feitas, e a conferência do enunciado (doc 15).
