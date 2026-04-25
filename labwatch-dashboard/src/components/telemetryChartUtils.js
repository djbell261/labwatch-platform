const ALERT_MATCH_WINDOW_MS = 10 * 60 * 1000;
const ALERT_FILTER_BUFFER_MS = 10 * 60 * 1000;
const ANOMALY_MATCH_WINDOW_MS = 10 * 60 * 1000;

export const metricMeta = {
  CPU: { key: "cpuUsage", color: "#38bdf8", label: "CPU" },
  MEMORY: { key: "memoryUsage", color: "#f59e0b", label: "Memory" },
  DISK: { key: "diskUsage", color: "#34d399", label: "Disk" },
};

export function formatChartTime(value) {
  if (!value) {
    return "--";
  }

  const parsed = parseTelemetryTime(value);
  if (!parsed) {
    return value;
  }

  return parsed.toLocaleTimeString([], {
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  });
}

export function formatAlertTime(value) {
  if (!value) {
    return "Unknown time";
  }

  const parsed = parseAlertTime(value);
  if (!parsed) {
    return value;
  }

  return parsed.toLocaleString();
}

export function buildChartData(telemetryHistory) {
  if (!Array.isArray(telemetryHistory)) {
    return [];
  }

  return [...telemetryHistory]
    .sort((left, right) => {
      const leftTime = parseTelemetryTime(left.timestamp || left.createdAt)?.getTime() ?? 0;
      const rightTime = parseTelemetryTime(right.timestamp || right.createdAt)?.getTime() ?? 0;
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

export function parseTelemetryTime(value) {
  return parseDateValue(value, { assumeUtcWhenNoZone: false });
}

export function parseAlertTime(value) {
  return parseDateValue(value, { assumeUtcWhenNoZone: true });
}

export function buildAlertMarkers(chartData, alerts) {
  if (!Array.isArray(alerts) || alerts.length === 0 || chartData.length === 0) {
    return [];
  }

  const minChartTime = parseTelemetryTime(chartData[0]?.timestamp)?.getTime() ?? 0;
  const maxChartTime =
    parseTelemetryTime(chartData[chartData.length - 1]?.timestamp)?.getTime() ?? 0;

  console.debug("[TelemetryChart] chart time range", {
    minChartTimestamp: chartData[0]?.timestamp,
    maxChartTimestamp: chartData[chartData.length - 1]?.timestamp,
    minChartTime,
    maxChartTime,
  });

  return alerts
    .filter((alert) => {
      if (!alert?.createdAt || !metricMeta[alert.alertType]) {
        console.debug("[TelemetryChart] skipping alert: unsupported type or missing createdAt", alert);
        return false;
      }

      const rawAlertDate = parseAlertTime(alert.createdAt);
      const adjustedAlertDate = adjustAlertTimeByTimezoneOffset(rawAlertDate);
      const isInsideBufferedWindow =
        isTimeWithinBufferedRange(rawAlertDate, minChartTime, maxChartTime) ||
        isTimeWithinBufferedRange(adjustedAlertDate, minChartTime, maxChartTime);

      if (!isInsideBufferedWindow) {
        console.debug("[TelemetryChart] skipping alert: outside buffered chart window", {
          rawAlertCreatedAt: alert.createdAt,
          parsedAlertTime: rawAlertDate?.toISOString() ?? null,
          adjustedAlertTime: adjustedAlertDate?.toISOString() ?? null,
          minChartTime,
          maxChartTime,
        });
      }

      return isInsideBufferedWindow;
    })
    .map((alert) => {
      const metric = metricMeta[alert.alertType];
      const matchedPoint = findClosestTelemetryPoint(chartData, alert.createdAt);

      if (!matchedPoint) {
        console.debug("[TelemetryChart] skipping alert: no telemetry match inside window", {
          rawAlertCreatedAt: alert.createdAt,
          alertType: alert.alertType,
        });
        return null;
      }

      return {
        id: alert.id || `${alert.alertType}-${alert.createdAt}`,
        type: "alert",
        metricType: alert.alertType,
        severity: alert.severity || "UNKNOWN",
        status: alert.status || "UNKNOWN",
        message: alert.message || `${alert.alertType} alert`,
        createdAt: alert.createdAt,
        resolvedAt: alert.resolvedAt || null,
        timestamp: alert.createdAt,
        x: matchedPoint.time,
        y: matchedPoint[metric.key],
        chartTimestamp: matchedPoint.timestamp,
        metricKey: metric.key,
        color: metric.color,
      };
    })
    .filter(Boolean)
    .sort((left, right) => new Date(right.createdAt).getTime() - new Date(left.createdAt).getTime())
    .slice(0, 12);
}

export function buildAnomalyMarkers(chartData, anomalies = []) {
  if (!Array.isArray(anomalies) || anomalies.length === 0 || chartData.length === 0) {
    return [];
  }

  const minChartTime = parseTelemetryTime(chartData[0]?.timestamp)?.getTime() ?? 0;
  const maxChartTime =
    parseTelemetryTime(chartData[chartData.length - 1]?.timestamp)?.getTime() ?? 0;

  return anomalies
    .map(normalizeAnomaly)
    .filter((anomaly) => {
      if (!anomaly?.metricType || !metricMeta[anomaly.metricType] || !anomaly.detectedAt) {
        return false;
      }

      const anomalyDate = parseAlertTime(anomaly.detectedAt) || parseTelemetryTime(anomaly.detectedAt);
      return isTimeWithinRange(anomalyDate, minChartTime, maxChartTime);
    })
    .map((anomaly) => {
      const metric = metricMeta[anomaly.metricType];
      if (!metric || !anomaly.detectedAt) {
        return null;
      }

      const matchedPoint = findClosestTelemetryPoint(chartData, anomaly.detectedAt, {
        matchWindowMs: ANOMALY_MATCH_WINDOW_MS,
      });
      if (!matchedPoint) {
        return null;
      }

      return {
        id: anomaly.id || anomaly.anomalyId || `${anomaly.metricType}-${anomaly.detectedAt}`,
        type: "anomaly",
        metricType: anomaly.metricType,
        severity: anomaly.severity || "MEDIUM",
        anomalyScore: anomaly.anomalyScore,
        explanation: anomaly.explanation || "Potential anomaly detected",
        detectedAt: anomaly.detectedAt,
        timestamp: anomaly.detectedAt,
        x: matchedPoint.time,
        y: matchedPoint[metric.key],
        chartTimestamp: matchedPoint.timestamp,
        metricKey: metric.key,
        color: "#a855f7",
      };
    })
    .filter(Boolean)
    .sort((left, right) => new Date(right.detectedAt).getTime() - new Date(left.detectedAt).getTime());
}

export function normalizeAnomaly(anomaly) {
  if (!anomaly) {
    return null;
  }

  const metricType = String(
    anomaly.metricType || anomaly.eventType || anomaly.alertType || ""
  )
    .trim()
    .toUpperCase();

  const anomalyScoreValue =
    anomaly.anomalyScore ??
    anomaly.score ??
    anomaly.zScore ??
    anomaly.anomaly_value;

  const normalizedScore =
    anomalyScoreValue === null || anomalyScoreValue === undefined || anomalyScoreValue === ""
      ? null
      : Number(anomalyScoreValue);

  return {
    ...anomaly,
    metricType,
    anomalyScore: Number.isFinite(normalizedScore) ? normalizedScore : null,
    detectedAt: anomaly.detectedAt || anomaly.timestamp || anomaly.createdAt || anomaly.detected_at,
    explanation:
      anomaly.explanation ||
      anomaly.message ||
      "Statistical deviation detected outside the expected telemetry baseline.",
  };
}

function parseDateValue(value, { assumeUtcWhenNoZone }) {
  if (!value) {
    return null;
  }

  if (value instanceof Date) {
    return Number.isNaN(value.getTime()) ? null : value;
  }

  if (typeof value !== "string") {
    return null;
  }

  const normalizedValue = value.trim();
  if (!normalizedValue) {
    return null;
  }

  const hasTimezone = /[zZ]|[+-]\d{2}:\d{2}$/.test(normalizedValue);
  if (hasTimezone) {
    const parsed = new Date(normalizedValue);
    return Number.isNaN(parsed.getTime()) ? null : parsed;
  }

  const localDateTimeMatch = normalizedValue.match(
    /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})(\.(\d+))?$/
  );

  if (!localDateTimeMatch) {
    const fallbackParsed = new Date(normalizedValue);
    return Number.isNaN(fallbackParsed.getTime()) ? null : fallbackParsed;
  }

  const [, year, month, day, hour, minute, second, , fractional = "0"] = localDateTimeMatch;
  const milliseconds = Number(fractional.slice(0, 3).padEnd(3, "0"));

  if (assumeUtcWhenNoZone) {
    return new Date(
      Date.UTC(
        Number(year),
        Number(month) - 1,
        Number(day),
        Number(hour),
        Number(minute),
        Number(second),
        milliseconds
      )
    );
  }

  return new Date(
    Number(year),
    Number(month) - 1,
    Number(day),
    Number(hour),
    Number(minute),
    Number(second),
    milliseconds
  );
}

function adjustAlertTimeByTimezoneOffset(dateValue) {
  if (!dateValue) {
    return null;
  }

  const timezoneOffsetMs = new Date().getTimezoneOffset() * 60 * 1000;
  return new Date(dateValue.getTime() - timezoneOffsetMs);
}

function isTimeWithinBufferedRange(dateValue, minChartTime, maxChartTime) {
  if (!dateValue) {
    return false;
  }

  const time = dateValue.getTime();
  return time >= minChartTime - ALERT_FILTER_BUFFER_MS && time <= maxChartTime + ALERT_FILTER_BUFFER_MS;
}

function isTimeWithinRange(dateValue, minChartTime, maxChartTime) {
  if (!dateValue) {
    return false;
  }

  const time = dateValue.getTime();
  return time >= minChartTime && time <= maxChartTime;
}

function findClosestTelemetryPoint(chartData, rawTimestamp, options = {}) {
  const { matchWindowMs = ALERT_MATCH_WINDOW_MS } = options;
  const directAlertDate = parseAlertTime(rawTimestamp) || parseTelemetryTime(rawTimestamp);
  const adjustedAlertDate = adjustAlertTimeByTimezoneOffset(directAlertDate);

  const candidateMatches = [
    buildMatchCandidate(chartData, directAlertDate, "as-is"),
    buildMatchCandidate(chartData, adjustedAlertDate, "timezone-adjusted"),
    buildSameDateTimeOfDayFallback(chartData, directAlertDate),
    buildSameDateTimeOfDayFallback(chartData, adjustedAlertDate),
  ].filter(Boolean);

  if (candidateMatches.length === 0) {
  console.debug("[TelemetryChart] no candidate matches available", {
      rawAlertCreatedAt: rawTimestamp,
      parsedAlertTime: directAlertDate?.toISOString() ?? null,
      adjustedAlertTime: adjustedAlertDate?.toISOString() ?? null,
    });
    return null;
  }

  const bestMatch = candidateMatches.reduce((best, candidate) =>
    candidate.diffMs < best.diffMs ? candidate : best
  );

  console.debug("[TelemetryChart] closest telemetry match", {
    rawAlertCreatedAt: rawTimestamp,
    parsedAlertTime: directAlertDate?.toISOString() ?? null,
    adjustedAlertTime: adjustedAlertDate?.toISOString() ?? null,
    closestTelemetryTime: bestMatch.point.timestamp,
    diffMinutes: Number((bestMatch.diffMs / 60000).toFixed(2)),
    strategy: bestMatch.strategy,
    matchSuccess: bestMatch.diffMs <= matchWindowMs,
  });

  if (bestMatch.diffMs > matchWindowMs) {
    console.debug("[TelemetryChart] skipping alert: closest telemetry point outside match window", {
      rawAlertCreatedAt: rawTimestamp,
      parsedAlertTime: directAlertDate?.toISOString() ?? null,
      adjustedAlertTime: adjustedAlertDate?.toISOString() ?? null,
      closestTelemetryTime: bestMatch.point.timestamp,
      diffMinutes: Number((bestMatch.diffMs / 60000).toFixed(2)),
      strategy: bestMatch.strategy,
      allowedWindowMinutes: matchWindowMs / 60000,
    });
    return null;
  }

  return bestMatch.point;
}

function buildMatchCandidate(chartData, candidateDate, strategy) {
  if (!candidateDate) {
    return null;
  }

  const targetTime = candidateDate.getTime();
  const closestPoint = findClosestPoint(chartData, (point) => parseTelemetryTime(point.timestamp)?.getTime(), targetTime);

  if (!closestPoint) {
    return null;
  }

  const pointTime = parseTelemetryTime(closestPoint.timestamp)?.getTime();
  if (pointTime === undefined) {
    return null;
  }

  return {
    point: closestPoint,
    diffMs: Math.abs(pointTime - targetTime),
    strategy,
  };
}

function buildSameDateTimeOfDayFallback(chartData, candidateDate) {
  if (!candidateDate) {
    return null;
  }

  const targetDay = candidateDate.toDateString();
  const targetTimeOfDayMs =
    candidateDate.getHours() * 3600000 +
    candidateDate.getMinutes() * 60000 +
    candidateDate.getSeconds() * 1000 +
    candidateDate.getMilliseconds();

  const sameDayPoints = chartData.filter((point) => {
    const pointDate = parseTelemetryTime(point.timestamp);
    return pointDate && pointDate.toDateString() === targetDay;
  });

  if (sameDayPoints.length === 0) {
    return null;
  }

  const closestPoint = findClosestPoint(
    sameDayPoints,
    (point) => {
      const pointDate = parseTelemetryTime(point.timestamp);
      if (!pointDate) {
        return null;
      }

      return (
        pointDate.getHours() * 3600000 +
        pointDate.getMinutes() * 60000 +
        pointDate.getSeconds() * 1000 +
        pointDate.getMilliseconds()
      );
    },
    targetTimeOfDayMs
  );

  if (!closestPoint) {
    return null;
  }

  const pointDate = parseTelemetryTime(closestPoint.timestamp);
  if (!pointDate) {
    return null;
  }

  const pointTimeOfDayMs =
    pointDate.getHours() * 3600000 +
    pointDate.getMinutes() * 60000 +
    pointDate.getSeconds() * 1000 +
    pointDate.getMilliseconds();

  return {
    point: closestPoint,
    diffMs: Math.abs(pointTimeOfDayMs - targetTimeOfDayMs),
    strategy: "same-date-time-of-day",
  };
}

function findClosestPoint(chartData, extractor, targetValue) {
  return chartData.reduce((closest, point) => {
    const pointValue = extractor(point);
    if (pointValue === null || pointValue === undefined || Number.isNaN(pointValue)) {
      return closest;
    }

    if (!closest) {
      return point;
    }

    const closestValue = extractor(closest);
    if (closestValue === null || closestValue === undefined || Number.isNaN(closestValue)) {
      return point;
    }

    const closestDiff = Math.abs(closestValue - targetValue);
    const pointDiff = Math.abs(pointValue - targetValue);
    return pointDiff < closestDiff ? point : closest;
  }, null);
}
