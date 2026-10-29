-- U11__crm_purchase_order_steps.sql · reverso do V11 (aplicar à mão, pelo MIGRATIONS_RUNBOOK).
-- Antes de voltar, pedidos em trânsito ou em conferência viram "OPEN" de novo (as datas de envio e chegada se perdem).
DROP INDEX IF EXISTS ix_purchase_order_closed;
UPDATE purchase_order SET status = 'OPEN' WHERE status IN ('IN_TRANSIT', 'CHECKING');
ALTER TABLE purchase_order DROP CONSTRAINT IF EXISTS ck_purchase_order_invoice_len;
ALTER TABLE purchase_order DROP CONSTRAINT IF EXISTS ck_purchase_order_steps;
ALTER TABLE purchase_order DROP CONSTRAINT ck_purchase_order_closed;
ALTER TABLE purchase_order ADD CONSTRAINT ck_purchase_order_closed
  CHECK ((status = 'OPEN') = (closed_at IS NULL) AND (status <> 'CANCELLED' OR closing_note IS NOT NULL));
ALTER TABLE purchase_order DROP CONSTRAINT ck_purchase_order_status;
ALTER TABLE purchase_order ADD CONSTRAINT ck_purchase_order_status CHECK (status IN ('OPEN', 'RECEIVED', 'CANCELLED'));
ALTER TABLE purchase_order DROP COLUMN arrived_at, DROP COLUMN shipped_at, DROP COLUMN invoice_number;
DELETE FROM flyway_schema_history WHERE version = '11';
