CREATE UNIQUE INDEX IF NOT EXISTS uq_alert_active_machine_type
    ON alert (machine_id, alert_type)
    WHERE status = 'ACTIVE';
