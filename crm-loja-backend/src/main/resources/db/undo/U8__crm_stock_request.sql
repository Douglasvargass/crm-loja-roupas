-- U8__crm_stock_request.sql · reverso do V8 (aplicar à mão, pelo MIGRATIONS_RUNBOOK; apaga os chamados)
DROP TABLE IF EXISTS stock_request;
DELETE FROM flyway_schema_history WHERE version = '8';
