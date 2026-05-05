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

function formatTopProcess(topProcess) {
  if (!topProcess?.processName) {
    return "No data";
  }

  const cpuLabel =
    topProcess.cpuPercent !== null && topProcess.cpuPercent !== undefined
      ? `${topProcess.cpuPercent.toFixed(1)}% CPU`
      : null;
  const memoryLabel =
    topProcess.memoryPercent !== null && topProcess.memoryPercent !== undefined
      ? `${topProcess.memoryPercent.toFixed(1)}% memory`
      : null;
  const usageLabel = [cpuLabel, memoryLabel].filter(Boolean).join(" · ");

  return usageLabel ? `${topProcess.processName} (${usageLabel})` : topProcess.processName;
}

function resolveTopIssue({ cpuUsage, memoryUsage, diskUsage, activeAlerts, anomalyList }) {
  const activeAlertTypes = activeAlerts
    .map((alert) => String(alert?.alertType || "").toUpperCase())
    .filter(Boolean);
  const anomalyTypes = anomalyList
    .map((anomaly) => String(anomaly?.metricType || anomaly?.eventType || anomaly?.alertType || "").toUpperCase())
    .filter(Boolean);

  if (activeAlertTypes.includes("MEMORY") || memoryUsage > 80) {
    return "Memory pressure";
  }
  if (activeAlertTypes.includes("DISK") || diskUsage > 85) {
    return "Disk pressure";
  }
  if (activeAlertTypes.includes("CPU") || cpuUsage > 85) {
    return "CPU pressure";
  }
  if (anomalyTypes.includes("MEMORY")) {
    return "Memory anomaly";
  }
  if (anomalyTypes.includes("DISK")) {
    return "Disk anomaly";
  }
  if (anomalyTypes.includes("CPU")) {
    return "CPU anomaly";
  }

  const highestUsage = Math.max(cpuUsage, memoryUsage, diskUsage);
  if (highestUsage === memoryUsage && memoryUsage > 65) {
    return "Memory pressure";
  }
  if (highestUsage === diskUsage && diskUsage > 70) {
    return "Disk pressure";
  }
  if (highestUsage === cpuUsage && cpuUsage > 70) {
    return "CPU pressure";
  }

  return "Stable telemetry";
}

export function buildInsightModel(latestTelemetry, alerts, anomalies) {
  const cpuUsage = toNumber(latestTelemetry?.cpuUsage) ?? 0;
  const memoryUsage = toNumber(latestTelemetry?.memoryUsage) ?? 0;
  const diskUsage = toNumber(latestTelemetry?.diskUsage) ?? 0;
  const activeAlerts = Array.isArray(alerts)
    ? alerts.filter((alert) => alert?.status === "ACTIVE")
    : [];
  const anomalyList = Array.isArray(anomalies) ? anomalies : [];
  const topProcess = getTopProcess(latestTelemetry?.processMetrics);

  let status = "Healthy";
  let statusTone = "green";

  if (activeAlerts.length > 0 || anomalyList.length > 0 || memoryUsage > 80 || diskUsage > 85 || cpuUsage > 85) {
    status = "Warning";
    statusTone = "yellow";
  }

  if (
    activeAlerts.some((alert) => ["CRITICAL", "HIGH"].includes(alert?.severity)) ||
    anomalyList.some((anomaly) => ["CRITICAL", "HIGH"].includes(String(anomaly?.severity || "").toUpperCase())) ||
    diskUsage > 90 ||
    memoryUsage > 90 ||
    cpuUsage > 95
  ) {
    status = "Critical";
    statusTone = "red";
  }

  return {
    status,
    statusTone,
    topIssue: resolveTopIssue({
      cpuUsage,
      memoryUsage,
      diskUsage,
      activeAlerts,
      anomalyList,
    }),
    topProcess,
    topProcessSummary: formatTopProcess(topProcess),
    activeAlertsCount: activeAlerts.length,
    anomalyCount: anomalyList.length,
    cpuUsage,
    memoryUsage,
    diskUsage,
  };
}
