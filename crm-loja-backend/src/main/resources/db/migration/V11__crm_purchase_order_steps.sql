-- V11__crm_purchase_order_steps.sql
-- Etapas do pedido de compra (spec crm/11, REQ-06, pedido do Douglas em 29/10/2026): antes era só "a caminho",
-- "recebido" ou "cancelado". Agora o pedido passa por
--   OPEN (pedido feito) → IN_TRANSIT (saiu do fornecedor, em trânsito) → CHECKING (chegou na loja, em conferência)
--   → RECEIVED (conferido: entrada no estoque, com o número da nota fiscal).
-- Pular etapas continua possível (ex.: a mercadoria chegou sem aviso de envio). Cancelar vale enquanto não deu entrada.
-- Os pedidos que já existem continuam como estão (OPEN, RECEIVED ou CANCELLED); nenhum dado é apagado.

ALTER TABLE purchase_order
  ADD COLUMN invoice_number varchar(30),   -- nº da nota fiscal: pode vir no envio; é obrigatório para dar entrada
  ADD COLUMN shipped_at     timestamptz,   -- quando o fornecedor despachou (em trânsito)
  ADD COLUMN arrived_at     timestamptz;   -- quando a mercadoria chegou na loja (em conferência)

ALTER TABLE purchase_order DROP CONSTRAINT ck_purchase_order_status;
ALTER TABLE purchase_order ADD CONSTRAINT ck_purchase_order_status
  CHECK (status IN ('OPEN', 'IN_TRANSIT', 'CHECKING', 'RECEIVED', 'CANCELLED'));

-- Fechado (recebido ou cancelado) tem data de fechamento; aberto, em trânsito e em conferência não. Cancelado tem motivo.
ALTER TABLE purchase_order DROP CONSTRAINT ck_purchase_order_closed;
ALTER TABLE purchase_order ADD CONSTRAINT ck_purchase_order_closed
  CHECK ((status IN ('OPEN', 'IN_TRANSIT', 'CHECKING')) = (closed_at IS NULL)
         AND (status <> 'CANCELLED' OR closing_note IS NOT NULL));

-- Em trânsito tem data de envio; em conferência tem data de chegada.
ALTER TABLE purchase_order ADD CONSTRAINT ck_purchase_order_steps
  CHECK ((status <> 'IN_TRANSIT' OR shipped_at IS NOT NULL) AND (status <> 'CHECKING' OR arrived_at IS NOT NULL));

ALTER TABLE purchase_order ADD CONSTRAINT ck_purchase_order_invoice_len
  CHECK (invoice_number IS NULL OR char_length(btrim(invoice_number)) >= 1);

-- "Notas que deram entrada" no Dashboard: os recebimentos mais recentes.
CREATE INDEX ix_purchase_order_closed ON purchase_order (status, closed_at);
