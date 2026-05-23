import { useEffect, useMemo, useState } from "react";
import ChatAssistant from "../components/ChatAssistant";
import {
  getAlerts,
  getAnomalies,
  getInvestigationsByAlertId,
  getInvestigationsByIncidentId,
  getRecentInvestigations,
  sendChatMessage,
} from "../services/api";
import { formatDuration } from "../utils/operations";

function formatTimestamp(value) {
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

function TimelineItem({ label, title, meta, tone = "blue" }) {
  return (
    <div className="timeline-item">
      <div className="timeline-item-top">
        <span className="timeline-label">{label}</span>
        <span className={`status-pill ${tone}`}>
          <span className={`status-dot ${tone}`} />
          {meta}
        </span>
      </div>
      <div className="timeline-title">{title}</div>
    </div>
  );
}

function parseSectionLines(value) {
  if (!value) {
    return [];
  }

  return String(value)
    .split("\n")
    .map((line) => line.replace(/^\s*-\s*/, "").trim())
    .filter(Boolean);
}

function InvestigationSection({ label, value, asList = false }) {
  const lines = parseSectionLines(value);

  if (asList) {
    return (
      <div>
        <div className="detail-label">{label}</div>
        {lines.length > 0 ? (
          <ul className="incident-detail-list">
            {lines.map((line) => (
              <li key={`${label}-${line}`}>{line}</li>
            ))}
          </ul>
        ) : (
          <p className="incident-detail-paragraph">No details available.</p>
        )}
      </div>
    );
  }

  return (
    <div>
      <div className="detail-label">{label}</div>
      <p className="incident-detail-paragraph">{value || "No details available."}</p>
    </div>
  );
}

function formatTimelineTime(value) {
  if (!value) {
    return "Unknown time";
  }

  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return value;
  }

  return parsed.toLocaleTimeString([], {
    hour: "2-digit",
    minute: "2-digit",
  });
}

function formatTimelineType(type) {
  return String(type || "UNKNOWN")
    .replace(/_/g, " ")
    .toLowerCase()
    .replace(/\b\w/g, (match) => match.toUpperCase());
}

function IncidentDetailPage({ investigationId, initialIncident = null, onBack }) {
  const [incident, setIncident] = useState(
    initialIncident?.investigationId === investigationId ? initialIncident : null
  );
  const [relatedInvestigations, setRelatedInvestigations] = useState([]);
  const [recentAlerts, setRecentAlerts] = useState([]);
  const [recentAnomalies, setRecentAnomalies] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [chatTriggerMessage, setChatTriggerMessage] = useState(null);

  useEffect(() => {
    let isActive = true;

    const loadIncident = async () => {
      setLoading(true);
      setError("");

      try {
        let baseIncident = initialIncident?.investigationId === investigationId ? initialIncident : null;

        if (!baseIncident) {
          const latestInvestigations = await getRecentInvestigations();
          baseIncident =
            latestInvestigations.find((item) => item.investigationId === investigationId) || null;
        }

        if (!baseIncident) {
          throw new Error("Unable to locate this incident.");
        }

        if (!isActive) {
          return;
        }

        const [
          alertInvestigations,
          incidentInvestigations,
          machineInvestigations,
          machineAlerts,
          machineAnomalies,
        ] = await Promise.all([
          getInvestigationsByAlertId(baseIncident.alertId),
          getInvestigationsByIncidentId(baseIncident.incidentId || baseIncident.investigationId),
          getRecentInvestigations(baseIncident.machineIdentifier),
          getAlerts(baseIncident.machineIdentifier),
          getAnomalies(baseIncident.machineIdentifier),
        ]);

        if (!isActive) {
          return;
        }

        const resolvedIncident =
          incidentInvestigations.find((item) => item.investigationId === investigationId) ||
          alertInvestigations.find((item) => item.investigationId === investigationId) ||
          incidentInvestigations[0] ||
          alertInvestigations[0] ||
          baseIncident;

        setIncident(resolvedIncident);
        setRelatedInvestigations(
          (incidentInvestigations.length ? incidentInvestigations : machineInvestigations)
            .filter((item) => item.investigationId !== resolvedIncident.investigationId)
            .slice(0, 5)
        );
        setRecentAlerts(machineAlerts.slice(0, 5));
        setRecentAnomalies(machineAnomalies.slice(0, 5));
        setChatTriggerMessage({
          id: `incident-${resolvedIncident.investigationId}`,
          message: [
            "Investigate this incident with the full stored context:",
            `Machine: ${resolvedIncident.machineIdentifier || "unknown"}`,
            `Incident ID: ${resolvedIncident.incidentId || "unknown"}`,
            `Incident Status: ${resolvedIncident.incidentStatus || "unknown"}`,
            `Affected Metrics: ${resolvedIncident.affectedMetrics || "unknown"}`,
            `Suspected Contributor: ${resolvedIncident.suspectedContributor || "unknown"}`,
            `Alert Type: ${resolvedIncident.alertType || "unknown"}`,
            `Severity: ${resolvedIncident.severity || "unknown"}`,
            `Summary: ${resolvedIncident.summary || "No summary available."}`,
            `Likely Cause: ${resolvedIncident.likelyCause || "Unknown"}`,
            `Evidence: ${resolvedIncident.evidence || "Unknown"}`,
            `Recommended Checks: ${resolvedIncident.recommendedChecks || "Unknown"}`,
            `Recommended Actions: ${resolvedIncident.recommendedAction || "Unknown"}`,
            `Persistence Assessment: ${resolvedIncident.persistenceAssessment || "Unknown"}`,
            `Machine Baseline: ${resolvedIncident.baselineSummary || "Unknown"}`,
            `Historical Pattern Notes: ${resolvedIncident.historicalPatternNotes || "Unknown"}`,
            `Correlation Timeline: ${resolvedIncident.correlationTimeline?.map((entry) => `${formatTimelineTime(entry.timestamp)} ${entry.type}: ${entry.description}`).join(" | ") || "Unknown"}`,
            `Confidence: ${resolvedIncident.confidenceScore ? `${resolvedIncident.confidenceScore}% ${resolvedIncident.confidenceLevel || ""}` : resolvedIncident.confidence || "unknown"}`,
            "Provide a concise operator-ready triage plan and what to verify next.",
          ].join("\n"),
        });
      } catch (loadError) {
        if (!isActive) {
          return;
        }
        setError(loadError?.message || "Unable to load this incident.");
      } finally {
        if (isActive) {
          setLoading(false);
        }
      }
    };

    loadIncident();

    return () => {
      isActive = false;
    };
  }, [initialIncident, investigationId]);

  const handleChatMessage = async (message) => {
    try {
      const response = await sendChatMessage(message, incident?.machineIdentifier || "");
      return response || "I don’t have a confident answer yet, but the incident details on this page still reflect the current system state.";
    } catch {
      return `AI is unavailable right now. Incident summary: ${incident?.summary || "No summary available."} Likely cause: ${incident?.likelyCause || "Unknown"}. Recommended action: ${incident?.recommendedAction || "Unknown"}.`;
    }
  };

  const rawAlertFields = useMemo(() => {
    if (!incident) {
      return [];
    }

    return [
      ["Investigation ID", incident.investigationId],
      ["Incident ID", incident.incidentId],
      ["Incident Status", incident.incidentStatus],
      ["Alert ID", incident.alertId],
      ["Machine", incident.machineIdentifier],
      ["Affected Metrics", incident.affectedMetrics || incident.alertType],
      ["Suspected Contributor", incident.suspectedContributor],
      ["Severity", incident.severity],
      ["Confidence", incident.confidenceScore ? `${incident.confidenceScore}% ${incident.confidenceLevel || ""}` : incident.confidence],
      ["Created At", formatTimestamp(incident.createdAt)],
    ];
  }, [incident]);

  const aiContextPanel = incident ? (
    <>
      <div>
        <div className="card-label">Incident Context</div>
        <h2 className="section-title">Stored Investigation</h2>
      </div>
      <div className="incident-context-card">
        <div className="incident-context-header">
          <span className={`status-pill ${getSeverityTone(incident.severity)}`}>
            <span className={`status-dot ${getSeverityTone(incident.severity)}`} />
            {incident.severity || "UNKNOWN"}
          </span>
          <span className="machine-card-subtle">
            {incident.confidenceScore ? `${incident.confidenceScore}% ${incident.confidenceLevel || ""}` : incident.confidence || "UNKNOWN"} confidence
          </span>
        </div>
        <div className="incident-context-title">
          {incident.machineIdentifier || "Unknown machine"} · {incident.alertType || "Unknown alert"}
        </div>
        <div className="incident-context-copy">{incident.summary || "No summary available."}</div>
        <div className="incident-context-copy subtle">
          Recommended action: {incident.recommendedAction || "No recommendation available."}
        </div>
      </div>
      <div className="context-grid">
        <div className="context-row">
          <span className="context-label">Likely Cause</span>
          <span className="context-value">{incident.suspectedContributor || incident.likelyCause || "Unknown"}</span>
        </div>
        <div className="context-row">
          <span className="context-label">Created At</span>
          <span className="context-value">{formatTimestamp(incident.createdAt)}</span>
        </div>
        <div className="context-row">
          <span className="context-label">Related Investigations</span>
          <span className="context-value">{relatedInvestigations.length}</span>
        </div>
      </div>
    </>
  ) : null;

  if (loading) {
    return (
      <div className="dashboard-main">
        <div className="content-page">
          <div className="empty-state">Loading incident…</div>
        </div>
      </div>
    );
  }

  if (error || !incident) {
    return (
      <div className="dashboard-main">
        <div className="content-page">
          <div className="inline-notice error">
            <span>{error || "Unable to load this incident."}</span>
            <button type="button" className="ghost-button" onClick={onBack}>
              Back to Dashboard
            </button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="dashboard-main">
      <div className="content-page incident-detail-page">
        <header className="page-header">
          <div className="page-title-group">
            <div className="eyebrow">Incident Detail</div>
            <h1 className="page-title">Recent Incident</h1>
            <p className="page-subtitle">Review the full persisted investigation, supporting context, and continue the investigation with AI.</p>
          </div>
          <div className="detail-actions">
            <button type="button" className="ghost-button" onClick={onBack}>
              Back to Dashboard
            </button>
          </div>
        </header>

        <div className="incident-detail-layout">
          <div className="incident-detail-main">
            <section className="surface-card section-card">
              <div className="section-header">
                <div>
                  <div className="card-label">Investigation Summary</div>
                  <h2 className="section-title">{incident.machineIdentifier || "Unknown machine"} · {incident.alertType || "Unknown alert"}</h2>
                </div>
                <span className={`status-pill ${getSeverityTone(incident.severity)}`}>
                  <span className={`status-dot ${getSeverityTone(incident.severity)}`} />
                  {incident.severity || "UNKNOWN"}
                </span>
              </div>

              <div className="incident-detail-meta">
                <div className="detail-stat">
                  <div className="detail-label">Machine</div>
                  <div className="detail-value">{incident.machineIdentifier || "Unknown"}</div>
                </div>
                <div className="detail-stat">
                  <div className="detail-label">Confidence</div>
                  <div className="detail-value">
                    {incident.confidenceScore ? `${incident.confidenceScore}% ${incident.confidenceLevel || ""}` : incident.confidence || "Unknown"}
                  </div>
                </div>
                <div className="detail-stat">
                  <div className="detail-label">Status</div>
                  <div className="detail-value">{incident.incidentStatus || "ACTIVE"}</div>
                </div>
                <div className="detail-stat">
                  <div className="detail-label">Duration</div>
                  <div className="detail-value">{formatDuration(initialIncident?.durationMs || 0)}</div>
                </div>
                <div className="detail-stat">
                  <div className="detail-label">Timestamp</div>
                  <div className="detail-value">{formatTimestamp(incident.createdAt)}</div>
                </div>
              </div>

              <div className="incident-detail-copy">
                <InvestigationSection label="Summary" value={incident.summary} />
                <InvestigationSection label="Suspected Contributor" value={incident.suspectedContributor || incident.likelyCause} />
                <InvestigationSection label="Likely Cause" value={incident.likelyCause} />
                <InvestigationSection label="Evidence" value={incident.evidence} asList />
                <InvestigationSection label="Confidence Reasoning" value={incident.confidenceReasoning} asList />
                <InvestigationSection label="Possible Contributing Factors" value={incident.contributingFactors} asList />
                <InvestigationSection label="Historical Pattern Notes" value={incident.historicalPatternNotes} />
                <InvestigationSection label="Machine Baseline" value={incident.baselineSummary} />
                <InvestigationSection label="Recommended Checks" value={incident.recommendedChecks} asList />
                <InvestigationSection label="Recommended Actions" value={incident.recommendedAction} asList />
                <InvestigationSection label="Severity / Urgency" value={incident.urgencyAssessment} />
                <InvestigationSection label="Transient vs Persistent Assessment" value={incident.persistenceAssessment} />
                <InvestigationSection label="What to Monitor Next" value={incident.monitorNext} asList />
              </div>
            </section>

            <section className="surface-card section-card">
              <div>
                <div className="card-label">Correlation Timeline</div>
                <h2 className="section-title">Cross-Signal Story</h2>
              </div>
              {incident.correlationTimeline?.length ? (
                <div className="correlation-timeline">
                  {incident.correlationTimeline.map((entry, index) => (
                    <div key={`${entry.timestamp || "unknown"}-${entry.type || "UNKNOWN"}-${index}`} className="correlation-timeline-item">
                      <div className="correlation-timeline-time">{formatTimelineTime(entry.timestamp)}</div>
                      <div className="correlation-timeline-body">
                        <div className="correlation-timeline-header">
                          <span className="timeline-label">{formatTimelineType(entry.type)}</span>
                          {entry.metric ? <span className="machine-card-subtle">{entry.metric}</span> : null}
                          {entry.value !== null && entry.value !== undefined ? (
                            <span className="machine-card-subtle">{Number(entry.value).toFixed(1)}%</span>
                          ) : null}
                        </div>
                        <div className="timeline-title">{entry.description || "No description available."}</div>
                      </div>
                    </div>
                  ))}
                </div>
              ) : (
                <div className="empty-state recent-incidents-empty">
                  No correlated timeline events available for this incident yet.
                </div>
              )}
            </section>

            <section className="surface-card section-card">
              <div>
                <div className="card-label">Raw Alert Data</div>
                <h2 className="section-title">Captured Fields</h2>
              </div>
              <div className="context-grid">
                {rawAlertFields.map(([label, value]) => (
                  <div key={label} className="context-row">
                    <span className="context-label">{label}</span>
                    <span className="context-value">{value || "Unknown"}</span>
                  </div>
                ))}
              </div>
            </section>

            <section className="surface-card section-card">
              <div>
                <div className="card-label">Timeline</div>
                <h2 className="section-title">Recent Context for {incident.machineIdentifier || "this machine"}</h2>
              </div>
              <div className="timeline-grid">
                {relatedInvestigations.slice(0, 3).map((item) => (
                  <TimelineItem
                    key={item.investigationId}
                    label="Investigation"
                    title={item.summary || "Stored investigation"}
                    meta={item.severity || "UNKNOWN"}
                    tone={getSeverityTone(item.severity)}
                  />
                ))}
                {recentAlerts.slice(0, 2).map((item) => (
                  <TimelineItem
                    key={item.id || `${item.alertType}-${item.createdAt}`}
                    label="Alert"
                    title={`${item.alertType || item.eventType || "Unknown"} · ${formatTimestamp(item.createdAt || item.timestamp || item.resolvedAt)}`}
                    meta={item.status || "UNKNOWN"}
                    tone={String(item.status || "").toUpperCase() === "ACTIVE" ? "red" : "green"}
                  />
                ))}
                {recentAnomalies.slice(0, 2).map((item) => (
                  <TimelineItem
                    key={item.id || item.anomalyId}
                    label="Anomaly"
                    title={item.message || `${item.eventType || "Unknown"} anomaly`}
                    meta={item.severity || "UNKNOWN"}
                    tone={getSeverityTone(item.severity)}
                  />
                ))}
                {relatedInvestigations.length === 0 && recentAlerts.length === 0 && recentAnomalies.length === 0 ? (
                  <div className="empty-state recent-incidents-empty">No supporting timeline items available</div>
                ) : null}
              </div>
            </section>
          </div>

          <ChatAssistant
            contextPanel={aiContextPanel}
            onSendMessage={handleChatMessage}
            subtitle="Continue this investigation with AI"
            title="AI Assistant"
            triggerMessage={chatTriggerMessage}
            variant="page"
          />
        </div>
      </div>
    </div>
  );
}

export default IncidentDetailPage;
