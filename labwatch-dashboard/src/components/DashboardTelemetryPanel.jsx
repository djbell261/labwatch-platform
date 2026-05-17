import { useMemo } from "react";
import {
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { buildChartData, formatChartTime, metricMeta } from "./telemetryChartUtils";
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

function DashboardTelemetryPanel({ telemetryHistory = [], latestTelemetry = null }) {
  const chartData = useMemo(() => buildChartData(telemetryHistory).slice(-24), [telemetryHistory]);
  const latestPoint = chartData[chartData.length - 1] || null;

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
            <div className="section-title" style={{ marginBottom: "8px" }}>Waiting for telemetry</div>
            <div className="machine-card-subtle">Live resource trends will appear after a few more samples arrive.</div>
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
            </LineChart>
          </ResponsiveContainer>
        </div>
      )}
    </section>
  );
}

export default DashboardTelemetryPanel;
