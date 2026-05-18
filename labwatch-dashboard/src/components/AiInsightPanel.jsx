import { buildInsightModel } from "./aiInsightModel";

function SnapshotRow({ label, value, tone = "", clickable = false, onClick }) {
  const Element = clickable ? "button" : "div";
  const className = clickable ? "snapshot-row" : "snapshot-row";

  return (
    <Element type={clickable ? "button" : undefined} onClick={clickable ? onClick : undefined} className={className}>
      <span className="snapshot-row-label">{label}</span>
      <span className="snapshot-row-value" style={tone ? { color: tone } : undefined}>
        {value}
      </span>
    </Element>
  );
}

function SkeletonRows() {
  return (
    <div className="snapshot-grid">
      {[0, 1, 2, 3, 4].map((row) => (
        <div key={row} className="snapshot-row">
          <span className="skeleton-line" style={{ width: "34%" }} />
          <span className="skeleton-line" style={{ width: "42%" }} />
        </div>
      ))}
    </div>
  );
}

function AiInsightPanel({
  latestTelemetry,
  alerts,
  anomalies,
  insightSource = "backend",
  insightLoading = false,
  insightError = "",
  onRetryInsight,
  onInvestigateWithAi,
  onAskAboutTopIssue,
  onAskAboutTopProcess,
  cardLabel = "AI Snapshot",
  title = "Current Assessment",
  actionButtons = [],
}) {
  const insight = buildInsightModel(latestTelemetry, alerts, anomalies);

  return (
    <section className="surface-card section-card">
      <div className="section-header">
        <div>
          <div className="card-label">{cardLabel}</div>
          <h2 className="section-title">{title}</h2>
        </div>
        <div className={`status-pill ${insight.statusTone}`}>
          <span className={`status-dot ${insight.statusTone}`} />
          {insight.status}
        </div>
      </div>

      {insightError ? (
        <div className="inline-notice warning">
          <span>{insightError}</span>
          <button type="button" className="ghost-button" onClick={onRetryInsight}>
            Retry
          </button>
        </div>
      ) : null}

      {insightLoading ? (
        <SkeletonRows />
      ) : (
        <div className="snapshot-grid">
          <SnapshotRow label="Status" value={insight.status} tone={insight.statusTone === "green" ? "#86efac" : insight.statusTone === "yellow" ? "#fde68a" : "#fecaca"} />
          <SnapshotRow label="Top Issue" value={insight.topIssue} clickable onClick={onAskAboutTopIssue} />
          <SnapshotRow label="Active Alerts" value={String(insight.activeAlertsCount)} clickable onClick={onInvestigateWithAi} />
          <SnapshotRow label="Anomalies" value={String(insight.anomalyCount)} clickable onClick={onInvestigateWithAi} />
          <SnapshotRow
            label="Top Process"
            value={insight.topProcessSummary}
            clickable={Boolean(insight.topProcess?.processName)}
            onClick={onAskAboutTopProcess}
          />
        </div>
      )}

      {actionButtons.length > 0 ? (
        <div className="quick-actions-grid">
          {actionButtons.map((action, index) => (
            <button
              key={action.label}
              type="button"
              className={`quick-action-button ${index === 0 ? "primary-ai-cta" : ""}`}
              onClick={action.onClick}
            >
              <div className="quick-action-copy">
                <span className="quick-action-title">{action.label}</span>
                {action.description ? <span className="quick-action-subtitle">{action.description}</span> : null}
              </div>
              <span>→</span>
            </button>
          ))}
        </div>
      ) : null}

      <div className="machine-card-subtle">
        Source: {insightSource === "backend" ? "AI service" : "Local fallback"}
      </div>
    </section>
  );
}

export default AiInsightPanel;
