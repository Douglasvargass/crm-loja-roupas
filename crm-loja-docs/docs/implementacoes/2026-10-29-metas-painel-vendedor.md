# 29/10/2026 · Meu painel do vendedor, metas e processo de requisitos

Resumo da entrega. A spec completa está em `specs/crm/06-painel-vendedor-metas/`.

## O que mudou

| Mudança | Onde | Por quê |
| --- | --- | --- |
| Tabela `sales_target` (meta por vendedor e mês) | `V6__crm_sales_target.sql` (+ `U6`) | Meta não existia no sistema; o gerente controlava fora dele |
| Conta do ritmo: dias úteis (segunda a sábado), média, projeção, falta, por dia, situação | `modules/crm/target/Pace.java` | Uma conta só para o painel, o quadro e a Thalita, testada sozinha |
| Regras de meta e painel | `TargetService`, `TargetController` | REQ-01 a REQ-11 da spec 06 |
| **Meu painel** (tela inicial do vendedor) | `MyPanelPage.tsx`, `/meu-painel` | O vendedor vê o próprio resultado e quanto falta por dia, sem ranking |
| **Metas** (gerente e equipe técnica) | `TargetsPage.tsx`, `/metas` | Definir a meta na própria linha e acompanhar a equipe |
| Thalita: `meu_painel` e `metas_vendedores`; as ferramentas recebem o id de quem pergunta | `AssistantTools`, `AssistantService`, `AssistantPrompt`, `ThalitaPersona` | "Quanto falta para a minha meta?" e "quem está mais longe da meta?" |
| Metas de demonstração | `seed/MetasSeeder.java` | A demo tem um vendedor que bate, um perto e um abaixo |
| Processo de requisitos e modelos de issue | `docs/16-processo-de-requisitos.md`, `.github/ISSUE_TEMPLATE/` | Versão enxuta do processo da Plataforma de Operações da Vialaser |
| Spec do espaço do cliente | `specs/crm/07-espaco-cliente/requirements.md` | Pronta para implementar, com 4 decisões e propostas padrão |

## Decisões

- **O painel reaproveita o dashboard filtrado por vendedor.** Os números do Meu painel e do dashboard com o filtro "Vendedor(a)" são iguais por construção.
- **Isolamento sem validação:** `/crm/my-panel` não aceita id de vendedor; usa sempre o da sessão.
- **Mês fechado não muda** em vez de versionar a meta. A auditoria guarda cada mudança feita enquanto o mês estava aberto.
- **Dia útil igual ao Raio-X de Vendas da Vialaser:** segunda a sábado, feriado não descontado (o shopping abre).

## Testes

| Teste | Casos |
| --- | --- |
| `PaceTest` (unitário) | 7: calendário de out/set/nov 2026, ritmo e projeção, faixas de situação, mês fechado, sem meta, último dia útil |
| `TargetIT` (integração) | 8: meta e painel ponta a ponta, vendedor não vê colega nem mexe em meta, sem meta, validações, auditoria, quadro, cancelamento, gerente sem painel |
| `AssistantIT` | contagem de ferramentas por papel atualizada |

Total: 85 testes automáticos. Roteiro manual: seção 10 do doc 12.

**Verificado em 29/10/2026:** back-end compila (código e testes) com Java 21; `PaceTest`, `ThalitaPersonaTest`, `CpfTest`
e `AssistantPropertiesTest` passam; front-end passa em lint, typecheck e build. **Não verificado:** os testes de
integração (`./mvnw verify`), porque a máquina estava com menos de 1 GB de memória livre.
