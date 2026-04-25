function toNumber(value) {
  const numericValue = Number(value);
  return Number.isFinite(numericValue) ? numericValue : null;
}

function getTopProcess(processMetrics = []) {
  if (!Array.isArray(processMetrics) || processMetrics.length === 0) {
    return null;
  }

  return [...processMetrics]
    .map((processMetric) => ({
      ...processMetric,
      cpuPercent: toNumber(processMetric?.cpuPercent),
      memoryPercent: toNumber(processMetric?.memoryPercent),
    }))
    .sort((left, right) => {
      const rightCpu = right.cpuPercent ?? -1;
      const leftCpu = left.cpuPercent ?? -1;
      if (rightCpu !== leftCpu) {
        return rightCpu - leftCpu;
      }

      const rightMemory = right.memoryPercent ?? -1;
      const leftMemory = left.memoryPercent ?? -1;
      return rightMemory - leftMemory;
    })[0];
}

function buildInsightModel(latestTelemetry, alerts, anomalies) {
  const cpuUsage = toNumber(latestTelemetry?.cpuUsage) ?? 0;
  const memoryUsage = toNumber(latestTelemetry?.memoryUsage) ?? 0;
  const diskUsage = toNumber(latestTelemetry?.diskUsage) ?? 0;
  const activeAlerts = Array.isArray(alerts)
    ? alerts.filter((alert) => alert?.status === "ACTIVE")
    : [];
  const anomalyList = Array.isArray(anomalies) ? anomalies : [];
  const topProcess = getTopProcess(latestTelemetry?.processMetrics);

  let status = "Healthy";
  let statusTone = "#34d399";
  let likelyIssue = "No major system pressure is visible right now.";
  const recommendedActions = [];

  if (activeAlerts.length > 0 || anomalyList.length > 0 || memoryUsage > 80 || diskUsage > 85 || cpuUsage > 85) {
    status = "Warning";
    statusTone = "#fbbf24";
  }

  if (
    activeAlerts.some((alert) => ["CRITICAL", "HIGH"].includes(alert?.severity)) ||
    diskUsage > 90 ||
    memoryUsage > 90 ||
    cpuUsage > 95
  ) {
    status = "Critical";
    statusTone = "#f87171";
  }

  const newestAnomaly = anomalyList[0] || null;
  const newestActiveAlert = activeAlerts[0] || null;

  if (activeAlerts.length > 0) {
    likelyIssue = `${activeAlerts.length} active alert${activeAlerts.length > 1 ? "s are" : " is"} affecting the system.`;
  } else if (newestAnomaly) {
    const anomalyMetric = String(
      newestAnomaly.metricType || newestAnomaly.eventType || newestAnomaly.alertType || "system"
    ).toUpperCase();
    likelyIssue = `${anomalyMetric} behavior is abnormal compared to its recent baseline.`;
  } else if (memoryUsage > 80) {
    likelyIssue = "Memory pressure is elevated and the system may start slowing down.";
  } else if (diskUsage > 85) {
    likelyIssue = "Disk usage is high and storage headroom is getting tight.";
  } else if (cpuUsage > 85) {
    likelyIssue = "CPU usage is elevated and compute-intensive work is likely competing for resources.";
  }

  if (newestAnomaly) {
    recommendedActions.push(
      "Review the most recent anomaly and compare it with the current telemetry trend to confirm whether the spike is ongoing."
    );
  }

  if (memoryUsage > 80) {
    recommendedActions.push(
      "Close heavy applications or background tasks to reduce memory pressure before the system starts swapping."
    );
  }

  if (diskUsage > 85) {
    recommendedActions.push(
      "Clean up large files, old downloads, or stale logs to create more free disk space."
    );
  }

  if (cpuUsage > 75 && topProcess?.processName) {
    recommendedActions.push(
      `Inspect ${topProcess.processName} because it is one of the highest CPU consumers right now.`
    );
  }

  if (activeAlerts.length > 0 && newestActiveAlert?.message) {
    recommendedActions.push(`Prioritize the active alert state: ${newestActiveAlert.message}`);
  }

  if (recommendedActions.length === 0) {
    recommendedActions.push(
      "Continue monitoring the dashboard. No immediate action is recommended while telemetry remains stable."
    );
  }

  const processSummary = topProcess?.processName
    ? `${topProcess.processName} is currently one of the highest resource consumers` +
      `${topProcess.cpuPercent !== null ? ` at ${topProcess.cpuPercent.toFixed(1)}% CPU` : ""}` +
      `${topProcess.memoryPercent !== null ? ` and ${topProcess.memoryPercent.toFixed(1)}% memory` : ""}.`
    : "No process-level contributor is available in the latest telemetry snapshot.";

  return {
    status,
    statusTone,
    likelyIssue,
    processSummary,
    recommendedActions,
  };
}

function AiInsightPanel({ latestTelemetry, alerts, anomalies }) {
  const insight = buildInsightModel(latestTelemetry, alerts, anomalies);

  return (
    <section
      style={{
        background: "rgba(15, 23, 42, 0.72)",
        border: "1px solid rgba(148, 163, 184, 0.14)",
        borderRadius: "24px",
        boxShadow: "0 24px 60px rgba(2, 6, 23, 0.28)",
        padding: "24px",
      }}
    >
      <div
        style={{
          alignItems: "center",
          display: "flex",
          gap: "12px",
          justifyContent: "space-between",
          marginBottom: "18px",
        }}
      >
        <div>
          <div
            style={{
              color: "#94a3b8",
              fontSize: "0.85rem",
              letterSpacing: "0.12em",
              marginBottom: "8px",
              textTransform: "uppercase",
            }}
          >
            AI Insight
          </div>
          <h2 style={{ fontSize: "1.5rem", margin: 0 }}>System Health Summary</h2>
        </div>
        <div
          style={{
            background: `${insight.statusTone}22`,
            border: `1px solid ${insight.statusTone}55`,
            borderRadius: "999px",
            color: insight.statusTone,
            fontWeight: 700,
            padding: "10px 14px",
          }}
        >
          {insight.status}
        </div>
      </div>

      <div
        style={{
          background: "rgba(15, 23, 42, 0.55)",
          border: "1px solid rgba(148, 163, 184, 0.12)",
          borderRadius: "18px",
          color: "#e2e8f0",
          lineHeight: 1.7,
          marginBottom: "16px",
          padding: "18px",
        }}
      >
        {insight.likelyIssue} {insight.processSummary}
      </div>

      <div
        style={{
          display: "grid",
          gap: "16px",
          gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))",
        }}
      >
        <div
          style={{
            background: "rgba(15, 23, 42, 0.55)",
            border: "1px solid rgba(148, 163, 184, 0.12)",
            borderRadius: "18px",
            padding: "18px",
          }}
        >
          <div style={{ color: "#94a3b8", marginBottom: "8px" }}>Likely issue</div>
          <div style={{ color: "#e2e8f0", fontWeight: 600, lineHeight: 1.6 }}>
            {insight.likelyIssue}
          </div>
        </div>

        <div
          style={{
            background: "rgba(15, 23, 42, 0.55)",
            border: "1px solid rgba(148, 163, 184, 0.12)",
            borderRadius: "18px",
            padding: "18px",
          }}
        >
          <div style={{ color: "#94a3b8", marginBottom: "8px" }}>Top contributing process</div>
          <div style={{ color: "#e2e8f0", fontWeight: 600, lineHeight: 1.6 }}>
            {insight.processSummary}
          </div>
        </div>
      </div>

      <div
        style={{
          marginTop: "18px",
        }}
      >
        <div
          style={{
            color: "#94a3b8",
            fontSize: "0.85rem",
            letterSpacing: "0.1em",
            marginBottom: "12px",
            textTransform: "uppercase",
          }}
        >
          Recommended Actions
        </div>
        <div
          style={{
            display: "grid",
            gap: "10px",
          }}
        >
          {insight.recommendedActions.map((action) => (
            <div
              key={action}
              style={{
                background: "rgba(15, 23, 42, 0.55)",
                border: "1px solid rgba(148, 163, 184, 0.12)",
                borderRadius: "16px",
                color: "#cbd5e1",
                padding: "14px 16px",
              }}
            >
              {action}
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}

export default AiInsightPanel;
