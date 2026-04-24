import axios from "axios";

const monitoringApi = axios.create({
  baseURL: "http://localhost:8089",
  timeout: 8000,
});

const alertsApi = axios.create({
  baseURL: "http://localhost:8082",
  timeout: 8000,
});

export async function getTelemetrySnapshots() {
  const response = await monitoringApi.get("/api/v1/telemetry/snapshots");
  return Array.isArray(response.data) ? response.data : [];
}

export async function getAlerts() {
  const response = await alertsApi.get("/api/alerts");
  return Array.isArray(response.data) ? response.data : [];
}

export { monitoringApi, alertsApi };
