# 09-chamados-peca-em-falta · Design

| Peça | Onde |
| --- | --- |
| Tabela | `V8__crm_stock_request.sql` (+ `U8`): status em texto com CHECK, fechamento exige data e nota (`ck_stock_request_closed`) |
| Entidade e regras | `modules/crm/request/`: `StockRequest`, `StockRequestRepository`, `StockRequestService` (abrir, fechar, `onStockEntry`, listar, sino, visto) |
| Rotas | `StockRequestController`: `GET /crm/requests`, `POST /crm/requests`, `POST /{id}/resolve`, `POST /{id}/cancel`, `GET /alerts`, `POST /alerts/seen` |
| Peça chegou | `StockService.entry` chama `onStockEntry` na mesma transação |
| Thalita | `abrir_chamado` (G V, exige `confirmado`) e `listar_chamados` (todos); as ferramentas passam a receber também o papel de quem pergunta |
| Telas | `RequestsPage` (`/chamados`, abas por situação, novo chamado, resolver/cancelar), `AlertBell` (sino no topo, de minuto em minuto), botão "Cliente quer esta peça" na ficha de produto zerado |

**Testes:** `StockRequestIT` (6): abrir e recusar com estoque, validações e quem abre, vendedor só vê os dele, resolver e cancelar com auditoria, entrada resolve e avisa, Thalita só com confirmação. `AssistantIT` com 13 ferramentas para o gerente e 14 para a equipe técnica.
