import { useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { normalizeAnomaly } from "../components/telemetryChartUtils";
import { useOperationsData } from "../hooks/useOperationsData";
import { formatScore, formatTimestamp, getSeverityTone } from "../utils/operations";

function AnomaliesPage() {
  const navigate = useNavigate();
  const { anomalies, anomaliesLoading, anomaliesError, machines } = useOperationsData();
  const [machineFilter, setMachineFilter] = useState("ALL");
  const [severityFilter, setSeverityFilter] = useState("ALL");

  const filteredAnomalies = useMemo(() => {
    return anomalies
      .map(normalizeAnomaly)
      .filter(Boolean)
      .sort((left, right) => new Date(right.detectedAt || 0).getTime() - new Date(left.detectedAt || 0).getTime())
      .filter((anomaly) => {
      const matchesMachine = machineFilter === "ALL" || anomaly?.machineIdentifier === machineFilter;
      const matchesSeverity =
        severityFilter === "ALL" || String(anomaly?.severity || "").toUpperCase() === severityFilter;
      return matchesMachine && matchesSeverity;
      });
  }, [anomalies, machineFilter, severityFilter]);

  return (
    <div className="content-page">
      <header className="page-header">
        <div className="page-title-group">
          <div className="eyebrow">Detection History</div>
          <h1 className="page-title">Anomalies</h1>
          <p className="page-subtitle">
            Explore anomaly history separately from incident triage so operators can inspect model behavior without clutter.
          </p>
        </div>
      </header>

      <section className="surface-card section-card">
        <div className="table-toolbar">
          <select className="table-filter" onChange={(event) => setSeverityFilter(event.target.value)} value={severityFilter}>
            <option value="ALL">All severities</option>
            <option value="HIGH">High</option>
            <option value="MEDIUM">Medium</option>
            <option value="LOW">Low</option>
          </select>
          <select className="table-filter" onChange={(event) => setMachineFilter(event.target.value)} value={machineFilter}>
            <option value="ALL">All machines</option>
            {machines.map((machine) => (
              <option key={machine.machineIdentifier} value={machine.machineIdentifier}>
                {machine.machineIdentifier}
              </option>
            ))}
          </select>
        </div>

        {anomaliesLoading ? (
          <div className="empty-state">Loading anomalies...</div>
        ) : anomaliesError ? (
          <div className="empty-state">{anomaliesError}</div>
        ) : filteredAnomalies.length === 0 ? (
          <div className="empty-state">No anomalies detected yet. The system may still be learning normal behavior.</div>
        ) : (
          <div className="alerts-table-wrapper">
            <table className="alerts-table">
              <thead>
                <tr>
                  <th>Severity</th>
                  <th>Machine</th>
                  <th>Metric</th>
                  <th>Z-score</th>
                  <th>Promoted</th>
                  <th>Detected</th>
                </tr>
              </thead>
              <tbody>
                {filteredAnomalies.map((anomaly) => {
                  const tone = getSeverityTone(anomaly.severity);
                  return (
                    <tr
                      key={anomaly.id || anomaly.anomalyId}
                      className="clickable-row"
                      onClick={() =>
                        navigate(`/anomalies/${encodeURIComponent(anomaly.id || anomaly.anomalyId)}`, { state: { anomaly } })
                      }
                    >
                      <td>
                        <span className={`status-pill ${tone}`}>
                          <span className={`status-dot ${tone}`} />
                          {anomaly.severity || "UNKNOWN"}
                        </span>
                      </td>
                      <td>{anomaly.machineIdentifier || "Unknown machine"}</td>
                      <td>{anomaly.eventType || "Unknown metric"}</td>
                      <td>
                        {anomaly.anomalyScore == null ? (
                          <span className="timestamp-copy">Baseline building</span>
                        ) : (
                          formatScore(anomaly.anomalyScore)
                        )}
                      </td>
                      <td>{anomaly.promotedAlertId || anomaly.promotedToAlert ? "Yes" : "No"}</td>
                      <td>{formatTimestamp(anomaly.detectedAt)}</td>
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
}

export default AnomaliesPage;
