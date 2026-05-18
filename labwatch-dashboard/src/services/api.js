import axios from "axios";

const AUTH_STORAGE_KEY = "labwatch.auth";
const AUTH_INVALIDATED_EVENT = "labwatch:auth-invalidated";

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

function readStoredAuth() {
  try {
    const rawValue = window.localStorage.getItem(AUTH_STORAGE_KEY);
    if (!rawValue) {
      return null;
    }

    return JSON.parse(rawValue);
  } catch {
    return null;
  }
}

function isExpired(expiresAt) {
  if (!expiresAt) {
    return false;
  }

  const timestamp = new Date(expiresAt).getTime();
  if (Number.isNaN(timestamp)) {
    return false;
  }

  return timestamp <= Date.now();
}

function invalidateStoredAuth(reason = "invalid_session") {
  try {
    window.localStorage.removeItem(AUTH_STORAGE_KEY);
    window.dispatchEvent(new CustomEvent(AUTH_INVALIDATED_EVENT, { detail: { reason } }));
  } catch {
    window.localStorage.removeItem(AUTH_STORAGE_KEY);
  }
}

function shouldInvalidateFromResponse(error) {
  const statusCode = error?.response?.status ?? 0;
  if (statusCode !== 401 && statusCode !== 403) {
    return false;
  }

  const requestUrl = extractRequestUrl({
    baseURL: error?.config?.baseURL,
    url: error?.config?.url,
  });

  return !requestUrl.includes("/api/v1/auth/login")
    && !requestUrl.includes("/api/v1/auth/register")
    && !requestUrl.includes("/api/v1/auth/config");
}

[monitoringApi, alertsApi, anomaliesApi].forEach((client) => {
  client.interceptors.request.use((config) => {
    const storedAuth = readStoredAuth();
    if (isExpired(storedAuth?.expiresAt)) {
      invalidateStoredAuth("expired_session");
      return config;
    }

    const token = typeof storedAuth?.token === "string" ? storedAuth.token : "";
    if (token) {
      config.headers = config.headers || {};
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  });

  client.interceptors.response.use(
    (response) => response,
    (error) => {
      if (shouldInvalidateFromResponse(error)) {
        const statusCode = error?.response?.status ?? 0;
        invalidateStoredAuth(statusCode === 401 ? "expired_session" : "access_denied");
      }
      return Promise.reject(error);
    }
  );
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

export async function getTelemetrySnapshots(machineIdentifier = "") {
  const response = await monitoringApi.get("/api/v1/telemetry/snapshots", {
    params: machineIdentifier ? { machineIdentifier } : {},
  });
  return Array.isArray(response.data) ? response.data : [];
}

export async function getMachines() {
  const response = await monitoringApi.get("/api/v1/machines");
  return Array.isArray(response.data) ? response.data : [];
}

export async function getAvailableMachines() {
  const response = await monitoringApi.get("/api/v1/machines/available");
  return Array.isArray(response.data) ? response.data : [];
}

export async function claimMachine(machineIdentifier) {
  const response = await monitoringApi.post(`/api/v1/machines/${encodeURIComponent(machineIdentifier)}/claim`);
  return response.data ?? null;
}

export async function unclaimMachine(machineIdentifier) {
  const response = await monitoringApi.delete(`/api/v1/machines/${encodeURIComponent(machineIdentifier)}/claim`);
  return response.data ?? null;
}

export async function getAuthConfig() {
  const response = await monitoringApi.get("/api/v1/auth/config");
  return Boolean(response.data?.enabled);
}

export async function registerUser({ email, password, displayName }) {
  const response = await monitoringApi.post("/api/v1/auth/register", {
    email,
    password,
    displayName,
  });
  return response.data ?? null;
}

export async function loginUser({ email, password }) {
  const response = await monitoringApi.post("/api/v1/auth/login", {
    email,
    password,
  });
  return response.data ?? null;
}

export async function getAlerts(machineIdentifier = "") {
  const response = await alertsApi.get("/api/alerts", {
    params: machineIdentifier ? { machineIdentifier } : {},
  });
  return Array.isArray(response.data) ? response.data : [];
}

export async function getActiveAlerts(machineIdentifier = "") {
  const response = await alertsApi.get("/api/alerts/active", {
    params: machineIdentifier ? { machineIdentifier } : {},
  });
  return Array.isArray(response.data) ? response.data : [];
}

export async function getAnomalies(machineIdentifier = "") {
  try {
    const response = await anomaliesApi.get("/api/anomalies", {
      params: machineIdentifier ? { machineIdentifier } : {},
    });
    return Array.isArray(response.data) ? response.data : [];
  } catch (error) {
    logRequestError("getAnomalies", error);
    throw error;
  }
}

export async function getAnomalyById(anomalyId) {
  try {
    const response = await anomaliesApi.get(`/api/anomalies/${encodeURIComponent(anomalyId)}`);
    return response.data ?? null;
  } catch (error) {
    logRequestError("getAnomalyById", error);
    throw error;
  }
}

export async function getRecentInvestigations(machineIdentifier = "") {
  try {
    const endpoint = machineIdentifier
      ? `/api/investigations/machine/${encodeURIComponent(machineIdentifier)}`
      : "/api/investigations";
    const response = await anomaliesApi.get(endpoint);
    return Array.isArray(response.data) ? response.data : [];
  } catch (error) {
    logRequestError("getRecentInvestigations", error);
    throw error;
  }
}

export async function getInvestigationsByAlertId(alertId) {
  try {
    const response = await anomaliesApi.get(`/api/investigations/alert/${encodeURIComponent(alertId)}`);
    return Array.isArray(response.data) ? response.data : [];
  } catch (error) {
    logRequestError("getInvestigationsByAlertId", error);
    throw error;
  }
}

export async function getInsight(machineIdentifier = "") {
  try {
    const response = await anomaliesApi.get("/api/ai/insight", {
      params: machineIdentifier ? { machineIdentifier } : {},
    });
    return typeof response.data === "string" ? response.data : "";
  } catch (error) {
    logRequestError("getInsight", error);
    throw error;
  }
}

export async function getEventInsight({ timestamp, metric, value, source, machineIdentifier }) {
  try {
    const response = await anomaliesApi.get("/api/ai/event-insight", {
      params: { timestamp, metric, value, source, machineIdentifier },
    });
    return typeof response.data === "string" ? response.data : "";
  } catch (error) {
    logRequestError("getEventInsight", error);
    throw error;
  }
}

export async function sendChatMessage(message, machineIdentifier = "") {
  try {
    const response = await anomaliesApi.post("/api/ai/chat", {
      message,
      machineIdentifier: machineIdentifier || null,
    });
    return typeof response.data?.response === "string" ? response.data.response : "";
  } catch (error) {
    logRequestError("sendChatMessage", error);
    throw error;
  }
}

export { AUTH_STORAGE_KEY, AUTH_INVALIDATED_EVENT, monitoringApi, alertsApi, anomaliesApi };
