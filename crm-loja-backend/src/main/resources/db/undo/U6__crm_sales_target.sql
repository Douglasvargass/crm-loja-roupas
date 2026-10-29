-- U6__crm_sales_target.sql · reverso do V6 (aplicar à mão, pelo MIGRATIONS_RUNBOOK; apaga as metas cadastradas)
DROP INDEX IF EXISTS ix_sale_created_by_confirmed;
DROP TABLE IF EXISTS sales_target;
DELETE FROM flyway_schema_history WHERE version = '6';
