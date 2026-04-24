from __future__ import annotations

from dataclasses import asdict, dataclass, field
from datetime import datetime


@dataclass
class ProcessMetric:
    processName: str
    cpuPercent: float
    memoryPercent: float


@dataclass
class TelemetrySnapshot:
    machineIdentifier: str
    hostname: str
    osType: str
    osVersion: str
    uptimeSeconds: int
    timestamp: str
    cpuUsage: float
    memoryUsage: float
    diskUsage: float
    source: str = "python-agent"
    processMetrics: list[ProcessMetric] = field(default_factory=list)

    def to_dict(self) -> dict:
        data = asdict(self)

    # Remove any None values
        return {k: v for k, v in data.items() if v is not None}

    @staticmethod
    def current_local_iso() -> str:
        return datetime.now().replace(microsecond=0).isoformat()