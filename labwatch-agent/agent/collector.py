from __future__ import annotations

import platform
import socket
import time

import psutil

from agent.models import TelemetrySnapshot
from agent.processes import collect_top_processes


class TelemetryCollector:
    def __init__(self, machine_identifier: str, top_process_count: int = 5) -> None:
        self.machine_identifier = machine_identifier
        self.top_process_count = top_process_count

    def collect(self) -> TelemetrySnapshot:
        hostname = socket.gethostname()
        os_type = platform.system()
        os_version = platform.version()
        uptime_seconds = int(time.time() - psutil.boot_time())

        cpu_usage = round(psutil.cpu_percent(interval=1.0) or 0.0, 2)
        memory_usage = round(psutil.virtual_memory().percent or 0.0, 2)
        disk_usage = round(psutil.disk_usage("/").percent or 0.0, 2)
        process_metrics = collect_top_processes(self.top_process_count)

        return TelemetrySnapshot(
            machineIdentifier=self.machine_identifier,
            hostname=hostname,
            osType=os_type,
            osVersion=os_version,
            uptimeSeconds=uptime_seconds,
            timestamp=TelemetrySnapshot.current_local_iso(),
            cpuUsage=cpu_usage,
            memoryUsage=memory_usage,
            diskUsage=disk_usage,
            processMetrics=process_metrics,
        )
