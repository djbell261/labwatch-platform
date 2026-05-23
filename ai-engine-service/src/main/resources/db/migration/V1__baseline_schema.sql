CREATE TABLE IF NOT EXISTS ai_investigations (
    id BIGSERIAL PRIMARY KEY,
    investigation_id VARCHAR(255) NOT NULL UNIQUE,
    incident_id VARCHAR(255) NOT NULL DEFAULT 'legacy-incident',
    incident_group_key VARCHAR(1000) NOT NULL DEFAULT 'legacy-group',
    incident_status VARCHAR(255) NOT NULL DEFAULT 'ACTIVE',
    alert_id VARCHAR(255) NOT NULL,
    machine_identifier VARCHAR(255) NOT NULL,
    alert_type VARCHAR(255) NOT NULL,
    severity VARCHAR(255) NOT NULL,
    summary VARCHAR(4000) NOT NULL,
    likely_cause VARCHAR(4000) NOT NULL,
    evidence VARCHAR(4000) NOT NULL DEFAULT 'No evidence captured yet.',
    contributing_factors VARCHAR(4000) NOT NULL DEFAULT 'No contributing factors captured yet.',
    recommended_checks VARCHAR(4000) NOT NULL DEFAULT 'No recommended checks captured yet.',
    recommended_action VARCHAR(4000) NOT NULL,
    urgency_assessment VARCHAR(4000) NOT NULL DEFAULT 'Urgency has not been assessed yet.',
    persistence_assessment VARCHAR(4000) NOT NULL DEFAULT 'Persistence has not been assessed yet.',
    monitor_next VARCHAR(4000) NOT NULL DEFAULT 'No follow-up monitoring guidance captured yet.',
    suspected_contributor VARCHAR(255) NOT NULL DEFAULT 'unknown',
    affected_metrics VARCHAR(1000) NOT NULL DEFAULT 'UNKNOWN',
    confidence_score INTEGER NOT NULL DEFAULT 0,
    confidence_level VARCHAR(255) NOT NULL DEFAULT 'UNKNOWN',
    confidence_reasoning VARCHAR(4000) NOT NULL DEFAULT 'No confidence reasoning captured yet.',
    baseline_summary VARCHAR(4000) NOT NULL DEFAULT 'No machine baseline summary captured yet.',
    historical_pattern_notes VARCHAR(4000) NOT NULL DEFAULT 'No historical pattern notes captured yet.',
    correlation_timeline VARCHAR(12000) NOT NULL DEFAULT '[]',
    confidence VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    persisted_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_ai_investigations_incident_id
    ON ai_investigations (incident_id);

CREATE INDEX IF NOT EXISTS idx_ai_investigations_machine_status_created
    ON ai_investigations (machine_identifier, incident_status, created_at DESC);

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
