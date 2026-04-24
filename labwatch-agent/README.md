# LabWatch Agent

`labwatch-agent` is a lightweight Python host monitoring agent for LabWatch 2.0. It collects local machine telemetry and sends normalized telemetry snapshots to `monitoring-api`.

## Features

- CPU, memory, and disk usage collection
- Hostname, OS type/version, and uptime collection
- Top process metrics collection
- One-shot mode for quick verification
- Continuous mode for interval-based monitoring
- Retry with exponential backoff on API failures
- Structured key-value logging

## Project Structure

```text
labwatch-agent/
- agent/
- agent/__main__.py
- agent/config.py
- agent/models.py
- agent/collector.py
- agent/processes.py
- agent/client.py
- agent/runner.py
- agent/backoff.py
- agent/logging_config.py
- requirements.txt
- .env.example
- README.md
```

## Setup

```bash
cd labwatch-agent
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env
```

Update `.env` with your local values before running.

## Run Once

```bash
python -m agent --once
```

## Run Continuously

```bash
python -m agent
```

## Configuration

Required:

- `LABWATCH_API_URL`
- `LABWATCH_MACHINE_IDENTIFIER`
- `COLLECTION_INTERVAL_SECONDS`

Optional:

- `REQUEST_TIMEOUT_SECONDS`
- `MAX_RETRIES`
- `MAX_BACKOFF_SECONDS`
- `TOP_PROCESS_COUNT`

## Target Endpoint

The agent sends telemetry to:

`POST /api/v1/telemetry/snapshots`

## Sample Payload

```json
{
  "machineIdentifier": "derwins-macbook",
  "hostname": "Derwins-MacBook-Air",
  "osType": "Darwin",
  "osVersion": "Darwin Kernel Version 23.5.0",
  "uptimeSeconds": 123456,
  "timestamp": "2026-04-24T15:10:00+00:00",
  "cpuUsage": 42.5,
  "memoryUsage": 68.2,
  "diskUsage": 74.1,
  "source": "python-agent",
  "processMetrics": [
    {
      "processName": "java",
      "cpuPercent": 22.4,
      "memoryPercent": 18.7
    }
  ]
}
```

## Example Logs

```text
timestamp=2026-04-24T15:10:00+0000 level=INFO logger=agent.runner message="snapshot collected" cpuUsage=42.5 diskUsage=74.1 hostname=Derwins-MacBook-Air machineIdentifier=derwins-macbook memoryUsage=68.2 processCount=5
timestamp=2026-04-24T15:10:00+0000 level=INFO logger=agent.client message="telemetry request sent" attempt=1 endpoint=http://localhost:8089/api/v1/telemetry/snapshots machineIdentifier=derwins-macbook
timestamp=2026-04-24T15:10:01+0000 level=INFO logger=agent.client message="telemetry request succeeded" machineIdentifier=derwins-macbook statusCode=201
```
