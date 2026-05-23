DO $$
BEGIN
    IF to_regclass('public.labwatch_user') IS NULL THEN
        IF to_regclass('public.users') IS NOT NULL THEN
            ALTER TABLE public.users RENAME TO labwatch_user;
        ELSIF to_regclass('public.user_account') IS NOT NULL THEN
            ALTER TABLE public.user_account RENAME TO labwatch_user;
        ELSIF to_regclass('public.labwatch_users') IS NOT NULL THEN
            ALTER TABLE public.labwatch_users RENAME TO labwatch_user;
        END IF;
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS labwatch_user (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    role VARCHAR(32) NOT NULL DEFAULT 'OPERATOR',
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS machine (
    id BIGSERIAL PRIMARY KEY,
    owner_user_id BIGINT,
    machine_id VARCHAR(255) NOT NULL UNIQUE,
    hostname VARCHAR(255) NOT NULL,
    location VARCHAR(255),
    os_type VARCHAR(255),
    os_version VARCHAR(255),
    last_uptime_seconds BIGINT,
    last_telemetry_source VARCHAR(255),
    status VARCHAR(255) NOT NULL,
    last_seen TIMESTAMP,
    created_at TIMESTAMP,
    CONSTRAINT fk_machine_owner_user
        FOREIGN KEY (owner_user_id) REFERENCES labwatch_user (id)
);

CREATE TABLE IF NOT EXISTS agent (
    id BIGSERIAL PRIMARY KEY,
    agent_id VARCHAR(255) NOT NULL UNIQUE,
    agent_token_hash VARCHAR(128) NOT NULL,
    machine_ref_id BIGINT NOT NULL,
    agent_version VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL,
    registered_at TIMESTAMP NOT NULL,
    last_seen_at TIMESTAMP,
    CONSTRAINT fk_agent_machine
        FOREIGN KEY (machine_ref_id) REFERENCES machine (id)
);

CREATE TABLE IF NOT EXISTS telemetry_snapshot (
    id BIGSERIAL PRIMARY KEY,
    snapshot_id UUID NOT NULL UNIQUE,
    machine_id BIGINT NOT NULL,
    hostname VARCHAR(255) NOT NULL,
    os_type VARCHAR(255) NOT NULL,
    os_version VARCHAR(255) NOT NULL,
    uptime_seconds BIGINT NOT NULL,
    collected_at TIMESTAMP NOT NULL,
    cpu_usage NUMERIC(5, 2) NOT NULL,
    memory_usage NUMERIC(5, 2) NOT NULL,
    disk_usage NUMERIC(5, 2) NOT NULL,
    source VARCHAR(255) NOT NULL,
    process_metrics_json TEXT,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_telemetry_snapshot_machine
        FOREIGN KEY (machine_id) REFERENCES machine (id)
);

CREATE TABLE IF NOT EXISTS health_event (
    id BIGSERIAL PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    machine_id BIGINT NOT NULL,
    metric_value NUMERIC(5, 2),
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    event_type VARCHAR(30) NOT NULL,
    message VARCHAR(225),
    CONSTRAINT fk_health_event_machine
        FOREIGN KEY (machine_id) REFERENCES machine (id)
);

ALTER TABLE public.labwatch_user
    ADD COLUMN IF NOT EXISTS role VARCHAR(32);

UPDATE public.labwatch_user
SET role = 'OPERATOR'
WHERE role IS NULL OR trim(role) = '';

ALTER TABLE public.labwatch_user
    ALTER COLUMN role SET DEFAULT 'OPERATOR';

ALTER TABLE public.labwatch_user
    ALTER COLUMN role SET NOT NULL;
