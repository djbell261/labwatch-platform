import axios from "axios";

const AUTH_STORAGE_KEY = "labwatch.auth";
const AUTH_INVALIDATED_EVENT = "labwatch:auth-invalidated";
const DEFAULT_MONITORING_API_URL = "http://localhost:8089";
const DEFAULT_ALERT_ENGINE_URL = "http://localhost:8088";
const DEFAULT_AI_ENGINE_URL = "http://localhost:8090";
const DEFAULT_NOTIFICATION_SERVICE_URL = "http://localhost:8091";

function getConfiguredBaseUrl(envKey, fallbackValue) {
  const configuredValue = String(import.meta.env[envKey] || "").trim();
  return configuredValue || fallbackValue;
}

export const monitoringApiBaseUrl = getConfiguredBaseUrl(
  "VITE_MONITORING_API_URL",
  DEFAULT_MONITORING_API_URL
);
export const alertEngineBaseUrl = getConfiguredBaseUrl(
  "VITE_ALERT_ENGINE_URL",
  DEFAULT_ALERT_ENGINE_URL
);
export const aiEngineBaseUrl = getConfiguredBaseUrl(
  "VITE_AI_ENGINE_URL",
  DEFAULT_AI_ENGINE_URL
);
export const notificationServiceBaseUrl = getConfiguredBaseUrl(
  "VITE_NOTIFICATION_SERVICE_URL",
  DEFAULT_NOTIFICATION_SERVICE_URL
);

const monitoringApi = axios.create({
  baseURL: monitoringApiBaseUrl,
  timeout: 8000,
});

const alertsApi = axios.create({
  baseURL: alertEngineBaseUrl,
  timeout: 8000,
});

const aiEngineApi = axios.create({
  baseURL: aiEngineBaseUrl,
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

[monitoringApi, alertsApi, aiEngineApi].forEach((client) => {
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
    message: error?.message || "Unknown request failure",
    responseBody,
  });
}

function normalizeCollection(payload) {
  if (Array.isArray(payload)) {
    return payload;
  }

  if (Array.isArray(payload?.content)) {
    return payload.content;
  }

  if (Array.isArray(payload?.items)) {
    return payload.items;
  }

  if (Array.isArray(payload?.data)) {
    return payload.data;
  }

  if (Array.isArray(payload?.results)) {
    return payload.results;
  }

  return [];
}

function normalizeTelemetrySnapshot(snapshot = {}) {
  return {
    ...snapshot,
    id: snapshot.id ?? snapshot.snapshotId ?? null,
    snapshotId: snapshot.snapshotId ?? snapshot.id ?? null,
    machineIdentifier: snapshot.machineIdentifier ?? "",
    hostname: snapshot.hostname ?? "",
    timestamp: snapshot.timestamp ?? snapshot.createdAt ?? null,
    createdAt: snapshot.createdAt ?? snapshot.timestamp ?? null,
  };
}

function normalizeMachine(machine = {}) {
  return {
    ...machine,
    id: machine.id ?? null,
    machineIdentifier: machine.machineIdentifier ?? machine.hostname ?? "",
    hostname: machine.hostname ?? machine.machineIdentifier ?? "",
    status: machine.status ?? "UNKNOWN",
    lastSeenAt: machine.lastSeenAt ?? machine.updatedAt ?? null,
    owned: Boolean(machine.owned),
    ownerUserId: machine.ownerUserId ?? null,
    ownerDisplayName: machine.ownerDisplayName ?? null,
  };
}

function normalizeAlert(alert = {}) {
  return {
    ...alert,
    id: alert.id ?? alert.alertId ?? null,
    machineIdentifier: alert.machineIdentifier ?? "",
    alertType: alert.alertType ?? alert.eventType ?? "UNKNOWN",
    severity: alert.severity ?? "UNKNOWN",
    status: alert.status ?? "UNKNOWN",
    createdAt: alert.createdAt ?? alert.timestamp ?? null,
    resolvedAt: alert.resolvedAt ?? null,
  };
}

function normalizeAnomaly(anomaly = {}) {
  return {
    ...anomaly,
    id: anomaly.id ?? anomaly.anomalyId ?? null,
    anomalyId: anomaly.anomalyId ?? anomaly.id ?? null,
    machineIdentifier: anomaly.machineIdentifier ?? "",
    eventType: anomaly.eventType ?? anomaly.metricType ?? "UNKNOWN",
    metricType: anomaly.metricType ?? anomaly.eventType ?? "UNKNOWN",
    anomalyScore: anomaly.anomalyScore ?? anomaly.zScore ?? null,
    metricValue: anomaly.metricValue ?? null,
    detectedAt: anomaly.detectedAt ?? anomaly.createdAt ?? null,
    explanation: anomaly.explanation ?? anomaly.message ?? "",
    promotedAlertId: anomaly.promotedAlertId ?? null,
    promotedToAlert: Boolean(anomaly.promotedToAlert || anomaly.promotedAlertId),
    severity: anomaly.severity ?? "UNKNOWN",
  };
}

function normalizeInvestigation(investigation = {}) {
  return {
    ...investigation,
    investigationId: investigation.investigationId ?? investigation.id ?? "",
    incidentId: investigation.incidentId ?? investigation.investigationId ?? investigation.id ?? "",
    incidentGroupKey: investigation.incidentGroupKey ?? "",
    incidentStatus: investigation.incidentStatus ?? "ACTIVE",
    alertId: investigation.alertId ?? "",
    machineIdentifier: investigation.machineIdentifier ?? "",
    alertType: investigation.alertType ?? investigation.eventType ?? "UNKNOWN",
    severity: investigation.severity ?? "UNKNOWN",
    summary: investigation.summary ?? investigation.message ?? "",
    likelyCause: investigation.likelyCause ?? "",
    evidence: investigation.evidence ?? "",
    contributingFactors: investigation.contributingFactors ?? "",
    recommendedChecks: investigation.recommendedChecks ?? "",
    recommendedAction: investigation.recommendedAction ?? "",
    urgencyAssessment: investigation.urgencyAssessment ?? "",
    persistenceAssessment: investigation.persistenceAssessment ?? "",
    monitorNext: investigation.monitorNext ?? "",
    suspectedContributor: investigation.suspectedContributor ?? "",
    affectedMetrics: investigation.affectedMetrics ?? investigation.alertType ?? "UNKNOWN",
    confidenceScore: investigation.confidenceScore ?? null,
    confidenceLevel: investigation.confidenceLevel ?? investigation.confidence ?? "UNKNOWN",
    confidenceReasoning: investigation.confidenceReasoning ?? "",
    baselineSummary: investigation.baselineSummary ?? "",
    historicalPatternNotes: investigation.historicalPatternNotes ?? "",
    correlationTimeline: Array.isArray(investigation.correlationTimeline)
      ? investigation.correlationTimeline.map((entry) => ({
          timestamp: entry?.timestamp ?? null,
          machineIdentifier: entry?.machineIdentifier ?? investigation.machineIdentifier ?? "",
          type: entry?.type ?? "UNKNOWN",
          metric: entry?.metric ?? "",
          value: entry?.value ?? null,
          description: entry?.description ?? "",
          source: entry?.source ?? "",
        }))
      : [],
    confidence: investigation.confidence ?? "UNKNOWN",
    createdAt: investigation.createdAt ?? investigation.persistedAt ?? null,
    persistedAt: investigation.persistedAt ?? investigation.createdAt ?? null,
  };
}

async function getCollection(client, label, url, options, normalizer = (value) => value) {
  try {
    const response = await client.get(url, options);
    return normalizeCollection(response.data).map(normalizer);
  } catch (error) {
    logRequestError(label, error);
    throw error;
  }
}

export async function getTelemetrySnapshots(machineIdentifier = "") {
  return getCollection(
    monitoringApi,
    "getTelemetrySnapshots",
    "/api/v1/telemetry/snapshots",
    { params: machineIdentifier ? { machineIdentifier } : {} },
    normalizeTelemetrySnapshot
  );
}

export async function getMachines() {
  return getCollection(monitoringApi, "getMachines", "/api/v1/machines", undefined, normalizeMachine);
}

export async function getAvailableMachines() {
  return getCollection(
    monitoringApi,
    "getAvailableMachines",
    "/api/v1/machines/available",
    undefined,
    normalizeMachine
  );
}

export async function claimMachine(machineIdentifier) {
  try {
    const response = await monitoringApi.post(`/api/v1/machines/${encodeURIComponent(machineIdentifier)}/claim`);
    return response.data ? normalizeMachine(response.data) : null;
  } catch (error) {
    logRequestError("claimMachine", error);
    throw error;
  }
}

export async function unclaimMachine(machineIdentifier) {
  try {
    const response = await monitoringApi.delete(`/api/v1/machines/${encodeURIComponent(machineIdentifier)}/claim`);
    return response.data ? normalizeMachine(response.data) : null;
  } catch (error) {
    logRequestError("unclaimMachine", error);
    throw error;
  }
}

export async function getAuthConfig() {
  try {
    const response = await monitoringApi.get("/api/v1/auth/config");
    return Boolean(response.data?.enabled);
  } catch (error) {
    logRequestError("getAuthConfig", error);
    throw error;
  }
}

export async function registerUser({ email, password, displayName }) {
  try {
    const response = await monitoringApi.post("/api/v1/auth/register", {
      email,
      password,
      displayName,
    });
    return response.data ?? null;
  } catch (error) {
    logRequestError("registerUser", error);
    throw error;
  }
}

export async function loginUser({ email, password }) {
  try {
    const response = await monitoringApi.post("/api/v1/auth/login", {
      email,
      password,
    });
    return response.data ?? null;
  } catch (error) {
    logRequestError("loginUser", error);
    throw error;
  }
}

export async function getAlerts(machineIdentifier = "") {
  return getCollection(
    alertsApi,
    "getAlerts",
    "/api/alerts",
    { params: machineIdentifier ? { machineIdentifier } : {} },
    normalizeAlert
  );
}

export async function getActiveAlerts(machineIdentifier = "") {
  return getCollection(
    alertsApi,
    "getActiveAlerts",
    "/api/alerts/active",
    { params: machineIdentifier ? { machineIdentifier } : {} },
    normalizeAlert
  );
}

export async function getAnomalies(machineIdentifier = "") {
  return getCollection(
    aiEngineApi,
    "getAnomalies",
    "/api/anomalies",
    { params: machineIdentifier ? { machineIdentifier } : {} },
    normalizeAnomaly
  );
}

export async function getAnomalyById(anomalyId) {
  try {
    const response = await aiEngineApi.get(`/api/anomalies/${encodeURIComponent(anomalyId)}`);
    return response.data ? normalizeAnomaly(response.data) : null;
  } catch (error) {
    logRequestError("getAnomalyById", error);
    throw error;
  }
}

export async function getRecentInvestigations(machineIdentifier = "") {
  const endpoint = machineIdentifier
    ? `/api/investigations/machine/${encodeURIComponent(machineIdentifier)}`
    : "/api/investigations";
  return getCollection(aiEngineApi, "getRecentInvestigations", endpoint, undefined, normalizeInvestigation);
}

export async function getInvestigationsByAlertId(alertId) {
  return getCollection(
    aiEngineApi,
    "getInvestigationsByAlertId",
    `/api/investigations/alert/${encodeURIComponent(alertId)}`,
    undefined,
    normalizeInvestigation
  );
}

export async function getInvestigationsByIncidentId(incidentId) {
  return getCollection(
    aiEngineApi,
    "getInvestigationsByIncidentId",
    `/api/investigations/incident/${encodeURIComponent(incidentId)}`,
    undefined,
    normalizeInvestigation
  );
}

export async function getInsight(machineIdentifier = "") {
  try {
    const response = await aiEngineApi.get("/api/ai/insight", {
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
    const response = await aiEngineApi.get("/api/ai/event-insight", {
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
    const response = await aiEngineApi.post("/api/ai/chat", {
      message,
      machineIdentifier: machineIdentifier || null,
    });
    return typeof response.data?.response === "string" ? response.data.response : "";
  } catch (error) {
    logRequestError("sendChatMessage", error);
    throw error;
  }
}

export { AUTH_STORAGE_KEY, AUTH_INVALIDATED_EVENT, monitoringApi, alertsApi, aiEngineApi };
