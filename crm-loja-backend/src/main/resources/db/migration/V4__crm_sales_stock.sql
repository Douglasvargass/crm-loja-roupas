-- V4__crm_sales_stock.sql
-- Vendas e movimentação de estoque. A venda nunca é apagada: cancela-se com motivo.
CREATE TYPE sale_status AS ENUM ('DRAFT', 'CONFIRMED', 'CANCELLED');
CREATE TYPE stock_movement_type AS ENUM ('ENTRY', 'ADJUSTMENT', 'SALE', 'SALE_CANCELLATION');

CREATE TABLE sale (
  id            bigint GENERATED ALWAYS AS IDENTITY,
  customer_id   bigint,
  status        sale_status   NOT NULL DEFAULT 'DRAFT',
  total         numeric(12,2) NOT NULL DEFAULT 0,
  confirmed_at  timestamptz,
  cancelled_at  timestamptz,
  cancel_reason varchar(255),
  created_by    bigint,
  cancelled_by  bigint,
  created_at    timestamptz NOT NULL DEFAULT now(),
  updated_at    timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT pk_sale PRIMARY KEY (id),
  CONSTRAINT ck_sale_total     CHECK (total >= 0),
  CONSTRAINT ck_sale_confirmed CHECK (status <> 'CONFIRMED' OR confirmed_at IS NOT NULL),
  CONSTRAINT ck_sale_cancelled CHECK ((status = 'CANCELLED') = (cancelled_at IS NOT NULL AND cancel_reason IS NOT NULL)),
  CONSTRAINT fk_sale_customer     FOREIGN KEY (customer_id)  REFERENCES customer(id) ON DELETE RESTRICT,
  CONSTRAINT fk_sale_created_by   FOREIGN KEY (created_by)   REFERENCES app_user(id) ON DELETE SET NULL,
  CONSTRAINT fk_sale_cancelled_by FOREIGN KEY (cancelled_by) REFERENCES app_user(id) ON DELETE SET NULL
);
CREATE INDEX ix_sale_status_confirmed ON sale (status, confirmed_at);
CREATE INDEX ix_sale_customer         ON sale (customer_id, created_at);

CREATE TABLE sale_item (
  id           bigint GENERATED ALWAYS AS IDENTITY,
  sale_id      bigint        NOT NULL,
  product_id   bigint        NOT NULL,
  product_code varchar(30)   NOT NULL,   -- copiado: preserva a venda se o produto mudar
  product_name varchar(120)  NOT NULL,
  unit_price   numeric(12,2) NOT NULL,
  quantity     integer       NOT NULL,
  subtotal     numeric(12,2) NOT NULL,
  CONSTRAINT pk_sale_item PRIMARY KEY (id),
  CONSTRAINT ux_sale_item_product UNIQUE (sale_id, product_id),
  CONSTRAINT ck_sale_item_qty      CHECK (quantity > 0),
  CONSTRAINT ck_sale_item_price    CHECK (unit_price > 0),
  CONSTRAINT ck_sale_item_subtotal CHECK (subtotal = unit_price * quantity),
  CONSTRAINT fk_sale_item_sale    FOREIGN KEY (sale_id)    REFERENCES sale(id)    ON DELETE RESTRICT,
  CONSTRAINT fk_sale_item_product FOREIGN KEY (product_id) REFERENCES product(id) ON DELETE RESTRICT
);

CREATE TABLE stock_movement (
  id            bigint GENERATED ALWAYS AS IDENTITY,
  product_id    bigint              NOT NULL,
  sale_id       bigint,
  type          stock_movement_type NOT NULL,
  quantity      integer             NOT NULL,
  balance_after integer             NOT NULL,
  reason        varchar(255),
  created_by    bigint,
  created_at    timestamptz         NOT NULL DEFAULT now(),
  CONSTRAINT pk_stock_movement PRIMARY KEY (id),
  CONSTRAINT ck_stock_movement_qty     CHECK (quantity <> 0),
  CONSTRAINT ck_stock_movement_balance CHECK (balance_after >= 0),
  CONSTRAINT ck_stock_movement_sign CHECK (
       (type = 'ENTRY'             AND quantity > 0)
    OR (type = 'SALE'              AND quantity < 0)
    OR (type = 'SALE_CANCELLATION' AND quantity > 0)
    OR  type = 'ADJUSTMENT'),
  CONSTRAINT ck_stock_movement_sale   CHECK ((type IN ('SALE', 'SALE_CANCELLATION')) = (sale_id IS NOT NULL)),
  CONSTRAINT ck_stock_movement_reason CHECK (type <> 'ADJUSTMENT' OR reason IS NOT NULL),
  -- Uma baixa e um estorno por item, no máximo: segunda confirmação ou cancelamento falha no banco.
  CONSTRAINT ux_stock_movement_sale_item UNIQUE (sale_id, product_id, type),
  CONSTRAINT fk_stock_movement_product    FOREIGN KEY (product_id) REFERENCES product(id)  ON DELETE RESTRICT,
  CONSTRAINT fk_stock_movement_sale       FOREIGN KEY (sale_id)    REFERENCES sale(id)     ON DELETE RESTRICT,
  CONSTRAINT fk_stock_movement_created_by FOREIGN KEY (created_by) REFERENCES app_user(id) ON DELETE SET NULL
);
CREATE INDEX ix_stock_movement_product ON stock_movement (product_id, created_at);

CREATE TRIGGER trg_stock_movement_immutable BEFORE UPDATE OR DELETE ON stock_movement
  FOR EACH ROW EXECUTE FUNCTION fn_reject_mutation();
