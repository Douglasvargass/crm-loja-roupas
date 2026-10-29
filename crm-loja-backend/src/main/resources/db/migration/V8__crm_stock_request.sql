-- V8__crm_stock_request.sql
-- Chamado de peça em falta (spec crm/09): o cliente quis uma peça que acabou (ou que a loja não tem), o vendedor
-- registra, o estoque e o gerente são avisados, e quem abriu fica sabendo quando resolve. Nada se apaga: o chamado
-- é resolvido ou cancelado, sempre com nota.
CREATE TABLE stock_request (
  id                 bigint GENERATED ALWAYS AS IDENTITY,
  product_id         bigint,                          -- opcional: a peça pode nem existir no catálogo
  description        varchar(200)  NOT NULL,          -- o que o cliente queria, nas palavras do vendedor
  size               varchar(10),
  color              varchar(30),
  quantity           integer       NOT NULL DEFAULT 1,
  customer_id        bigint,                          -- opcional: cliente identificado
  status             varchar(10)   NOT NULL DEFAULT 'OPEN',
  opened_via         varchar(10)   NOT NULL DEFAULT 'SCREEN',
  opened_by          bigint,
  closing_note       varchar(255),
  closed_by          bigint,
  closed_at          timestamptz,
  requester_seen_at  timestamptz,                     -- quando quem abriu viu o aviso de que fechou
  created_at         timestamptz   NOT NULL DEFAULT now(),
  updated_at         timestamptz   NOT NULL DEFAULT now(),
  CONSTRAINT pk_stock_request PRIMARY KEY (id),
  CONSTRAINT ck_stock_request_status   CHECK (status IN ('OPEN', 'RESOLVED', 'CANCELLED')),
  CONSTRAINT ck_stock_request_via      CHECK (opened_via IN ('SCREEN', 'ASSISTANT')),
  CONSTRAINT ck_stock_request_qty      CHECK (quantity BETWEEN 1 AND 99),
  CONSTRAINT ck_stock_request_desc_len CHECK (char_length(btrim(description)) >= 3),
  -- Aberto não tem fechamento; fechado tem data e nota.
  CONSTRAINT ck_stock_request_closed   CHECK ((status = 'OPEN') = (closed_at IS NULL)
                                            AND (status = 'OPEN' OR closing_note IS NOT NULL)),
  CONSTRAINT fk_stock_request_product   FOREIGN KEY (product_id)  REFERENCES product(id)  ON DELETE RESTRICT,
  CONSTRAINT fk_stock_request_customer  FOREIGN KEY (customer_id) REFERENCES customer(id) ON DELETE RESTRICT,
  CONSTRAINT fk_stock_request_opened_by FOREIGN KEY (opened_by)   REFERENCES app_user(id) ON DELETE SET NULL,
  CONSTRAINT fk_stock_request_closed_by FOREIGN KEY (closed_by)   REFERENCES app_user(id) ON DELETE SET NULL
);
CREATE INDEX ix_stock_request_status  ON stock_request (status, created_at);
CREATE INDEX ix_stock_request_product ON stock_request (product_id, status);
CREATE INDEX ix_stock_request_opener  ON stock_request (opened_by, status);
