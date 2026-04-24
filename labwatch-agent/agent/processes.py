from __future__ import annotations

import time
from typing import Iterable

import psutil

from agent.models import ProcessMetric


def collect_top_processes(limit: int) -> list[ProcessMetric]:
    processes: list[ProcessMetric] = []

    _prime_process_cpu_percent(psutil.process_iter())
    time.sleep(0.1)

    for process in psutil.process_iter(["name", "cpu_percent", "memory_percent"]):
        try:
            info = process.info
            cpu_percent = float(info.get("cpu_percent") or 0.0)
            memory_percent = float(info.get("memory_percent") or 0.0)
            if cpu_percent <= 0.0 and memory_percent <= 0.0:
                continue

            if cpu_percent is None or memory_percent is None:
                continue

            processes.append(
                ProcessMetric(
                    processName=info.get("name") or "unknown",
                    cpuPercent=round(cpu_percent, 2),
                    memoryPercent=round(memory_percent, 2),
                )
            )
        except (psutil.NoSuchProcess, psutil.AccessDenied, psutil.ZombieProcess):
            continue

    processes.sort(key=lambda item: (item.cpuPercent, item.memoryPercent), reverse=True)
    return processes[:limit]


def _prime_process_cpu_percent(processes: Iterable[psutil.Process]) -> None:
    for process in processes:
        try:
            process.cpu_percent(interval=None)
        except (psutil.NoSuchProcess, psutil.AccessDenied, psutil.ZombieProcess):
            continue
