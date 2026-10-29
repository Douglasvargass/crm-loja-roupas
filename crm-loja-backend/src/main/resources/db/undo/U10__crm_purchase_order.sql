-- U10__crm_purchase_order.sql · reverso do V10 (aplicar à mão, pelo MIGRATIONS_RUNBOOK; apaga os pedidos de compra)
DROP INDEX IF EXISTS ix_sale_item_product;
DROP TABLE IF EXISTS purchase_order_item;
DROP TABLE IF EXISTS purchase_order;
DELETE FROM flyway_schema_history WHERE version = '10';
