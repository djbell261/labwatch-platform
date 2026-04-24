import {
  CartesianGrid,
  LabelList,
  Legend,
  Line,
  LineChart,
  ReferenceDot,
  ResponsiveContainer,
  Scatter,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import {
  buildAlertMarkers,
  buildAnomalyMarkers,
  buildChartData,
  formatAlertTime,
  metricMeta,
} from "./telemetryChartUtils";

function getAlertMarkerStyle(marker) {
  const isActive = marker.status === "ACTIVE";
  const isHighSeverity = marker.severity === "HIGH" || marker.severity === "CRITICAL";

  return {
    fill: isActive ? "#ef4444" : marker.color,
    stroke: "#ffffff",
    strokeWidth: isHighSeverity ? 2.5 : 1.5,
    r: isActive ? 11 : 6.5,
    fillOpacity: isActive ? 0.98 : 0.35,
  };
}

function getAnomalyMarkerStyle(marker) {
  return {
    fill: "#a78bfa",
    stroke: "#ffffff",
    strokeWidth: 1.5,
    r: 6,
    fillOpacity: 0.85,
  };
}

function CustomTooltip({ active, payload, label }) {
  if (!active || !payload?.length) {
    return null;
  }

  const linePayload = payload.filter((entry) => entry.dataKey);
  const markerPayload = payload.find((entry) => entry.payload?.type === "alert" || entry.payload?.type === "anomaly");

  return (
    <div
      style={{
        background: "rgba(15, 23, 42, 0.96)",
        border: "1px solid rgba(148, 163, 184, 0.2)",
        borderRadius: "14px",
        color: "#ffffff",
        maxWidth: "320px",
        padding: "12px 14px",
      }}
    >
      <div style={{ color: "#e2e8f0", fontWeight: 700, marginBottom: "8px" }}>{label}</div>

      {linePayload.map((entry) => (
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

      {markerPayload?.payload?.type === "alert" ? (
        <div
          style={{
            borderTop: "1px solid rgba(148, 163, 184, 0.16)",
            marginTop: "10px",
            paddingTop: "10px",
          }}
        >
          <div style={{ color: markerPayload.payload.color, fontWeight: 700, marginBottom: "6px" }}>
            Alert Event
          </div>
          <div style={{ color: "#e2e8f0" }}>Type: {markerPayload.payload.metricType}</div>
          <div style={{ color: "#e2e8f0" }}>Severity: {markerPayload.payload.severity}</div>
          <div style={{ color: "#e2e8f0" }}>Status: {markerPayload.payload.status}</div>
          <div style={{ color: "#94a3b8" }}>Created: {formatAlertTime(markerPayload.payload.createdAt)}</div>
          <div style={{ color: "#cbd5e1", marginTop: "6px" }}>{markerPayload.payload.message}</div>
        </div>
      ) : null}

      {markerPayload?.payload?.type === "anomaly" ? (
        <div
          style={{
            borderTop: "1px solid rgba(148, 163, 184, 0.16)",
            marginTop: "10px",
            paddingTop: "10px",
          }}
        >
          <div style={{ color: "#c4b5fd", fontWeight: 700, marginBottom: "6px" }}>
            AI Anomaly Marker
          </div>
          <div style={{ color: "#e2e8f0" }}>Metric: {markerPayload.payload.metricType}</div>
          <div style={{ color: "#e2e8f0" }}>Severity: {markerPayload.payload.severity}</div>
          <div style={{ color: "#e2e8f0" }}>Score: {markerPayload.payload.score ?? "--"}</div>
          <div style={{ color: "#94a3b8" }}>Timestamp: {formatAlertTime(markerPayload.payload.timestamp)}</div>
          <div style={{ color: "#cbd5e1", marginTop: "6px" }}>
            {markerPayload.payload.explanation}
          </div>
        </div>
      ) : null}
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

function renderAlertMarker(marker) {
  const style = getAlertMarkerStyle(marker);

  return (
    <ReferenceDot
      key={marker.id}
      x={marker.x}
      y={marker.y}
      r={style.r}
      fill={style.fill}
      fillOpacity={style.fillOpacity}
      stroke={style.stroke}
      strokeWidth={style.strokeWidth}
      ifOverflow="visible"
      isFront
    />
  );
}

function TelemetryTrendChart({ telemetryHistory, alerts, anomalies = [] }) {
  const chartData = buildChartData(telemetryHistory);
  const alertMarkers = buildAlertMarkers(chartData, alerts);
  const anomalyMarkers = buildAnomalyMarkers(chartData, anomalies);
  const totalAlerts = Array.isArray(alerts) ? alerts.length : 0;
  const visibleAlerts = Array.isArray(alerts)
    ? alerts.filter((alert) => alert?.createdAt && metricMeta[alert.alertType]).length
    : 0;

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
          {chartData.length} points · {alertMarkers.length} alert markers
        </div>
      </div>

      <div
        style={{
          color: "#94a3b8",
          fontSize: "0.92rem",
          marginBottom: "12px",
        }}
      >
        total alerts received: {totalAlerts} · visible alerts: {visibleAlerts} · alert markers:{" "}
        {alertMarkers.length}
      </div>

      <div
        style={{
          alignItems: "center",
          color: "#cbd5e1",
          display: "flex",
          flexWrap: "wrap",
          gap: "16px",
          marginBottom: "14px",
        }}
      >
        <div
          style={{
            alignItems: "center",
            display: "flex",
            gap: "8px",
          }}
        >
          <span
            style={{
              background: "#ef4444",
              border: "2px solid #ffffff",
              borderRadius: "999px",
              display: "inline-block",
              height: "14px",
              width: "14px",
            }}
          />
          <span>Red marker = active alert</span>
        </div>

        <div
          style={{
            alignItems: "center",
            display: "flex",
            gap: "8px",
          }}
        >
          <span
            style={{
              background: "rgba(148, 163, 184, 0.45)",
              border: "2px solid #ffffff",
              borderRadius: "999px",
              display: "inline-block",
              height: "12px",
              width: "12px",
            }}
          />
          <span>Muted marker = resolved alert</span>
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

                {alertMarkers.map(renderAlertMarker)}

                {anomalyMarkers.length > 0 ? (
                  <Scatter
                    data={anomalyMarkers}
                    dataKey="y"
                    fill="#a78bfa"
                    name="AI Anomalies"
                  >
                    <LabelList dataKey="metricType" position="top" fill="#c4b5fd" fontSize={11} />
                  </Scatter>
                ) : null}

                {anomalyMarkers.map((marker) => {
                  const style = getAnomalyMarkerStyle(marker);
                  return (
                    <ReferenceDot
                      key={marker.id}
                      x={marker.x}
                      y={marker.y}
                      r={style.r}
                      fill={style.fill}
                      fillOpacity={style.fillOpacity}
                      stroke={style.stroke}
                      strokeWidth={style.strokeWidth}
                      ifOverflow="visible"
                      isFront
                    />
                  );
                })}
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
