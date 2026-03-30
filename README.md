# LabWatch Platform

**LabWatch Platform** is a distributed, event-driven monitoring system designed to simulate real-world infrastructure alerting workflows.

It ingests machine telemetry, processes events asynchronously using Kafka, and manages alert lifecycle state using a microservice architecture.

---

## 🚀 Key Highlights

- Event-driven architecture using Kafka
- Microservices built with Spring Boot
- Real-time alert processing pipeline
- Alert deduplication and lifecycle management
- Dockerized system for consistent deployment
- PostgreSQL-backed persistence layer

---

## 🧠 System Architecture
Client / Agent
↓
monitoring-api (REST ingestion)
↓
Kafka (health-events topic)
↓
alert-engine (async processing)
↓
PostgreSQL (alerts + events)


---

## ⚙️ Services

### monitoring-api
- Receives telemetry via REST (`POST /api/events`)
- Validates and persists machine health events
- Publishes events to Kafka topic (`health-events`)

### alert-engine
- Consumes Kafka events asynchronously
- Applies threshold-based alert logic
- Prevents duplicate ACTIVE alerts
- Transitions alerts from ACTIVE → RESOLVED
- Persists alerts in PostgreSQL

---

## 🔥 Core Features

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

---

## 🐳 Running the System (Docker)

### Prerequisites
- Docker Desktop

### Run everything

docker compose up --build

## Services

| Service        | URL                                            |
| -------------- | ---------------------------------------------- |
| monitoring-api | [http://localhost:8089](http://localhost:8089) |
| alert-engine   | [http://localhost:8088](http://localhost:8088) |

## API Usage
Create Health Event

### POST /api/events

{
  "machineIdentifier": "lab-pc-01",
  "hostname": "lab-pc-01",
  "location": "Room 101",
  "eventType": "CPU",
  "metricValue": 92.4,
  "status": "WARNING",
  "message": "CPU usage exceeded threshold"
}

### GET /api/alerts

## Example Flow

- Machine sends event → monitoring-api
- Event stored + published to Kafka
- alert-engine consumes event
- Alert created if threshold exceeded
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
- React dashboard for real-time alerts
- Python-based monitoring agent
- Alert severity levels (INFO / WARNING / CRITICAL)
- Observability (metrics + logging)
- Cloud deployment (AWS)



## This project demonstrates:
- Distributed system design
- Event-driven architecture with Kafka
- Microservice communication patterns
- Backend system scalability concepts
- Real-world alert lifecycle handling
- DevOps fundamentals with Docker

## Author
Derwin Bell
