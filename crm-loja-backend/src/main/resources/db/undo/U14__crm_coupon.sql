-- U14__crm_coupon.sql · reverso do V14 (aplicar à mão, pelo MIGRATIONS_RUNBOOK). ATENÇÃO: vendas com cupom voltam
-- a mostrar o total líquido sem o registro do desconto.
DROP INDEX IF EXISTS ix_sale_coupon;
ALTER TABLE sale DROP CONSTRAINT IF EXISTS fk_sale_coupon;
ALTER TABLE sale DROP CONSTRAINT IF EXISTS ck_sale_discount;
ALTER TABLE sale DROP COLUMN IF EXISTS coupon_min_order;
ALTER TABLE sale DROP COLUMN IF EXISTS coupon_value;
ALTER TABLE sale DROP COLUMN IF EXISTS coupon_kind;
ALTER TABLE sale DROP COLUMN IF EXISTS coupon_code;
ALTER TABLE sale DROP COLUMN IF EXISTS coupon_id;
ALTER TABLE sale DROP COLUMN IF EXISTS discount;
DROP TABLE IF EXISTS coupon;
