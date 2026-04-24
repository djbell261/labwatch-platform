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

function getStatusColor(status) {
  return status === "ACTIVE" ? "#ef4444" : "#22c55e";
}

function AlertsPanel({ alerts, loading, error }) {
  return (
    <section
      style={{
        background: "rgba(15, 23, 42, 0.72)",
        border: "1px solid rgba(148, 163, 184, 0.14)",
        borderRadius: "24px",
        padding: "24px",
        boxShadow: "0 24px 60px rgba(2, 6, 23, 0.25)",
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
            Alerts
          </div>
          <h2 style={{ fontSize: "1.8rem", margin: 0 }}>Alert Activity</h2>
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
          {loading ? "Refreshing..." : `${alerts.length} total`}
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
          gap: "12px",
        }}
      >
        {alerts.length === 0 ? (
          <div
            style={{
              background: "rgba(15, 23, 42, 0.55)",
              border: "1px dashed rgba(148, 163, 184, 0.25)",
              borderRadius: "18px",
              color: "#cbd5e1",
              padding: "22px",
              textAlign: "center",
            }}
          >
            No alerts available.
          </div>
        ) : (
          alerts.map((alert) => {
            const alertType = alert.alertType || alert.eventType || "Unknown";
            const status = alert.status || "UNKNOWN";
            const timestamp = alert.createdAt || alert.timestamp || alert.resolvedAt;

            return (
              <div
                key={alert.id || `${alertType}-${timestamp}`}
                style={{
                  alignItems: "center",
                  background: "rgba(15, 23, 42, 0.55)",
                  border: "1px solid rgba(148, 163, 184, 0.12)",
                  borderRadius: "18px",
                  display: "grid",
                  gap: "12px",
                  gridTemplateColumns: "minmax(120px, 1fr) minmax(120px, 160px) minmax(160px, 1.3fr)",
                  padding: "16px 18px",
                }}
              >
                <div>
                  <div style={{ color: "#94a3b8", fontSize: "0.82rem", marginBottom: "6px" }}>
                    Alert Type
                  </div>
                  <div style={{ fontSize: "1rem", fontWeight: 600 }}>{alertType}</div>
                </div>

                <div>
                  <div style={{ color: "#94a3b8", fontSize: "0.82rem", marginBottom: "6px" }}>
                    Status
                  </div>
                  <div
                    style={{
                      color: getStatusColor(status),
                      fontSize: "0.95rem",
                      fontWeight: 700,
                    }}
                  >
                    {status}
                  </div>
                </div>

                <div>
                  <div style={{ color: "#94a3b8", fontSize: "0.82rem", marginBottom: "6px" }}>
                    Timestamp
                  </div>
                  <div style={{ fontSize: "0.98rem", fontWeight: 500 }}>
                    {formatTimestamp(timestamp)}
                  </div>
                </div>
              </div>
            );
          })
        )}
      </div>
    </section>
  );
}

export default AlertsPanel;
