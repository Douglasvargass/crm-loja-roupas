-- U7__crm_demo_agent_day.sql · reverso do V7 (aplicar à mão, pelo MIGRATIONS_RUNBOOK; apaga o diário do agente)
DROP TRIGGER IF EXISTS trg_demo_agent_day_immutable ON demo_agent_day;
DROP TABLE IF EXISTS demo_agent_day;
DELETE FROM flyway_schema_history WHERE version = '7';
