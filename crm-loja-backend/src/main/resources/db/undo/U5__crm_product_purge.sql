-- U5__crm_product_purge.sql (reversão manual do V5; ver MIGRATIONS_RUNBOOK.md)
DROP TRIGGER trg_stock_movement_immutable ON stock_movement;
CREATE TRIGGER trg_stock_movement_immutable BEFORE UPDATE OR DELETE ON stock_movement
  FOR EACH ROW EXECUTE FUNCTION fn_reject_mutation();
DROP FUNCTION fn_stock_movement_guard();
DELETE FROM flyway_schema_history WHERE version = '5';
