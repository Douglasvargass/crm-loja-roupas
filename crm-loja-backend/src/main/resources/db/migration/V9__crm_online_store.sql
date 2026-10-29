-- V9__crm_online_store.sql
-- Espaço do cliente (spec crm/07). O pedido online é a mesma venda do balcão, com canal ONLINE; o cliente entra com
-- uma conta própria ligada ao cadastro dele; o pagamento é simulado e só o aprovado é gravado.

-- Canal da venda: balcão (todas as vendas que já existem) ou online.
ALTER TABLE sale ADD COLUMN channel varchar(10) NOT NULL DEFAULT 'STORE';
ALTER TABLE sale ADD CONSTRAINT ck_sale_channel CHECK (channel IN ('STORE', 'ONLINE'));
CREATE INDEX ix_sale_customer_channel ON sale (customer_id, channel, status);

-- Conta de acesso do cliente: no máximo uma por cadastro, e uma conta aponta para um cadastro só.
ALTER TABLE customer ADD COLUMN user_id bigint;
ALTER TABLE customer ADD CONSTRAINT ux_customer_user UNIQUE (user_id);
ALTER TABLE customer ADD CONSTRAINT fk_customer_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE RESTRICT;

-- Pagamento simulado. Nunca guarda número de cartão: só os 4 últimos dígitos.
CREATE TABLE payment (
  id          bigint GENERATED ALWAYS AS IDENTITY,
  sale_id     bigint        NOT NULL,
  method      varchar(10)   NOT NULL,
  status      varchar(10)   NOT NULL DEFAULT 'APPROVED',
  amount      numeric(12,2) NOT NULL,
  card_last4  char(4),
  created_at  timestamptz   NOT NULL DEFAULT now(),
  refunded_at timestamptz,
  CONSTRAINT pk_payment PRIMARY KEY (id),
  CONSTRAINT ux_payment_sale UNIQUE (sale_id),           -- um pagamento por pedido: clique duplo não cobra duas vezes
  CONSTRAINT ck_payment_method CHECK (method IN ('PIX', 'CARD')),
  CONSTRAINT ck_payment_status CHECK (status IN ('APPROVED', 'REFUNDED')),
  CONSTRAINT ck_payment_amount CHECK (amount > 0),
  CONSTRAINT ck_payment_card   CHECK ((method = 'CARD') = (card_last4 IS NOT NULL)),
  CONSTRAINT ck_payment_refund CHECK ((status = 'REFUNDED') = (refunded_at IS NOT NULL)),
  CONSTRAINT fk_payment_sale FOREIGN KEY (sale_id) REFERENCES sale(id) ON DELETE RESTRICT
);
