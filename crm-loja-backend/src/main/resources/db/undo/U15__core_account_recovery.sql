-- U15__core_account_recovery.sql · reverso do V15 (aplicar à mão, pelo MIGRATIONS_RUNBOOK; apaga os links de recuperação)
DROP TABLE IF EXISTS password_reset_token;
DROP INDEX IF EXISTS ux_app_user_email;
ALTER TABLE app_user DROP COLUMN IF EXISTS email;
