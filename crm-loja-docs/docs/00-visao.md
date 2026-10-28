## 1. Visão geral

O MVP é uma aplicação web em Java (Spring Boot) com front em React e banco PostgreSQL, seguindo uma arquitetura de monólito modular (migrations versionadas, testes contra banco real). Ele centraliza clientes, produtos, categorias, estoque e vendas de uma loja de roupas, e tem um dashboard que lê os dados reais do banco.

**Problema:** sem sistema integrado, a loja não sabe o que tem disponível, perde o histórico de compras dos clientes e não enxerga o resultado das vendas.

**Objetivo do MVP:** cobrir o ciclo completo cadastro → entrada em estoque → venda → baixa automática → cancelamento com estorno → indicadores, sempre mantendo estoque e histórico coerentes.

### Módulos

| Módulo | O que entrega |
| --- | --- |
| Clientes | CRUD, busca por nome/CPF, histórico de compras |
| Categorias | CRUD, bloqueio de exclusão com produtos vinculados |
| Produtos | CRUD, filtros por nome/categoria/tamanho/código/disponibilidade, ativo × indisponível |
| Estoque | Saldo, entradas, ajustes, histórico de movimentações, estoque baixo/zerado |
| Vendas | Carrinho com cálculo automático, confirmação com baixa, cancelamento com estorno, consulta por número/cliente/data/status |
| Dashboard | Totais, vendas e faturamento por período, mais vendidos, estoque baixo e zerado |
| Acesso | Login, troca de senha, 3 perfis (Gerente, Vendedor, Estoquista), auditoria das operações sensíveis |

### Fora do escopo do MVP

- Escopo por loja ou região (o sistema atende uma única loja)
- Variações de produto (tamanho/cor como tabela própria): cada combinação é um produto com código próprio
- Descontos, formas de pagamento, nota fiscal, troca/devolução parcial
- Integração com sistemas externos

Esses itens só entram depois que todos os critérios obrigatórios estiverem aprovados.
