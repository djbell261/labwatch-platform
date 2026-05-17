import { useMemo } from "react";
import { useNavigate, useParams } from "react-router-dom";
import RecentIncidentsPanel from "../components/RecentIncidentsPanel";
import TelemetryTrendChart from "../components/TelemetryTrendChart";
import { useAuth } from "../context/AuthContext";
import { useOperationsData } from "../hooks/useOperationsData";
import { formatRelativeLastSeen, formatTimestamp, resolveMachineStatus, resolveOwnership } from "../utils/operations";

function MachineDetailPage() {
  const { machineIdentifier = "" } = useParams();
  const navigate = useNavigate();
  const { user } = useAuth();
  const {
    alerts,
    anomalies,
    investigationsLoading,
    latestTelemetry,
    recentInvestigations,
    selectedMachine,
    telemetryHistory,
    telemetryLoading,
  } = useOperationsData(machineIdentifier);

  const activeAlerts = useMemo(
    () => alerts.filter((alert) => String(alert?.status || "").toUpperCase() === "ACTIVE"),
    [alerts]
  );
  const status = resolveMachineStatus(selectedMachine);
  const ownership = resolveOwnership(selectedMachine, user?.userId);

  return (
    <div className="content-page">
      <header className="page-header">
        <div className="page-title-group">
          <div className="eyebrow">Machine Detail</div>
          <h1 className="page-title">{selectedMachine?.hostname || machineIdentifier}</h1>
          <p className="page-subtitle">Focused machine view with latest telemetry, alerts, anomalies, and recent investigations.</p>
        </div>
        <div className="page-actions">
          <button type="button" className="ghost-button" onClick={() => navigate("/machines")}>
            Back to Machines
          </button>
        </div>
      </header>

      <div className="detail-grid detail-grid-hero">
        <div className="detail-stat">
          <div className="detail-label">Status</div>
          <div className="detail-value">{status.label}</div>
          <div className="machine-card-subtle">Last seen {formatRelativeLastSeen(selectedMachine?.lastSeenAt)}</div>
        </div>
        <div className="detail-stat">
          <div className="detail-label">Ownership</div>
          <div className="detail-value">{ownership.label}</div>
          <div className="machine-card-subtle">{selectedMachine?.machineIdentifier || machineIdentifier}</div>
        </div>
        <div className="detail-stat">
          <div className="detail-label">Latest Telemetry</div>
          <div className="detail-value">{formatTimestamp(latestTelemetry?.timestamp || latestTelemetry?.createdAt)}</div>
          <div className="machine-card-subtle">Most recent snapshot</div>
        </div>
      </div>

      <div className="dashboard-grid">
        <TelemetryTrendChart
          alerts={activeAlerts}
          anomalies={anomalies}
          anomaliesError=""
          anomaliesLoading={false}
          eventVisibility={{ alerts: true, anomalies: true, resolvedAlerts: false }}
          loading={telemetryLoading}
          onExplainSpike={() => navigate("/assistant", { state: { machineIdentifier } })}
          onToggleEventVisibility={() => {}}
          onToggleMetric={() => {}}
          selectedEventKey=""
          selectedMetrics={["cpuUsage", "memoryUsage", "diskUsage"]}
          telemetryHistory={telemetryHistory}
        />

        <div className="stack-column">
          <section className="surface-card section-card">
            <div className="section-header">
              <div>
                <div className="card-label">Open Issues</div>
                <h2 className="section-title">Alert and Anomaly Context</h2>
              </div>
            </div>
            <div className="context-grid">
              <div className="context-row">
                <span className="context-label">Active alerts</span>
                <span className="context-value">{activeAlerts.length}</span>
              </div>
              <div className="context-row">
                <span className="context-label">Anomaly history</span>
                <span className="context-value">{anomalies.length}</span>
              </div>
              <div className="context-row">
                <span className="context-label">Investigations</span>
                <span className="context-value">{recentInvestigations.length}</span>
              </div>
            </div>
          </section>

          <RecentIncidentsPanel
            error=""
            investigations={recentInvestigations}
            loading={investigationsLoading}
            onSelectIncident={(incident) =>
              navigate(`/incidents/${encodeURIComponent(incident.investigationId)}`, { state: { incident } })
            }
          />
        </div>
      </div>
    </div>
  );
}

export default MachineDetailPage;
