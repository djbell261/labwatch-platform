import { useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useOperationsData } from "../hooks/useOperationsData";
import { formatTimestamp, getSeverityTone, truncate } from "../utils/operations";

const PAGE_SIZE = 10;

function IncidentsPage() {
  const navigate = useNavigate();
  const { machines, recentInvestigations, investigationsLoading, investigationsError } = useOperationsData();
  const [severityFilter, setSeverityFilter] = useState("ALL");
  const [machineFilter, setMachineFilter] = useState("ALL");
  const [query, setQuery] = useState("");
  const [page, setPage] = useState(1);

  const filteredIncidents = useMemo(() => {
    return [...recentInvestigations]
      .sort((left, right) => new Date(right.createdAt || 0).getTime() - new Date(left.createdAt || 0).getTime())
      .filter((incident) => {
        const matchesSeverity =
          severityFilter === "ALL" || String(incident?.severity || "").toUpperCase() === severityFilter;
        const matchesMachine = machineFilter === "ALL" || incident?.machineIdentifier === machineFilter;
        const haystack = [
          incident?.machineIdentifier,
          incident?.alertType,
          incident?.summary,
          incident?.likelyCause,
        ]
          .join(" ")
          .toLowerCase();
        const matchesQuery = !query.trim() || haystack.includes(query.trim().toLowerCase());
        return matchesSeverity && matchesMachine && matchesQuery;
      });
  }, [machineFilter, query, recentInvestigations, severityFilter]);

  const pagedIncidents = filteredIncidents.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);
  const totalPages = Math.max(1, Math.ceil(filteredIncidents.length / PAGE_SIZE));

  return (
    <div className="content-page">
      <header className="page-header">
        <div className="page-title-group">
          <div className="eyebrow">Operational Workflow</div>
          <h1 className="page-title">Incidents</h1>
          <p className="page-subtitle">
            Persisted AI investigations, organized as a focused triage queue instead of a dashboard feed.
          </p>
        </div>
      </header>

      <section className="surface-card section-card">
        <div className="table-toolbar">
          <input
            className="table-search"
            onChange={(event) => {
              setQuery(event.target.value);
              setPage(1);
            }}
            placeholder="Search machine, alert type, or summary"
            value={query}
          />
          <select className="table-filter" onChange={(event) => setSeverityFilter(event.target.value)} value={severityFilter}>
            <option value="ALL">All severities</option>
            <option value="CRITICAL">Critical</option>
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

        {investigationsLoading ? (
          <div className="empty-state">Loading incidents...</div>
        ) : investigationsError ? (
          <div className="empty-state">{investigationsError}</div>
        ) : filteredIncidents.length === 0 ? (
          <div className="empty-state">No active incidents. Stored AI investigations will appear here as alerts are analyzed.</div>
        ) : (
          <>
            <div className="alerts-table-wrapper">
              <table className="alerts-table">
                <thead>
                  <tr>
                    <th>Severity</th>
                    <th>Machine</th>
                    <th>Alert Type</th>
                    <th>Status</th>
                    <th>Summary</th>
                    <th>Created</th>
                  </tr>
                </thead>
                <tbody>
                  {pagedIncidents.map((incident) => {
                    const tone = getSeverityTone(incident.severity);
                    return (
                      <tr
                        key={incident.investigationId}
                        className="clickable-row"
                        onClick={() =>
                          navigate(`/incidents/${encodeURIComponent(incident.investigationId)}`, { state: { incident } })
                        }
                      >
                        <td>
                          <span className={`status-pill ${tone}`}>
                            <span className={`status-dot ${tone}`} />
                            {incident.severity || "UNKNOWN"}
                          </span>
                        </td>
                        <td>{incident.machineIdentifier || "Unknown machine"}</td>
                        <td>
                          <div className="incident-table-type">{incident.alertType || "Unknown alert"}</div>
                        </td>
                        <td>
                          <span className="status-pill red">
                            <span className="status-dot red" />
                            Open
                          </span>
                        </td>
                        <td className="incident-summary-cell">
                          <div className="incident-summary-strong">{truncate(incident.summary, 92)}</div>
                          <div className="timestamp-copy">
                            {truncate(incident.recommendedAction || incident.likelyCause || "Awaiting operator review.", 82)}
                          </div>
                        </td>
                        <td className="timestamp-nowrap">{formatTimestamp(incident.createdAt)}</td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>

            <div className="pagination-row">
              <button
                type="button"
                className="ghost-button"
                disabled={page === 1}
                onClick={() => setPage((current) => Math.max(1, current - 1))}
              >
                Previous
              </button>
              <span className="machine-card-subtle">
                Page {page} of {totalPages}
              </span>
              <button
                type="button"
                className="ghost-button"
                disabled={page === totalPages}
                onClick={() => setPage((current) => Math.min(totalPages, current + 1))}
              >
                Next
              </button>
            </div>
          </>
        )}
      </section>
    </div>
  );
}

export default IncidentsPage;
