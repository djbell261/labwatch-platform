import axios from "axios";

const monitoringApi = axios.create({
  baseURL: "http://localhost:8089",
  timeout: 8000,
});

const alertsApi = axios.create({
  baseURL: "http://localhost:8088",
  timeout: 8000,
});

const anomaliesApi = axios.create({
  baseURL: "http://localhost:8090",
  timeout: 8000,
});

function extractRequestUrl(config = {}) {
  const baseURL = String(config.baseURL || "").replace(/\/$/, "");
  const url = String(config.url || "").trim();

  if (!url) {
    return baseURL || "unknown-url";
  }

  if (/^https?:\/\//i.test(url)) {
    return url;
  }

  return `${baseURL}${url.startsWith("/") ? url : `/${url}`}`;
}

function logRequestError(label, error) {
  const statusCode = error?.response?.status ?? "NO_RESPONSE";
  const responseBody = error?.response?.data ?? null;
  const requestUrl = extractRequestUrl({
    baseURL: error?.config?.baseURL,
    url: error?.config?.url,
  });

  console.error(`[${label}] request failed`, {
    statusCode,
    url: requestUrl,
    responseBody,
  });
}

export async function getTelemetrySnapshots() {
  const response = await monitoringApi.get("/api/v1/telemetry/snapshots");
  return Array.isArray(response.data) ? response.data : [];
}

export async function getAlerts() {
  const response = await alertsApi.get("/api/alerts");
  return Array.isArray(response.data) ? response.data : [];
}

export async function getAnomalies() {
  try {
    const response = await anomaliesApi.get("/api/anomalies");
    return Array.isArray(response.data) ? response.data : [];
  } catch (error) {
    logRequestError("getAnomalies", error);
    throw error;
  }
}

export { monitoringApi, alertsApi, anomaliesApi };
