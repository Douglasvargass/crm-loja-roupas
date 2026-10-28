-- Reverso de V1.
DROP TABLE IF EXISTS user_module_access;
DROP TABLE IF EXISTS module_role;
DROP TABLE IF EXISTS module;
DROP TABLE IF EXISTS refresh_token;
DROP TABLE IF EXISTS app_user;
DELETE FROM flyway_schema_history WHERE version = '1';
