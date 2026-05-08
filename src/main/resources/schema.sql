-- =============================================================================
-- IncidentIQ — supplemental schema (FULLTEXT index for similar-incident search)
--
-- Hibernate's @Index annotation cannot generate FULLTEXT indexes, so we add
-- it here. This file runs AFTER ddl-auto=update creates the tables, because
-- spring.jpa.defer-datasource-initialization=true is set in application.properties.
--
-- MySQL has no `CREATE FULLTEXT INDEX IF NOT EXISTS`, so this statement is
-- not natively idempotent. spring.sql.init.continue-on-error=true makes
-- subsequent boots log a "Duplicate key name" warning and move on.
-- First boot: index created. Later boots: harmless warning.
-- =============================================================================

CREATE FULLTEXT INDEX idx_incident_fts ON incident(title, description);
