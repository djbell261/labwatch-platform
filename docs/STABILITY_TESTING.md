# LabWatch Backend Stability Testing

This guide is a practical, local-first stability test plan for the LabWatch backend platform:

- `monitoring-api`
- `alert-engine`
- `ai-engine-service`
- `notification-service`
- Kafka
- PostgreSQL
- Redis
- Docker Compose
- Prometheus/Grafana

It is meant to be useful in two ways:

- As an engineering runbook for validating backend reliability upgrades locally.
- As interview-ready talking material that shows how the platform behaves under normal load, restarts, duplicate events, and dependency failures.

## Goals

These tests focus on the recent reliability upgrades:

- Kafka retries and dead-letter topics
- Redis-backed notification cooldowns
- Redis-backed anomaly promotion cooldowns
- DB-backed alert deduplication protection
- Micrometer domain metrics
- Async AI investigation execution

The emphasis is backend behavior, not UI validation.

## Prerequisites

- Docker Desktop or a compatible local Docker runtime
- `docker compose`
- `curl`
- Optional but helpful:
  - `jq`
  - `watch`

If you want to exercise live OpenAI-backed investigations instead of only validating queueing/failure behavior:

- Set `AI_PROVIDER`, `OPENAI_API_KEY`, and `OPENAI_MODEL` in your shell or `.env`

If no valid AI provider configuration is present, the AI workflow tests are still useful because they validate:

- async execution
- bounded failure handling
- failure metrics
- consumer resilience

## Local Stack Startup

Start the full backend stack:

```bash
docker compose up --build -d
```

Confirm service health:

```bash
docker compose ps
```

Follow backend logs when running tests:

```bash
docker compose logs -f monitoring-api alert-engine ai-engine-service notification-service
```

Stop the stack:

```bash
docker compose down
```

Reset containers and named volumes for a clean test run:

```bash
docker compose down -v
```

## Useful Local Endpoints

- Monitoring API: `http://localhost:8089`
- Alert Engine: `http://localhost:8088`
- AI Engine: `http://localhost:8090`
- Notification Service: `http://localhost:8091`
- Prometheus: `http://localhost:9091`
- Grafana: `http://localhost:3001`

Prometheus-style metrics endpoints:

- `http://localhost:8089/actuator/prometheus`
- `http://localhost:8088/actuator/prometheus`
- `http://localhost:8090/actuator/prometheus`
- `http://localhost:8091/actuator/prometheus`

## Reusable Shell Variables

These keep the commands below shorter:

```bash
export MONITORING_API=http://localhost:8089
export ALERT_ENGINE=http://localhost:8088
export AI_ENGINE=http://localhost:8090
export NOTIFICATION_SERVICE=http://localhost:8091

export PG_CONTAINER=labwatch-postgres
export KAFKA_CONTAINER=labwatch-kafka
export REDIS_CONTAINER=labwatch-redis

export PG_DB=labwatch
export PG_USER=postgres

export TS=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
```

## Reusable Telemetry Payloads

### Healthy baseline event

Use this to build history without creating an alert:

```bash
curl -sS -X POST "$MONITORING_API/api/v1/telemetry/snapshots" \
  -H "Content-Type: application/json" \
  -d "{
    \"machineIdentifier\": \"machine-stability-01\",
    \"hostname\": \"machine-stability-01.local\",
    \"osType\": \"macOS\",
    \"osVersion\": \"14.5\",
    \"uptimeSeconds\": 86400,
    \"timestamp\": \"$TS\",
    \"cpuUsage\": 22.5,
    \"memoryUsage\": 48.0,
    \"diskUsage\": 51.0,
    \"source\": \"stability-test\",
    \"processMetrics\": [
      {
        \"processName\": \"java\",
        \"cpuPercent\": 8.2,
        \"memoryPercent\": 12.4
      },
      {
        \"processName\": \"postgres\",
        \"cpuPercent\": 3.0,
        \"memoryPercent\": 5.5
      }
    ]
  }"
```

### High-CPU alert-triggering event

Use this to drive alerting and AI investigation:

```bash
curl -sS -X POST "$MONITORING_API/api/v1/telemetry/snapshots" \
  -H "Content-Type: application/json" \
  -d "{
    \"machineIdentifier\": \"machine-stability-01\",
    \"hostname\": \"machine-stability-01.local\",
    \"osType\": \"macOS\",
    \"osVersion\": \"14.5\",
    \"uptimeSeconds\": 86400,
    \"timestamp\": \"$TS\",
    \"cpuUsage\": 96.0,
    \"memoryUsage\": 88.0,
    \"diskUsage\": 51.0,
    \"source\": \"stability-test\",
    \"processMetrics\": [
      {
        \"processName\": \"java\",
        \"cpuPercent\": 67.0,
        \"memoryPercent\": 21.0
      },
      {
        \"processName\": \"postgres\",
        \"cpuPercent\": 12.0,
        \"memoryPercent\": 9.0
      }
    ]
  }"
```

### Recovery event

Use this after an alert is active to test resolution:

```bash
curl -sS -X POST "$MONITORING_API/api/v1/telemetry/snapshots" \
  -H "Content-Type: application/json" \
  -d "{
    \"machineIdentifier\": \"machine-stability-01\",
    \"hostname\": \"machine-stability-01.local\",
    \"osType\": \"macOS\",
    \"osVersion\": \"14.5\",
    \"uptimeSeconds\": 86400,
    \"timestamp\": \"$TS\",
    \"cpuUsage\": 18.0,
    \"memoryUsage\": 40.0,
    \"diskUsage\": 50.0,
    \"source\": \"stability-test\",
    \"processMetrics\": [
      {
        \"processName\": \"java\",
        \"cpuPercent\": 5.0,
        \"memoryPercent\": 10.0
      }
    ]
  }"
```

## Kafka Inspection Commands

List topics:

```bash
docker exec -it "$KAFKA_CONTAINER" \
  kafka-topics --bootstrap-server localhost:9092 --list
```

Expected application topics:

- `health-events`
- `alert-events`
- `anomaly-events`
- `ai-investigation-events`
- `health-events.alert-engine.dlt`
- `health-events.ai-engine.dlt`
- `alert-events.ai-engine.dlt`
- `alert-events.notification-service.dlt`
- `ai-investigation-events.notification-service.dlt`

Read recent messages from a topic:

```bash
docker exec -it "$KAFKA_CONTAINER" \
  kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic health-events \
  --from-beginning \
  --max-messages 5
```

Read recent messages from a DLT:

```bash
docker exec -it "$KAFKA_CONTAINER" \
  kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic health-events.alert-engine.dlt \
  --from-beginning \
  --max-messages 5
```

Publish a raw message to a topic:

```bash
docker exec -i "$KAFKA_CONTAINER" \
  kafka-console-producer \
  --bootstrap-server localhost:9092 \
  --topic health-events
```

## PostgreSQL Verification Commands

Open a SQL shell:

```bash
docker exec -it "$PG_CONTAINER" psql -U "$PG_USER" -d "$PG_DB"
```

Check active alerts:

```bash
docker exec -it "$PG_CONTAINER" psql -U "$PG_USER" -d "$PG_DB" -c "
SELECT id, machine_id, machine_identifier, alert_type, severity, status, created_at, resolved_at
FROM alert
ORDER BY created_at DESC;
"
```

Check for duplicate ACTIVE alerts by machine and alert type:

```bash
docker exec -it "$PG_CONTAINER" psql -U "$PG_USER" -d "$PG_DB" -c "
SELECT machine_id, machine_identifier, alert_type, status, COUNT(*) AS active_count
FROM alert
WHERE status = 'ACTIVE'
GROUP BY machine_id, machine_identifier, alert_type, status
HAVING COUNT(*) > 1;
"
```

Check anomalies:

```bash
docker exec -it "$PG_CONTAINER" psql -U "$PG_USER" -d "$PG_DB" -c "
SELECT id, anomaly_id, machine_identifier, event_type, severity, detected_at
FROM anomaly
ORDER BY detected_at DESC
LIMIT 20;
"
```

Check AI investigations:

```bash
docker exec -it "$PG_CONTAINER" psql -U "$PG_USER" -d "$PG_DB" -c "
SELECT id, investigation_id, alert_id, machine_identifier, alert_type, severity, persisted_at
FROM ai_investigations
ORDER BY persisted_at DESC
LIMIT 20;
"
```

## Redis Verification Commands

List LabWatch cooldown keys:

```bash
docker exec -it "$REDIS_CONTAINER" redis-cli KEYS 'labwatch:*'
```

Inspect notification cooldown keys:

```bash
docker exec -it "$REDIS_CONTAINER" redis-cli KEYS 'labwatch:notifications:cooldown:*'
```

Inspect anomaly promotion cooldown keys:

```bash
docker exec -it "$REDIS_CONTAINER" redis-cli KEYS 'labwatch:anomaly-promotion:cooldown:*'
```

Check a specific key TTL:

```bash
docker exec -it "$REDIS_CONTAINER" redis-cli TTL 'labwatch:notifications:cooldown:machine-stability-01:CPU_USAGE:CRITICAL'
```

## 1. Normal Event Flow

### Objective

Validate the happy path:

- telemetry snapshot ingestion
- health-event publication
- alert creation
- notification dispatch behavior
- AI investigation creation
- metrics exposure

### Steps

1. Start the stack.

```bash
docker compose up --build -d
```

2. Send 3 to 5 healthy baseline snapshots.

```bash
for i in 1 2 3 4 5; do
  export TS=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
  curl -sS -X POST "$MONITORING_API/api/v1/telemetry/snapshots" \
    -H "Content-Type: application/json" \
    -d "{
      \"machineIdentifier\": \"machine-stability-01\",
      \"hostname\": \"machine-stability-01.local\",
      \"osType\": \"macOS\",
      \"osVersion\": \"14.5\",
      \"uptimeSeconds\": 86400,
      \"timestamp\": \"$TS\",
      \"cpuUsage\": 25.0,
      \"memoryUsage\": 44.0,
      \"diskUsage\": 51.0,
      \"source\": \"stability-test\",
      \"processMetrics\": [{\"processName\": \"java\", \"cpuPercent\": 7.0, \"memoryPercent\": 11.0}]
    }"
done
```

3. Send one alert-triggering telemetry event.

```bash
export TS=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
curl -sS -X POST "$MONITORING_API/api/v1/telemetry/snapshots" \
  -H "Content-Type: application/json" \
  -d "{
    \"machineIdentifier\": \"machine-stability-01\",
    \"hostname\": \"machine-stability-01.local\",
    \"osType\": \"macOS\",
    \"osVersion\": \"14.5\",
    \"uptimeSeconds\": 86400,
    \"timestamp\": \"$TS\",
    \"cpuUsage\": 97.0,
    \"memoryUsage\": 90.0,
    \"diskUsage\": 51.0,
    \"source\": \"stability-test\",
    \"processMetrics\": [{\"processName\": \"java\", \"cpuPercent\": 71.0, \"memoryPercent\": 18.0}]
  }"
```

4. Inspect produced health events.

```bash
docker exec -it "$KAFKA_CONTAINER" \
  kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic health-events \
  --from-beginning \
  --max-messages 10
```

5. Inspect alert events.

```bash
docker exec -it "$KAFKA_CONTAINER" \
  kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic alert-events \
  --from-beginning \
  --max-messages 10
```

6. Check database state.

```bash
docker exec -it "$PG_CONTAINER" psql -U "$PG_USER" -d "$PG_DB" -c "
SELECT id, machine_identifier, alert_type, severity, status, created_at, resolved_at
FROM alert
ORDER BY created_at DESC
LIMIT 10;
"
```

7. Check AI investigations.

```bash
curl -sS "$AI_ENGINE/api/investigations" | jq .
```

8. Check metrics exposure.

```bash
curl -sS "$MONITORING_API/actuator/prometheus" | rg 'labwatch.telemetry.events.received'
curl -sS "$ALERT_ENGINE/actuator/prometheus" | rg 'labwatch.health.events.consumed|labwatch.alerts.created|labwatch.alerts.resolved'
curl -sS "$AI_ENGINE/actuator/prometheus" | rg 'labwatch.anomalies.detected|labwatch.ai.investigations'
curl -sS "$NOTIFICATION_SERVICE/actuator/prometheus" | rg 'labwatch.notifications.sent|labwatch.notifications.failed'
```

### Expected Results

- `monitoring-api` accepts all telemetry snapshots.
- `health-events` contains records for the ingested telemetry.
- `alert-engine` creates one `ACTIVE` alert for the threshold breach.
- `notification-service` processes the alert event and either sends or intentionally suppresses duplicates based on cooldown state.
- `ai-engine-service` creates an investigation record for qualifying alerts.
- Metrics appear on the expected `/actuator/prometheus` endpoints.

## 2. Service Restart Behavior

### Objective

Validate that restarts do not break alert state, cooldown state, or deduplication guarantees.

### 2A. Restart `alert-engine` during active telemetry

1. Start a loop that sends repeated high-CPU snapshots.

```bash
while true; do
  export TS=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
  curl -sS -X POST "$MONITORING_API/api/v1/telemetry/snapshots" \
    -H "Content-Type: application/json" \
    -d "{
      \"machineIdentifier\": \"machine-stability-02\",
      \"hostname\": \"machine-stability-02.local\",
      \"osType\": \"Linux\",
      \"osVersion\": \"6.8\",
      \"uptimeSeconds\": 14400,
      \"timestamp\": \"$TS\",
      \"cpuUsage\": 95.0,
      \"memoryUsage\": 66.0,
      \"diskUsage\": 45.0,
      \"source\": \"stability-test\",
      \"processMetrics\": [{\"processName\": \"java\", \"cpuPercent\": 52.0, \"memoryPercent\": 17.0}]
    }" >/dev/null
  sleep 1
done
```

2. Restart `alert-engine`.

```bash
docker compose restart alert-engine
```

3. Stop the telemetry loop after 15 to 30 seconds and verify alerts.

```bash
docker exec -it "$PG_CONTAINER" psql -U "$PG_USER" -d "$PG_DB" -c "
SELECT machine_identifier, alert_type, status, COUNT(*) AS count
FROM alert
WHERE machine_identifier = 'machine-stability-02'
GROUP BY machine_identifier, alert_type, status
ORDER BY machine_identifier, alert_type, status;
"
```

Expected results:

- `alert-engine` reconnects and resumes consuming.
- No duplicate `ACTIVE` alerts are created for the same machine and alert type.
- At most one `ACTIVE` row exists for a given dedupe key.

### 2B. Restart `ai-engine-service` during alert processing

1. Trigger several HIGH or CRITICAL alerts quickly.

```bash
for i in 1 2 3 4 5; do
  export TS=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
  curl -sS -X POST "$MONITORING_API/api/v1/telemetry/snapshots" \
    -H "Content-Type: application/json" \
    -d "{
      \"machineIdentifier\": \"machine-ai-restart-$i\",
      \"hostname\": \"machine-ai-restart-$i.local\",
      \"osType\": \"Linux\",
      \"osVersion\": \"6.8\",
      \"uptimeSeconds\": 9000,
      \"timestamp\": \"$TS\",
      \"cpuUsage\": 98.0,
      \"memoryUsage\": 91.0,
      \"diskUsage\": 49.0,
      \"source\": \"stability-test\",
      \"processMetrics\": [{\"processName\": \"java\", \"cpuPercent\": 80.0, \"memoryPercent\": 24.0}]
    }"
done
```

2. Immediately restart the AI engine.

```bash
docker compose restart ai-engine-service
```

3. Watch the logs and investigation records.

```bash
docker compose logs -f ai-engine-service
curl -sS "$AI_ENGINE/api/investigations" | jq .
```

Expected results:

- Kafka consumer threads recover after restart.
- AI investigations resume for new alert events.
- Failures should be logged, but consumer threads should not die permanently.

### 2C. Restart `notification-service` during a cooldown period

1. Trigger an alert for a machine.
2. Confirm the notification cooldown key exists in Redis.

```bash
docker exec -it "$REDIS_CONTAINER" redis-cli KEYS 'labwatch:notifications:cooldown:*'
```

3. Restart `notification-service`.

```bash
docker compose restart notification-service
```

4. Re-send the same high alert for the same machine inside the cooldown window.

Expected results:

- Cooldown keys persist across restart.
- Duplicate notifications are still suppressed.
- Restart does not reset cooldown behavior.

## 3. Duplicate Event Handling

### Objective

Prove that repeated events do not create duplicate active alerts and do not spam notifications.

### Steps

1. Send the same alert-triggering telemetry several times in a row.

```bash
for i in 1 2 3 4 5 6; do
  export TS=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
  curl -sS -X POST "$MONITORING_API/api/v1/telemetry/snapshots" \
    -H "Content-Type: application/json" \
    -d "{
      \"machineIdentifier\": \"machine-dedupe-01\",
      \"hostname\": \"machine-dedupe-01.local\",
      \"osType\": \"Linux\",
      \"osVersion\": \"6.8\",
      \"uptimeSeconds\": 3600,
      \"timestamp\": \"$TS\",
      \"cpuUsage\": 99.0,
      \"memoryUsage\": 55.0,
      \"diskUsage\": 40.0,
      \"source\": \"stability-test\",
      \"processMetrics\": [{\"processName\": \"java\", \"cpuPercent\": 73.0, \"memoryPercent\": 15.0}]
    }"
done
```

2. Verify dedupe at the database layer.

```bash
docker exec -it "$PG_CONTAINER" psql -U "$PG_USER" -d "$PG_DB" -c "
SELECT machine_id, machine_identifier, alert_type, status, COUNT(*) AS count
FROM alert
WHERE machine_identifier = 'machine-dedupe-01'
GROUP BY machine_id, machine_identifier, alert_type, status
ORDER BY alert_type, status;
"
```

3. Verify Redis-backed notification cooldowns.

```bash
docker exec -it "$REDIS_CONTAINER" redis-cli KEYS 'labwatch:notifications:cooldown:*machine-dedupe-01*'
```

4. If anomaly promotion is enabled, send repeated anomalous patterns and inspect promotion cooldown keys.

```bash
docker exec -it "$REDIS_CONTAINER" redis-cli KEYS 'labwatch:anomaly-promotion:cooldown:*'
```

### Expected Results

- Only one `ACTIVE` alert exists for the same machine and alert type.
- Subsequent duplicate events do not create additional active rows.
- Notification cooldowns suppress duplicate downstream sends during the TTL window.
- Anomaly promotion cooldowns prevent repeated alert promotion for the same anomaly pattern during their configured TTL window.

## 4. Kafka Failure and Recovery

### Objective

Validate broker outage behavior and recovery.

### Steps

1. Stop Kafka.

```bash
docker compose stop kafka
```

2. Attempt telemetry ingestion while Kafka is unavailable.

```bash
export TS=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
curl -i -X POST "$MONITORING_API/api/v1/telemetry/snapshots" \
  -H "Content-Type: application/json" \
  -d "{
    \"machineIdentifier\": \"machine-kafka-outage-01\",
    \"hostname\": \"machine-kafka-outage-01.local\",
    \"osType\": \"Linux\",
    \"osVersion\": \"6.8\",
    \"uptimeSeconds\": 1000,
    \"timestamp\": \"$TS\",
    \"cpuUsage\": 92.0,
    \"memoryUsage\": 63.0,
    \"diskUsage\": 40.0,
    \"source\": \"stability-test\",
    \"processMetrics\": [{\"processName\": \"java\", \"cpuPercent\": 50.0, \"memoryPercent\": 12.0}]
  }"
```

3. Inspect service logs.

```bash
docker compose logs --tail=200 monitoring-api alert-engine ai-engine-service notification-service
```

4. Restart Kafka.

```bash
docker compose start kafka
```

5. Wait for health, then send a fresh telemetry event.

```bash
docker compose ps
export TS=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
curl -sS -X POST "$MONITORING_API/api/v1/telemetry/snapshots" \
  -H "Content-Type: application/json" \
  -d "{
    \"machineIdentifier\": \"machine-kafka-outage-01\",
    \"hostname\": \"machine-kafka-outage-01.local\",
    \"osType\": \"Linux\",
    \"osVersion\": \"6.8\",
    \"uptimeSeconds\": 1000,
    \"timestamp\": \"$TS\",
    \"cpuUsage\": 95.0,
    \"memoryUsage\": 64.0,
    \"diskUsage\": 40.0,
    \"source\": \"stability-test\",
    \"processMetrics\": [{\"processName\": \"java\", \"cpuPercent\": 52.0, \"memoryPercent\": 12.0}]
  }"
```

### Expected Results

- During the outage, telemetry publication should fail clearly rather than silently succeeding end-to-end.
- Producer or consumer connectivity failures should be visible in logs.
- After Kafka returns, services should reconnect without manual redeploy.
- New valid events should flow normally after recovery.

### What To Watch

- connection or broker-unavailable errors in `monitoring-api`
- consumer reconnect behavior in `alert-engine`, `ai-engine-service`, and `notification-service`
- backlog recovery after broker restart

## 5. Redis Failure and Recovery

### Objective

Validate cooldown-store outage behavior.

### Steps

1. Stop Redis.

```bash
docker compose stop redis
```

2. Trigger an alert that would normally create a notification cooldown.

```bash
export TS=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
curl -sS -X POST "$MONITORING_API/api/v1/telemetry/snapshots" \
  -H "Content-Type: application/json" \
  -d "{
    \"machineIdentifier\": \"machine-redis-outage-01\",
    \"hostname\": \"machine-redis-outage-01.local\",
    \"osType\": \"Linux\",
    \"osVersion\": \"6.8\",
    \"uptimeSeconds\": 2200,
    \"timestamp\": \"$TS\",
    \"cpuUsage\": 96.0,
    \"memoryUsage\": 72.0,
    \"diskUsage\": 42.0,
    \"source\": \"stability-test\",
    \"processMetrics\": [{\"processName\": \"java\", \"cpuPercent\": 60.0, \"memoryPercent\": 14.0}]
  }"
```

3. Inspect logs.

```bash
docker compose logs --tail=200 notification-service ai-engine-service
```

4. Restart Redis.

```bash
docker compose start redis
```

5. Re-run the trigger event.

### Expected Results

- Services that depend on Redis-backed cooldown acquisition should fail safely, not silently bypass cooldown protection.
- The likely operational shape is retry and eventual DLT if cooldown state cannot be accessed.
- After Redis returns, services should recover cleanly and resume normal cooldown behavior.

### Documented Expected Behavior

- `notification-service`: duplicate suppression depends on Redis. If Redis is unavailable, processing should error rather than pretending the cooldown was acquired.
- `ai-engine-service`: anomaly promotion cooldown checks depend on Redis. If Redis is unavailable, promotion should fail safely and be retried or dead-lettered according to Kafka error handling.

## 6. Poison Message and DLT Behavior

### Objective

Validate bounded retries and dead-letter routing for malformed messages.

### 6A. Malformed `health-events` message

1. Publish invalid JSON to `health-events`.

```bash
printf 'not-json\n' | docker exec -i "$KAFKA_CONTAINER" \
  kafka-console-producer \
  --bootstrap-server localhost:9092 \
  --topic health-events
```

2. Watch service logs.

```bash
docker compose logs -f alert-engine ai-engine-service
```

3. Check DLTs.

```bash
docker exec -it "$KAFKA_CONTAINER" \
  kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic health-events.alert-engine.dlt \
  --from-beginning \
  --max-messages 5

docker exec -it "$KAFKA_CONTAINER" \
  kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic health-events.ai-engine.dlt \
  --from-beginning \
  --max-messages 5
```

### 6B. Malformed `alert-events` message

1. Publish invalid JSON to `alert-events`.

```bash
printf 'not-json\n' | docker exec -i "$KAFKA_CONTAINER" \
  kafka-console-producer \
  --bootstrap-server localhost:9092 \
  --topic alert-events
```

2. Check DLTs.

```bash
docker exec -it "$KAFKA_CONTAINER" \
  kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic alert-events.ai-engine.dlt \
  --from-beginning \
  --max-messages 5

docker exec -it "$KAFKA_CONTAINER" \
  kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic alert-events.notification-service.dlt \
  --from-beginning \
  --max-messages 5
```

3. After the poison message, send a valid telemetry alert trigger and confirm later processing still works.

### Expected Results

- The malformed message is retried only a bounded number of times.
- Structured retry and DLT logging appears in the affected services.
- The bad record lands in the correct dead-letter topic.
- Consumers continue processing later valid messages and do not remain blocked forever by the poison message.

## 7. Async AI Investigation Behavior

### Objective

Validate that long-running AI work does not block Kafka consumers.

### Steps

1. Trigger multiple HIGH or CRITICAL alerts quickly.

```bash
for i in $(seq 1 12); do
  export TS=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
  curl -sS -X POST "$MONITORING_API/api/v1/telemetry/snapshots" \
    -H "Content-Type: application/json" \
    -d "{
      \"machineIdentifier\": \"machine-ai-burst-$i\",
      \"hostname\": \"machine-ai-burst-$i.local\",
      \"osType\": \"Linux\",
      \"osVersion\": \"6.8\",
      \"uptimeSeconds\": 5000,
      \"timestamp\": \"$TS\",
      \"cpuUsage\": 97.0,
      \"memoryUsage\": 89.0,
      \"diskUsage\": 44.0,
      \"source\": \"stability-test\",
      \"processMetrics\": [{\"processName\": \"java\", \"cpuPercent\": 76.0, \"memoryPercent\": 19.0}]
    }"
done
```

2. Watch the AI engine logs for queueing, completion, and failure behavior.

```bash
docker compose logs -f ai-engine-service
```

3. Inspect AI investigation records.

```bash
docker exec -it "$PG_CONTAINER" psql -U "$PG_USER" -d "$PG_DB" -c "
SELECT machine_identifier, alert_type, severity, persisted_at
FROM ai_investigations
ORDER BY persisted_at DESC
LIMIT 20;
"
```

4. Inspect AI metrics.

```bash
curl -sS "$AI_ENGINE/actuator/prometheus" | rg 'labwatch.ai.investigations.started|labwatch.ai.investigations.completed|labwatch.ai.investigations.failed|labwatch.ai.investigation.latency'
```

### Expected Results

- Kafka consumption should continue while investigations execute asynchronously.
- AI failures should be logged and counted without crashing consumer threads.
- Investigation start, completion, failure, and latency metrics should update.
- If the executor queue is stressed, behavior should remain bounded rather than spawning unbounded work.

### Optional Stress Variation

Lower concurrency and queue capacity to make saturation easier to observe:

```bash
export LABWATCH_AI_INVESTIGATION_CONCURRENCY=1
export LABWATCH_AI_INVESTIGATION_QUEUE_CAPACITY=2
docker compose up --build -d ai-engine-service
```

Then rerun the burst test and observe how the service behaves at its concurrency boundary.

## 8. Metrics Validation

### Objective

Verify that the core domain metrics exist and move during tests.

### Metrics To Validate

- `labwatch.telemetry.events.received`
- `labwatch.health.events.consumed`
- `labwatch.alerts.created`
- `labwatch.alerts.resolved`
- `labwatch.anomalies.detected`
- `labwatch.notifications.sent`
- `labwatch.notifications.failed`
- `labwatch.ai.investigations.started`
- `labwatch.ai.investigations.completed`
- `labwatch.ai.investigations.failed`
- `labwatch.ai.investigation.latency`

### Commands

```bash
curl -sS "$MONITORING_API/actuator/prometheus" | rg 'labwatch.telemetry.events.received'
curl -sS "$ALERT_ENGINE/actuator/prometheus" | rg 'labwatch.health.events.consumed|labwatch.alerts.created|labwatch.alerts.resolved'
curl -sS "$AI_ENGINE/actuator/prometheus" | rg 'labwatch.anomalies.detected|labwatch.ai.investigations.started|labwatch.ai.investigations.completed|labwatch.ai.investigations.failed|labwatch.ai.investigation.latency'
curl -sS "$NOTIFICATION_SERVICE/actuator/prometheus" | rg 'labwatch.notifications.sent|labwatch.notifications.failed'
```

### Expected Results

- Each metric appears on the service that owns it.
- Counter values increase after the relevant tests are run.
- The latency metric appears in timer form after AI investigation execution occurs.

## Alert Resolution Validation

This is worth running once because it validates lifecycle completion, not just alert creation.

### Steps

1. Trigger a high-CPU alert on `machine-stability-01`.
2. Confirm an `ACTIVE` alert exists.
3. Send the recovery event from the reusable payload section.
4. Recheck the alert table.

```bash
docker exec -it "$PG_CONTAINER" psql -U "$PG_USER" -d "$PG_DB" -c "
SELECT machine_identifier, alert_type, severity, status, created_at, resolved_at
FROM alert
WHERE machine_identifier = 'machine-stability-01'
ORDER BY created_at DESC;
"
```

### Expected Results

- The prior active alert transitions to `RESOLVED`.
- `resolved_at` is populated.
- `labwatch.alerts.resolved` increments on the alert-engine metrics endpoint.

## Troubleshooting

### No alert is created

Check:

- `monitoring-api` successfully accepted the telemetry payload
- `health-events` contains the event
- `alert-engine` logs show health-event consumption
- the test payload is actually above the configured threshold

Useful commands:

```bash
docker compose logs --tail=200 monitoring-api alert-engine
docker exec -it "$KAFKA_CONTAINER" kafka-console-consumer --bootstrap-server localhost:9092 --topic health-events --from-beginning --max-messages 20
```

### No AI investigation appears

Check:

- the alert severity is high enough to trigger investigation
- `ai-engine-service` is healthy
- AI provider configuration is valid if you expect successful model calls
- failure metrics and logs for the async executor path

Useful commands:

```bash
docker compose logs --tail=200 ai-engine-service
curl -sS "$AI_ENGINE/actuator/prometheus" | rg 'labwatch.ai.investigations'
```

### Notifications seem missing

Check:

- cooldown suppression may be working as designed
- `notification-service` logs may show success, suppression, retry, or DLT behavior
- Redis cooldown keys may still be active

Useful commands:

```bash
docker compose logs --tail=200 notification-service
docker exec -it "$REDIS_CONTAINER" redis-cli KEYS 'labwatch:notifications:cooldown:*'
```

### DLT appears empty after a poison message test

Check:

- you are inspecting the correct DLT topic for the service
- retries may still be in progress
- the malformed record actually hit the expected topic

Useful commands:

```bash
docker compose logs --tail=200 alert-engine ai-engine-service notification-service
docker exec -it "$KAFKA_CONTAINER" kafka-topics --bootstrap-server localhost:9092 --list
```

### Metrics are missing

Check:

- the service is up and healthy
- the relevant workflow has actually been exercised
- the endpoint path is `/actuator/prometheus`

Useful commands:

```bash
curl -i "$MONITORING_API/actuator/prometheus"
curl -i "$ALERT_ENGINE/actuator/prometheus"
curl -i "$AI_ENGINE/actuator/prometheus"
curl -i "$NOTIFICATION_SERVICE/actuator/prometheus"
```

## Recommended Test Order

For a clean local validation pass, this order works well:

1. Normal event flow
2. Metrics validation
3. Duplicate event handling
4. Alert resolution validation
5. Service restart behavior
6. Poison message and DLT behavior
7. Redis failure and recovery
8. Kafka failure and recovery
9. Async AI burst behavior

## Interview Talking Points

If you want to use this in demos or interviews, the strongest backend talking points are:

- The platform validates both happy-path flow and operational failure modes.
- Deduplication is verified at the database layer, not only at the application layer.
- Cooldown logic is tested across restarts because it is Redis-backed and shared-state aware.
- Kafka reliability is tested with malformed records, bounded retries, and dead-letter routing.
- AI workflows are treated as async operational work, not something allowed to block stream consumers.
- Metrics are validated as part of workflow testing so observability is part of the platform contract, not an afterthought.
