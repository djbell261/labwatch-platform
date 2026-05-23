#!/usr/bin/env bash

set -euo pipefail

MONITORING_API_URL="${MONITORING_API_URL:-http://localhost:8089}"

post_snapshot() {
  local machine_identifier="$1"
  local hostname="$2"
  local cpu_usage="$3"
  local memory_usage="$4"
  local disk_usage="$5"

  local timestamp
  timestamp="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"

  curl -sS -X POST "${MONITORING_API_URL}/api/v1/telemetry/snapshots" \
    -H "Content-Type: application/json" \
    -d "{
      \"machineIdentifier\": \"${machine_identifier}\",
      \"hostname\": \"${hostname}\",
      \"osType\": \"Linux\",
      \"osVersion\": \"6.8\",
      \"uptimeSeconds\": 86400,
      \"timestamp\": \"${timestamp}\",
      \"cpuUsage\": ${cpu_usage},
      \"memoryUsage\": ${memory_usage},
      \"diskUsage\": ${disk_usage},
      \"source\": \"demo-seed\",
      \"processMetrics\": [
        {
          \"processName\": \"java\",
          \"cpuPercent\": ${cpu_usage},
          \"memoryPercent\": ${memory_usage}
        }
      ]
    }" >/dev/null
}

echo "Seeding demo telemetry into ${MONITORING_API_URL} ..."

post_snapshot "machine-demo-01" "machine-demo-01.local" 21.0 44.0 38.0
sleep 1
post_snapshot "machine-demo-02" "machine-demo-02.local" 94.0 82.0 41.0
sleep 1
post_snapshot "machine-demo-03" "machine-demo-03.local" 33.0 57.0 91.0
sleep 1
post_snapshot "machine-demo-02" "machine-demo-02.local" 97.0 86.0 42.0
sleep 1
post_snapshot "machine-demo-03" "machine-demo-03.local" 28.0 49.0 29.0

echo "Demo telemetry seed complete."
