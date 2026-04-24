from __future__ import annotations

import logging
import time

from agent.client import LabWatchApiClient
from agent.collector import TelemetryCollector

logger = logging.getLogger(__name__)


class AgentRunner:
    def __init__(
        self,
        collector: TelemetryCollector,
        client: LabWatchApiClient,
        *,
        interval_seconds: int,
    ) -> None:
        self.collector = collector
        self.client = client
        self.interval_seconds = interval_seconds

    def run_once(self) -> None:
        snapshot = self.collector.collect()
        logger.info(
            "snapshot collected",
            extra={
                "machineIdentifier": snapshot.machineIdentifier,
                "hostname": snapshot.hostname,
                "cpuUsage": snapshot.cpuUsage,
                "memoryUsage": snapshot.memoryUsage,
                "diskUsage": snapshot.diskUsage,
                "processCount": len(snapshot.processMetrics),
            },
        )
        self.client.send_snapshot(snapshot)

    def run_forever(self) -> None:
        logger.info(
            "agent loop started",
            extra={"intervalSeconds": self.interval_seconds},
        )

        while True:
            try:
                self.run_once()
            except Exception:
                logger.exception("agent iteration failed")

            time.sleep(self.interval_seconds)
