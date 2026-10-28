-- Reverso de V3.
DROP TABLE IF EXISTS product;
DROP TABLE IF EXISTS category;
DROP TABLE IF EXISTS customer;
DELETE FROM flyway_schema_history WHERE version = '3';
