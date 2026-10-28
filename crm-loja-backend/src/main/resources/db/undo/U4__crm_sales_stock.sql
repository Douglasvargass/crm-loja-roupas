-- Reverso de V4. Aplicar à mão (MIGRATIONS_RUNBOOK.md). Apaga vendas e movimentações: só em ambiente local.
DROP TRIGGER IF EXISTS trg_stock_movement_immutable ON stock_movement;
DROP TABLE IF EXISTS stock_movement;
DROP TABLE IF EXISTS sale_item;
DROP TABLE IF EXISTS sale;
DROP TYPE IF EXISTS stock_movement_type;
DROP TYPE IF EXISTS sale_status;
DELETE FROM flyway_schema_history WHERE version = '4';
