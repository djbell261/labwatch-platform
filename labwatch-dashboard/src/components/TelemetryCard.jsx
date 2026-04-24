const metricColors = {
  cpu: "#38bdf8",
  memory: "#f59e0b",
  disk: "#34d399",
};

function formatMetric(value) {
  if (value === null || value === undefined || Number.isNaN(Number(value))) {
    return "--";
  }

  return `${Number(value).toFixed(1)}%`;
}

function formatTimestamp(value) {
  if (!value) {
    return "No timestamp available";
  }

  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return value;
  }

  return parsed.toLocaleString();
}

function getUsageTone(value) {
  const numericValue = Number(value);

  if (Number.isNaN(numericValue)) {
    return "#94a3b8";
  }

  if (numericValue >= 90) {
    return "#f87171";
  }

  if (numericValue >= 75) {
    return "#fbbf24";
  }

  return "#34d399";
}

function TelemetryMetric({ label, value, accent }) {
  return (
    <div
      style={{
        background: "rgba(15, 23, 42, 0.78)",
        border: "1px solid rgba(148, 163, 184, 0.15)",
        borderRadius: "18px",
        padding: "18px",
      }}
    >
      <div
        style={{
          color: "#94a3b8",
          fontSize: "0.85rem",
          marginBottom: "10px",
          textTransform: "uppercase",
          letterSpacing: "0.08em",
        }}
      >
        {label}
      </div>
      <div
        style={{
          color: accent || getUsageTone(value),
          fontSize: "2rem",
          fontWeight: 700,
        }}
      >
        {formatMetric(value)}
      </div>
    </div>
  );
}

function TelemetryCard({ telemetry, loading, error }) {
  const machineIdentifier =
    telemetry?.machineIdentifier || telemetry?.machineId || "Unknown machine";

  return (
    <section
      style={{
        background: "rgba(15, 23, 42, 0.72)",
        border: "1px solid rgba(148, 163, 184, 0.14)",
        borderRadius: "24px",
        padding: "24px",
        boxShadow: "0 24px 60px rgba(2, 6, 23, 0.35)",
      }}
    >
      <div
        style={{
          alignItems: "center",
          display: "flex",
          flexWrap: "wrap",
          gap: "12px",
          justifyContent: "space-between",
          marginBottom: "20px",
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
            Telemetry
          </div>
          <h2 style={{ fontSize: "1.8rem", margin: 0 }}>{machineIdentifier}</h2>
        </div>
        <div
          style={{
            background: "rgba(30, 41, 59, 0.9)",
            border: "1px solid rgba(148, 163, 184, 0.14)",
            borderRadius: "999px",
            color: "#cbd5e1",
            padding: "10px 14px",
          }}
        >
          {loading ? "Refreshing..." : "Live every 5s"}
        </div>
      </div>

      {error ? (
        <div
          style={{
            background: "rgba(127, 29, 29, 0.45)",
            border: "1px solid rgba(248, 113, 113, 0.35)",
            borderRadius: "16px",
            color: "#fecaca",
            marginBottom: "18px",
            padding: "14px 16px",
          }}
        >
          {error}
        </div>
      ) : null}

      <div
        style={{
          display: "grid",
          gap: "16px",
          gridTemplateColumns: "repeat(auto-fit, minmax(180px, 1fr))",
          marginBottom: "18px",
        }}
      >
        <TelemetryMetric
          label="CPU Usage"
          value={telemetry?.cpuUsage}
          accent={metricColors.cpu}
        />
        <TelemetryMetric
          label="Memory Usage"
          value={telemetry?.memoryUsage}
          accent={metricColors.memory}
        />
        <TelemetryMetric
          label="Disk Usage"
          value={telemetry?.diskUsage}
          accent={metricColors.disk}
        />
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
          <div style={{ color: "#94a3b8", marginBottom: "8px" }}>Hostname</div>
          <div style={{ fontSize: "1.05rem", fontWeight: 600 }}>
            {telemetry?.hostname || "--"}
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
          <div style={{ color: "#94a3b8", marginBottom: "8px" }}>Timestamp</div>
          <div style={{ fontSize: "1.05rem", fontWeight: 600 }}>
            {formatTimestamp(telemetry?.timestamp || telemetry?.createdAt)}
          </div>
        </div>
      </div>
    </section>
  );
}

export default TelemetryCard;
