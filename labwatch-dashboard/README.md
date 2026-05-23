# LabWatch Dashboard

Frontend workspace for the LabWatch monitoring platform.

## Local API wiring

The dashboard expects these local backend defaults:

```env
VITE_MONITORING_API_URL=http://localhost:8089
VITE_ALERT_ENGINE_URL=http://localhost:8088
VITE_AI_ENGINE_URL=http://localhost:8090
VITE_NOTIFICATION_SERVICE_URL=http://localhost:8091
```

Copy [`.env.example`](/Users/derwinbell/dev/ResumeProjects/labwatch-platform/labwatch-dashboard/.env.example) to `.env.local` if you want to override the frontend defaults.

## Backend health checks

Operational pages like `/dashboard`, `/incidents`, `/anomalies`, and `/machines` depend on the backend services being reachable:

```bash
curl http://localhost:8089/actuator/health
curl http://localhost:8088/actuator/health
curl http://localhost:8090/actuator/health
curl http://localhost:8091/actuator/health
```

If these are healthy, the frontend should show empty/demo states for missing data rather than generic service-unavailable messaging.
