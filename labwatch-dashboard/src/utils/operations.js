export function formatTimestamp(value) {
  if (!value) {
    return "No data";
  }

  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return value;
  }

  return parsed.toLocaleString();
}

export function formatRelativeTimestamp(value) {
  if (!value) {
    return "No data";
  }

  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return value;
  }

  const diffSeconds = Math.max(0, Math.round((Date.now() - parsed.getTime()) / 1000));
  if (diffSeconds < 10) {
    return "Just now";
  }
  if (diffSeconds < 60) {
    return `${diffSeconds}s ago`;
  }
  if (diffSeconds < 3600) {
    return `${Math.round(diffSeconds / 60)}m ago`;
  }
  if (diffSeconds < 86400) {
    return `${Math.round(diffSeconds / 3600)}h ago`;
  }
  return `${Math.round(diffSeconds / 86400)}d ago`;
}

export function formatRelativeLastSeen(value) {
  return formatRelativeTimestamp(value);
}

export function resolveMachineStatus(machine) {
  const parsed = machine?.lastSeenAt ? new Date(machine.lastSeenAt) : null;
  if (!parsed || Number.isNaN(parsed.getTime())) {
    return { label: machine?.status || "UNKNOWN", tone: "yellow" };
  }

  const diffSeconds = Math.max(0, Math.round((Date.now() - parsed.getTime()) / 1000));
  if (diffSeconds <= 45) {
    return { label: "ONLINE", tone: "green" };
  }

  return { label: "OFFLINE", tone: "yellow" };
}

export function resolveOwnership(machine, currentUserId) {
  if (!machine?.owned) {
    return { label: "Unclaimed", tone: "blue" };
  }

  if (machine.ownerUserId && currentUserId && machine.ownerUserId === currentUserId) {
    return { label: "Owned by you", tone: "green" };
  }

  return { label: machine.ownerDisplayName ? `Owned by ${machine.ownerDisplayName}` : "Restricted", tone: "red" };
}

export function formatPercent(value) {
  const numericValue = Number(value);
  return Number.isFinite(numericValue) ? `${numericValue.toFixed(1)}%` : "No data";
}

export function formatCount(value) {
  const numericValue = Number(value);
  return Number.isFinite(numericValue) ? String(numericValue) : "0";
}

export function formatScore(value, digits = 2) {
  const numericValue = Number(value);
  return Number.isFinite(numericValue) ? numericValue.toFixed(digits) : "--";
}

export function getUsageTone(value) {
  const numericValue = Number(value);
  if (!Number.isFinite(numericValue)) {
    return "blue";
  }
  if (numericValue >= 90) {
    return "red";
  }
  if (numericValue >= 75) {
    return "yellow";
  }
  return "green";
}

export function getInitials(value) {
  const label = String(value || "LW").trim();
  return label
    .split(/\s+/)
    .slice(0, 2)
    .map((segment) => segment[0]?.toUpperCase() || "")
    .join("");
}

export function getSeverityTone(severity) {
  const normalized = String(severity || "").toUpperCase();
  if (normalized === "CRITICAL" || normalized === "HIGH") {
    return "red";
  }
  if (normalized === "MEDIUM") {
    return "yellow";
  }
  return "blue";
}

export function getSeverityRank(severity) {
  const normalized = String(severity || "").toUpperCase();
  if (normalized === "CRITICAL") {
    return 4;
  }
  if (normalized === "HIGH") {
    return 3;
  }
  if (normalized === "MEDIUM") {
    return 2;
  }
  if (normalized === "LOW") {
    return 1;
  }
  return 0;
}

export function getAlertTimestamp(alert) {
  return alert?.createdAt || alert?.timestamp || alert?.resolvedAt || null;
}

export function getAlertRecommendedAction(alert) {
  const metric = String(alert?.alertType || alert?.eventType || "").toUpperCase();
  if (metric === "CPU") {
    return "Review the top CPU-consuming process and confirm whether the spike is still ongoing.";
  }
  if (metric === "MEMORY") {
    return "Inspect memory-heavy processes and check whether pressure is increasing or stabilizing.";
  }
  if (metric === "DISK") {
    return "Check disk utilization growth and identify files, logs, or workloads causing pressure.";
  }
  return "Review the affected machine and confirm whether operator action is needed now.";
}

export function sortAlertsForQueue(alerts = []) {
  if (!Array.isArray(alerts)) {
    return [];
  }

  return [...alerts].sort((left, right) => {
    const leftActive = String(left?.status || "").toUpperCase() === "ACTIVE" ? 1 : 0;
    const rightActive = String(right?.status || "").toUpperCase() === "ACTIVE" ? 1 : 0;
    if (rightActive !== leftActive) {
      return rightActive - leftActive;
    }

    const rightTime = new Date(getAlertTimestamp(right) || 0).getTime();
    const leftTime = new Date(getAlertTimestamp(left) || 0).getTime();
    if (rightTime !== leftTime) {
      return rightTime - leftTime;
    }

    const severityDelta = getSeverityRank(right?.severity) - getSeverityRank(left?.severity);
    if (severityDelta !== 0) {
      return severityDelta;
    }

    return String(right?.machineIdentifier || "").localeCompare(String(left?.machineIdentifier || ""));
  });
}

export function truncate(value, limit = 120) {
  const content = String(value || "").trim();
  if (!content) {
    return "No summary available.";
  }

  return content.length > limit ? `${content.slice(0, limit - 1)}...` : content;
}

export function groupInvestigationsByIncident(investigations = []) {
  const groups = new Map();

  investigations.forEach((investigation) => {
    const incidentId = investigation?.incidentId || investigation?.investigationId || "unknown-incident";
    const existing = groups.get(incidentId) || [];
    existing.push(investigation);
    groups.set(incidentId, existing);
  });

  return Array.from(groups.values()).map((items) => {
    const sortedItems = [...items].sort((left, right) => {
      const rightTime = new Date(right?.createdAt || 0).getTime();
      const leftTime = new Date(left?.createdAt || 0).getTime();
      return rightTime - leftTime;
    });
    const primary = sortedItems[0] || {};
    const affectedMetrics = Array.from(
      new Set(
        sortedItems
          .flatMap((item) => String(item?.affectedMetrics || item?.alertType || "UNKNOWN").split(","))
          .map((metric) => metric.trim().toUpperCase())
          .filter(Boolean)
      )
    );
    const startTimes = sortedItems
      .map((item) => new Date(item?.createdAt || 0).getTime())
      .filter((time) => Number.isFinite(time) && time > 0);
    const firstTime = startTimes.length ? Math.min(...startTimes) : null;
    const lastTime = startTimes.length ? Math.max(...startTimes) : null;
    const durationMs = firstTime && lastTime ? Math.max(0, lastTime - firstTime) : 0;

    return {
      ...primary,
      relatedInvestigationCount: Math.max(0, sortedItems.length - 1),
      groupedInvestigations: sortedItems,
      affectedMetrics: affectedMetrics.join(", "),
      firstSignalAt: firstTime ? new Date(firstTime).toISOString() : primary.createdAt,
      lastSignalAt: lastTime ? new Date(lastTime).toISOString() : primary.createdAt,
      durationMs,
    };
  });
}

export function formatDuration(durationMs) {
  const numericValue = Number(durationMs);
  if (!Number.isFinite(numericValue) || numericValue <= 0) {
    return "Single signal";
  }
  const minutes = Math.max(1, Math.round(numericValue / 60000));
  if (minutes < 60) {
    return `${minutes}m`;
  }
  return `${Math.round(minutes / 60)}h`;
}

export function getLatestSnapshot(snapshots) {
  if (!Array.isArray(snapshots) || snapshots.length === 0) {
    return null;
  }

  return [...snapshots].sort((left, right) => {
    const leftTime = new Date(left.timestamp || left.createdAt || 0).getTime();
    const rightTime = new Date(right.timestamp || right.createdAt || 0).getTime();
    return rightTime - leftTime;
  })[0];
}

export function mergeSnapshotIntoHistory(existingSnapshots, incomingSnapshot) {
  const combinedSnapshots = [incomingSnapshot, ...existingSnapshots];
  const deduplicatedSnapshots = combinedSnapshots.filter((snapshot, index, snapshots) => {
    const snapshotId = snapshot.snapshotId || snapshot.id;
    return snapshots.findIndex((candidate) => (candidate.snapshotId || candidate.id) === snapshotId) === index;
  });

  return deduplicatedSnapshots
    .sort((left, right) => {
      const leftTime = new Date(left.timestamp || left.createdAt || 0).getTime();
      const rightTime = new Date(right.timestamp || right.createdAt || 0).getTime();
      return rightTime - leftTime;
    })
    .slice(0, 50);
}
