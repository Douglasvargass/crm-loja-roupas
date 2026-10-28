-- V5__crm_product_purge.sql
-- HU13 permite excluir o produto que só tem a movimentação de "Estoque inicial". O trigger do V4
-- recusaria esse DELETE; aqui ele passa a aceitar UMA exceção: apagar movimentações ENTRY sem venda
-- do produto cujo id foi liberado com set_config('crm.product_purge', id, true) na mesma transação.
-- O ProductService só libera depois de conferir que não há vendas nem outras movimentações.
CREATE OR REPLACE FUNCTION fn_stock_movement_guard() RETURNS trigger AS $$
BEGIN
  IF TG_OP = 'DELETE'
     AND OLD.type = 'ENTRY'
     AND OLD.sale_id IS NULL
     AND current_setting('crm.product_purge', true) = OLD.product_id::text THEN
    RETURN OLD;
  END IF;
  RAISE EXCEPTION '% é um registro permanente: as linhas não se alteram nem se apagam', TG_TABLE_NAME;
END; $$ LANGUAGE plpgsql;

DROP TRIGGER trg_stock_movement_immutable ON stock_movement;
CREATE TRIGGER trg_stock_movement_immutable BEFORE UPDATE OR DELETE ON stock_movement
  FOR EACH ROW EXECUTE FUNCTION fn_stock_movement_guard();
