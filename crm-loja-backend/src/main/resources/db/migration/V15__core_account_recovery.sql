-- V15 · Conta do cliente pelo site e "Esqueci minha senha".
-- app_user.email: e-mail para recuperar a senha (o cliente usa o do cadastro; a equipe pode ter um). Único sem
-- diferença de maiúsculas. password_reset_token guarda só o HASH do link enviado por e-mail: o link vale 30 minutos
-- e uma vez só.
ALTER TABLE app_user ADD COLUMN email varchar(120);
CREATE UNIQUE INDEX ux_app_user_email ON app_user (lower(email)) WHERE email IS NOT NULL;

CREATE TABLE password_reset_token (
  id          bigint GENERATED ALWAYS AS IDENTITY,
  user_id     bigint      NOT NULL,
  token_hash  char(64)    NOT NULL,
  expires_at  timestamptz NOT NULL,
  used_at     timestamptz,
  created_at  timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT pk_password_reset_token PRIMARY KEY (id),
  CONSTRAINT ux_password_reset_token_hash UNIQUE (token_hash),
  CONSTRAINT fk_password_reset_token_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE
);
CREATE INDEX ix_password_reset_token_user ON password_reset_token (user_id, created_at);

-- Contas de cliente que já existem: o e-mail do cadastro vira o e-mail da conta (um por e-mail, sem repetir).
UPDATE app_user u SET email = x.email
  FROM (SELECT DISTINCT ON (lower(c.email)) c.user_id, lower(c.email) AS email
          FROM customer c WHERE c.user_id IS NOT NULL
         ORDER BY lower(c.email), c.user_id) x
 WHERE x.user_id = u.id AND u.email IS NULL
   AND NOT EXISTS (SELECT 1 FROM app_user o WHERE lower(o.email) = x.email);
