import { useMemo, useState } from "react";
import {
  CartesianGrid,
  Line,
  LineChart,
  ReferenceDot,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { useNavigate } from "react-router-dom";
import {
  buildAlertMarkers,
  buildAnomalyMarkers,
  buildChartData,
  formatAlertTime,
  formatChartTime,
  metricMeta,
} from "./telemetryChartUtils";
import { formatPercent, formatTimestamp, getUsageTone } from "../utils/operations";

function TelemetryLegend() {
  return (
    <div className="overview-legend">
      <span className="overview-legend-item">
        <span className="metric-swatch" style={{ background: metricMeta.CPU.color }} />
        CPU
      </span>
      <span className="overview-legend-item">
        <span className="metric-swatch" style={{ background: metricMeta.MEMORY.color }} />
        Memory
      </span>
      <span className="overview-legend-item">
        <span className="metric-swatch" style={{ background: metricMeta.DISK.color }} />
        Disk
      </span>
      <span className="overview-legend-item">
        <span className="chart-legend-swatch dot" style={{ "--legend-color": "#ef4444" }} />
        Active alert
      </span>
      <span className="overview-legend-item">
        <span className="chart-legend-swatch dot" style={{ "--legend-color": "#94a3b8" }} />
        Resolved alert
      </span>
      <span className="overview-legend-item">
        <span className="chart-legend-swatch diamond" style={{ "--legend-color": "#a855f7" }} />
        Anomaly
      </span>
    </div>
  );
}

function OverviewTooltip({ active, payload, label }) {
  if (!active || !payload?.length) {
    return null;
  }

  return (
    <div className="overview-tooltip">
      <div className="overview-tooltip-time">{label}</div>
      {payload.map((entry) => (
        <div key={entry.dataKey} className="overview-tooltip-row" style={{ color: entry.color }}>
          <span>{entry.name}</span>
          <strong>{Number(entry.value).toFixed(1)}%</strong>
        </div>
      ))}
    </div>
  );
}

function buildInvestigateMessage(event) {
  if (!event) {
    return "";
  }

  if (event.type === "anomaly") {
    return [
      "Investigate this anomaly:",
      `Machine: ${event.machineIdentifier || "Unknown"}`,
      `Metric: ${event.metricType || "Unknown"}`,
      `Severity: ${event.severity || "UNKNOWN"}`,
      `Value: ${Number.isFinite(Number(event.y)) ? `${Number(event.y).toFixed(1)}%` : "Unknown"}`,
      `Z-score: ${Number.isFinite(Number(event.anomalyScore)) ? Number(event.anomalyScore).toFixed(2) : "Unknown"}`,
      `Detected at: ${event.detectedAt || event.timestamp || "Unknown"}`,
      `Summary: ${event.explanation || "Potential anomaly detected."}`,
      "Provide a short operator triage plan and what to verify next.",
    ].join("\n");
  }

  return [
    "Investigate this alert:",
    `Machine: ${event.machineIdentifier || "Unknown"}`,
    `Metric: ${event.metricType || "Unknown"}`,
    `Severity: ${event.severity || "UNKNOWN"}`,
    `Status: ${event.status || "UNKNOWN"}`,
    `Value: ${Number.isFinite(Number(event.y)) ? `${Number(event.y).toFixed(1)}%` : "Unknown"}`,
    `Timestamp: ${event.createdAt || event.timestamp || "Unknown"}`,
    `Summary: ${event.message || "Alert triggered."}`,
    "Provide a concise operator triage plan and what to verify next.",
  ].join("\n");
}

function EventHoverCard({ event, onInvestigate }) {
  if (!event) {
    return null;
  }

  const isAnomaly = event.type === "anomaly";
  const title = isAnomaly
    ? "Anomaly detected"
    : event.status === "ACTIVE"
      ? "Alert triggered"
      : "Alert resolved";

  return (
    <div className="overview-event-card">
      <div className="overview-tooltip-time">{title}</div>
      <div className="overview-event-copy">Metric: {event.metricType || "Unknown"}</div>
      <div className="overview-event-copy">
        Value: {Number.isFinite(Number(event.y)) ? `${Number(event.y).toFixed(1)}%` : "Unknown"}
      </div>
      <div className="overview-event-copy">Severity: {event.severity || "UNKNOWN"}</div>
      <div className="overview-event-copy">
        {isAnomaly
          ? `Z-score: ${Number.isFinite(Number(event.anomalyScore)) ? Number(event.anomalyScore).toFixed(2) : "Unknown"}`
          : `Status: ${event.status || "UNKNOWN"}`}
      </div>
      <div className="overview-event-copy">Timestamp: {formatAlertTime(event.createdAt || event.detectedAt || event.timestamp)}</div>
      <button type="button" className="chart-tooltip-action" onClick={() => onInvestigate(event)}>
        Investigate with AI
      </button>
    </div>
  );
}

function OverviewStat({ label, value, tone, subtext }) {
  return (
    <div className="overview-stat">
      <div className="overview-stat-top">
        <span className={`status-dot ${tone}`} />
        <span className="card-label">{label}</span>
      </div>
      <div className="overview-stat-value">{value}</div>
      <div className="overview-stat-subtext">{subtext}</div>
    </div>
  );
}

function getOverviewAlertMarkerStyle(marker) {
  const isActive = marker.status === "ACTIVE";

  return {
    fill: isActive ? "#ef4444" : "rgba(148, 163, 184, 0.82)",
    stroke: "rgba(241, 245, 249, 0.92)",
    strokeWidth: isActive ? 2 : 1.5,
    r: isActive ? 6.5 : 5,
  };
}

function OverviewDiamondMarker({ cx, cy, size, fill, stroke, strokeWidth }) {
  if (cx == null || cy == null) {
    return null;
  }

  const radius = size;
  const path = `${cx},${cy - radius} ${cx + radius},${cy} ${cx},${cy + radius} ${cx - radius},${cy}`;

  return <polygon points={path} fill={fill} stroke={stroke} strokeWidth={strokeWidth} />;
}

function DashboardTelemetryPanel({ telemetryHistory = [], latestTelemetry = null, alerts = [], anomalies = [] }) {
  const navigate = useNavigate();
  const chartData = useMemo(() => buildChartData(telemetryHistory).slice(-24), [telemetryHistory]);
  const latestPoint = chartData[chartData.length - 1] || null;
  const alertMarkers = useMemo(() => buildAlertMarkers(chartData, alerts).slice(0, 8), [alerts, chartData]);
  const anomalyMarkers = useMemo(() => buildAnomalyMarkers(chartData, anomalies).slice(0, 8), [anomalies, chartData]);
  const [hoveredEvent, setHoveredEvent] = useState(null);

  const handleInvestigateEvent = (event) => {
    if (!event) {
      return;
    }

    navigate("/assistant", {
      state: {
        machineIdentifier: event.machineIdentifier || "",
        triggerMessage: {
          id: `overview-${event.eventKey}`,
          message: buildInvestigateMessage(event),
        },
      },
    });
  };

  return (
    <section className="surface-card section-card dashboard-telemetry-panel">
      <div className="section-header">
        <div>
          <div className="card-label">Telemetry</div>
          <h2 className="section-title">Live Resource Trends</h2>
        </div>
        <div className="dashboard-telemetry-meta">
          <span className="status-pill blue">
            <span className="status-dot blue" />
            {chartData.length} samples
          </span>
          <span className="machine-card-subtle">
            Latest: {formatTimestamp(latestTelemetry?.timestamp || latestTelemetry?.createdAt)}
          </span>
        </div>
      </div>

      <div className="overview-stat-grid">
        <OverviewStat
          label="CPU"
          subtext="Current utilization"
          tone={getUsageTone(latestPoint?.cpuUsage)}
          value={formatPercent(latestPoint?.cpuUsage)}
        />
        <OverviewStat
          label="Memory"
          subtext="Current utilization"
          tone={getUsageTone(latestPoint?.memoryUsage)}
          value={formatPercent(latestPoint?.memoryUsage)}
        />
        <OverviewStat
          label="Disk"
          subtext="Current utilization"
          tone={getUsageTone(latestPoint?.diskUsage)}
          value={formatPercent(latestPoint?.diskUsage)}
        />
      </div>

      <TelemetryLegend />

      {chartData.length < 2 ? (
        <div className="empty-state telemetry-empty-state">
          <div>
            <div className="section-title" style={{ marginBottom: "8px" }}>
              {chartData.length === 0 ? "No telemetry samples yet" : "Waiting for more telemetry"}
            </div>
            <div className="machine-card-subtle">
              {chartData.length === 0
                ? "Live resource trends will appear as soon as machines begin reporting."
                : "Live resource trends will appear after a few more samples arrive."}
            </div>
          </div>
        </div>
      ) : (
        <div className="overview-chart-frame">
          <ResponsiveContainer height={320} width="100%">
            <LineChart data={chartData} margin={{ top: 8, right: 12, left: -18, bottom: 6 }}>
              <CartesianGrid stroke="rgba(148, 163, 184, 0.08)" strokeDasharray="3 3" vertical={false} />
              <XAxis
                axisLine={false}
                dataKey="time"
                tick={{ fill: "#8ea0b8", fontSize: 12 }}
                tickLine={false}
                tickMargin={10}
              />
              <YAxis
                axisLine={false}
                domain={[0, 100]}
                tick={{ fill: "#8ea0b8", fontSize: 12 }}
                tickFormatter={(value) => `${value}%`}
                tickLine={false}
                tickMargin={8}
              />
              <Tooltip content={<OverviewTooltip />} labelFormatter={(label) => formatChartTime(label)} />
              {hoveredEvent?.x ? (
                <ReferenceLine
                  ifOverflow="visible"
                  stroke={hoveredEvent.type === "anomaly" ? "rgba(168, 85, 247, 0.26)" : "rgba(241, 245, 249, 0.24)"}
                  strokeDasharray="4 4"
                  x={hoveredEvent.x}
                />
              ) : null}
              <Line
                dataKey={metricMeta.CPU.key}
                dot={false}
                name="CPU"
                stroke={metricMeta.CPU.color}
                strokeWidth={3}
                type="monotone"
              />
              <Line
                dataKey={metricMeta.MEMORY.key}
                dot={false}
                name="Memory"
                stroke={metricMeta.MEMORY.color}
                strokeWidth={3}
                type="monotone"
              />
              <Line
                dataKey={metricMeta.DISK.key}
                dot={false}
                name="Disk"
                stroke={metricMeta.DISK.color}
                strokeWidth={3}
                type="monotone"
              />
              {alertMarkers.map((marker) => {
                const style = getOverviewAlertMarkerStyle(marker);

                return (
                  <ReferenceDot
                    key={`overview-alert-${marker.id}`}
                    x={marker.x}
                    y={marker.y}
                    fill={style.fill}
                    ifOverflow="visible"
                    isFront
                    r={style.r}
                    stroke={style.stroke}
                    strokeWidth={style.strokeWidth}
                    onClick={() => handleInvestigateEvent(marker)}
                    onMouseEnter={() => setHoveredEvent(marker)}
                    onMouseLeave={() => setHoveredEvent((current) => (current?.eventKey === marker.eventKey ? null : current))}
                  />
                );
              })}
              {anomalyMarkers.map((marker) => (
                <ReferenceDot
                  key={`overview-anomaly-${marker.id}`}
                  x={marker.x}
                  y={marker.y}
                  ifOverflow="visible"
                  isFront
                  shape={(props) => (
                    <OverviewDiamondMarker
                      {...props}
                      fill="#a855f7"
                      size={6}
                      stroke="#f3e8ff"
                      strokeWidth={1.4}
                    />
                  )}
                  onClick={() => handleInvestigateEvent(marker)}
                  onMouseEnter={() => setHoveredEvent(marker)}
                  onMouseLeave={() => setHoveredEvent((current) => (current?.eventKey === marker.eventKey ? null : current))}
                />
              ))}
            </LineChart>
          </ResponsiveContainer>
          <EventHoverCard event={hoveredEvent} onInvestigate={handleInvestigateEvent} />
        </div>
      )}
    </section>
  );
}

export default DashboardTelemetryPanel;
