-- U9__crm_online_store.sql · reverso do V9 (aplicar à mão, pelo MIGRATIONS_RUNBOOK; apaga pagamentos e o vínculo das contas)
DROP TABLE IF EXISTS payment;
ALTER TABLE customer DROP CONSTRAINT IF EXISTS fk_customer_user;
ALTER TABLE customer DROP CONSTRAINT IF EXISTS ux_customer_user;
ALTER TABLE customer DROP COLUMN IF EXISTS user_id;
DROP INDEX IF EXISTS ix_sale_customer_channel;
ALTER TABLE sale DROP CONSTRAINT IF EXISTS ck_sale_channel;
ALTER TABLE sale DROP COLUMN IF EXISTS channel;
DELETE FROM flyway_schema_history WHERE version = '9';
