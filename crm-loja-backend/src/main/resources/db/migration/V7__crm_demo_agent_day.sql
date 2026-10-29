-- V7__crm_demo_agent_day.sql
-- Diário do agente de vendas da demonstração (spec crm/08): uma linha por dia que o agente fechou, gerado ou pulado.
-- Só é preenchida nos perfis dev/demo; em uso real fica vazia.
CREATE TABLE demo_agent_day (
  id               bigint GENERATED ALWAYS AS IDENTITY,
  day              date          NOT NULL,
  status           varchar(10)   NOT NULL,
  source           varchar(10)   NOT NULL,
  demand_factor    numeric(4,2),
  highlight_family varchar(3),
  highlight_factor numeric(4,2),
  reason           varchar(300)  NOT NULL,
  sales_created    integer       NOT NULL DEFAULT 0,
  revenue          numeric(12,2) NOT NULL DEFAULT 0,
  created_at       timestamptz   NOT NULL DEFAULT now(),
  CONSTRAINT pk_demo_agent_day PRIMARY KEY (id),
  -- Um dia só é fechado uma vez: duas subidas ao mesmo tempo não geram o mesmo dia em dobro.
  CONSTRAINT ux_demo_agent_day UNIQUE (day),
  CONSTRAINT ck_demo_agent_day_status CHECK (status IN ('GENERATED', 'SKIPPED')),
  CONSTRAINT ck_demo_agent_day_source CHECK (source IN ('AI', 'DEFAULT', 'NONE')),
  CONSTRAINT ck_demo_agent_day_sales  CHECK (sales_created >= 0)
);

-- Diário é registro: não se altera nem se apaga (mesma função do V2).
CREATE TRIGGER trg_demo_agent_day_immutable BEFORE UPDATE OR DELETE ON demo_agent_day
  FOR EACH ROW EXECUTE FUNCTION fn_reject_mutation();
