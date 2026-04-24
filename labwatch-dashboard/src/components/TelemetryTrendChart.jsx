import {
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ReferenceDot,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";

const metricMeta = {
  CPU: { key: "cpuUsage", color: "#38bdf8", label: "CPU" },
  MEMORY: { key: "memoryUsage", color: "#f59e0b", label: "Memory" },
  DISK: { key: "diskUsage", color: "#34d399", label: "Disk" },
};

function formatChartTime(value) {
  if (!value) {
    return "--";
  }

  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return value;
  }

  return parsed.toLocaleTimeString([], {
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  });
}

function formatAlertTime(value) {
  if (!value) {
    return "Unknown time";
  }

  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return value;
  }

  return parsed.toLocaleString();
}

function buildChartData(telemetryHistory) {
  if (!Array.isArray(telemetryHistory)) {
    return [];
  }

  return [...telemetryHistory]
    .sort((left, right) => {
      const leftTime = new Date(left.timestamp || left.createdAt || 0).getTime();
      const rightTime = new Date(right.timestamp || right.createdAt || 0).getTime();
      return leftTime - rightTime;
    })
    .map((snapshot) => ({
      id: snapshot.snapshotId || snapshot.id,
      timestamp: snapshot.timestamp || snapshot.createdAt,
      time: formatChartTime(snapshot.timestamp || snapshot.createdAt),
      cpuUsage: Number(snapshot.cpuUsage ?? 0),
      memoryUsage: Number(snapshot.memoryUsage ?? 0),
      diskUsage: Number(snapshot.diskUsage ?? 0),
    }));
}

function buildAlertMarkers(chartData, alerts) {
  if (!Array.isArray(alerts) || alerts.length === 0 || chartData.length === 0) {
    return [];
  }

  const recentChartPoints = chartData.slice(-12);

  return alerts
    .filter((alert) => alert?.createdAt && metricMeta[alert.alertType])
    .slice()
    .sort((left, right) => new Date(left.createdAt).getTime() - new Date(right.createdAt).getTime())
    .slice(-8)
    .map((alert) => {
      const metric = metricMeta[alert.alertType];
      const alertTimestamp = new Date(alert.createdAt).getTime();
      const closestPoint = recentChartPoints.reduce((closest, point) => {
        if (!closest) {
          return point;
        }

        const closestDiff = Math.abs(new Date(closest.timestamp).getTime() - alertTimestamp);
        const pointDiff = Math.abs(new Date(point.timestamp).getTime() - alertTimestamp);
        return pointDiff < closestDiff ? point : closest;
      }, null);

      if (!closestPoint) {
        return null;
      }

      return {
        id: alert.id || `${alert.alertType}-${alert.createdAt}`,
        label: alert.alertType,
        severity: alert.severity || "UNKNOWN",
        status: alert.status || "UNKNOWN",
        x: closestPoint.time,
        y: closestPoint[metric.key],
        color: metric.color,
      };
    })
    .filter(Boolean);
}

function CustomTooltip({ active, payload, label }) {
  if (!active || !payload?.length) {
    return null;
  }

  return (
    <div
      style={{
        background: "rgba(15, 23, 42, 0.96)",
        border: "1px solid rgba(148, 163, 184, 0.2)",
        borderRadius: "14px",
        color: "#ffffff",
        padding: "12px 14px",
      }}
    >
      <div style={{ color: "#e2e8f0", fontWeight: 700, marginBottom: "8px" }}>{label}</div>
      {payload.map((entry) => (
        <div
          key={entry.dataKey}
          style={{
            alignItems: "center",
            color: entry.color,
            display: "flex",
            gap: "8px",
            marginTop: "4px",
          }}
        >
          <span
            style={{
              background: entry.color,
              borderRadius: "999px",
              display: "inline-block",
              height: "8px",
              width: "8px",
            }}
          />
          <span>
            {entry.name}: {Number(entry.value).toFixed(1)}%
          </span>
        </div>
      ))}
    </div>
  );
}

function AlertEventsStrip({ alerts }) {
  const recentAlerts = Array.isArray(alerts)
    ? alerts
        .filter((alert) => alert?.createdAt)
        .slice()
        .sort((left, right) => new Date(right.createdAt).getTime() - new Date(left.createdAt).getTime())
        .slice(0, 6)
    : [];

  if (recentAlerts.length === 0) {
    return (
      <div
        style={{
          background: "rgba(15, 23, 42, 0.55)",
          border: "1px dashed rgba(148, 163, 184, 0.2)",
          borderRadius: "16px",
          color: "#94a3b8",
          padding: "16px",
          textAlign: "center",
        }}
      >
        No recent alert events.
      </div>
    );
  }

  return (
    <div
      style={{
        display: "grid",
        gap: "10px",
        gridTemplateColumns: "repeat(auto-fit, minmax(180px, 1fr))",
      }}
    >
      {recentAlerts.map((alert) => {
        const metric = metricMeta[alert.alertType];
        const accentColor = metric?.color || "#94a3b8";

        return (
          <div
            key={alert.id || `${alert.alertType}-${alert.createdAt}`}
            style={{
              background: "rgba(15, 23, 42, 0.55)",
              border: `1px solid ${accentColor}33`,
              borderRadius: "16px",
              padding: "14px 16px",
            }}
          >
            <div
              style={{
                color: accentColor,
                fontSize: "0.82rem",
                fontWeight: 700,
                letterSpacing: "0.08em",
                marginBottom: "8px",
                textTransform: "uppercase",
              }}
            >
              {alert.alertType || "Alert"}
            </div>
            <div style={{ color: "#e2e8f0", fontWeight: 600, marginBottom: "4px" }}>
              {alert.severity || "UNKNOWN"} · {alert.status || "UNKNOWN"}
            </div>
            <div style={{ color: "#94a3b8", fontSize: "0.92rem" }}>
              {formatAlertTime(alert.createdAt)}
            </div>
          </div>
        );
      })}
    </div>
  );
}

function TelemetryTrendChart({ telemetryHistory, alerts }) {
  const chartData = buildChartData(telemetryHistory);
  const alertMarkers = buildAlertMarkers(chartData, alerts);

  return (
    <section
      style={{
        background: "rgba(15, 23, 42, 0.72)",
        border: "1px solid rgba(148, 163, 184, 0.14)",
        borderRadius: "24px",
        padding: "24px",
        boxShadow: "0 24px 60px rgba(2, 6, 23, 0.3)",
      }}
    >
      <div
        style={{
          alignItems: "center",
          display: "flex",
          flexWrap: "wrap",
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
            Trends
          </div>
          <h2 style={{ fontSize: "1.8rem", margin: 0 }}>Telemetry Trend</h2>
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
          {chartData.length} points
        </div>
      </div>

      {chartData.length < 2 ? (
        <div
          style={{
            background: "rgba(15, 23, 42, 0.55)",
            border: "1px dashed rgba(148, 163, 184, 0.25)",
            borderRadius: "18px",
            color: "#cbd5e1",
            minHeight: "320px",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            textAlign: "center",
            padding: "24px",
          }}
        >
          Waiting for more telemetry data...
        </div>
      ) : (
        <>
          <div
            style={{
              background: "rgba(15, 23, 42, 0.55)",
              border: "1px solid rgba(148, 163, 184, 0.12)",
              borderRadius: "18px",
              padding: "16px 12px 8px",
              marginBottom: "18px",
            }}
          >
            <ResponsiveContainer height={340} width="100%">
              <LineChart data={chartData} margin={{ top: 10, right: 24, left: 0, bottom: 6 }}>
                <CartesianGrid stroke="rgba(148, 163, 184, 0.12)" strokeDasharray="3 3" />
                <XAxis
                  dataKey="time"
                  stroke="#94a3b8"
                  tick={{ fill: "#94a3b8", fontSize: 12 }}
                  tickMargin={10}
                />
                <YAxis
                  domain={[0, 100]}
                  stroke="#94a3b8"
                  tick={{ fill: "#94a3b8", fontSize: 12 }}
                  tickMargin={8}
                />
                <Tooltip content={<CustomTooltip />} />
                <Legend
                  wrapperStyle={{
                    color: "#e2e8f0",
                    paddingTop: "8px",
                  }}
                />
                <Line
                  type="monotone"
                  dataKey="cpuUsage"
                  name="CPU"
                  stroke={metricMeta.CPU.color}
                  strokeWidth={2.5}
                  dot={false}
                  activeDot={{ r: 5 }}
                />
                <Line
                  type="monotone"
                  dataKey="memoryUsage"
                  name="Memory"
                  stroke={metricMeta.MEMORY.color}
                  strokeWidth={2.5}
                  dot={false}
                  activeDot={{ r: 5 }}
                />
                <Line
                  type="monotone"
                  dataKey="diskUsage"
                  name="Disk"
                  stroke={metricMeta.DISK.color}
                  strokeWidth={2.5}
                  dot={false}
                  activeDot={{ r: 5 }}
                />
                {alertMarkers.map((marker) => (
                  <ReferenceDot
                    key={marker.id}
                    x={marker.x}
                    y={marker.y}
                    r={6}
                    fill={marker.color}
                    stroke="#ffffff"
                    strokeWidth={1.5}
                    ifOverflow="visible"
                  />
                ))}
              </LineChart>
            </ResponsiveContainer>
          </div>

          <div>
            <div
              style={{
                color: "#94a3b8",
                fontSize: "0.85rem",
                letterSpacing: "0.1em",
                marginBottom: "12px",
                textTransform: "uppercase",
              }}
            >
              Alert Events
            </div>
            <AlertEventsStrip alerts={alerts} />
          </div>
        </>
      )}
    </section>
  );
}

export default TelemetryTrendChart;
