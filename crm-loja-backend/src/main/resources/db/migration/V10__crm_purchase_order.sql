-- V10__crm_purchase_order.sql
-- Pedido de compra ao fornecedor (spec crm/11). Quem cuida das compras (Operações e Compras, gerente) registra o que
-- pediu, de quem e quando deve chegar; o painel "O que vai faltar" desconta o que está a caminho; quem recebe a
-- mercadoria (estoque, gerente) dá entrada com um clique, e a entrada fecha sozinha os chamados da peça (spec crm/09).
-- Nada se apaga: o pedido é recebido ou cancelado (com motivo), e cada troca de data fica na auditoria.
CREATE TABLE purchase_order (
  id                bigint GENERATED ALWAYS AS IDENTITY,
  supplier          varchar(80)   NOT NULL,
  expected_date     date          NOT NULL,           -- quando o fornecedor prometeu entregar
  status            varchar(10)   NOT NULL DEFAULT 'OPEN',
  note              varchar(255),                     -- observação de quem pediu (ex.: nº do pedido no fornecedor)
  reschedule_count  integer       NOT NULL DEFAULT 0, -- quantas vezes a data mudou (o histórico está na auditoria)
  created_by        bigint,
  closing_note      varchar(255),                     -- recebido: observação opcional; cancelado: motivo obrigatório
  closed_by         bigint,
  closed_at         timestamptz,
  created_at        timestamptz   NOT NULL DEFAULT now(),
  updated_at        timestamptz   NOT NULL DEFAULT now(),
  CONSTRAINT pk_purchase_order PRIMARY KEY (id),
  CONSTRAINT ck_purchase_order_status       CHECK (status IN ('OPEN', 'RECEIVED', 'CANCELLED')),
  CONSTRAINT ck_purchase_order_supplier_len CHECK (char_length(btrim(supplier)) >= 2),
  CONSTRAINT ck_purchase_order_reschedule   CHECK (reschedule_count >= 0),
  -- Aberto não tem fechamento; fechado tem data; cancelado tem motivo.
  CONSTRAINT ck_purchase_order_closed       CHECK ((status = 'OPEN') = (closed_at IS NULL)
                                                   AND (status <> 'CANCELLED' OR closing_note IS NOT NULL)),
  CONSTRAINT fk_purchase_order_created_by FOREIGN KEY (created_by) REFERENCES app_user(id) ON DELETE SET NULL,
  CONSTRAINT fk_purchase_order_closed_by  FOREIGN KEY (closed_by)  REFERENCES app_user(id) ON DELETE SET NULL
);
CREATE INDEX ix_purchase_order_status ON purchase_order (status, expected_date);

CREATE TABLE purchase_order_item (
  id          bigint GENERATED ALWAYS AS IDENTITY,
  order_id    bigint  NOT NULL,
  product_id  bigint  NOT NULL,
  quantity    integer NOT NULL,
  CONSTRAINT pk_purchase_order_item PRIMARY KEY (id),
  CONSTRAINT ux_purchase_order_item_product UNIQUE (order_id, product_id),   -- uma linha por peça no pedido
  CONSTRAINT ck_purchase_order_item_qty     CHECK (quantity BETWEEN 1 AND 9999),
  CONSTRAINT fk_purchase_order_item_order   FOREIGN KEY (order_id)   REFERENCES purchase_order(id) ON DELETE RESTRICT,
  CONSTRAINT fk_purchase_order_item_product FOREIGN KEY (product_id) REFERENCES product(id)        ON DELETE RESTRICT
);
CREATE INDEX ix_purchase_order_item_product ON purchase_order_item (product_id);

-- O painel de reposição soma as vendas confirmadas por peça nos últimos 30 dias.
CREATE INDEX ix_sale_item_product ON sale_item (product_id);
