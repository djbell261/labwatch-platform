import { useMemo } from "react";
import { useLocation } from "react-router-dom";
import ChatAssistant from "../components/ChatAssistant";
import { useOperationsData } from "../hooks/useOperationsData";
import { sendChatMessage } from "../services/api";
import { buildInsightModel } from "../components/aiInsightModel";
import { formatCount, formatTimestamp } from "../utils/operations";

function AssistantPage() {
  const location = useLocation();
  const machineIdentifier = location.state?.machineIdentifier || "";
  const triggerMessage = location.state?.triggerMessage || null;
  const { alerts, anomalies, latestTelemetry } = useOperationsData(machineIdentifier);
  const insightModel = useMemo(
    () => buildInsightModel(latestTelemetry, alerts, anomalies),
    [alerts, anomalies, latestTelemetry]
  );

  return (
    <div className="content-page assistant-page">
      <header className="page-header">
        <div className="page-title-group">
          <div className="eyebrow">AI Operations</div>
          <h1 className="page-title">Assistant</h1>
          <p className="page-subtitle">
            Use AI for operational troubleshooting with machine, anomaly, and incident context injected when needed.
          </p>
        </div>
      </header>

      <ChatAssistant
        contextPanel={
          <>
            <div>
              <div className="card-label">Operational Context</div>
              <h2 className="section-title">Current Scope</h2>
            </div>
            <div className="context-grid">
              <div className="context-row">
                <span className="context-label">Machine scope</span>
                <span className="context-value">{machineIdentifier || "All machines"}</span>
              </div>
              <div className="context-row">
                <span className="context-label">Status</span>
                <span className="context-value">{insightModel.status}</span>
              </div>
              <div className="context-row">
                <span className="context-label">Top issue</span>
                <span className="context-value">{insightModel.topIssue}</span>
              </div>
              <div className="context-row">
                <span className="context-label">Open alerts</span>
                <span className="context-value">{formatCount(alerts.filter((alert) => alert.status === "ACTIVE").length)}</span>
              </div>
              <div className="context-row">
                <span className="context-label">Anomalies</span>
                <span className="context-value">{formatCount(anomalies.length)}</span>
              </div>
              <div className="context-row">
                <span className="context-label">Latest telemetry</span>
                <span className="context-value">{formatTimestamp(latestTelemetry?.timestamp || latestTelemetry?.createdAt)}</span>
              </div>
            </div>
          </>
        }
        onSendMessage={(message) => sendChatMessage(message, machineIdentifier)}
        subtitle="Summaries, troubleshooting, and next-step recommendations"
        title="AI Assistant"
        triggerMessage={triggerMessage}
        variant="page"
      />
    </div>
  );
}

export default AssistantPage;
