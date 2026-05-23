ALTER TABLE ai_investigations
    ADD COLUMN IF NOT EXISTS incident_id VARCHAR(255) NOT NULL DEFAULT 'legacy-incident';

ALTER TABLE ai_investigations
    ADD COLUMN IF NOT EXISTS incident_group_key VARCHAR(1000) NOT NULL DEFAULT 'legacy-group';

ALTER TABLE ai_investigations
    ADD COLUMN IF NOT EXISTS incident_status VARCHAR(255) NOT NULL DEFAULT 'ACTIVE';

ALTER TABLE ai_investigations
    ADD COLUMN IF NOT EXISTS suspected_contributor VARCHAR(255) NOT NULL DEFAULT 'unknown';

ALTER TABLE ai_investigations
    ADD COLUMN IF NOT EXISTS affected_metrics VARCHAR(1000) NOT NULL DEFAULT 'UNKNOWN';

ALTER TABLE ai_investigations
    ADD COLUMN IF NOT EXISTS confidence_score INTEGER NOT NULL DEFAULT 0;

ALTER TABLE ai_investigations
    ADD COLUMN IF NOT EXISTS confidence_level VARCHAR(255) NOT NULL DEFAULT 'UNKNOWN';

ALTER TABLE ai_investigations
    ADD COLUMN IF NOT EXISTS confidence_reasoning VARCHAR(4000) NOT NULL DEFAULT 'No confidence reasoning captured yet.';

ALTER TABLE ai_investigations
    ADD COLUMN IF NOT EXISTS baseline_summary VARCHAR(4000) NOT NULL DEFAULT 'No machine baseline summary captured yet.';

ALTER TABLE ai_investigations
    ADD COLUMN IF NOT EXISTS historical_pattern_notes VARCHAR(4000) NOT NULL DEFAULT 'No historical pattern notes captured yet.';

CREATE INDEX IF NOT EXISTS idx_ai_investigations_incident_id
    ON ai_investigations (incident_id);

CREATE INDEX IF NOT EXISTS idx_ai_investigations_machine_status_created
    ON ai_investigations (machine_identifier, incident_status, created_at DESC);
