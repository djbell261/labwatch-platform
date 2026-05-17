import { useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { useOperationsData } from "../hooks/useOperationsData";
import { formatRelativeLastSeen, resolveMachineStatus, resolveOwnership } from "../utils/operations";

function MachinesPage() {
  const navigate = useNavigate();
  const { user } = useAuth();
  const {
    availableMachines,
    claimError,
    claimSelectedMachine,
    machines,
    recentInvestigations,
    alerts,
    anomalies,
  } = useOperationsData();

  return (
    <div className="content-page">
      <header className="page-header">
        <div className="page-title-group">
          <div className="eyebrow">Fleet View</div>
          <h1 className="page-title">Machines</h1>
          <p className="page-subtitle">
            A machine-centric operational view with clean health, alert, anomaly, and investigation context.
          </p>
        </div>
      </header>

      {claimError ? (
        <div className="inline-notice error">
          <span>{claimError}</span>
        </div>
      ) : null}

      <div className="machine-grid">
        {machines.map((machine) => {
          const status = resolveMachineStatus(machine);
          const ownership = resolveOwnership(machine, user?.userId);
          const machineAlerts = alerts.filter((alert) => alert.machineIdentifier === machine.machineIdentifier);
          const machineAnomalies = anomalies.filter((anomaly) => anomaly.machineIdentifier === machine.machineIdentifier);
          const machineInvestigations = recentInvestigations.filter(
            (investigation) => investigation.machineIdentifier === machine.machineIdentifier
          );

          return (
            <button
              key={machine.machineIdentifier}
              type="button"
              className="machine-card clickable-card"
              onClick={() => navigate(`/machines/${encodeURIComponent(machine.machineIdentifier)}`)}
            >
              <div className="machine-card-top">
                <div>
                  <div className="machine-card-name">{machine.hostname || machine.machineIdentifier}</div>
                  <div className="machine-card-identifier">{machine.machineIdentifier}</div>
                </div>
                <span className={`status-pill ${status.tone}`}>
                  <span className={`status-dot ${status.tone}`} />
                  {status.label}
                </span>
              </div>
              <div className="machine-card-tags">
                <span className="tag">{ownership.label}</span>
                <span className="tag">Last seen {formatRelativeLastSeen(machine.lastSeenAt)}</span>
              </div>
              <div className="machine-summary-grid">
                <div>
                  <div className="machine-card-subtle">Active alerts</div>
                  <div className="detail-value">{machineAlerts.filter((alert) => alert.status === "ACTIVE").length}</div>
                </div>
                <div>
                  <div className="machine-card-subtle">Anomalies</div>
                  <div className="detail-value">{machineAnomalies.length}</div>
                </div>
                <div>
                  <div className="machine-card-subtle">Investigations</div>
                  <div className="detail-value">{machineInvestigations.length}</div>
                </div>
              </div>
            </button>
          );
        })}

        {availableMachines.map((machine) => (
          <div key={`claim-${machine.machineIdentifier}`} className="machine-card">
            <div className="machine-card-top">
              <div>
                <div className="machine-card-name">{machine.hostname || machine.machineIdentifier}</div>
                <div className="machine-card-identifier">{machine.machineIdentifier}</div>
              </div>
              <span className="status-pill blue">
                <span className="status-dot blue" />
                Claimable
              </span>
            </div>
            <div className="machine-card-subtle">Available to add to your monitoring workspace.</div>
            <button
              type="button"
              className="action-button"
              onClick={() => claimSelectedMachine(machine.machineIdentifier)}
            >
              Claim machine
            </button>
          </div>
        ))}
      </div>
    </div>
  );
}

export default MachinesPage;
