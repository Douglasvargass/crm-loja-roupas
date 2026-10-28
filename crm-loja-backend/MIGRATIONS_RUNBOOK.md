# Runbook de migrations

1. Criar `src/main/resources/db/migration/V{n}__descricao.sql` com SQL explícito, constraints nomeadas e comentário do porquê.
2. Criar o reverso em `src/main/resources/db/undo/U{n}__descricao.sql` (ordem inversa: triggers → FKs → índices → tabelas → tipos).
3. Rodar `./mvnw verify`: os testes sobem um Postgres vazio e aplicam TODAS as migrations do zero.
4. A migration precisa funcionar com o código da versão anterior (o deploy aplica schema antes de trocar o código).

## Expand/contract

| Mudança | Entrega 1 (expand) | Entrega 2 (contract) |
| --- | --- | --- |
| Coluna nova obrigatória | nullable ou com default; código já grava | SET NOT NULL |
| Renomear coluna | cria a nova; código grava nas duas | DROP da antiga |
| Remover coluna ou tabela | código para de usar (sem SQL) | DROP |
| Trocar tipo | coluna nova + backfill | DROP da antiga |
| UNIQUE em dado existente | conferir duplicatas (GROUP BY ... HAVING count(*) > 1) | — |

## Reverter localmente

```bash
docker compose exec -T postgres psql -U crm -d crm_loja < src/main/resources/db/undo/U4__crm_sales_stock.sql
```

Nunca reverter schema em produção sem backup (`pg_dump -Fc`).
