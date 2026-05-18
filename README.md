# LabWatch Platform

**LabWatch Platform** is a distributed, event-driven monitoring system designed to simulate real-world infrastructure alerting workflows.

It ingests machine telemetry, processes events asynchronously using Kafka, and manages alert lifecycle state using a microservice architecture.

---

## Key Highlights

- Event-driven architecture using Kafka
- Microservices built with Spring Boot
- Real-time alert processing pipeline
- Alert deduplication and lifecycle management
- Dockerized system for consistent deployment
- PostgreSQL-backed persistence layer

---

## System Architecture
Client / Agent
↓
monitoring-api (REST ingestion)
↓
Kafka (health-events topic)
↓
alert-engine (async processing)
↓
ai-engine-service (anomaly detection)
↓
PostgreSQL (alerts + events)


---

## Services

### monitoring-api
- Registers machines/agents before telemetry ingestion
- Receives telemetry via REST (`POST /api/v1/telemetry/snapshots`)
- Optionally validates `X-Agent-Token` on ingestion
- Publishes events to Kafka topic (`health-events`)

### alert-engine
- Consumes Kafka events asynchronously
- Applies threshold-based alert logic
- Prevents duplicate ACTIVE alerts
- Transitions alerts from ACTIVE → RESOLVED
- Persists alerts in PostgreSQL

### ai-engine-service
- Consumes Kafka `health-events`
- Maintains rolling baselines per machine + metric type
- Detects anomalies with rolling average, standard deviation, and z-score
- Publishes anomaly messages to Kafka topic `anomaly-events`
- Persists detected anomalies in PostgreSQL
- Exposes REST API at `GET /api/anomalies`

### Multi-Device Foundation
- Agents can register through `POST /api/v1/agents/register`
- `monitoring-api` now tracks machines and agent records separately
- Agent auth can be enabled with `LABWATCH_AGENT_AUTH_ENABLED=true`
- Dashboard can switch between multiple reported machines while keeping the single-machine view intact

### Account + Ownership Foundation
- Users can register and login through `POST /api/v1/auth/register` and `POST /api/v1/auth/login`
- JWT auth is optional and disabled by default for local development
- Machines can remain unowned for backward compatibility, then be claimed later by a user
- Claimed machines are filtered to their owner when auth is enabled

---

## Core Features

### Event-Driven Processing
Decoupled services using Kafka to enable scalability and fault tolerance.

### Alert Deduplication
Prevents alert spam by ensuring only one ACTIVE alert exists per machine + alert type.

### Alert Lifecycle Management
Alerts automatically transition:
ACTIVE → RESOLVED


Each alert includes:
- `createdAt`
- `resolvedAt`

### Threshold-Based Detection
Supports CPU, Memory, and Disk thresholds.

### Statistical Anomaly Detection
Uses a rolling window with configurable minimum samples and z-score threshold to flag outlier telemetry values.

---

## Running the System (Docker)

### Prerequisites
- Docker Desktop

### Run everything

```bash
docker compose up --build -d
```

### Seed demo telemetry

```bash
./scripts/seed-demo-telemetry.sh
```

## Services

| Service        | URL                                            |
| -------------- | ---------------------------------------------- |
| monitoring-api | [http://localhost:8089](http://localhost:8089) |
| alert-engine   | [http://localhost:8088](http://localhost:8088) |
| ai-engine-service | [http://localhost:8090](http://localhost:8090) |

## API Usage
### Register Agent

`POST /api/v1/agents/register`

```json
{
  "machineIdentifier": "lab-pc-01",
  "hostname": "lab-pc-01.local",
  "osType": "Darwin",
  "osVersion": "23.5.0",
  "agentVersion": "1.0.0"
}
```

### Send Telemetry Snapshot

`POST /api/v1/telemetry/snapshots`

Include `X-Agent-Token` when agent auth is enabled.

### List Machines

`GET /api/v1/machines`

### Register Account

`POST /api/v1/auth/register`

```json
{
  "email": "user@example.com",
  "password": "password123",
  "displayName": "Derwin"
}
```

### Login

`POST /api/v1/auth/login`

### Claim Machine

`POST /api/v1/machines/{machineIdentifier}/claim`

Requires `Authorization: Bearer <jwt>`.

### Get Alerts

`GET /api/alerts`

## Example Flow

- Machine sends event → monitoring-api
- Event stored + published to Kafka
- alert-engine consumes event
- ai-engine-service evaluates the same event stream for anomalies
- Alert created if threshold exceeded
- Anomaly published to `anomaly-events` when z-score exceeds the configured threshold
- Alert resolved when metric normalizes

## Tech Stack

- Java
- Spring Boot
- Spring Data JPA (Hibernate)
- PostgreSQL
- Apache Kafka
- Docker + Docker Compose
- Maven

## Roadmap
- Alert severity levels (INFO / WARNING / CRITICAL)
- Multi-user account ownership for machines
- Observability (metrics + logging)
- Cloud deployment (AWS)

## Deployment Readiness

- Deployment and demo environment notes: [docs/DEPLOYMENT_READINESS.md](/Users/derwinbell/dev/ResumeProjects/labwatch-platform/docs/DEPLOYMENT_READINESS.md)
- Backend stability runbook: [docs/STABILITY_TESTING.md](/Users/derwinbell/dev/ResumeProjects/labwatch-platform/docs/STABILITY_TESTING.md)

### Local demo mode

- `LABWATCH_AUTH_ENABLED=false`
- `AI_PROVIDER=mock`
- Landing page still shows auth/product entry points
- Dashboard remains directly accessible for recruiter demos

### Auth-enabled MVP mode

- `LABWATCH_AUTH_ENABLED=true`
- JWT auth is active
- first registered user becomes `ADMIN`
- later users default to `OPERATOR`

## Auth Modes

### Local dev default
- `LABWATCH_AUTH_ENABLED=false`
- `LABWATCH_AGENT_AUTH_ENABLED=false`
- Dashboard works without login
- Existing unowned machines remain visible

### Enable lightweight auth
- Set `LABWATCH_AUTH_ENABLED=true`
- Set `JWT_SECRET` to a non-default value
- Optionally set `JWT_EXPIRATION_MINUTES`
- Register/login in the dashboard, then claim unowned machines from the machine sidebar

## Migration Notes

- No manual SQL migration is required in the current dev setup because JPA is still using schema update/create-drop behavior.
- Existing `machine` rows remain valid because ownership is nullable.
- Existing machines will show as unowned until a logged-in user claims them.



## This project demonstrates:
- Distributed system design
- Event-driven architecture with Kafka
- Microservice communication patterns
- Backend system scalability concepts
- Real-world alert lifecycle handling
- DevOps fundamentals with Docker

## Author
Derwin Bell
