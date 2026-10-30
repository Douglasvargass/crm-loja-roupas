-- V14 · Cupom de desconto (pedido online e venda no balcão).
-- A venda guarda uma CÓPIA da regra do cupom (tipo, valor, mínimo) quando ele é aplicado: o total é sempre
-- recalculado no servidor a partir dos itens e dessa cópia, mesmo que o cupom mude depois. O uso só conta na
-- confirmação. sale.total passa a ser o valor LÍQUIDO (itens − desconto); sale.discount guarda o desconto.
CREATE TABLE coupon (
  id          bigint GENERATED ALWAYS AS IDENTITY,
  code        varchar(30)   NOT NULL,
  kind        varchar(10)   NOT NULL,
  value       numeric(12,2) NOT NULL,
  min_order   numeric(12,2) NOT NULL DEFAULT 0,
  valid_from  date          NOT NULL,
  valid_until date          NOT NULL,
  max_uses    integer,
  uses        integer       NOT NULL DEFAULT 0,
  is_active   boolean       NOT NULL DEFAULT true,
  created_by  bigint,
  created_at  timestamptz   NOT NULL DEFAULT now(),
  CONSTRAINT pk_coupon PRIMARY KEY (id),
  CONSTRAINT ck_coupon_code CHECK (code ~ '^[A-Z0-9_-]{3,30}$'),
  CONSTRAINT ck_coupon_kind CHECK (kind IN ('PERCENT', 'FIXED')),
  CONSTRAINT ck_coupon_value CHECK (value > 0 AND (kind <> 'PERCENT' OR value <= 90)),
  CONSTRAINT ck_coupon_min CHECK (min_order >= 0),
  CONSTRAINT ck_coupon_dates CHECK (valid_until >= valid_from),
  CONSTRAINT ck_coupon_uses CHECK (uses >= 0 AND (max_uses IS NULL OR (max_uses > 0 AND uses <= max_uses))),
  CONSTRAINT fk_coupon_user FOREIGN KEY (created_by) REFERENCES app_user(id) ON DELETE SET NULL
);
CREATE UNIQUE INDEX ux_coupon_code ON coupon (code);

ALTER TABLE sale ADD COLUMN discount numeric(12,2) NOT NULL DEFAULT 0;
ALTER TABLE sale ADD COLUMN coupon_id bigint;
ALTER TABLE sale ADD COLUMN coupon_code varchar(30);
ALTER TABLE sale ADD COLUMN coupon_kind varchar(10);
ALTER TABLE sale ADD COLUMN coupon_value numeric(12,2);
ALTER TABLE sale ADD COLUMN coupon_min_order numeric(12,2);
ALTER TABLE sale ADD CONSTRAINT ck_sale_discount CHECK (discount >= 0);
ALTER TABLE sale ADD CONSTRAINT fk_sale_coupon FOREIGN KEY (coupon_id) REFERENCES coupon(id) ON DELETE RESTRICT;
CREATE INDEX ix_sale_coupon ON sale (coupon_id) WHERE coupon_id IS NOT NULL;
