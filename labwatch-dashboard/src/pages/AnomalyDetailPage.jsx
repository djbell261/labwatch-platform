import { useEffect, useMemo, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { useOperationsData } from "../hooks/useOperationsData";
import { getAnomalyById } from "../services/api";
import { formatTimestamp, getSeverityTone } from "../utils/operations";

function AnomalyDetailPage() {
  const { anomalyId = "" } = useParams();
  const navigate = useNavigate();
  const [anomaly, setAnomaly] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const machineIdentifier = anomaly?.machineIdentifier || "";
  const { recentInvestigations, alerts, latestTelemetry } = useOperationsData(machineIdentifier);

  useEffect(() => {
    let isActive = true;
    getAnomalyById(anomalyId)
      .then((response) => {
        if (isActive) {
          setAnomaly(response);
          setError("");
        }
      })
      .catch(() => {
        if (isActive) {
          setError("Unable to load this anomaly.");
        }
      })
      .finally(() => {
        if (isActive) {
          setLoading(false);
        }
      });

    return () => {
      isActive = false;
    };
  }, [anomalyId]);

  const relatedAlerts = useMemo(
    () => alerts.filter((alert) => alert.machineIdentifier === machineIdentifier).slice(0, 5),
    [alerts, machineIdentifier]
  );

  if (loading) {
    return <div className="content-page"><div className="empty-state">Loading anomaly...</div></div>;
  }

  if (error || !anomaly) {
    return <div className="content-page"><div className="empty-state">{error || "Unable to load anomaly."}</div></div>;
  }

  const tone = getSeverityTone(anomaly.severity);

  return (
    <div className="content-page">
      <header className="page-header">
        <div className="page-title-group">
          <div className="eyebrow">Anomaly Detail</div>
          <h1 className="page-title">{anomaly.eventType || "Anomaly"}</h1>
          <p className="page-subtitle">Review anomaly context, related alerts, telemetry timing, and hand off to AI.</p>
        </div>
        <div className="page-actions">
          <button type="button" className="ghost-button" onClick={() => navigate("/anomalies")}>
            Back to Anomalies
          </button>
          <button
            type="button"
            className="action-button"
            onClick={() =>
              navigate("/assistant", {
                state: {
                  machineIdentifier,
                  triggerMessage: {
                    id: `anomaly-${anomaly.id || anomaly.anomalyId}`,
                    message: `Investigate anomaly on ${machineIdentifier}: ${anomaly.message}`,
                  },
                },
              })
            }
          >
            Open in Assistant
          </button>
        </div>
      </header>

      <div className="incident-detail-layout">
        <div className="incident-detail-main">
          <section className="surface-card section-card">
            <div className="section-header">
              <div>
                <div className="card-label">Detection Summary</div>
                <h2 className="section-title">{machineIdentifier || "Unknown machine"}</h2>
              </div>
              <span className={`status-pill ${tone}`}>
                <span className={`status-dot ${tone}`} />
                {anomaly.severity || "UNKNOWN"}
              </span>
            </div>
            <p className="incident-context-copy">{anomaly.message || "Potential anomaly detected."}</p>
            <div className="context-grid">
              <div className="context-row">
                <span className="context-label">Z-score</span>
                <span className="context-value">{Number(anomaly.zScore).toFixed(2)}</span>
              </div>
              <div className="context-row">
                <span className="context-label">Metric value</span>
                <span className="context-value">{Number(anomaly.metricValue).toFixed(2)}</span>
              </div>
              <div className="context-row">
                <span className="context-label">Detected at</span>
                <span className="context-value">{formatTimestamp(anomaly.detectedAt)}</span>
              </div>
              <div className="context-row">
                <span className="context-label">Latest telemetry</span>
                <span className="context-value">{formatTimestamp(latestTelemetry?.timestamp || latestTelemetry?.createdAt)}</span>
              </div>
            </div>
          </section>
        </div>

        <aside className="incident-detail-sidebar">
          <section className="surface-card section-card">
            <div className="card-label">Related Context</div>
            <h2 className="section-title">Connected Signals</h2>
            <div className="context-grid">
              <div className="context-row">
                <span className="context-label">Related alerts</span>
                <span className="context-value">{relatedAlerts.length}</span>
              </div>
              <div className="context-row">
                <span className="context-label">Investigations</span>
                <span className="context-value">{recentInvestigations.length}</span>
              </div>
              <div className="context-row">
                <span className="context-label">Promoted</span>
                <span className="context-value">{anomaly.promotedAlertId || anomaly.promotedToAlert ? "Yes" : "No"}</span>
              </div>
            </div>
          </section>
        </aside>
      </div>
    </div>
  );
}

export default AnomalyDetailPage;
