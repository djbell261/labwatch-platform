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

function truncate(value, limit) {
  const content = String(value || "").trim();
  if (!content) {
    return "No summary available.";
  }

  return content.length > limit ? `${content.slice(0, limit - 1)}…` : content;
}

function IncidentSkeletonCard() {
  return (
    <article className="incident-card is-loading" aria-hidden="true">
      <div className="incident-card-top">
        <span className="skeleton-line" style={{ width: "92px" }} />
        <span className="skeleton-line" style={{ width: "110px" }} />
      </div>
      <span className="skeleton-line" style={{ width: "46%" }} />
      <span className="skeleton-line" style={{ width: "82%", height: "14px" }} />
      <span className="skeleton-line" style={{ width: "67%" }} />
      <span className="skeleton-line" style={{ width: "74%" }} />
    </article>
  );
}

function RecentIncidentsPanel({
  investigations = [],
  loading = false,
  error = "",
  onSelectIncident,
  limit = 5,
}) {
  const visibleInvestigations = investigations.slice(0, limit);

  return (
    <section className="surface-card section-card">
      <div className="section-header recent-incidents-header">
        <div>
          <div className="card-label">Recent Incidents</div>
          <h2 className="section-title">Persisted AI Investigations</h2>
        </div>
        <button type="button" className="ghost-button is-placeholder" disabled>
          View all
        </button>
      </div>

      {loading ? (
        <div className="incidents-list">
          {[0, 1, 2].map((item) => (
            <IncidentSkeletonCard key={item} />
          ))}
        </div>
      ) : error ? (
        <div className="empty-state recent-incidents-empty">Unable to load recent incidents</div>
      ) : visibleInvestigations.length === 0 ? (
        <div className="empty-state recent-incidents-empty">No AI investigations yet</div>
      ) : (
        <div className="incidents-list">
          {visibleInvestigations.map((incident) => {
            const severityTone = getSeverityTone(incident.severity);
            return (
              <button
                key={incident.investigationId || `${incident.alertId}-${incident.createdAt}`}
                type="button"
                className="incident-card"
                onClick={() => onSelectIncident?.(incident)}
              >
                <div className="incident-card-top">
                  <span className={`status-pill ${severityTone}`}>
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
                <p className="incident-summary">{truncate(incident.summary, 180)}</p>
                <div className="incident-action-preview">
                  <span className="incident-action-label">Recommended action</span>
                  <span>{truncate(incident.recommendedAction, 120)}</span>
                </div>
              </button>
            );
          })}
        </div>
      )}
    </section>
  );
}

export default RecentIncidentsPanel;
