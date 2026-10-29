-- V6__crm_sales_target.sql
-- Meta mensal de vendas por vendedor (spec crm/06). Uma linha por vendedor e mês. O valor pode ser corrigido
-- enquanto o mês não fechou; cada mudança vai para audit_event (de → para), então o histórico não se perde.
CREATE TABLE sales_target (
  id           bigint GENERATED ALWAYS AS IDENTITY,
  seller_id    bigint        NOT NULL,
  period_month date          NOT NULL,   -- sempre o dia 1 do mês (2026-10-01 = outubro/2026)
  amount       numeric(12,2) NOT NULL,
  created_by   bigint,
  updated_by   bigint,
  created_at   timestamptz   NOT NULL DEFAULT now(),
  updated_at   timestamptz   NOT NULL DEFAULT now(),
  CONSTRAINT pk_sales_target PRIMARY KEY (id),
  CONSTRAINT ux_sales_target_seller_month UNIQUE (seller_id, period_month),
  CONSTRAINT ck_sales_target_amount    CHECK (amount > 0),
  CONSTRAINT ck_sales_target_first_day CHECK (extract(day from period_month) = 1),
  -- RESTRICT: vendedor com meta registrada não some do banco (é inativado, como todo cadastro com histórico).
  CONSTRAINT fk_sales_target_seller     FOREIGN KEY (seller_id)  REFERENCES app_user(id) ON DELETE RESTRICT,
  CONSTRAINT fk_sales_target_created_by FOREIGN KEY (created_by) REFERENCES app_user(id) ON DELETE SET NULL,
  CONSTRAINT fk_sales_target_updated_by FOREIGN KEY (updated_by) REFERENCES app_user(id) ON DELETE SET NULL
);
CREATE INDEX ix_sales_target_month ON sales_target (period_month);

-- O painel do vendedor e o quadro de metas somam as vendas de quem abriu a venda (sale.created_by).
CREATE INDEX ix_sale_created_by_confirmed ON sale (created_by, status, confirmed_at);
