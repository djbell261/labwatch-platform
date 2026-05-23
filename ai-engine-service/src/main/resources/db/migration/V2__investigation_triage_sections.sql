CREATE TABLE IF NOT EXISTS ai_investigations (
    id BIGSERIAL PRIMARY KEY,
    investigation_id VARCHAR(255) NOT NULL UNIQUE,
    alert_id VARCHAR(255) NOT NULL,
    machine_identifier VARCHAR(255) NOT NULL,
    alert_type VARCHAR(255) NOT NULL,
    severity VARCHAR(255) NOT NULL,
    summary VARCHAR(4000) NOT NULL,
    likely_cause VARCHAR(4000) NOT NULL,
    recommended_action VARCHAR(4000) NOT NULL,
    confidence VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    persisted_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS anomaly (
    id BIGSERIAL PRIMARY KEY,
    anomaly_id UUID NOT NULL UNIQUE,
    source_event_id UUID NOT NULL,
    machine_id BIGINT NOT NULL,
    machine_identifier VARCHAR(255) NOT NULL,
    hostname VARCHAR(255) NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    metric_value DOUBLE PRECISION NOT NULL,
    rolling_average DOUBLE PRECISION NOT NULL,
    standard_deviation DOUBLE PRECISION NOT NULL,
    z_score DOUBLE PRECISION NOT NULL,
    sample_size INTEGER NOT NULL,
    severity VARCHAR(255) NOT NULL,
    message VARCHAR(255) NOT NULL,
    detected_at TIMESTAMP NOT NULL
);

ALTER TABLE ai_investigations
    ADD COLUMN IF NOT EXISTS evidence VARCHAR(4000) NOT NULL DEFAULT 'No evidence captured yet.';

ALTER TABLE ai_investigations
    ADD COLUMN IF NOT EXISTS contributing_factors VARCHAR(4000) NOT NULL DEFAULT 'No contributing factors captured yet.';

ALTER TABLE ai_investigations
    ADD COLUMN IF NOT EXISTS recommended_checks VARCHAR(4000) NOT NULL DEFAULT 'No recommended checks captured yet.';

ALTER TABLE ai_investigations
    ADD COLUMN IF NOT EXISTS urgency_assessment VARCHAR(4000) NOT NULL DEFAULT 'Urgency has not been assessed yet.';

ALTER TABLE ai_investigations
    ADD COLUMN IF NOT EXISTS persistence_assessment VARCHAR(4000) NOT NULL DEFAULT 'Persistence has not been assessed yet.';

ALTER TABLE ai_investigations
    ADD COLUMN IF NOT EXISTS monitor_next VARCHAR(4000) NOT NULL DEFAULT 'No follow-up monitoring guidance captured yet.';

ALTER TABLE ai_investigations
    ADD COLUMN IF NOT EXISTS correlation_timeline VARCHAR(12000) NOT NULL DEFAULT '[]';
