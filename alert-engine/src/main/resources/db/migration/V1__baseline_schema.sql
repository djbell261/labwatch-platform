CREATE TABLE IF NOT EXISTS alert (
    id BIGSERIAL PRIMARY KEY,
    event_id UUID NOT NULL,
    machine_id BIGINT NOT NULL,
    machine_identifier VARCHAR(255) NOT NULL,
    hostname VARCHAR(255) NOT NULL,
    alert_type VARCHAR(255) NOT NULL,
    severity VARCHAR(255) NOT NULL,
    message VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    resolved_at TIMESTAMP
);
