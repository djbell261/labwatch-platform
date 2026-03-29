# LabWatch Platform

LabWatch Platform is a microservice-based monitoring and alerting system that simulates real-world infrastructure monitoring workflows using event-driven architecture.

The system ingests machine telemetry, publishes events through Kafka, processes alert logic asynchronously, and manages alert lifecycle state in PostgreSQL.

---

## 🚀 Features

* REST API for ingesting machine health metrics
* Kafka-based event streaming between services
* Asynchronous alert processing engine
* Threshold-based alert detection (CPU, Memory, Disk)
* Alert deduplication (prevents duplicate active alerts)
* Alert lifecycle management (ACTIVE → RESOLVED)
* PostgreSQL persistence for machines, events, and alerts

---

## 🧠 Architecture

```
Client / Agent
        ↓
 monitoring-api
        ↓
 Kafka (health-events topic)
        ↓
 alert-engine
        ↓
 PostgreSQL
```

---

## ⚙️ Services

### 🔹 monitoring-api

Responsible for:

* Receiving telemetry via REST endpoints
* Validating incoming data
* Persisting health events
* Publishing events to Kafka

### 🔹 alert-engine

Responsible for:

* Consuming Kafka events
* Applying alert threshold logic
* Preventing duplicate alerts
* Resolving alerts when metrics normalize
* Persisting alerts to PostgreSQL

---

## 🛠️ Tech Stack

* Java
* Spring Boot
* Spring Data JPA (Hibernate)
* PostgreSQL
* Apache Kafka
* Maven
* REST APIs
* Postman

---

## 🔄 Example Workflow

1. A machine sends a CPU usage event to `monitoring-api`
2. The event is stored and published to Kafka
3. `alert-engine` consumes the event
4. If threshold is exceeded → ACTIVE alert is created
5. If metric returns to normal → alert is RESOLVED

---

## 📡 API Example

### Create Health Event

`POST /api/events`

```json
{
  "machineIdentifier": "lab-pc-01",
  "hostname": "lab-pc-01",
  "location": "Room 101",
  "eventType": "CPU",
  "metricValue": 92.4,
  "status": "WARNING",
  "message": "CPU usage exceeded threshold"
}
```

---

### Get Alerts

`GET /api/alerts`

---

## 📌 Project Status

### ✅ Completed

* monitoring-api service
* alert-engine service
* Kafka integration
* Alert creation + persistence
* Deduplication logic
* Alert resolution logic

### 🚧 In Progress / Planned

* React dashboard (real-time alert visualization)
* Python monitoring agent
* Docker Compose setup (Kafka + PostgreSQL + services)
* Advanced alert severity tiers

---

## 💡 Why This Project Matters

This project demonstrates:

* Microservice architecture design
* Event-driven systems using Kafka
* Asynchronous backend processing
* Clean API design with DTOs
* Database modeling and persistence
* Real-world alert lifecycle management

---

## 👨‍💻 Author

Derwin Bell
