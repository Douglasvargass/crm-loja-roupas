-- U12__crm_purchase_order_checking.sql · reverso do V12 (aplicar à mão, pelo MIGRATIONS_RUNBOOK).
ALTER TABLE purchase_order DROP CONSTRAINT IF EXISTS ck_purchase_order_return_count;
ALTER TABLE purchase_order DROP COLUMN return_count, DROP COLUMN bonus_invoice_number;
ALTER TABLE purchase_order_item DROP CONSTRAINT IF EXISTS ck_purchase_order_item_received;
ALTER TABLE purchase_order_item DROP COLUMN received_quantity;
DELETE FROM flyway_schema_history WHERE version = '12';
