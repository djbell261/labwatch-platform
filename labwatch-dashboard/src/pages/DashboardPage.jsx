import { useMemo } from "react";
import { useNavigate } from "react-router-dom";
import AiInsightPanel from "../components/AiInsightPanel";
import DashboardTelemetryPanel from "../components/DashboardTelemetryPanel";
import LatestAnomaliesPanel from "../components/LatestAnomaliesPanel";
import RecentIncidentsPanel from "../components/RecentIncidentsPanel";
import { useOperationsData } from "../hooks/useOperationsData";
import { buildInsightModel } from "../components/aiInsightModel";
import {
  formatCount,
  formatPercent,
  getUsageTone,
  resolveMachineStatus,
} from "../utils/operations";

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
    telemetryLoading,
  } = useOperationsData();

  const activeAlerts = useMemo(
    () => alerts.filter((alert) => String(alert?.status || "").toUpperCase() === "ACTIVE"),
    [alerts]
  );
  const machineStatusSummary = useMemo(() => {
    const onlineMachines = machines.filter((machine) => resolveMachineStatus(machine).label === "ONLINE").length;
    return { online: onlineMachines, total: machines.length };
  }, [machines]);
  const insightModel = useMemo(
    () => buildInsightModel(latestTelemetry, activeAlerts, anomalies),
    [anomalies, activeAlerts, latestTelemetry]
  );

  const serviceNotices = [telemetryError, alertsError, anomaliesError].filter(Boolean);

  return (
    <div className="content-page">
      <header className="page-header">
        <div className="page-title-group">
          <div className="eyebrow">Mission Control</div>
          <h1 className="page-title">Dashboard</h1>
          <p className="page-subtitle">
            A high-level operational overview with only the signals an operator should see first.
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

      <div className="kpi-grid">
        <StatCard
          label="Active Alerts"
          value={alertsLoading ? "Loading" : formatCount(activeAlerts.length)}
          tone={activeAlerts.length > 0 ? "red" : "green"}
          subtext="Open operational issues"
        />
        <StatCard
          label="Active Anomalies"
          value={anomaliesLoading ? "Loading" : formatCount(anomalies.length)}
          tone={anomalies.length > 0 ? "yellow" : "green"}
          subtext="Recent statistical anomalies"
        />
        <StatCard
          label="Fleet Health"
          value={`${machineStatusSummary.online}/${machineStatusSummary.total}`}
          tone={machineStatusSummary.online === machineStatusSummary.total ? "green" : "yellow"}
          subtext="Machines online right now"
        />
        <StatCard
          label="CPU Usage"
          value={telemetryLoading ? "Loading" : formatPercent(latestTelemetry?.cpuUsage)}
          tone={getUsageTone(latestTelemetry?.cpuUsage)}
          subtext={latestTelemetry ? `Top issue: ${insightModel.topIssue}` : "Waiting for telemetry"}
        />
      </div>

      <div className="dashboard-grid dashboard-overview-grid">
        <div className="overview-main-column">
          <DashboardTelemetryPanel latestTelemetry={latestTelemetry} telemetryHistory={telemetryHistory} />

          <AiInsightPanel
            alerts={activeAlerts}
            anomalies={anomalies}
            insightError={insightError}
            insightLoading={insightLoading}
            insightSource={insightSource}
            latestTelemetry={latestTelemetry}
            onAskAboutTopIssue={() => navigate("/assistant")}
            onAskAboutTopProcess={() => navigate("/assistant")}
            onInvestigateWithAi={() => navigate("/assistant")}
            onRetryInsight={() => navigate("/assistant")}
          />
        </div>

        <div className="stack-column">
          <RecentIncidentsPanel
            error={investigationsError}
            investigations={recentInvestigations}
            limit={3}
            loading={investigationsLoading}
            onSelectIncident={(incident) =>
              navigate(`/incidents/${encodeURIComponent(incident.investigationId)}`, { state: { incident } })
            }
          />

          <LatestAnomaliesPanel
            anomalies={anomalies}
            error={anomaliesError}
            limit={4}
            loading={anomaliesLoading}
            onInvestigate={(anomaly) =>
              navigate(`/anomalies/${encodeURIComponent(anomaly.id || anomaly.anomalyId)}`, { state: { anomaly } })
            }
          />
        </div>
      </div>
    </div>
  );
}

export default DashboardPage;
