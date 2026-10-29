-- V12__crm_purchase_order_checking.sql
-- Conferência do pedido de compra (spec crm/11, REQ-08, regra do Douglas em 29/10/2026): o que chegou é contado peça
-- por peça. Chegou a menos → a nota VOLTA ao fornecedor (nada entra, o pedido volta a "pedido feito"). Chegou a mais →
-- o extra entra com a NOTA DE BONIFICAÇÃO. Nunca se dá entrada "mais ou menos" para ajustar depois.
ALTER TABLE purchase_order_item
  ADD COLUMN received_quantity integer;            -- contado na conferência; nulo até a entrada
ALTER TABLE purchase_order_item
  ADD CONSTRAINT ck_purchase_order_item_received CHECK (received_quantity IS NULL OR received_quantity >= 0);

ALTER TABLE purchase_order
  ADD COLUMN bonus_invoice_number varchar(30),     -- nota de bonificação do que veio a mais
  ADD COLUMN return_count         integer NOT NULL DEFAULT 0;   -- quantas vezes a nota voltou (motivos na auditoria)
ALTER TABLE purchase_order
  ADD CONSTRAINT ck_purchase_order_return_count CHECK (return_count >= 0);
