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

export function formatRelativeLastSeen(value) {
  if (!value) {
    return "No data";
  }

  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return value;
  }

  const diffSeconds = Math.max(0, Math.round((Date.now() - parsed.getTime()) / 1000));
  if (diffSeconds < 5) {
    return "Just now";
  }
  if (diffSeconds < 60) {
    return `${diffSeconds}s ago`;
  }
  if (diffSeconds < 3600) {
    return `${Math.round(diffSeconds / 60)}m ago`;
  }
  return `${Math.round(diffSeconds / 3600)}h ago`;
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

export function truncate(value, limit = 120) {
  const content = String(value || "").trim();
  if (!content) {
    return "No summary available.";
  }

  return content.length > limit ? `${content.slice(0, limit - 1)}...` : content;
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
