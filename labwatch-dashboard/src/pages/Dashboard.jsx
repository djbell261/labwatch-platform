import { useEffect, useMemo, useState } from "react";
import AiInsightPanel from "../components/AiInsightPanel";
import ChatAssistant from "../components/ChatAssistant";
import LatestAnomaliesPanel from "../components/LatestAnomaliesPanel";
import RecentIncidentsPanel from "../components/RecentIncidentsPanel";
import TelemetryTrendChart from "../components/TelemetryTrendChart";
import { buildInsightModel } from "../components/aiInsightModel";
import { useAuth } from "../context/AuthContext";
import {
  claimMachine,
  getAlerts,
  getAnomalies,
  getAvailableMachines,
  getInsight,
  getRecentInvestigations,
  getMachines,
  getTelemetrySnapshots,
  sendChatMessage,
  unclaimMachine,
} from "../services/api";
import { createTelemetrySocket } from "../services/socket";

const NAV_ITEMS = [
  { id: "overview", label: "Overview", subtitle: "Health summary", icon: "◫" },
  { id: "machines", label: "Machines", subtitle: "Fleet inventory", icon: "⌘" },
  { id: "alerts", label: "Alerts", subtitle: "Alert workflow", icon: "!" },
  { id: "ai", label: "AI Assistant", subtitle: "Investigate issues", icon: "✦" },
];

const SEVERITY_FILTERS = ["ALL", "CRITICAL", "HIGH", "MEDIUM", "LOW"];
const STATUS_FILTERS = ["ALL", "ACTIVE", "RESOLVED"];

function getLatestSnapshot(snapshots) {
  if (!Array.isArray(snapshots) || snapshots.length === 0) {
    return null;
  }

  return [...snapshots].sort((left, right) => {
    const leftTime = new Date(left.timestamp || left.createdAt || 0).getTime();
    const rightTime = new Date(right.timestamp || right.createdAt || 0).getTime();
    return rightTime - leftTime;
  })[0];
}

function mergeSnapshotIntoHistory(existingSnapshots, incomingSnapshot) {
  const combinedSnapshots = [incomingSnapshot, ...existingSnapshots];
  const deduplicatedSnapshots = combinedSnapshots.filter((snapshot, index, snapshots) => {
    const snapshotId = snapshot.snapshotId || snapshot.id;
    return snapshots.findIndex((candidate) => (candidate.snapshotId || candidate.id) === snapshotId) === index;
  });

  return deduplicatedSnapshots
    .sort((left, right) => {
      const leftTime = new Date(left.timestamp || left.createdAt || 0).getTime();
      const rightTime = new Date(right.timestamp || right.createdAt || 0).getTime();
      return rightTime - leftTime;
    })
    .slice(0, 50);
}

function formatRelativeLastSeen(value) {
  if (!value) {
    return "No data";
  }

  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return value;
  }

  const diffSeconds = Math.max(0, Math.round((Date.now() - parsed.getTime()) / 1000));
  if (diffSeconds < 5) {
    return "Just now";
  }
  if (diffSeconds < 60) {
    return `${diffSeconds}s ago`;
  }
  if (diffSeconds < 3600) {
    return `${Math.round(diffSeconds / 60)}m ago`;
  }
  return `${Math.round(diffSeconds / 3600)}h ago`;
}

function resolveMachineStatus(machine) {
  const parsed = machine?.lastSeenAt ? new Date(machine.lastSeenAt) : null;
  if (!parsed || Number.isNaN(parsed.getTime())) {
    return { label: machine?.status || "UNKNOWN", tone: "yellow" };
  }

  const diffSeconds = Math.max(0, Math.round((Date.now() - parsed.getTime()) / 1000));
  if (diffSeconds <= 45) {
    return { label: "ONLINE", tone: "green" };
  }

  return { label: "OFFLINE", tone: "yellow" };
}

function resolveOwnership(machine, currentUserId) {
  if (!machine?.owned) {
    return { label: "Unclaimed", tone: "blue" };
  }

  if (machine.ownerUserId && currentUserId && machine.ownerUserId === currentUserId) {
    return { label: "Owned by you", tone: "green" };
  }

  return { label: "Restricted", tone: "red" };
}

function formatTimestamp(value) {
  if (!value) {
    return "No data";
  }

  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return value;
  }

  return parsed.toLocaleString();
}

function formatPercent(value) {
  const numericValue = Number(value);
  return Number.isFinite(numericValue) ? `${numericValue.toFixed(1)}%` : "No data";
}

function formatCount(value) {
  const numericValue = Number(value);
  return Number.isFinite(numericValue) ? String(numericValue) : "0";
}

function getUsageTone(value) {
  const numericValue = Number(value);

  if (!Number.isFinite(numericValue)) {
    return "blue";
  }
  if (numericValue >= 90) {
    return "red";
  }
  if (numericValue >= 75) {
    return "yellow";
  }
  return "green";
}

function getToneColor(tone) {
  if (tone === "green") {
    return "#22c55e";
  }
  if (tone === "yellow") {
    return "#f59e0b";
  }
  if (tone === "red") {
    return "#ef4444";
  }
  return "#38bdf8";
}

function getInitials(value) {
  const label = String(value || "LW").trim();
  return label
    .split(/\s+/)
    .slice(0, 2)
    .map((segment) => segment[0]?.toUpperCase() || "")
    .join("");
}

function Sparkline({ points = [], color = "#38bdf8" }) {
  if (!points.length) {
    return (
      <svg className="sparkline" viewBox="0 0 120 38" aria-hidden="true">
        <rect fill="rgba(148, 163, 184, 0.12)" height="6" rx="3" width="64" x="56" y="16" />
      </svg>
    );
  }

  const min = Math.min(...points);
  const max = Math.max(...points);
  const range = max - min || 1;
  const path = points
    .map((point, index) => {
      const x = (index / Math.max(points.length - 1, 1)) * 120;
      const y = 34 - ((point - min) / range) * 28;
      return `${index === 0 ? "M" : "L"}${x.toFixed(2)} ${y.toFixed(2)}`;
    })
    .join(" ");

  return (
    <svg className="sparkline" viewBox="0 0 120 38" aria-hidden="true">
      <path
        d={path}
        fill="none"
        stroke={color}
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth="2.5"
      />
    </svg>
  );
}

function KpiCard({ label, value, tone, subtext, points }) {
  return (
    <section className="surface-card kpi-card">
      <div className="card-header">
        <div className="card-label">{label}</div>
        <span className={`status-dot ${tone}`} />
      </div>
      <div className="kpi-value">{value}</div>
      <div className="kpi-footer">
        <div className="kpi-subtext">{subtext}</div>
        <Sparkline points={points} color={getToneColor(tone)} />
      </div>
    </section>
  );
}

function TopStatus({ label, value, tone = "blue" }) {
  return (
    <div className="top-status">
      <span className={`status-dot ${tone}`} />
      <div className="profile-meta">
        <span className="machine-card-subtle">{label}</span>
        <span className="profile-name">{value}</span>
      </div>
    </div>
  );
}

function SelectFilter({ value, onChange, options, label }) {
  return (
    <label>
      <span className="machine-card-subtle" style={{ display: "block", marginBottom: "6px" }}>
        {label}
      </span>
      <select className="table-filter" value={value} onChange={(event) => onChange(event.target.value)}>
        {options.map((option) => (
          <option key={option} value={option}>
            {option}
          </option>
        ))}
      </select>
    </label>
  );
}

function Toast({ toast }) {
  if (!toast) {
    return null;
  }

  return (
    <div
      style={{
        background: toast.tone === "success" ? "rgba(34, 197, 94, 0.16)" : "rgba(239, 68, 68, 0.16)",
        border: `1px solid ${toast.tone === "success" ? "rgba(34, 197, 94, 0.2)" : "rgba(239, 68, 68, 0.22)"}`,
        borderRadius: "16px",
        bottom: "24px",
        boxShadow: "0 18px 40px rgba(2, 6, 23, 0.32)",
        color: "#e5edf8",
        padding: "14px 16px",
        position: "fixed",
        right: "24px",
        zIndex: 100,
      }}
    >
      {toast.message}
    </div>
  );
}

function ConfirmModal({ open, machineIdentifier, onCancel, onConfirm }) {
  if (!open) {
    return null;
  }

  return (
    <div
      style={{
        alignItems: "center",
        background: "rgba(2, 6, 23, 0.74)",
        display: "flex",
        inset: 0,
        justifyContent: "center",
        padding: "24px",
        position: "fixed",
        zIndex: 90,
      }}
    >
      <div className="surface-card section-card" style={{ maxWidth: "460px", width: "100%" }}>
        <div>
          <div className="card-label">Confirm Action</div>
          <h2 className="section-title" style={{ marginTop: "10px" }}>
            Unclaim {machineIdentifier}?
          </h2>
        </div>
        <div className="page-subtitle">
          This removes the machine from your dashboard until you claim it again later.
        </div>
        <div className="detail-actions" style={{ justifyContent: "flex-end" }}>
          <button type="button" className="ghost-button" onClick={onCancel}>
            Cancel
          </button>
          <button
            type="button"
            className="secondary-button"
            style={{ borderColor: "rgba(239, 68, 68, 0.2)", color: "#fecaca" }}
            onClick={onConfirm}
          >
            Unclaim Machine
          </button>
        </div>
      </div>
    </div>
  );
}

function Dashboard({ onOpenIncident = () => {} }) {
  const { authEnabled, user, logout } = useAuth();
  const [activePage, setActivePage] = useState("overview");
  const [machines, setMachines] = useState([]);
  const [availableMachines, setAvailableMachines] = useState([]);
  const [selectedMachineIdentifier, setSelectedMachineIdentifier] = useState("");
  const [latestTelemetry, setLatestTelemetry] = useState(null);
  const [telemetryHistory, setTelemetryHistory] = useState([]);
  const [alerts, setAlerts] = useState([]);
  const [anomalies, setAnomalies] = useState([]);
  const [recentInvestigations, setRecentInvestigations] = useState([]);
  const [insightSource, setInsightSource] = useState("backend");
  const [selectedEventKey, setSelectedEventKey] = useState("");
  const [chatTriggerMessage, setChatTriggerMessage] = useState(null);
  const [telemetryLoading, setTelemetryLoading] = useState(true);
  const [alertsLoading, setAlertsLoading] = useState(true);
  const [anomaliesLoading, setAnomaliesLoading] = useState(true);
  const [investigationsLoading, setInvestigationsLoading] = useState(true);
  const [insightLoading, setInsightLoading] = useState(true);
  const [telemetryError, setTelemetryError] = useState("");
  const [alertsError, setAlertsError] = useState("");
  const [anomaliesError, setAnomaliesError] = useState("");
  const [investigationsError, setInvestigationsError] = useState("");
  const [insightError, setInsightError] = useState("");
  const [claimError, setClaimError] = useState("");
  const [toast, setToast] = useState(null);
  const [unclaimTarget, setUnclaimTarget] = useState("");
  const [socketStatus, setSocketStatus] = useState("connecting");
  const [selectedMetrics, setSelectedMetrics] = useState(["cpuUsage", "memoryUsage", "diskUsage"]);
  const [eventVisibility, setEventVisibility] = useState({
    alerts: true,
    anomalies: true,
    resolvedAlerts: true,
  });
  const [severityFilter, setSeverityFilter] = useState("ALL");
  const [statusFilter, setStatusFilter] = useState("ALL");
  const [machineFilter, setMachineFilter] = useState("ALL");

  const selectedMachine = useMemo(
    () => machines.find((machine) => machine.machineIdentifier === selectedMachineIdentifier) || null,
    [machines, selectedMachineIdentifier]
  );

  const filteredAlerts = useMemo(() => {
    if (!selectedMachineIdentifier) {
      return alerts;
    }
    return alerts.filter((alert) => alert?.machineIdentifier === selectedMachineIdentifier);
  }, [alerts, selectedMachineIdentifier]);

  const filteredAnomalies = useMemo(() => {
    if (!selectedMachineIdentifier) {
      return anomalies;
    }
    return anomalies.filter((anomaly) => anomaly?.machineIdentifier === selectedMachineIdentifier);
  }, [anomalies, selectedMachineIdentifier]);

  const insightModel = useMemo(
    () => buildInsightModel(latestTelemetry, filteredAlerts, filteredAnomalies),
    [latestTelemetry, filteredAlerts, filteredAnomalies]
  );

  const machineStatusSummary = useMemo(() => {
    const onlineMachines = machines.filter((machine) => resolveMachineStatus(machine).label === "ONLINE").length;
    return { total: machines.length, online: onlineMachines };
  }, [machines]);

  const activeAlertsCount = useMemo(
    () => filteredAlerts.filter((alert) => alert?.status === "ACTIVE").length,
    [filteredAlerts]
  );

  const filteredAlertsTable = useMemo(() => {
    return alerts.filter((alert) => {
      const matchesSeverity = severityFilter === "ALL" || String(alert?.severity || "").toUpperCase() === severityFilter;
      const matchesStatus = statusFilter === "ALL" || String(alert?.status || "").toUpperCase() === statusFilter;
      const matchesMachine = machineFilter === "ALL" || alert?.machineIdentifier === machineFilter;
      return matchesSeverity && matchesStatus && matchesMachine;
    });
  }, [alerts, machineFilter, severityFilter, statusFilter]);

  useEffect(() => {
    if (!toast) {
      return undefined;
    }

    const timeoutId = window.setTimeout(() => setToast(null), 2600);
    return () => window.clearTimeout(timeoutId);
  }, [toast]);

  const refreshMachines = async (isMountedRef) => {
    try {
      const machinesResponse = await getMachines();
      if (!isMountedRef.current) {
        return;
      }
      setMachines(machinesResponse);
      if (
        selectedMachineIdentifier &&
        !machinesResponse.some((machine) => machine.machineIdentifier === selectedMachineIdentifier)
      ) {
        setSelectedMachineIdentifier("");
      }
    } catch (error) {
      if (isMountedRef.current) {
        console.error(error?.message || "Unable to load machines.");
      }
    }
  };

  const refreshAvailableMachines = async (isMountedRef) => {
    if (!authEnabled) {
      if (isMountedRef.current) {
        setAvailableMachines([]);
      }
      return;
    }

    try {
      const machinesResponse = await getAvailableMachines();
      if (!isMountedRef.current) {
        return;
      }
      setAvailableMachines(machinesResponse);
    } catch (error) {
      if (isMountedRef.current) {
        console.error(error?.message || "Unable to load available machines.");
      }
    }
  };

  const refreshTelemetry = async (isMountedRef, machineIdentifier = selectedMachineIdentifier) => {
    setTelemetryLoading(true);
    try {
      const telemetryResponse = await getTelemetrySnapshots(machineIdentifier);
      if (!isMountedRef.current) {
        return;
      }
      const recentTelemetry = telemetryResponse.slice(0, 50);
      setTelemetryHistory(recentTelemetry);
      setLatestTelemetry(getLatestSnapshot(recentTelemetry));
      setTelemetryError("");
    } catch (error) {
      if (!isMountedRef.current) {
        return;
      }
      const message = error?.response?.data?.message || error?.message || "Unable to load telemetry data.";
      setTelemetryError("Telemetry stream is unavailable.");
      console.error(message);
    } finally {
      if (isMountedRef.current) {
        setTelemetryLoading(false);
      }
    }
  };

  const refreshAlerts = async (isMountedRef, machineIdentifier = selectedMachineIdentifier) => {
    setAlertsLoading(true);
    try {
      const alertsResponse = await getAlerts(machineIdentifier);
      if (!isMountedRef.current) {
        return;
      }
      setAlerts(alertsResponse);
      setAlertsError("");
    } catch (error) {
      if (!isMountedRef.current) {
        return;
      }
      const message = error?.response?.data?.message || error?.message || "Unable to load alert data.";
      setAlertsError("Alert service is unavailable.");
      console.error(message);
    } finally {
      if (isMountedRef.current) {
        setAlertsLoading(false);
      }
    }
  };

  const refreshAnomalies = async (isMountedRef, machineIdentifier = selectedMachineIdentifier) => {
    setAnomaliesLoading(true);
    try {
      const anomaliesResponse = await getAnomalies(machineIdentifier);
      if (!isMountedRef.current) {
        return;
      }
      setAnomalies(anomaliesResponse);
      setAnomaliesError("");
    } catch (error) {
      if (!isMountedRef.current) {
        return;
      }
      const message = error?.response?.data?.message || error?.message || "Unable to load anomaly data.";
      setAnomaliesError("AI anomaly service is unavailable.");
      console.error(message);
    } finally {
      if (isMountedRef.current) {
        setAnomaliesLoading(false);
      }
    }
  };

  const refreshInsight = async (isMountedRef, machineIdentifier = selectedMachineIdentifier) => {
    setInsightLoading(true);
    try {
      const nextInsight = await getInsight(machineIdentifier);
      if (!isMountedRef.current) {
        return;
      }

      if (nextInsight) {
        setInsightSource("backend");
        setInsightError("");
      } else {
        setInsightSource("fallback");
        setInsightError("AI summary fell back to local insight.");
      }
    } catch {
      if (!isMountedRef.current) {
        return;
      }
      setInsightSource("fallback");
      setInsightError("AI summary fell back to local insight.");
    } finally {
      if (isMountedRef.current) {
        setInsightLoading(false);
      }
    }
  };

  const refreshInvestigations = async (isMountedRef, machineIdentifier = selectedMachineIdentifier) => {
    setInvestigationsLoading(true);
    try {
      const investigationsResponse = await getRecentInvestigations(machineIdentifier);
      if (!isMountedRef.current) {
        return;
      }
      setRecentInvestigations(investigationsResponse);
      setInvestigationsError("");
    } catch (error) {
      if (!isMountedRef.current) {
        return;
      }
      const message = error?.response?.data?.message || error?.message || "Unable to load recent incidents.";
      setInvestigationsError("Unable to load recent incidents");
      console.error(message);
    } finally {
      if (isMountedRef.current) {
        setInvestigationsLoading(false);
      }
    }
  };

  const openAssistantWithMessage = (message) => {
    setActivePage("ai");
    setChatTriggerMessage({
      id: `trigger-${Date.now()}`,
      message,
    });
  };

  const handleExplainSpike = ({ timestamp, metric, value, source, eventKey }) => {
    const resolvedEventKey = eventKey || `${source}-${metric}-${timestamp}`;
    const numericValue = Number(value);
    const valueLabel = Number.isFinite(numericValue) ? numericValue.toFixed(1) : "elevated";
    setSelectedEventKey(resolvedEventKey);
    openAssistantWithMessage(
      `Explain this ${metric} signal at ${timestamp} with value ${valueLabel}${selectedMachineIdentifier ? ` for ${selectedMachineIdentifier}` : ""}.`
    );
  };

  const handleInvestigateAnomaly = (anomaly) => {
    if (!anomaly) {
      return;
    }

    const metricValue = Number(anomaly.metricValue);
    const scoreValue = Number(anomaly.anomalyScore ?? anomaly.zScore);
    const formattedValue = Number.isFinite(metricValue) ? `${metricValue.toFixed(1)}%` : "Unknown";
    const formattedScore = Number.isFinite(scoreValue) ? scoreValue.toFixed(2) : "Unknown";
    const timestamp = anomaly.detectedAt || anomaly.createdAt || anomaly.timestamp || "Unknown";

    openAssistantWithMessage(
      `Incident Context:\n\n* Machine: ${anomaly.machineIdentifier || selectedMachineIdentifier || "Unknown"}\n* Metric: ${anomaly.metricType || anomaly.eventType || "Unknown"}\n* Value: ${formattedValue}\n* Z-score: ${formattedScore}\n* Severity: ${anomaly.severity || "UNKNOWN"}\n* Timestamp: ${timestamp}\n* Message: ${anomaly.explanation || anomaly.message || "Potential anomaly detected."}\n\nUser Request:\nHelp me investigate this anomaly and decide whether it needs action.`
    );
  };

  const handleInvestigateWithAi = () => {
    const machineLabel = selectedMachineIdentifier || latestTelemetry?.machineIdentifier || "the current system";
    const topProcessLabel = insightModel.topProcessSummary;
    openAssistantWithMessage(
      `Analyze current system state for ${machineLabel}. CPU: ${insightModel.cpuUsage.toFixed(1)}%, Memory: ${insightModel.memoryUsage.toFixed(1)}%, Alerts: ${insightModel.activeAlertsCount}, Anomalies: ${insightModel.anomalyCount}, Top process: ${topProcessLabel}. Explain root cause and what to fix first.`
    );
  };

  const handleAskAboutTopIssue = () => {
    openAssistantWithMessage(
      `${selectedMachineIdentifier ? `For ${selectedMachineIdentifier}, ` : ""}why is ${insightModel.topIssue.toLowerCase()} happening?`
    );
  };

  const handleAskAboutTopProcess = () => {
    if (!insightModel.topProcess?.processName) {
      return;
    }
    openAssistantWithMessage(
      `${selectedMachineIdentifier ? `For ${selectedMachineIdentifier}, ` : ""}why is ${insightModel.topProcess.processName} using ${insightModel.topProcess.cpuPercent?.toFixed?.(1) ?? "high"}% CPU?`
    );
  };

  const handleSelectIncident = (incident) => {
    if (!incident) {
      return;
    }
    onOpenIncident(incident);
  };

  const handleChatMessage = async (message) => {
    try {
      const response = await sendChatMessage(message, selectedMachineIdentifier);
      return response || "I don’t have a confident answer yet, but the live metrics and alerts still reflect the current system state.";
    } catch {
      const fallback = buildInsightModel(latestTelemetry, filteredAlerts, filteredAnomalies);
      return `AI is unavailable right now. Current status: ${fallback.status}. Top issue: ${fallback.topIssue}. Top process: ${fallback.topProcessSummary}.`;
    }
  };

  const handleClaimMachine = async (machineIdentifier) => {
    setClaimError("");
    try {
      await claimMachine(machineIdentifier);
      setSelectedMachineIdentifier(machineIdentifier);
      setToast({ tone: "success", message: "Machine claimed successfully" });
      const mounted = { current: true };
      await Promise.all([
        refreshMachines(mounted),
        refreshAvailableMachines(mounted),
        refreshTelemetry(mounted, machineIdentifier),
        refreshAlerts(mounted, machineIdentifier),
        refreshAnomalies(mounted, machineIdentifier),
        refreshInvestigations(mounted, machineIdentifier),
        refreshInsight(mounted, machineIdentifier),
      ]);
    } catch (error) {
      setClaimError(error?.response?.data?.message || "Unable to claim this machine right now.");
    }
  };

  const handleUnclaimMachine = async () => {
    const machineIdentifier = unclaimTarget;
    setUnclaimTarget("");
    if (!machineIdentifier) {
      return;
    }

    try {
      await unclaimMachine(machineIdentifier);
      setToast({ tone: "success", message: "Machine unclaimed successfully" });
      if (selectedMachineIdentifier === machineIdentifier) {
        setSelectedMachineIdentifier("");
      }
      const mounted = { current: true };
      await Promise.all([
        refreshMachines(mounted),
        refreshAvailableMachines(mounted),
        refreshTelemetry(mounted, ""),
        refreshAlerts(mounted, ""),
        refreshAnomalies(mounted, ""),
        refreshInvestigations(mounted, ""),
        refreshInsight(mounted, ""),
      ]);
    } catch (error) {
      setClaimError(error?.response?.data?.message || "Unable to unclaim this machine right now.");
      setToast({ tone: "error", message: "Unable to unclaim machine" });
    }
  };

  const handleRetryInsight = () => {
    const mounted = { current: true };
    refreshInsight(mounted, selectedMachineIdentifier);
  };

  const toggleMetric = (metricKey) => {
    setSelectedMetrics((existing) => {
      if (existing.includes(metricKey)) {
        return existing.length === 1 ? existing : existing.filter((metric) => metric !== metricKey);
      }
      return [...existing, metricKey];
    });
  };

  const toggleEventVisibility = (key) => {
    setEventVisibility((existing) => ({
      ...existing,
      [key]: !existing[key],
    }));
  };

  useEffect(() => {
    const isMountedRef = { current: true };
    const bootstrap = async () => {
      await Promise.all([
        refreshTelemetry(isMountedRef, selectedMachineIdentifier),
        refreshMachines(isMountedRef),
        refreshAvailableMachines(isMountedRef),
        refreshAlerts(isMountedRef, selectedMachineIdentifier),
        refreshAnomalies(isMountedRef, selectedMachineIdentifier),
        refreshInvestigations(isMountedRef, selectedMachineIdentifier),
        refreshInsight(isMountedRef, selectedMachineIdentifier),
      ]);
    };

    bootstrap();

    const telemetrySocket = createTelemetrySocket({
      onConnect: () => {
        if (isMountedRef.current) {
          setSocketStatus("connected");
          setTelemetryError("");
        }
      },
      onDisconnect: () => {
        if (isMountedRef.current) {
          setSocketStatus("reconnecting");
        }
      },
      onError: () => {
        if (isMountedRef.current) {
          setSocketStatus("reconnecting");
        }
      },
      onTelemetry: (snapshot) => {
        if (!isMountedRef.current) {
          return;
        }

        if (selectedMachineIdentifier && snapshot.machineIdentifier !== selectedMachineIdentifier) {
          refreshMachines(isMountedRef);
          refreshAvailableMachines(isMountedRef);
          return;
        }

        setTelemetryHistory((existingSnapshots) => {
          const nextHistory = mergeSnapshotIntoHistory(existingSnapshots, snapshot);
          setLatestTelemetry(nextHistory[0] || null);
          return nextHistory;
        });
        refreshMachines(isMountedRef);
        refreshAvailableMachines(isMountedRef);
        refreshAlerts(isMountedRef, selectedMachineIdentifier);
        refreshAnomalies(isMountedRef, selectedMachineIdentifier);
        refreshInvestigations(isMountedRef, selectedMachineIdentifier);
        refreshInsight(isMountedRef, selectedMachineIdentifier);
      },
    });

    telemetrySocket.connect();

    return () => {
      isMountedRef.current = false;
      telemetrySocket.disconnect();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedMachineIdentifier, authEnabled]);

  useEffect(() => {
    const isMountedRef = { current: true };
    const intervalId = window.setInterval(() => {
      refreshMachines(isMountedRef);
      refreshAvailableMachines(isMountedRef);
      refreshTelemetry(isMountedRef, selectedMachineIdentifier);
      refreshAlerts(isMountedRef, selectedMachineIdentifier);
      refreshAnomalies(isMountedRef, selectedMachineIdentifier);
      refreshInvestigations(isMountedRef, selectedMachineIdentifier);
      refreshInsight(isMountedRef, selectedMachineIdentifier);
    }, 5000);

    return () => {
      isMountedRef.current = false;
      window.clearInterval(intervalId);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedMachineIdentifier, authEnabled]);

  const showSingleClaimSuggestion =
    authEnabled && machines.length === 0 && availableMachines.length === 1;

  const kpiCards = [
    {
      label: "CPU Usage",
      value: telemetryLoading ? "Loading" : formatPercent(latestTelemetry?.cpuUsage),
      tone: getUsageTone(latestTelemetry?.cpuUsage),
      subtext: latestTelemetry ? `Top issue: ${insightModel.topIssue}` : "Live system load",
      points: telemetryHistory
        .slice()
        .reverse()
        .map((snapshot) => Number(snapshot.cpuUsage))
        .filter(Number.isFinite),
    },
    {
      label: "Memory Usage",
      value: telemetryLoading ? "Loading" : formatPercent(latestTelemetry?.memoryUsage),
      tone: getUsageTone(latestTelemetry?.memoryUsage),
      subtext: latestTelemetry ? `${filteredAnomalies.length} anomalies in scope` : "Memory pressure",
      points: telemetryHistory
        .slice()
        .reverse()
        .map((snapshot) => Number(snapshot.memoryUsage))
        .filter(Number.isFinite),
    },
    {
      label: "Disk Usage",
      value: telemetryLoading ? "Loading" : formatPercent(latestTelemetry?.diskUsage),
      tone: getUsageTone(latestTelemetry?.diskUsage),
      subtext: selectedMachineIdentifier || "All machines",
      points: telemetryHistory
        .slice()
        .reverse()
        .map((snapshot) => Number(snapshot.diskUsage))
        .filter(Number.isFinite),
    },
    {
      label: "Active Alerts",
      value: alertsLoading ? "Loading" : formatCount(activeAlertsCount),
      tone: activeAlertsCount > 0 ? (activeAlertsCount > 3 ? "red" : "yellow") : "green",
      subtext: `${machineStatusSummary.online}/${machineStatusSummary.total} machines online`,
      points: filteredAlerts
        .slice(0, 12)
        .reverse()
        .map((alert, index) => (alert?.status === "ACTIVE" ? Math.min(index + 1, 8) : 0)),
    },
  ];

  const serviceNotices = [telemetryError, alertsError, anomaliesError].filter(Boolean);

  const overviewPage = (
    <div className="content-page">
      <header className="page-header">
        <div className="page-title-group">
          <div className="eyebrow">Overview Dashboard</div>
          <h1 className="page-title">System Overview</h1>
          <p className="page-subtitle">Real-time system health summary across telemetry, alerts, anomalies, and AI investigation context.</p>
        </div>
        <div className="page-actions">
          {user ? <TopStatus label="Operator" value={user.displayName || user.email} tone="blue" /> : null}
          <TopStatus
            label="Connection"
            value={socketStatus === "connected" ? "Live telemetry connected" : "Reconnecting"}
            tone={socketStatus === "connected" ? "green" : "yellow"}
          />
        </div>
      </header>

      {showSingleClaimSuggestion ? (
        <div className="inline-notice">
          <span>One machine is ready to claim and start monitoring.</span>
          <button
            type="button"
            className="action-button"
            onClick={() => handleClaimMachine(availableMachines[0].machineIdentifier)}
          >
            Claim Machine
          </button>
        </div>
      ) : null}

      {serviceNotices.length > 0 ? (
        <div className="inline-notice warning">
          <span>{serviceNotices.join(" ")}</span>
        </div>
      ) : null}

      <div className="kpi-grid">
        {kpiCards.map((card) => (
          <KpiCard key={card.label} {...card} />
        ))}
      </div>

      <div className="dashboard-grid">
        <TelemetryTrendChart
          telemetryHistory={telemetryHistory}
          alerts={filteredAlerts}
          anomalies={filteredAnomalies}
          anomaliesLoading={anomaliesLoading}
          anomaliesError={anomaliesError}
          loading={telemetryLoading}
          onExplainSpike={handleExplainSpike}
          onToggleEventVisibility={toggleEventVisibility}
          onToggleMetric={toggleMetric}
          eventVisibility={eventVisibility}
          selectedEventKey={selectedEventKey}
          selectedMetrics={selectedMetrics}
        />

        <div className="stack-column">
          <RecentIncidentsPanel
            investigations={recentInvestigations}
            loading={investigationsLoading}
            error={investigationsError}
            onSelectIncident={handleSelectIncident}
          />

          <LatestAnomaliesPanel
            anomalies={filteredAnomalies}
            loading={anomaliesLoading}
            error={anomaliesError}
            onInvestigate={handleInvestigateAnomaly}
          />

          <AiInsightPanel
            latestTelemetry={latestTelemetry}
            alerts={filteredAlerts}
            anomalies={filteredAnomalies}
            insightSource={insightSource}
            insightLoading={insightLoading}
            insightError={insightError}
            onRetryInsight={handleRetryInsight}
            onInvestigateWithAi={handleInvestigateWithAi}
            onAskAboutTopIssue={handleAskAboutTopIssue}
            onAskAboutTopProcess={handleAskAboutTopProcess}
          />

          <section className="surface-card section-card">
            <div>
              <div className="card-label">Quick Actions</div>
              <h2 className="section-title">Next Steps</h2>
            </div>
            <div className="quick-actions-grid">
              <button type="button" className="quick-action-button" onClick={handleInvestigateWithAi}>
                <div className="quick-action-copy">
                  <span className="quick-action-title">Investigate with AI</span>
                  <span className="quick-action-subtitle">Open the assistant with the current incident context</span>
                </div>
                <span>→</span>
              </button>
              <button type="button" className="quick-action-button" onClick={() => setActivePage("alerts")}>
                <div className="quick-action-copy">
                  <span className="quick-action-title">View Alerts</span>
                  <span className="quick-action-subtitle">Jump to the alert table and triage queue</span>
                </div>
                <span>→</span>
              </button>
              <button type="button" className="quick-action-button" onClick={() => setActivePage("machines")}>
                <div className="quick-action-copy">
                  <span className="quick-action-title">View Machines</span>
                  <span className="quick-action-subtitle">Inspect machine ownership, availability, and last seen status</span>
                </div>
                <span>→</span>
              </button>
            </div>
          </section>
        </div>
      </div>
    </div>
  );

  const machinesPage = (
    <div className="content-page">
      <header className="page-header">
        <div className="page-title-group">
          <div className="eyebrow">Fleet View</div>
          <h1 className="page-title">Machines</h1>
          <p className="page-subtitle">Browse monitored machines, claim devices, and open a focused system view without overloading the main dashboard.</p>
        </div>
      </header>

      {claimError ? <div className="inline-notice error"><span>{claimError}</span></div> : null}

      <div className="machine-toolbar">
        <div className="status-pill blue">
          <span className="status-dot blue" />
          {machines.length} tracked machines
        </div>
        <div className="status-pill green">
          <span className="status-dot green" />
          {machineStatusSummary.online} online now
        </div>
      </div>

      <div className="machine-grid">
        <button
          type="button"
          className={`machine-card ${selectedMachineIdentifier ? "" : "is-selected"}`}
          onClick={() => {
            setSelectedMachineIdentifier("");
            setSelectedEventKey("");
          }}
        >
          <div className="machine-card-top">
            <div>
              <div className="machine-card-name">All Machines</div>
              <div className="machine-card-identifier">Combined live system scope</div>
            </div>
            <span className="status-pill blue">
              <span className="status-dot blue" />
              Combined
            </span>
          </div>
          <div className="machine-card-subtle">Use this scope for a fleet-wide overview and shared alerts.</div>
        </button>

        {machines.map((machine) => {
          const status = resolveMachineStatus(machine);
          const ownership = resolveOwnership(machine, user?.userId);
          const canUnclaim = authEnabled && machine.owned && machine.ownerUserId === user?.userId;
          const isSelected = machine.machineIdentifier === selectedMachineIdentifier;

          return (
            <div key={machine.machineIdentifier} className={`machine-card ${isSelected ? "is-selected" : ""}`}>
              <button
                type="button"
                onClick={() => {
                  setSelectedMachineIdentifier(machine.machineIdentifier);
                  setSelectedEventKey("");
                }}
                style={{ background: "transparent", border: "none", color: "inherit", cursor: "pointer", padding: 0, textAlign: "left" }}
              >
                <div className="machine-card-top">
                  <div>
                    <div className="machine-card-name">{machine.hostname || machine.machineIdentifier}</div>
                    <div className="machine-card-identifier">{machine.machineIdentifier}</div>
                  </div>
                  <span className={`status-pill ${status.tone}`}>
                    <span className={`status-dot ${status.tone}`} />
                    {status.label}
                  </span>
                </div>
                <div className="machine-card-tags">
                  <span className={`tag`}>{ownership.label}</span>
                  <span className="tag">Last seen {formatRelativeLastSeen(machine.lastSeenAt)}</span>
                </div>
              </button>
              {canUnclaim ? (
                <button type="button" className="ghost-button" onClick={() => setUnclaimTarget(machine.machineIdentifier)}>
                  Unclaim Machine
                </button>
              ) : null}
            </div>
          );
        })}

        {authEnabled
          ? availableMachines.map((machine) => (
              <div key={`available-${machine.machineIdentifier}`} className="machine-card">
                <div className="machine-card-top">
                  <div>
                    <div className="machine-card-name">{machine.hostname || machine.machineIdentifier}</div>
                    <div className="machine-card-identifier">{machine.machineIdentifier}</div>
                  </div>
                  <span className="status-pill blue">
                    <span className="status-dot blue" />
                    Claimable
                  </span>
                </div>
                <div className="machine-card-subtle">Available to add to your dashboard.</div>
                <button type="button" className="action-button" onClick={() => handleClaimMachine(machine.machineIdentifier)}>
                  Claim Machine
                </button>
              </div>
            ))
          : null}
      </div>

      <section className="surface-card section-card">
        <div className="section-header">
          <div>
            <div className="card-label">Machine Detail</div>
            <h2 className="section-title">{selectedMachineIdentifier || "Fleet Summary"}</h2>
          </div>
          <div className="machine-card-subtle">{selectedMachine ? formatTimestamp(selectedMachine.lastSeenAt) : "All machine scope"}</div>
        </div>

        <div className="detail-grid">
          <div className="detail-stat">
            <div className="detail-label">Status</div>
            <div className="detail-value">{selectedMachine ? resolveMachineStatus(selectedMachine).label : `${machineStatusSummary.online} online`}</div>
          </div>
          <div className="detail-stat">
            <div className="detail-label">Ownership</div>
            <div className="detail-value">{selectedMachine ? resolveOwnership(selectedMachine, user?.userId).label : "All owners"}</div>
          </div>
          <div className="detail-stat">
            <div className="detail-label">Top Process</div>
            <div className="detail-value">{insightModel.topProcessSummary}</div>
          </div>
          <div className="detail-stat">
            <div className="detail-label">Last Telemetry</div>
            <div className="detail-value">{latestTelemetry ? formatTimestamp(latestTelemetry.timestamp || latestTelemetry.createdAt) : "No data"}</div>
          </div>
        </div>

        <div className="detail-grid">
          <div className="detail-stat">
            <div className="detail-label">CPU</div>
            <div className="detail-value">{formatPercent(latestTelemetry?.cpuUsage)}</div>
          </div>
          <div className="detail-stat">
            <div className="detail-label">Memory</div>
            <div className="detail-value">{formatPercent(latestTelemetry?.memoryUsage)}</div>
          </div>
          <div className="detail-stat">
            <div className="detail-label">Disk</div>
            <div className="detail-value">{formatPercent(latestTelemetry?.diskUsage)}</div>
          </div>
          <div className="detail-stat">
            <div className="detail-label">Active Alerts</div>
            <div className="detail-value">{formatCount(activeAlertsCount)}</div>
          </div>
        </div>

        <div className="detail-actions">
          <button type="button" className="action-button" onClick={handleInvestigateWithAi}>
            Investigate with AI
          </button>
          <button type="button" className="ghost-button" onClick={() => setActivePage("overview")}>
            Back to Overview
          </button>
        </div>
      </section>
    </div>
  );

  const alertsPage = (
    <div className="content-page">
      <header className="page-header">
        <div className="page-title-group">
          <div className="eyebrow">Alert Workflow</div>
          <h1 className="page-title">Alerts</h1>
          <p className="page-subtitle">Use filters to narrow severity, machine, and status without burying operators in a crowded layout.</p>
        </div>
      </header>

      <div className="alerts-toolbar">
        <SelectFilter label="Severity" options={SEVERITY_FILTERS} value={severityFilter} onChange={setSeverityFilter} />
        <SelectFilter label="Machine" options={["ALL", ...machines.map((machine) => machine.machineIdentifier)]} value={machineFilter} onChange={setMachineFilter} />
        <SelectFilter label="Status" options={STATUS_FILTERS} value={statusFilter} onChange={setStatusFilter} />
      </div>

      {alertsError ? <div className="inline-notice warning"><span>{alertsError}</span></div> : null}

      <section className="surface-card section-card">
        <div className="section-header">
          <div>
            <div className="card-label">Alert Table</div>
            <h2 className="section-title">Active and Resolved Events</h2>
          </div>
          <div className="machine-card-subtle">{alertsLoading ? "Refreshing…" : `${filteredAlertsTable.length} visible alerts`}</div>
        </div>

        {alertsLoading ? (
          <div className="skeleton-block" />
        ) : filteredAlertsTable.length === 0 ? (
          <div className="empty-state">No alerts match the current filters.</div>
        ) : (
          <div className="alerts-table-wrapper">
            <table className="alerts-table">
              <thead>
                <tr>
                  <th>Alert Type</th>
                  <th>Severity</th>
                  <th>Machine</th>
                  <th>Status</th>
                  <th>Timestamp</th>
                </tr>
              </thead>
              <tbody>
                {filteredAlertsTable.map((alert) => {
                  const severity = String(alert?.severity || "UNKNOWN").toUpperCase();
                  const status = String(alert?.status || "UNKNOWN").toUpperCase();
                  const tone =
                    severity === "CRITICAL" || severity === "HIGH"
                      ? "red"
                      : severity === "MEDIUM"
                        ? "yellow"
                        : "green";

                  return (
                    <tr key={alert.id || `${alert.alertType}-${alert.createdAt}`}>
                      <td>
                        <div className="severity-label">{alert.alertType || alert.eventType || "Unknown"}</div>
                      </td>
                      <td>
                        <span className={`status-pill ${tone}`}>
                          <span className={`status-dot ${tone}`} />
                          {severity}
                        </span>
                      </td>
                      <td>
                        <div className="machine-name-inline">{alert.machineIdentifier || "Unknown machine"}</div>
                      </td>
                      <td>
                        <span className={`status-pill ${status === "ACTIVE" ? "red" : "green"}`}>
                          <span className={`status-dot ${status === "ACTIVE" ? "red" : "green"}`} />
                          {status}
                        </span>
                      </td>
                      <td>
                        <div>{formatTimestamp(alert.createdAt || alert.timestamp || alert.resolvedAt)}</div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  );

  const aiContextPanel = (
    <>
      <div>
        <div className="card-label">System Context</div>
        <h2 className="section-title">Current Scope</h2>
      </div>
      <div className="context-grid">
        <div className="context-row">
          <span className="context-label">Scope</span>
          <span className="context-value">{selectedMachineIdentifier || "All machines"}</span>
        </div>
        <div className="context-row">
          <span className="context-label">Status</span>
          <span className="context-value">{insightModel.status}</span>
        </div>
        <div className="context-row">
          <span className="context-label">Top Issue</span>
          <span className="context-value">{insightModel.topIssue}</span>
        </div>
        <div className="context-row">
          <span className="context-label">Active Alerts</span>
          <span className="context-value">{formatCount(activeAlertsCount)}</span>
        </div>
        <div className="context-row">
          <span className="context-label">Anomalies</span>
          <span className="context-value">{formatCount(filteredAnomalies.length)}</span>
        </div>
        <div className="context-row">
          <span className="context-label">Top Process</span>
          <span className="context-value">{insightModel.topProcessSummary}</span>
        </div>
        <div className="context-row">
          <span className="context-label">Latest Telemetry</span>
          <span className="context-value">{latestTelemetry ? formatTimestamp(latestTelemetry.timestamp || latestTelemetry.createdAt) : "No data"}</span>
        </div>
      </div>
      <div className="detail-actions">
        <button type="button" className="ghost-button" onClick={handleInvestigateWithAi}>
          Refresh Analysis
        </button>
        <button type="button" className="ghost-button" onClick={() => setActivePage("overview")}>
          Return to Overview
        </button>
      </div>
    </>
  );

  const aiPage = (
    <div className="content-page assistant-page">
      <header className="page-header">
        <div className="page-title-group">
          <div className="eyebrow">AI Workspace</div>
          <h1 className="page-title">AI Assistant</h1>
          <p className="page-subtitle">A focused chat interface for investigation, with live system context visible alongside the conversation.</p>
        </div>
      </header>

      <ChatAssistant
        onSendMessage={handleChatMessage}
        triggerMessage={chatTriggerMessage}
        variant="page"
        title="AI Assistant"
        subtitle="Investigate incidents with current system context"
        contextPanel={aiContextPanel}
      />
    </div>
  );

  const pageContent = {
    overview: overviewPage,
    machines: machinesPage,
    alerts: alertsPage,
    ai: aiPage,
  }[activePage];

  return (
    <>
      <div className="dashboard-shell">
        <aside className="dashboard-sidebar">
          <div className="brand-block">
            <span className="brand-badge">LabWatch Platform</span>
            <h1 className="brand-title">Monitoring Control Plane</h1>
            <p className="brand-copy">A calm, production-style workspace for telemetry, alerting, and AI-assisted investigation.</p>
          </div>

          <nav className="sidebar-nav" aria-label="Dashboard pages">
            {NAV_ITEMS.map((item) => (
              <button
                key={item.id}
                type="button"
                className={`sidebar-link ${activePage === item.id ? "is-active" : ""}`}
                onClick={() => setActivePage(item.id)}
              >
                <span className="sidebar-link-icon">{item.icon}</span>
                <span className="sidebar-link-copy">
                  <span className="sidebar-link-title">{item.label}</span>
                  <span className="sidebar-link-subtitle">{item.subtitle}</span>
                </span>
              </button>
            ))}
          </nav>

          <div className="sidebar-footer">
            <div className="sidebar-profile">
              <div className="profile-row">
                <div className="profile-avatar">{getInitials(user?.displayName || user?.email || "LabWatch")}</div>
                <div className="profile-meta">
                  <span className="profile-name">{user?.displayName || "Guest Operator"}</span>
                  <span className="profile-email">{user?.email || "Authentication disabled"}</span>
                </div>
              </div>
              <div className="profile-row" style={{ justifyContent: "space-between" }}>
                <span className={`status-pill ${socketStatus === "connected" ? "green" : "yellow"}`}>
                  <span className={`status-dot ${socketStatus === "connected" ? "green" : "yellow"}`} />
                  {socketStatus}
                </span>
                {authEnabled && user ? (
                  <button type="button" className="ghost-button" onClick={logout}>
                    Logout
                  </button>
                ) : null}
              </div>
            </div>
          </div>
        </aside>

        <main className="dashboard-main">{pageContent}</main>
      </div>

      <Toast toast={toast} />
      <ConfirmModal
        open={Boolean(unclaimTarget)}
        machineIdentifier={unclaimTarget}
        onCancel={() => setUnclaimTarget("")}
        onConfirm={handleUnclaimMachine}
      />
    </>
  );
}

export default Dashboard;
