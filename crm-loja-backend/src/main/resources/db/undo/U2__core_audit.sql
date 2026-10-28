-- Reverso de V2.
DROP TRIGGER IF EXISTS trg_audit_event_immutable ON audit_event;
DROP TABLE IF EXISTS audit_event;
DROP FUNCTION IF EXISTS fn_reject_mutation();
DELETE FROM flyway_schema_history WHERE version = '2';
