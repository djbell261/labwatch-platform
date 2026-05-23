import { normalizeAnomaly } from "./telemetryChartUtils";
import EmptyState from "./states/EmptyState";
import ErrorState from "./states/ErrorState";

function formatAnomalyTime(value) {
  if (!value) {
    return "No timestamp";
  }

  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return value;
  }

  return parsed.toLocaleString();
}

function getSeverityTone(severity) {
  const normalized = String(severity || "").toUpperCase();
  if (normalized === "CRITICAL" || normalized === "HIGH") {
    return "red";
  }
  if (normalized === "MEDIUM") {
    return "yellow";
  }
  return "blue";
}

function truncate(value, limit) {
  const content = String(value || "").trim();
  if (!content) {
    return "Potential anomaly detected.";
  }

  return content.length > limit ? `${content.slice(0, limit - 1)}…` : content;
}

function formatMetricValue(value) {
  const numericValue = Number(value);
  return Number.isFinite(numericValue) ? `${numericValue.toFixed(1)}%` : "Unknown";
}

function formatScore(value) {
  const numericValue = Number(value);
  return Number.isFinite(numericValue) ? numericValue.toFixed(2) : "Unknown";
}

function AnomalySkeletonCard() {
  return (
    <article className="anomaly-card anomaly-card-compact is-loading" aria-hidden="true">
      <div className="incident-card-top">
        <span className="skeleton-line" style={{ width: "86px" }} />
        <span className="skeleton-line" style={{ width: "100px" }} />
      </div>
      <span className="skeleton-line" style={{ width: "48%" }} />
      <span className="skeleton-line" style={{ width: "78%", height: "14px" }} />
    </article>
  );
}

function LatestAnomaliesPanel({
  anomalies = [],
  loading = false,
  error = "",
  onInvestigate,
  onViewAll,
  limit = 5,
}) {
  const visibleAnomalies = anomalies
    .map(normalizeAnomaly)
    .filter(Boolean)
    .sort((left, right) => new Date(right.detectedAt || 0).getTime() - new Date(left.detectedAt || 0).getTime())
    .slice(0, limit);

  return (
    <section className="surface-card section-card">
      <div className="section-header recent-incidents-header">
        <div>
          <div className="card-label">Latest Anomalies</div>
          <h2 className="section-title">Persistent Anomaly Timeline</h2>
        </div>
        <button type="button" className="ghost-button" disabled={!onViewAll} onClick={() => onViewAll?.()}>
          View all anomalies
        </button>
      </div>

      {loading ? (
        <div className="incidents-list">
          {[0, 1, 2].map((item) => (
            <AnomalySkeletonCard key={item} />
          ))}
        </div>
      ) : error ? (
        <ErrorState message={error || "Unable to load anomalies"} />
      ) : visibleAnomalies.length === 0 ? (
        <EmptyState message="No anomalies detected yet — system may still be learning." />
      ) : (
        <div className="incidents-list">
          {visibleAnomalies.map((anomaly) => {
            const severityTone = getSeverityTone(anomaly.severity);
            return (
              <article
                key={anomaly.id || anomaly.anomalyId || `${anomaly.metricType}-${anomaly.detectedAt}`}
                className="anomaly-card anomaly-card-compact"
              >
                <div className="incident-card-top">
                  <span className={`status-pill ${severityTone}`}>
                    <span className={`status-dot ${severityTone}`} />
                    {anomaly.severity || "UNKNOWN"}
                  </span>
                  <span className="incident-confidence">z-score {formatScore(anomaly.anomalyScore)}</span>
                </div>
                <div className="incident-machine">{anomaly.machineIdentifier || "Unknown machine"}</div>
                <div className="incident-meta-row">
                  <span className="incident-alert-type">{anomaly.metricType || "Unknown metric"}</span>
                  <span className="incident-created-at">{formatAnomalyTime(anomaly.detectedAt)}</span>
                </div>
                <p className="incident-summary incident-summary-compact">{truncate(anomaly.explanation, 120)}</p>
                <div className="anomaly-card-footer">
                  <div className="anomaly-card-stats">
                    <span>{formatMetricValue(anomaly.metricValue)}</span>
                    <span>z {formatScore(anomaly.anomalyScore)}</span>
                  </div>
                  <button
                    type="button"
                    className="chart-tooltip-action anomaly-investigate-button"
                    onClick={() => onInvestigate?.(anomaly)}
                  >
                    Investigate with AI
                  </button>
                </div>
              </article>
            );
          })}
        </div>
      )}
    </section>
  );
}

export default LatestAnomaliesPanel;
