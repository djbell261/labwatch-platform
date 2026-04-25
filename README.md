# LabWatch Platform

**AI-Powered System Monitoring & Anomaly Detection Platform**

---

## Overview

LabWatch is a distributed, event-driven monitoring platform designed to simulate real-world observability systems like AWS CloudWatch or Prometheus — enhanced with AI-powered anomaly detection and intelligent system insights.

The platform ingests real-time telemetry data, processes it through microservices, detects anomalies using statistical models, and provides actionable insights through an integrated AI assistant.

---

## Key Features

### Real-Time Monitoring

* Live CPU, Memory, and Disk telemetry visualization
* WebSocket-based streaming updates
* Historical trend analysis

### Alert Engine

* Threshold-based alert detection (CPU, Memory, Disk)
* Alert lifecycle management:

  * `ACTIVE → RESOLVED`
* Deduplication to prevent alert spam

### AI Anomaly Detection

* Rolling statistical model (mean + standard deviation)
* Z-score–based anomaly detection
* Per-machine, per-metric anomaly tracking
* Kafka-based anomaly event pipeline

### AI Insights & Chat Assistant

* System-wide health summaries
* Event-level spike explanations
* Interactive AI assistant for debugging:

  * “Why is CPU high?”
  * “What caused this anomaly?”
  * “What should I fix first?”

### Monitoring Dashboard

* React + Vite frontend
* Recharts-based visualization
* Alert & anomaly overlays
* Clickable events → AI-powered explanations

### Python Monitoring Agent

* Collects real system telemetry using `psutil`
* Sends data to backend via HTTP
* Supports continuous and one-shot modes
* Retry + exponential backoff

---

## Architecture

```
[ Python Agent ]
        ↓
 monitoring-api (Spring Boot)
        ↓
      Kafka (health-events)
        ↓
   alert-engine (Spring Boot)
        ↓
   ai-engine-service (Spring Boot)
        ↓
 PostgreSQL (alerts, anomalies, telemetry)
        ↓
   React Dashboard (WebSocket + REST)
```

---

## Tech Stack

### Backend

* Java 17+
* Spring Boot (Microservices)
* Kafka (Event Streaming)
* PostgreSQL (Persistence)
* Docker & Docker Compose

### Frontend

* React (Vite)
* Recharts (Data Visualization)
* Axios (API calls)
* WebSockets (real-time updates)

### AI Layer

* Custom statistical anomaly detection
* OpenAI integration (optional)
* AWS Bedrock (extensible)
* Provider abstraction (mock / OpenAI / Bedrock)

### Agent

* Python
* psutil (system metrics)
* httpx (HTTP client)

---

## Services

| Service           | Port | Description                     |
| ----------------- | ---- | ------------------------------- |
| monitoring-api    | 8089 | Ingests telemetry data          |
| alert-engine      | 8088 | Processes alerts                |
| ai-engine-service | 8090 | Detects anomalies + AI insights |
| PostgreSQL        | 5434 | Database                        |
| Kafka             | 9092 | Event streaming                 |

---

## Getting Started

### 1. Clone the repo

```bash
git clone https://github.com/your-username/labwatch-platform.git
cd labwatch-platform
```

### 2. Create `.env` file

```env
OPENAI_API_KEY=your_api_key_here
```

### 3. Start all services

```bash
docker compose up --build
```

### 4. Run frontend

```bash
cd labwatch-dashboard
npm install
npm run dev
```

### 5. Start Python agent

```bash
cd labwatch-agent
python main.py --continuous
```

---

## API Endpoints

### Telemetry

```http
POST /api/v1/telemetry/snapshots
GET  /api/v1/telemetry/snapshots
```

### Alerts

```http
GET /api/alerts
```

### Anomalies

```http
GET /api/anomalies
```

### AI Insights

```http
GET /api/insight
GET /api/insight/event
POST /api/chat
```

---

## Example AI Query

```bash
curl -X POST http://localhost:8090/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message":"Why is CPU high?"}'
```

---

## How It Works

1. Python agent collects system metrics
2. Data is sent to `monitoring-api`
3. Events are published to Kafka (`health-events`)
4. `alert-engine` processes thresholds
5. `ai-engine-service`:

   * Detects anomalies
   * Generates insights
   * Handles AI chat queries
6. Dashboard visualizes everything in real-time

---

## What Makes This Project Stand Out

* Real microservices architecture (not monolithic)
* Event-driven system using Kafka
* AI integrated as a **core system component**, not a feature
* Real-time streaming + visualization
* Production-style Docker setup
* Extensible AI provider abstraction

---

## Future Improvements

* AWS deployment (ECS / EKS)
* Prometheus + Grafana integration
* Authentication & multi-user support
* Persistent anomaly baselines
* Auto-remediation actions (restart services, kill processes)

---

## Author

**Derwin Bell**
Computer Science Student | Backend & DevOps Focus
[LinkedIn](https://www.linkedin.com/in/derwin-bell-666336256)

---

## Final Note

LabWatch is more than a monitoring tool — it’s a step toward **AI-assisted system operations**, where developers don’t just observe systems, but actively understand and improve them in real time.
