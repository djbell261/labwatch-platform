import { useMemo } from "react";
import {
  CartesianGrid,
  Line,
  LineChart,
  ReferenceLine,
  ReferenceDot,
  ResponsiveContainer,
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
  normalizeAnomaly,
} from "./telemetryChartUtils";

function formatPercentValue(value) {
  const numericValue = Number(value);
  return Number.isFinite(numericValue) ? `${numericValue.toFixed(1)}%` : "--";
}

function getAlertMarkerStyle(marker, isSelected) {
  const isActive = marker.status === "ACTIVE";

  return {
    fill: isActive ? "#ef4444" : "rgba(148, 163, 184, 0.78)",
    stroke: isSelected ? "#ffffff" : "rgba(241, 245, 249, 0.82)",
    strokeWidth: isSelected ? 3 : isActive ? 2.2 : 1.6,
    r: isSelected ? 9 : isActive ? 7 : 5.5,
    glow: isActive ? "drop-shadow(0 0 8px rgba(239, 68, 68, 0.42))" : "none",
  };
}

function getAnomalyMarkerStyle(isSelected, hasAlertAtSamePoint = false) {
  return {
    fill: "#a855f7",
    stroke: "#f3e8ff",
    strokeWidth: isSelected ? 3 : 1.5,
    r: isSelected ? 9 : 7,
    glow: hasAlertAtSamePoint
      ? "drop-shadow(0 0 10px rgba(168, 85, 247, 0.55))"
      : "drop-shadow(0 0 6px rgba(168, 85, 247, 0.24))",
  };
}

function TooltipEventAction({ markerPayload, onExplainSpike }) {
  if (!markerPayload?.payload || !onExplainSpike) {
    return null;
  }

  const marker = markerPayload.payload;
  const timestamp = marker.createdAt || marker.detectedAt || marker.timestamp;

  return (
    <button
      type="button"
      className="chart-tooltip-action"
      onClick={() =>
        onExplainSpike({
          timestamp,
          metric: marker.metricType,
          value: marker.y,
          source: marker.type,
          eventKey: marker.eventKey,
        })
      }
    >
      Investigate with AI
    </button>
  );
}

function CustomTooltip({ active, payload, label, onExplainSpike, eventLookup = {} }) {
  if (!active || !payload?.length) {
    return null;
  }

  const series = payload.filter((entry) => entry.dataKey);
  const chartEvents = eventLookup[label] || [];

  return (
    <div
      style={{
        background: "rgba(9, 14, 25, 0.96)",
        border: "1px solid rgba(148, 163, 184, 0.2)",
        borderRadius: "14px",
        boxShadow: "0 20px 30px rgba(2, 6, 23, 0.35)",
        color: "#e5edf8",
        maxWidth: "280px",
        padding: "12px 14px",
      }}
    >
      <div style={{ color: "#c4d1e3", fontWeight: 700, marginBottom: "8px" }}>{label}</div>

      {series.map((entry) => (
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

      {chartEvents.map((event) => {
        const markerPayload = { payload: event };
        const isAlert = event.type === "alert";
        const title = isAlert
          ? event.status === "ACTIVE"
            ? "Alert triggered"
            : "Alert resolved"
          : "Anomaly detected";

        return (
          <div
            key={event.eventKey}
            style={{ borderTop: "1px solid rgba(148, 163, 184, 0.16)", marginTop: "10px", paddingTop: "10px" }}
          >
            <div
              style={{
                color: isAlert
                  ? event.status === "ACTIVE"
                    ? "#fecaca"
                    : "#dbe4f0"
                  : "#d8b4fe",
                fontWeight: 700,
                marginBottom: "6px",
              }}
            >
              {title}
            </div>
            <div style={{ color: "#c4d1e3" }}>Metric: {event.metricType}</div>
            <div style={{ color: "#c4d1e3" }}>Value: {Number(event.y).toFixed(1)}%</div>
            <div style={{ color: "#c4d1e3" }}>Severity: {event.severity || "UNKNOWN"}</div>
            <div style={{ color: "#c4d1e3" }}>Status: {isAlert ? event.status : "Investigate"}</div>
            <div style={{ color: "#8ea0b8" }}>
              Timestamp: {formatAlertTime(event.createdAt || event.detectedAt || event.timestamp)}
            </div>
            <TooltipEventAction markerPayload={markerPayload} onExplainSpike={onExplainSpike} />
          </div>
        );
      })}
    </div>
  );
}

function renderAlertMarker(marker, onExplainSpike, selectedEventKey) {
  const isSelected = selectedEventKey === marker.eventKey;
  const style = getAlertMarkerStyle(marker, isSelected);

  return (
    <ReferenceDot
      key={marker.id}
      x={marker.x}
      y={marker.renderY ?? marker.y}
      fill={style.fill}
      ifOverflow="visible"
      isFront
      r={style.r}
      stroke={style.stroke}
      strokeWidth={style.strokeWidth}
      onClick={() =>
        onExplainSpike?.({
          timestamp: marker.createdAt,
          metric: marker.metricType,
          value: marker.y,
          source: "alert",
          eventKey: marker.eventKey,
        })
      }
      style={{ cursor: onExplainSpike ? "pointer" : "default", filter: style.glow }}
    />
  );
}

function DiamondMarker({ cx, cy, size, fill, stroke, strokeWidth, onClick, selected, filterStyle }) {
  if (cx == null || cy == null) {
    return null;
  }

  const radius = size;
  const path = `${cx},${cy - radius} ${cx + radius},${cy} ${cx},${cy + radius} ${cx - radius},${cy}`;

  return (
    <polygon
      points={path}
      fill={fill}
      stroke={stroke}
      strokeWidth={strokeWidth}
      style={{
        cursor: onClick ? "pointer" : "default",
        filter: filterStyle || (selected ? "drop-shadow(0 0 10px rgba(168, 85, 247, 0.42))" : "drop-shadow(0 0 6px rgba(168, 85, 247, 0.24))"),
      }}
      onClick={onClick}
    />
  );
}

function renderAnomalyMarker(marker, onExplainSpike, selectedEventKey) {
  const isSelected = selectedEventKey === marker.eventKey;
  const style = getAnomalyMarkerStyle(isSelected, marker.hasAlertAtSamePoint);

  return (
    <ReferenceDot
      key={marker.id}
      x={marker.x}
      y={marker.renderY ?? marker.y}
      ifOverflow="visible"
      isFront
      shape={(props) => (
        <DiamondMarker
          {...props}
          fill={style.fill}
          size={style.r}
          selected={isSelected}
          stroke={style.stroke}
          strokeWidth={style.strokeWidth}
          filterStyle={style.glow}
          onClick={() =>
            onExplainSpike?.({
              timestamp: marker.detectedAt || marker.timestamp,
              metric: marker.metricType,
              value: marker.y,
              source: "anomaly",
              eventKey: marker.eventKey,
            })
          }
        />
      )}
      onClick={() =>
        onExplainSpike?.({
          timestamp: marker.detectedAt || marker.timestamp,
          metric: marker.metricType,
          value: marker.y,
          source: "anomaly",
          eventKey: marker.eventKey,
        })
      }
    />
  );
}

function TelemetryActiveDot({ cx, cy, payload, dataKey, stroke, onExplainSpike }) {
  if (cx == null || cy == null || !payload || !dataKey) {
    return null;
  }

  const metric = dataKey === "cpuUsage" ? "CPU" : dataKey === "memoryUsage" ? "MEMORY" : "DISK";
  const value = payload[dataKey];

  return (
    <circle
      cx={cx}
      cy={cy}
      fill={stroke}
      r={5}
      stroke="#ffffff"
      strokeWidth={1.5}
      style={{ cursor: onExplainSpike ? "pointer" : "default" }}
      onClick={() =>
        onExplainSpike?.({
          timestamp: payload.timestamp,
          metric,
          value,
          source: "telemetry",
          eventKey: `telemetry-${metric}-${payload.timestamp}`,
        })
      }
    />
  );
}

function MetricToggle({ label, color, active, onClick }) {
  return (
    <button
      type="button"
      className={`metric-toggle ${active ? "is-active" : ""}`}
      onClick={onClick}
      style={active ? { borderColor: `${color}55`, color } : undefined}
    >
      <span className="metric-swatch" style={{ background: color }} />
      {label}
    </button>
  );
}

function EventVisibilityToggle({ label, active, onClick }) {
  return (
    <button
      type="button"
      className={`metric-toggle ${active ? "is-active" : ""}`}
      onClick={onClick}
    >
      {label}
    </button>
  );
}

function LegendItem({ label, color, shape = "line" }) {
  return (
    <div className="chart-legend-item">
      <span className={`chart-legend-swatch ${shape}`} style={{ "--legend-color": color }} />
      <span>{label}</span>
    </div>
  );
}

function LoadingChart() {
  return <div className="skeleton-block" />;
}

function EmptyChartState({ message }) {
  return (
    <div className="empty-state telemetry-empty-state">
      <div>
        <div className="section-title" style={{ marginBottom: "8px" }}>{message}</div>
        <div className="machine-card-subtle">LabWatch needs a little more telemetry before it can draw reliable trends.</div>
      </div>
    </div>
  );
}

function buildEventSummary(alertMarkers, anomalyMarkers) {
  return [...alertMarkers, ...anomalyMarkers]
    .sort((left, right) => new Date(right.timestamp || right.createdAt || right.detectedAt).getTime() - new Date(left.timestamp || left.createdAt || left.detectedAt).getTime())
    .slice(0, 5);
}

function buildVerticalEventLines(alertMarkers, anomalyMarkers) {
  const events = [...alertMarkers, ...anomalyMarkers]
    .sort((left, right) => new Date(right.timestamp || right.createdAt || right.detectedAt).getTime() - new Date(left.timestamp || left.createdAt || left.detectedAt).getTime())
    .slice(0, 8);

  const seen = new Set();
  return events.filter((event) => {
    if (!event?.x || seen.has(event.x)) {
      return false;
    }
    seen.add(event.x);
    return true;
  });
}

function buildEventLookup(alertMarkers, anomalyMarkers) {
  return [...alertMarkers, ...anomalyMarkers].reduce((lookup, event) => {
    if (!event?.x) {
      return lookup;
    }

    lookup[event.x] = lookup[event.x] || [];
    lookup[event.x].push(event);
    lookup[event.x].sort((left, right) => {
      const leftTime = new Date(left.timestamp || left.createdAt || left.detectedAt).getTime();
      const rightTime = new Date(right.timestamp || right.createdAt || right.detectedAt).getTime();
      return rightTime - leftTime;
    });
    return lookup;
  }, {});
}

function applyMarkerOffsets(alertMarkers, anomalyMarkers) {
  const alertPositions = new Set(alertMarkers.map((marker) => `${marker.x}:${marker.metricKey}`));

  const nextAlertMarkers = alertMarkers.map((marker) => {
    const hasAnomalyAtSamePoint = anomalyMarkers.some(
      (anomaly) => anomaly.x === marker.x && anomaly.metricKey === marker.metricKey
    );

    return {
      ...marker,
      hasAnomalyAtSamePoint,
      renderY: hasAnomalyAtSamePoint ? Math.min(100, marker.y + 1.4) : marker.y,
    };
  });

  const nextAnomalyMarkers = anomalyMarkers.map((marker) => {
    const hasAlertAtSamePoint = alertPositions.has(`${marker.x}:${marker.metricKey}`);

    return {
      ...marker,
      hasAlertAtSamePoint,
      renderY: hasAlertAtSamePoint ? Math.max(0, marker.y - 1.4) : marker.y,
    };
  });

  return {
    alertMarkers: nextAlertMarkers,
    anomalyMarkers: nextAnomalyMarkers,
  };
}

function eventChipMeta(event) {
  if (event.type === "anomaly") {
    return {
      className: "event-chip anomaly",
      label: `${event.metricType} anomaly`,
    };
  }

  if (event.status === "ACTIVE") {
    return {
      className: "event-chip active-alert",
      label: `${event.metricType} alert`,
    };
  }

  return {
    className: "event-chip resolved-alert",
    label: `${event.metricType} alert`,
  };
}

function TelemetryTrendChart({
  telemetryHistory,
  alerts,
  anomalies = [],
  anomaliesLoading = false,
  anomaliesError = "",
  loading = false,
  onExplainSpike,
  selectedEventKey = "",
  selectedMetrics = ["cpuUsage", "memoryUsage", "diskUsage"],
  onToggleMetric,
  eventVisibility = {
    alerts: true,
    anomalies: true,
    resolvedAlerts: true,
  },
  onToggleEventVisibility,
}) {
  const chartData = useMemo(() => buildChartData(telemetryHistory), [telemetryHistory]);
  const alertMarkers = useMemo(() => buildAlertMarkers(chartData, alerts), [chartData, alerts]);
  const normalizedAnomalies = useMemo(
    () => (Array.isArray(anomalies) ? anomalies.map(normalizeAnomaly).filter(Boolean) : []),
    [anomalies]
  );
  const anomalyMarkers = useMemo(
    () => buildAnomalyMarkers(chartData, normalizedAnomalies),
    [chartData, normalizedAnomalies]
  );
  const visibleAlertMarkers = alertMarkers.filter((marker) => {
    if (!marker.metricKey || !selectedMetrics.includes(marker.metricKey)) {
      return false;
    }
    if (marker.status !== "ACTIVE" && !eventVisibility.resolvedAlerts) {
      return false;
    }
    return eventVisibility.alerts;
  });
  const visibleAnomalyMarkers = anomalyMarkers.filter((marker) => {
    if (!marker.metricKey || !selectedMetrics.includes(marker.metricKey)) {
      return false;
    }
    return eventVisibility.anomalies;
  });
  const offsetMarkers = useMemo(
    () => applyMarkerOffsets(visibleAlertMarkers, visibleAnomalyMarkers),
    [visibleAlertMarkers, visibleAnomalyMarkers]
  );
  const persistentAlertMarkers = offsetMarkers.alertMarkers;
  const persistentAnomalyMarkers = offsetMarkers.anomalyMarkers;
  const eventSummary = useMemo(
    () => buildEventSummary(persistentAlertMarkers, persistentAnomalyMarkers),
    [persistentAlertMarkers, persistentAnomalyMarkers]
  );
  const verticalEventLines = useMemo(
    () => buildVerticalEventLines(persistentAlertMarkers, persistentAnomalyMarkers),
    [persistentAlertMarkers, persistentAnomalyMarkers]
  );
  const eventLookup = useMemo(
    () => buildEventLookup(persistentAlertMarkers, persistentAnomalyMarkers),
    [persistentAlertMarkers, persistentAnomalyMarkers]
  );
  const showNoAnomaliesHint =
    !loading &&
    !anomaliesLoading &&
    !anomaliesError &&
    chartData.length >= 2 &&
    eventVisibility.anomalies &&
    persistentAnomalyMarkers.length === 0;
  const latestPoint = chartData[chartData.length - 1] || null;
  const previousPoint = chartData[chartData.length - 2] || null;
  const trendItems = [
    {
      key: "cpuUsage",
      label: "CPU",
      value: latestPoint?.cpuUsage,
      previousValue: previousPoint?.cpuUsage,
      color: metricMeta.CPU.color,
    },
    {
      key: "memoryUsage",
      label: "Memory",
      value: latestPoint?.memoryUsage,
      previousValue: previousPoint?.memoryUsage,
      color: metricMeta.MEMORY.color,
    },
    {
      key: "diskUsage",
      label: "Disk",
      value: latestPoint?.diskUsage,
      previousValue: previousPoint?.diskUsage,
      color: metricMeta.DISK.color,
    },
  ];

  return (
    <section className="surface-card section-card">
      <div className="section-header">
        <div>
          <div className="card-label">Telemetry</div>
          <h2 className="section-title">Live Resource Trends</h2>
        </div>
        <div className="status-pill blue">
          <span className="status-dot blue" />
          {chartData.length} samples
        </div>
      </div>

      <div className="chart-toolbar">
        <div className="metric-toggle-group">
          {Object.values(metricMeta).map((metric) => (
            <MetricToggle
              key={metric.key}
              label={metric.label}
              color={metric.color}
              active={selectedMetrics.includes(metric.key)}
              onClick={() => onToggleMetric?.(metric.key)}
            />
          ))}
        </div>
        <div className="metric-toggle-group">
          <EventVisibilityToggle
            label="Alerts"
            active={eventVisibility.alerts}
            onClick={() => onToggleEventVisibility?.("alerts")}
          />
          <EventVisibilityToggle
            label="Anomalies"
            active={eventVisibility.anomalies}
            onClick={() => onToggleEventVisibility?.("anomalies")}
          />
          <EventVisibilityToggle
            label="Resolved"
            active={eventVisibility.resolvedAlerts}
            onClick={() => onToggleEventVisibility?.("resolvedAlerts")}
          />
        </div>
        {anomaliesLoading ? <div className="machine-card-subtle">Refreshing anomaly markers…</div> : null}
      </div>

      {anomaliesError ? <div className="inline-notice warning"><span>{anomaliesError}</span></div> : null}

      <div className="chart-legend">
        <LegendItem color={metricMeta.CPU.color} label="Blue line = CPU" shape="line" />
        <LegendItem color={metricMeta.MEMORY.color} label="Orange line = Memory" shape="line" />
        <LegendItem color={metricMeta.DISK.color} label="Green line = Disk" shape="line" />
        <LegendItem color="#ef4444" label="Red dot = Active alert" shape="dot" />
        <LegendItem color="#94a3b8" label="Gray dot = Resolved alert" shape="dot" />
        <LegendItem color="#a855f7" label="Purple diamond = Anomaly" shape="diamond" />
      </div>

      <div className="telemetry-stat-row">
        {trendItems.map((item) => {
          const delta = Number(item.value) - Number(item.previousValue);
          const hasDelta = Number.isFinite(delta);

          return (
            <div key={item.key} className="telemetry-stat-card">
              <div className="telemetry-stat-top">
                <span className="metric-swatch" style={{ background: item.color }} />
                <span className="card-label">{item.label}</span>
              </div>
              <div className="telemetry-stat-value">{formatPercentValue(item.value)}</div>
              <div className="telemetry-stat-delta">
                {hasDelta ? `${delta >= 0 ? "+" : ""}${delta.toFixed(1)} pts vs previous sample` : "Waiting for comparison"}
              </div>
            </div>
          );
        })}
      </div>

      {loading ? (
        <LoadingChart />
      ) : chartData.length < 2 ? (
        <EmptyChartState message="Not enough telemetry yet" />
      ) : (
        <>
          <div className="chart-frame">
            <ResponsiveContainer height={320} width="100%">
              <LineChart data={chartData} margin={{ top: 10, right: 14, left: -12, bottom: 4 }}>
                <CartesianGrid stroke="rgba(148, 163, 184, 0.08)" strokeDasharray="3 3" vertical={false} />
                <XAxis dataKey="time" stroke="#8ea0b8" tick={{ fill: "#8ea0b8", fontSize: 12 }} tickMargin={10} axisLine={false} tickLine={false} />
                <YAxis
                  axisLine={false}
                  tick={{ fill: "#8ea0b8", fontSize: 12 }}
                  tickLine={false}
                  tickMargin={8}
                  stroke="#8ea0b8"
                  domain={[0, 100]}
                />
                <Tooltip content={<CustomTooltip eventLookup={eventLookup} onExplainSpike={onExplainSpike} />} />

                {verticalEventLines.map((event) => (
                  <ReferenceLine
                    key={`line-${event.id}`}
                    x={event.x}
                    stroke={event.type === "anomaly" ? "rgba(168, 85, 247, 0.24)" : event.status === "ACTIVE" ? "rgba(239, 68, 68, 0.22)" : "rgba(148, 163, 184, 0.18)"}
                    strokeDasharray="3 4"
                    ifOverflow="visible"
                  />
                ))}

                {selectedMetrics.includes(metricMeta.CPU.key) ? (
                  <Line
                    type="monotone"
                    dataKey={metricMeta.CPU.key}
                    name={metricMeta.CPU.label}
                    stroke={metricMeta.CPU.color}
                    strokeWidth={3.2}
                    dot={false}
                    connectNulls
                    activeDot={(props) => <TelemetryActiveDot {...props} onExplainSpike={onExplainSpike} />}
                  />
                ) : null}
                {selectedMetrics.includes(metricMeta.MEMORY.key) ? (
                  <Line
                    type="monotone"
                    dataKey={metricMeta.MEMORY.key}
                    name={metricMeta.MEMORY.label}
                    stroke={metricMeta.MEMORY.color}
                    strokeWidth={3.2}
                    dot={false}
                    connectNulls
                    activeDot={(props) => <TelemetryActiveDot {...props} onExplainSpike={onExplainSpike} />}
                  />
                ) : null}
                {selectedMetrics.includes(metricMeta.DISK.key) ? (
                  <Line
                    type="monotone"
                    dataKey={metricMeta.DISK.key}
                    name={metricMeta.DISK.label}
                    stroke={metricMeta.DISK.color}
                    strokeWidth={3.2}
                    dot={false}
                    connectNulls
                    activeDot={(props) => <TelemetryActiveDot {...props} onExplainSpike={onExplainSpike} />}
                  />
                ) : null}

                {persistentAlertMarkers.map((marker) => renderAlertMarker(marker, onExplainSpike, selectedEventKey))}
                {persistentAnomalyMarkers.map((marker) => renderAnomalyMarker(marker, onExplainSpike, selectedEventKey))}
              </LineChart>
            </ResponsiveContainer>
          </div>

          {showNoAnomaliesHint ? (
            <div className="machine-card-subtle" style={{ marginTop: "12px" }}>
              No anomalies detected yet — system may still be learning
            </div>
          ) : null}
        </>
      )}

      {eventSummary.length > 0 ? (
        <div>
          <div className="card-label" style={{ marginBottom: "10px" }}>Recent Events</div>
          <div className="event-chip-row">
          {eventSummary.map((event) => {
            const meta = eventChipMeta(event);
            const timestamp = event.createdAt || event.detectedAt || event.timestamp;

            return (
              <button
                key={event.id}
                type="button"
                className={meta.className}
                onClick={() =>
                  onExplainSpike?.({
                    timestamp,
                    metric: event.metricType,
                    value: event.y,
                    source: event.type,
                    eventKey: event.eventKey,
                  })
                }
              >
                {meta.label} · {formatAlertTime(timestamp)}
              </button>
            );
          })}
        </div>
        </div>
      ) : null}
    </section>
  );
}

export default TelemetryTrendChart;
