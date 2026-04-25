const ALERT_MATCH_WINDOW_MS = 10 * 60 * 1000;
const ALERT_FILTER_BUFFER_MS = 10 * 60 * 1000;
const ANOMALY_MATCH_WINDOW_MS = 30 * 60 * 1000;
const FALLBACK_ANOMALY_LIMIT = 5;

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
    console.debug("[TelemetryChart] anomaly summary", {
      totalAnomalies: Array.isArray(anomalies) ? anomalies.length : 0,
      matchedAnomalies: 0,
      fallbackAnomaliesUsed: 0,
    });
    return [];
  }

  const minChartTime = parseTelemetryTime(chartData[0]?.timestamp)?.getTime() ?? 0;
  const maxChartTime =
    parseTelemetryTime(chartData[chartData.length - 1]?.timestamp)?.getTime() ?? 0;

  const normalizedAnomalies = anomalies.map(normalizeAnomaly).filter(Boolean);
  const matchedMarkers = normalizedAnomalies
    .map((anomaly, index) => buildMatchedAnomalyMarker(chartData, anomaly, index, minChartTime, maxChartTime))
    .filter(Boolean)
    .sort((left, right) => new Date(right.detectedAt).getTime() - new Date(left.detectedAt).getTime());

  if (matchedMarkers.length > 0) {
    console.debug("[TelemetryChart] anomaly summary", {
      totalAnomalies: normalizedAnomalies.length,
      matchedAnomalies: matchedMarkers.length,
      fallbackAnomaliesUsed: 0,
      selectedTimestampFields: normalizedAnomalies.map((anomaly) => anomaly.resolvedTimestampField ?? null),
    });
    return matchedMarkers;
  }

  const fallbackMarkers = buildFallbackAnomalyMarkers(chartData, normalizedAnomalies);

  console.debug("[TelemetryChart] anomaly summary", {
    totalAnomalies: normalizedAnomalies.length,
    matchedAnomalies: 0,
    fallbackAnomaliesUsed: fallbackMarkers.length,
    selectedTimestampFields: normalizedAnomalies.map((anomaly) => anomaly.resolvedTimestampField ?? null),
  });

  return fallbackMarkers;
}

function buildMatchedAnomalyMarker(chartData, anomaly, index, minChartTime, maxChartTime) {
  const debugPrefix = `[TelemetryChart][Anomaly ${index + 1}]`;
  const metric = metricMeta[anomaly?.metricType];
  const rawTimestampValue = anomaly?.resolvedTimestampValue;
  const parsedAnomalyTime =
    parseAlertTime(rawTimestampValue) ||
    parseTelemetryTime(rawTimestampValue) ||
    null;

  console.debug(`${debugPrefix} raw anomaly`, anomaly?.raw ?? anomaly);
  console.debug(`${debugPrefix} selected timestamp field`, {
    resolvedTimestampField: anomaly?.resolvedTimestampField ?? null,
    resolvedTimestampValue: rawTimestampValue ?? null,
  });
  console.debug(`${debugPrefix} parsed anomaly time`, {
    parsedAnomalyTime: parsedAnomalyTime?.toISOString?.() ?? null,
    chartMinTime: minChartTime ? new Date(minChartTime).toISOString() : null,
    chartMaxTime: maxChartTime ? new Date(maxChartTime).toISOString() : null,
  });

  if (!metric) {
    console.debug(`${debugPrefix} skipped`, {
      reason: "unsupported or missing metric field",
      metricType: anomaly?.metricType ?? null,
    });
    return null;
  }

  if (!rawTimestampValue || !parsedAnomalyTime) {
    console.debug(`${debugPrefix} skipped`, {
      reason: "missing or unparsable timestamp",
      resolvedTimestampValue: rawTimestampValue ?? null,
    });
    return null;
  }

  const matchedPoint = findClosestTelemetryPoint(chartData, rawTimestampValue, {
    matchWindowMs: ANOMALY_MATCH_WINDOW_MS,
  });

  if (!matchedPoint) {
    console.debug(`${debugPrefix} skipped`, {
      reason: "no telemetry point found within 30-minute anomaly match window",
      resolvedTimestampValue: rawTimestampValue,
    });
    return null;
  }

  const matchedPointTime = parseTelemetryTime(matchedPoint.timestamp);
  const diffMs = matchedPointTime
    ? Math.abs(matchedPointTime.getTime() - parsedAnomalyTime.getTime())
    : null;

  console.debug(`${debugPrefix} closest telemetry match`, {
    closestTelemetryPointTime: matchedPointTime?.toISOString?.() ?? null,
    timeDifferenceMinutes: diffMs === null ? null : Number((diffMs / 60000).toFixed(2)),
    anomalyInsideVisibleRange: isTimeWithinRange(parsedAnomalyTime, minChartTime, maxChartTime),
    matchedPointInsideVisibleRange: matchedPointTime
      ? isTimeWithinRange(matchedPointTime, minChartTime, maxChartTime)
      : false,
  });

  return createAnomalyMarker({
    anomaly,
    matchedPoint,
    idSuffix: index,
    fallback: false,
    timeDifferenceMinutes: diffMs === null ? null : Number((diffMs / 60000).toFixed(2)),
  });
}

function buildFallbackAnomalyMarkers(chartData, normalizedAnomalies) {
  const sortedAnomalies = [...normalizedAnomalies]
    .filter((anomaly) => metricMeta[anomaly?.metricType])
    .sort((left, right) => {
      const leftTime =
        parseAlertTime(left.detectedAt)?.getTime() ||
        parseTelemetryTime(left.detectedAt)?.getTime() ||
        0;
      const rightTime =
        parseAlertTime(right.detectedAt)?.getTime() ||
        parseTelemetryTime(right.detectedAt)?.getTime() ||
        0;
      return rightTime - leftTime;
    })
    .slice(0, FALLBACK_ANOMALY_LIMIT);

  const sortedPoints = [...chartData]
    .sort((left, right) => {
      const leftTime = parseTelemetryTime(left.timestamp)?.getTime() ?? 0;
      const rightTime = parseTelemetryTime(right.timestamp)?.getTime() ?? 0;
      return rightTime - leftTime;
    })
    .slice(0, FALLBACK_ANOMALY_LIMIT);

  const fallbackMarkers = sortedAnomalies
    .map((anomaly, index) => {
      const matchedPoint = sortedPoints[index];

      if (!matchedPoint) {
        console.debug("[TelemetryChart][Fallback] skipped", {
          reason: "no remaining telemetry point for fallback placement",
          anomaly,
        });
        return null;
      }

      console.debug("[TelemetryChart][Fallback] using approximate placement", {
        anomalyId: anomaly.id || anomaly.anomalyId || null,
        metricType: anomaly.metricType,
        selectedTimestampField: anomaly.resolvedTimestampField ?? null,
        selectedTimestampValue: anomaly.resolvedTimestampValue ?? null,
        fallbackTelemetryPointTime: matchedPoint.timestamp,
        fallbackIndex: index,
      });

      return createAnomalyMarker({
        anomaly,
        matchedPoint,
        idSuffix: `fallback-${index}`,
        fallback: true,
        timeDifferenceMinutes: null,
      });
    })
    .filter(Boolean);

  return fallbackMarkers;
}

function createAnomalyMarker({ anomaly, matchedPoint, idSuffix, fallback, timeDifferenceMinutes }) {
  const metric = metricMeta[anomaly.metricType];

  return {
    id:
      anomaly.id ||
      anomaly.anomalyId ||
      `${anomaly.metricType}-${anomaly.resolvedTimestampValue}-${idSuffix}`,
    type: "anomaly",
    metricType: anomaly.metricType,
    severity: anomaly.severity || "MEDIUM",
    anomalyScore: anomaly.anomalyScore,
    explanation: anomaly.explanation || "Potential anomaly detected",
    detectedAt: anomaly.resolvedTimestampValue,
    timestamp: anomaly.resolvedTimestampValue,
    x: matchedPoint.time,
    y: matchedPoint[metric.key],
    chartTimestamp: matchedPoint.timestamp,
    metricKey: metric.key,
    color: "#a855f7",
    fallback,
    timeDifferenceMinutes,
  };
}

export function normalizeAnomaly(anomaly) {
  if (!anomaly) {
    return null;
  }

  const resolvedMetricField = ["metricType", "eventType", "alertType"].find(
    (field) => anomaly[field] !== undefined && anomaly[field] !== null && String(anomaly[field]).trim() !== ""
  );
  const resolvedMetricValue = resolvedMetricField ? anomaly[resolvedMetricField] : "";
  const metricType = String(resolvedMetricValue || "")
    .trim()
    .toUpperCase();

  const resolvedTimestampField = ["detectedAt", "createdAt", "timestamp", "detected_at"].find(
    (field) => anomaly[field] !== undefined && anomaly[field] !== null && String(anomaly[field]).trim() !== ""
  );
  const resolvedTimestampValue = resolvedTimestampField ? anomaly[resolvedTimestampField] : null;

  const resolvedScoreField = ["anomalyScore", "zScore", "score", "anomaly_value"].find(
    (field) => anomaly[field] !== undefined && anomaly[field] !== null && String(anomaly[field]).trim() !== ""
  );
  const anomalyScoreValue = resolvedScoreField ? anomaly[resolvedScoreField] : null;
  const normalizedScore =
    anomalyScoreValue === null || anomalyScoreValue === undefined || anomalyScoreValue === ""
      ? null
      : Number(anomalyScoreValue);

  return {
    ...anomaly,
    raw: anomaly,
    metricType,
    anomalyScore: Number.isFinite(normalizedScore) ? normalizedScore : null,
    detectedAt: resolvedTimestampValue,
    resolvedTimestampField,
    resolvedTimestampValue,
    resolvedMetricField,
    resolvedScoreField,
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
  const closestPoint = findClosestPoint(
    chartData,
    (point) => parseTelemetryTime(point.timestamp)?.getTime(),
    targetTime
  );

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
