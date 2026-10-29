# 08-agente-vendas-demo · Design

| Peça | Onde | Papel |
| --- | --- | --- |
| Diário | `V7__crm_demo_agent_day.sql` (+ `U7`) | Um dia por linha (`ux_demo_agent_day`), imutável por trigger |
| Agente | `seed/AgenteVendasDemo.java` (perfis dev/demo) | `colocarEmDia()`: na subida (em segundo plano) e de hora em hora (`@Scheduled`, minuto 5). Cada dia na sua transação |
| Motor | `seed/HistoricoVendasSeeder.java` | Os métodos do modelo deixaram de ser `private` (mesmo pacote); comportamento igual |
| Clima do dia | `seed/HumorDoDia.java` | Lê a resposta da IA e limita os números (REQ-03) |
| IA | `AssistantService.completeOnce` | Pergunta avulsa, sem ferramentas, no provedor da Talita; nunca lança erro |
| Relógio | `seed/AgendamentoDemoConfig.java` | `@EnableScheduling` só nos perfis dev/demo |
| Rota | `demo/DemoAgentController.java` | `GET /crm/demo-agent` (G): `enabled`, `aiEnabled`, últimos 14 dias |
| Tela | `organisms/DemoAgentDiary.tsx` no Dashboard | Some quando `enabled` é falso |
| Acordar | `.github/workflows/acordar-demo.yml` | Abre o site às 03:10 de São Paulo |

**Semente por dia:** `SEMENTE ^ dia`, então o mesmo dia num banco recriado sai igual (sem IA).

**Testes:** `HumorDoDiaTest` (6, limites e respostas ruins da IA) e `DemoAgentIT` (2, rota desligada fora da demo e só gerente).
A geração em si usa o motor já conferido no doc 14 (invariantes de saldo e total).
