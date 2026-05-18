import { useMemo } from "react";
import { useNavigate } from "react-router-dom";
import AiInsightPanel from "../components/AiInsightPanel";
import CompactActivityFeed from "../components/CompactActivityFeed";
import DashboardTelemetryPanel from "../components/DashboardTelemetryPanel";
import LatestAnomaliesPanel from "../components/LatestAnomaliesPanel";
import RecentIncidentsPanel from "../components/RecentIncidentsPanel";
import { useOperationsData } from "../hooks/useOperationsData";
import { buildInsightModel } from "../components/aiInsightModel";
import { normalizeAnomaly } from "../components/telemetryChartUtils";
import {
  formatCount,
  formatRelativeTimestamp,
  formatTimestamp,
  getAlertTimestamp,
  getSeverityTone,
  resolveMachineStatus,
  truncate,
} from "../utils/operations";

const INCIDENT_PREVIEW_LIMIT = 4;
const ANOMALY_PREVIEW_LIMIT = 4;
const ACTIVITY_PREVIEW_LIMIT = 4;

function StatCard({ label, value, tone = "blue", subtext }) {
  return (
    <section className="surface-card kpi-card">
      <div className="card-header">
        <div className="card-label">{label}</div>
        <span className={`status-dot ${tone}`} />
      </div>
      <div className="kpi-value">{value}</div>
      <div className="kpi-subtext">{subtext}</div>
    </section>
  );
}

function FleetHealthCard({ online, total }) {
  const offline = Math.max(0, total - online);
  const onlineRatio = total > 0 ? (online / total) * 100 : 0;

  return (
    <section className="surface-card kpi-card fleet-health-card">
      <div className="card-header">
        <div className="card-label">Fleet Health</div>
        <span className="status-dot yellow" />
      </div>
      <div className="kpi-value">{`${online}/${total}`}</div>
      <div className="fleet-health-meta">
        <div className="fleet-health-row">
          <span className="fleet-health-chip online">Online {online}</span>
          <span className="fleet-health-chip offline">Offline {offline}</span>
        </div>
        <div className="fleet-health-bar" aria-hidden="true">
          <span className="fleet-health-bar-online" style={{ width: `${onlineRatio}%` }} />
        </div>
        <div className="kpi-subtext">Machines online right now</div>
      </div>
    </section>
  );
}

function ServiceHealthCard({ service, status, detail, tone = "blue" }) {
  return (
    <article className="surface-card service-health-card">
      <div className="card-header">
        <div className="card-label">{service}</div>
        <span className={`status-dot ${tone}`} />
      </div>
      <div className="service-health-status">{status}</div>
      <div className="kpi-subtext">{detail}</div>
    </article>
  );
}

function DashboardPage() {
  const navigate = useNavigate();
  const {
    alerts,
    alertsError,
    alertsLoading,
    anomalies,
    anomaliesError,
    anomaliesLoading,
    insightError,
    insightLoading,
    insightSource,
    investigationsError,
    investigationsLoading,
    latestTelemetry,
    machines,
    recentInvestigations,
    socketStatus,
    telemetryError,
    telemetryHistory,
  } = useOperationsData();

  const activeAlerts = useMemo(
    () => alerts.filter((alert) => String(alert?.status || "").toUpperCase() === "ACTIVE"),
    [alerts]
  );
  const criticalHighAlerts = useMemo(
    () =>
      activeAlerts.filter((alert) => {
        const severity = String(alert?.severity || "").toUpperCase();
        return severity === "CRITICAL" || severity === "HIGH";
      }),
    [activeAlerts]
  );
  const normalizedAnomalies = useMemo(
    () =>
      anomalies
        .map(normalizeAnomaly)
        .filter(Boolean)
        .sort((left, right) => new Date(right.detectedAt || 0).getTime() - new Date(left.detectedAt || 0).getTime()),
    [anomalies]
  );
  const recentIncidentPreview = useMemo(
    () => recentInvestigations.slice(0, INCIDENT_PREVIEW_LIMIT),
    [recentInvestigations]
  );
  const latestAnomalyPreview = useMemo(
    () => normalizedAnomalies.slice(0, ANOMALY_PREVIEW_LIMIT),
    [normalizedAnomalies]
  );
  const machineStatusSummary = useMemo(() => {
    const onlineMachines = machines.filter((machine) => resolveMachineStatus(machine).label === "ONLINE").length;
    return { online: onlineMachines, total: machines.length };
  }, [machines]);
  const insightModel = useMemo(
    () => buildInsightModel(latestTelemetry, activeAlerts, normalizedAnomalies),
    [activeAlerts, latestTelemetry, normalizedAnomalies]
  );
  const serviceCards = useMemo(
    () => [
      {
        service: "monitoring-api",
        status: telemetryError ? "Needs attention" : "Healthy",
        detail: telemetryError || "Telemetry and machine data are loading normally.",
        tone: telemetryError ? "yellow" : "green",
      },
      {
        service: "alert-engine",
        status: alertsError ? "Needs attention" : "Healthy",
        detail: alertsError || `${formatCount(activeAlerts.length)} active alert${activeAlerts.length === 1 ? "" : "s"} in workflow.`,
        tone: alertsError ? "yellow" : activeAlerts.length > 0 ? "red" : "green",
      },
      {
        service: "ai-engine-service",
        status: anomaliesError || insightError ? "Needs attention" : "Healthy",
        detail:
          anomaliesError || insightError || `${formatCount(normalizedAnomalies.length)} anomaly signal${normalizedAnomalies.length === 1 ? "" : "s"} observed.`,
        tone: anomaliesError || insightError ? "yellow" : "green",
      },
      {
        service: "notification-service",
        status: "Indirect signal",
        detail: "Notification delivery is not directly probed from the frontend in this phase.",
        tone: "blue",
      },
    ],
    [activeAlerts.length, alertsError, anomaliesError, insightError, normalizedAnomalies.length, telemetryError]
  );
  const activityItems = useMemo(() => {
    const alertItems = activeAlerts.map((alert) => ({
      id: `alert-${alert.id}`,
      kind: "Alert",
      title: `${alert.machineIdentifier || "Unknown machine"}`,
      description: truncate(`${alert.alertType || "Alert"} alert triggered`, 88),
      timestamp: getAlertTimestamp(alert),
      tone: getSeverityTone(alert.severity),
    }));

    const anomalyItems = normalizedAnomalies.map((anomaly) => ({
      id: `anomaly-${anomaly.id || anomaly.anomalyId}`,
      kind: "Anomaly",
      title: `${anomaly.machineIdentifier || "Unknown machine"}`,
      description: truncate(
        `${anomaly.metricType || anomaly.eventType || "Metric"} anomaly detected`,
        88
      ),
      timestamp: anomaly.detectedAt,
      tone: getSeverityTone(anomaly.severity),
    }));

    const investigationItems = recentInvestigations.map((incident) => ({
      id: `incident-${incident.investigationId || incident.alertId}`,
      kind: "Incident",
      title: `${incident.machineIdentifier || "Unknown machine"}`,
      description: truncate(
        `AI investigation created for ${incident.alertType || "alert"}`,
        88
      ),
      timestamp: incident.createdAt,
      tone: getSeverityTone(incident.severity),
    }));

    return [...alertItems, ...anomalyItems, ...investigationItems]
      .sort((left, right) => new Date(right.timestamp || 0).getTime() - new Date(left.timestamp || 0).getTime())
      .slice(0, ACTIVITY_PREVIEW_LIMIT);
  }, [activeAlerts, normalizedAnomalies, recentInvestigations]);

  const serviceNotices = [telemetryError, alertsError, anomaliesError, investigationsError].filter(Boolean);

  return (
    <div className="content-page">
      <header className="page-header">
        <div className="page-title-group">
          <div className="eyebrow">Mission Control</div>
          <h1 className="page-title">Dashboard</h1>
          <p className="page-subtitle">
            A high-level operational overview of system health, recent incidents, anomaly activity, and fleet telemetry.
          </p>
        </div>
        <div className="page-actions">
          <span className={`status-pill ${socketStatus === "connected" ? "green" : "yellow"}`}>
            <span className={`status-dot ${socketStatus === "connected" ? "green" : "yellow"}`} />
            {socketStatus === "connected" ? "Live telemetry" : "Reconnecting"}
          </span>
        </div>
      </header>

      {serviceNotices.length > 0 ? (
        <div className="inline-notice warning">
          <span>{serviceNotices.join(" ")}</span>
        </div>
      ) : null}

      <div className="kpi-grid dashboard-kpi-grid">
        <StatCard
          label="Active Alerts"
          value={alertsLoading ? "Loading" : formatCount(activeAlerts.length)}
          tone={activeAlerts.length > 0 ? "red" : "green"}
          subtext="Operational issues currently open"
        />
        <StatCard
          label="Critical / High"
          value={alertsLoading ? "Loading" : formatCount(criticalHighAlerts.length)}
          tone={criticalHighAlerts.length > 0 ? "red" : "green"}
          subtext="Highest-severity alerts needing attention"
        />
        <StatCard
          label="Active Anomalies"
          value={anomaliesLoading ? "Loading" : formatCount(normalizedAnomalies.length)}
          tone={normalizedAnomalies.length > 0 ? "yellow" : "green"}
          subtext="Recent anomaly signals being tracked"
        />
        <StatCard
          label="Recent Incidents"
          value={investigationsLoading ? "Loading" : formatCount(recentInvestigations.length)}
          tone={recentInvestigations.length > 0 ? "blue" : "green"}
          subtext="Persisted AI investigation records"
        />
        <FleetHealthCard online={machineStatusSummary.online} total={machineStatusSummary.total} />
      </div>

      <section className="surface-card section-card">
        <div className="section-header recent-incidents-header">
          <div>
            <div className="card-label">Service Health</div>
            <h2 className="section-title">Connected System Surfaces</h2>
          </div>
        </div>
        <div className="service-health-grid">
          {serviceCards.map((serviceCard) => (
            <ServiceHealthCard key={serviceCard.service} {...serviceCard} />
          ))}
        </div>
      </section>

      <div className="dashboard-grid dashboard-overview-grid">
        <div className="overview-main-column">
          <DashboardTelemetryPanel
            alerts={alerts}
            anomalies={normalizedAnomalies}
            latestTelemetry={latestTelemetry}
            telemetryHistory={telemetryHistory}
          />

          <RecentIncidentsPanel
            actionLabel="View all incidents"
            cardLabel="Recent Incidents"
            investigations={recentIncidentPreview}
            limit={INCIDENT_PREVIEW_LIMIT}
            loading={investigationsLoading}
            error={investigationsError}
            onSelectIncident={(incident) =>
              navigate(`/incidents/${encodeURIComponent(incident.investigationId)}`, { state: { incident } })
            }
            onViewAll={() => navigate("/incidents")}
            title="Latest persisted investigations"
          />

          <CompactActivityFeed
            items={activityItems}
            loading={alertsLoading || anomaliesLoading || investigationsLoading}
            error={alertsError || anomaliesError || investigationsError}
          />
        </div>

        <div className="stack-column">
          <AiInsightPanel
            actionButtons={[
              {
                label: "Open assistant",
                description: "",
                onClick: () =>
                  navigate("/assistant", {
                    state: {
                      machineIdentifier: latestTelemetry?.machineIdentifier || "",
                    },
                  }),
              },
              {
                label: "Review incidents",
                description: "",
                onClick: () => navigate("/incidents"),
              },
            ]}
            alerts={activeAlerts}
            anomalies={normalizedAnomalies}
            cardLabel="AI Operations"
            insightError={insightError}
            insightLoading={insightLoading}
            insightSource={insightSource}
            latestTelemetry={latestTelemetry}
            onAskAboutTopIssue={() =>
              navigate("/assistant", {
                state: {
                  machineIdentifier: latestTelemetry?.machineIdentifier || "",
                  triggerMessage: {
                    id: `dashboard-top-issue-${Date.now()}`,
                    message: `Summarize the current top issue: ${insightModel.topIssue}. Explain what matters most and what to investigate next.`,
                  },
                },
              })
            }
            onAskAboutTopProcess={() =>
              navigate("/assistant", {
                state: {
                  machineIdentifier: latestTelemetry?.machineIdentifier || "",
                  triggerMessage: {
                    id: `dashboard-top-process-${Date.now()}`,
                    message: `Explain the current top process risk: ${insightModel.topProcessSummary}. Tell me what to verify next.`,
                  },
                },
              })
            }
            onInvestigateWithAi={() =>
              navigate("/assistant", {
                state: {
                  machineIdentifier: latestTelemetry?.machineIdentifier || "",
                  triggerMessage: {
                    id: `dashboard-overview-${Date.now()}`,
                    message: [
                      "Summarize current platform health from the dashboard overview.",
                      `Status: ${insightModel.status}`,
                      `Top issue: ${insightModel.topIssue}`,
                      `Active alerts: ${activeAlerts.length}`,
                      `Anomalies: ${normalizedAnomalies.length}`,
                      `Recent incidents: ${recentInvestigations.length}`,
                      "Give me the next best operator action.",
                    ].join("\n"),
                  },
                },
              })
            }
            onRetryInsight={() => window.location.reload()}
            title="Assistant teaser"
          />

          <LatestAnomaliesPanel
            anomalies={latestAnomalyPreview}
            error={anomaliesError}
            limit={ANOMALY_PREVIEW_LIMIT}
            loading={anomaliesLoading}
            onInvestigate={(anomaly) =>
              navigate(`/anomalies/${encodeURIComponent(anomaly.id || anomaly.anomalyId)}`, { state: { anomaly } })
            }
            onViewAll={() => navigate("/anomalies")}
          />

          <section className="surface-card section-card">
            <div className="section-header recent-incidents-header">
              <div>
                <div className="card-label">Fleet Snapshot</div>
                <h2 className="section-title">Machine Overview</h2>
              </div>
              <button type="button" className="ghost-button" onClick={() => navigate("/machines")}>
                View all machines
              </button>
            </div>
            <div className="snapshot-grid">
              <div className="snapshot-row">
                <span className="snapshot-row-label">Tracked machines</span>
                <span className="snapshot-row-value">{machines.length}</span>
              </div>
              <div className="snapshot-row">
                <span className="snapshot-row-label">Online now</span>
                <span className="snapshot-row-value">{machineStatusSummary.online}</span>
              </div>
              <div className="snapshot-row">
                <span className="snapshot-row-label">Latest telemetry</span>
                <span className="snapshot-row-value">{formatTimestamp(latestTelemetry?.timestamp || latestTelemetry?.createdAt)}</span>
              </div>
              <div className="snapshot-row">
                <span className="snapshot-row-label">Assistant handoff</span>
                <span className="snapshot-row-value">Available</span>
              </div>
            </div>
          </section>
        </div>
      </div>
    </div>
  );
}

export default DashboardPage;
