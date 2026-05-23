# LabWatch Deployment Readiness

This guide summarizes the production-shaped defaults and demo-friendly setup for LabWatch.

## Environment Setup

1. Copy `.env.example` to `.env`.
2. For hosted deployment, start from `.env.production.example`.
2. Set `JWT_SECRET` to a non-default value for any shared or cloud-hosted environment.
3. Choose one Spring runtime profile with `LABWATCH_SPRING_PROFILE`:
   - Local development: `local`
   - Demo/recruiter mode: `demo`
   - Hosted pre-production: `staging`
   - Production: `prod`
4. Choose one auth mode:
   - Local demo mode: `LABWATCH_AUTH_ENABLED=false`
   - Auth-enabled MVP mode: `LABWATCH_AUTH_ENABLED=true`
5. Choose one AI mode:
   - Stable local/demo mode: `AI_PROVIDER=mock`
   - Hosted AI mode: set `AI_PROVIDER`, `OPENAI_API_KEY`, and `OPENAI_MODEL`

## Local Demo Startup

Bring up the stack:

```bash
docker compose up --build -d
```

Seed meaningful demo telemetry:

```bash
./scripts/seed-demo-telemetry.sh
```

Then open:

- Home page: `http://localhost:5173`
- Monitoring API: `http://localhost:8089`
- Alert Engine: `http://localhost:8088`
- AI Engine: `http://localhost:8090`
- Notification Service: `http://localhost:8091`
- Prometheus: `http://localhost:9091`
- Grafana: `http://localhost:3001`

## Auth Modes

### Local Demo Mode

- `LABWATCH_AUTH_ENABLED=false`
- Landing page still shows product auth CTAs
- Login and Sign Up pages explain that auth is disabled locally
- Dashboard remains directly accessible for recruiter demos

### Auth-Enabled MVP Mode

- `LABWATCH_AUTH_ENABLED=true`
- JWT-based login/register is active
- Expired or invalid sessions are cleared cleanly on the frontend
- Users receive one of two lightweight roles:
  - `ADMIN`
  - `OPERATOR`

The first registered user is assigned `ADMIN`. Later registrations default to `OPERATOR`.

## Compose/Runtime Defaults

Phase 2 adds the following production-shaped behavior:

- `restart: unless-stopped` on platform services
- health checks for backend services through `/actuator/health`
- stable default AI provider in Compose: `mock`
- structured log formatting with service name and request ID support
- startup summary logging for the Spring services
- Flyway migrations with per-service schema history tables
- `ddl-auto=validate` for deployment-shaped startup behavior

## Troubleshooting

### Session loops back to login

Likely causes:

- expired JWT
- invalidated local auth state
- role mismatch on a guarded route

Fix:

1. Clear local storage for the dashboard app.
2. Re-authenticate if auth is enabled.
3. Verify the backend is running with the expected `LABWATCH_AUTH_ENABLED` mode.

### Dashboard loads but no meaningful data appears

Run the demo seed script:

```bash
./scripts/seed-demo-telemetry.sh
```

### AI requests fail in demo mode

That is expected if you want a stable local demo without provider credentials. Use:

```env
AI_PROVIDER=mock
```

### Service marked unhealthy in Compose

Check the actuator endpoint directly:

```bash
curl -fsS http://localhost:8089/actuator/health
curl -fsS http://localhost:8088/actuator/health
curl -fsS http://localhost:8090/actuator/health
curl -fsS http://localhost:8091/actuator/health
```

## Remaining Deployment Gaps

These are intentionally deferred beyond Phase 2:

- managed secrets storage
- TLS termination
- cloud networking and IAM design
- distributed tracing stack
- production autoscaling and multi-AZ topology
