import EmptyState from "./states/EmptyState";
import ErrorState from "./states/ErrorState";

function formatIncidentTime(value) {
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

function getSeverityAccentClass(severity) {
  const normalized = String(severity || "").toUpperCase();
  if (normalized === "CRITICAL") {
    return "severity-critical";
  }
  if (normalized === "HIGH") {
    return "severity-high";
  }
  return "";
}

function truncate(value, limit) {
  const content = String(value || "").trim();
  if (!content) {
    return "No summary available.";
  }

  return content.length > limit ? `${content.slice(0, limit - 1)}…` : content;
}

function IncidentSkeletonCard() {
  return (
    <article className="incident-card incident-card-compact is-loading" aria-hidden="true">
      <div className="incident-card-top">
        <span className="skeleton-line" style={{ width: "92px" }} />
        <span className="skeleton-line" style={{ width: "110px" }} />
      </div>
      <span className="skeleton-line" style={{ width: "46%" }} />
      <span className="skeleton-line" style={{ width: "82%", height: "14px" }} />
    </article>
  );
}

function RecentIncidentsPanel({
  investigations = [],
  loading = false,
  error = "",
  onSelectIncident,
  onInvestigateIncident,
  cardLabel = "Recent Incidents",
  title = "Persisted AI Investigations",
  actionLabel = "View all",
  onViewAll,
  limit = 5,
}) {
  const visibleInvestigations = investigations.slice(0, limit);

  return (
    <section className="surface-card section-card">
      <div className="section-header recent-incidents-header">
        <div>
          <div className="card-label">{cardLabel}</div>
          <h2 className="section-title">{title}</h2>
        </div>
        <button type="button" className="ghost-button" disabled={!onViewAll} onClick={() => onViewAll?.()}>
          {actionLabel}
        </button>
      </div>

      {loading ? (
        <div className="incidents-list">
          {[0, 1, 2].map((item) => (
            <IncidentSkeletonCard key={item} />
          ))}
        </div>
      ) : error ? (
        <ErrorState message={error || "Unable to load recent incidents"} />
      ) : visibleInvestigations.length === 0 ? (
        <EmptyState message="No active incidents" />
      ) : (
        <div className="incidents-list">
          {visibleInvestigations.map((incident) => {
            const severityTone = getSeverityTone(incident.severity);
            return (
              <article
                key={incident.investigationId || `${incident.alertId}-${incident.createdAt}`}
                className="incident-card incident-card-compact"
              >
                <button type="button" className="card-hit-area" onClick={() => onSelectIncident?.(incident)}>
                  <span className="sr-only">Open incident</span>
                </button>
                <div className="incident-card-top">
                  <span className={`status-pill ${severityTone}`}>
                    <span className={`status-pill-accent ${getSeverityAccentClass(incident.severity)}`} />
                    <span className={`status-dot ${severityTone}`} />
                    {incident.severity || "UNKNOWN"}
                  </span>
                  <span className="incident-confidence">{incident.confidence || "UNKNOWN"} confidence</span>
                </div>
                <div className="incident-machine">{incident.machineIdentifier || "Unknown machine"}</div>
                <div className="incident-meta-row">
                  <span className="incident-alert-type">{incident.alertType || "Unknown alert"}</span>
                  <span className="incident-created-at">{formatIncidentTime(incident.createdAt)}</span>
                </div>
                <p className="incident-summary incident-summary-compact">{truncate(incident.summary, 140)}</p>
                <div className="preview-card-actions">
                  <button type="button" className="ghost-button" onClick={() => onSelectIncident?.(incident)}>
                    Open incident
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

export default RecentIncidentsPanel;
