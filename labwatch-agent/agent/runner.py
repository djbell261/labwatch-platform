from __future__ import annotations

import logging
import time
import platform
import socket

from agent.client import LabWatchApiClient
from agent.collector import TelemetryCollector
from agent.models import AgentRegistration, AgentState
from agent.state import AgentStateStore

logger = logging.getLogger(__name__)


class AgentRunner:
    def __init__(
        self,
        collector: TelemetryCollector,
        client: LabWatchApiClient,
        state_store: AgentStateStore,
        *,
        interval_seconds: int,
        agent_version: str,
    ) -> None:
        self.collector = collector
        self.client = client
        self.state_store = state_store
        self.interval_seconds = interval_seconds
        self.agent_version = agent_version

    def ensure_registered(self) -> None:
        existing_state = self.state_store.load()
        if existing_state and existing_state.machineIdentifier == self.collector.machine_identifier:
            self.client.set_agent_token(existing_state.agentToken)
            logger.info(
                "reusing existing agent token",
                extra={"machineIdentifier": existing_state.machineIdentifier, "agentId": existing_state.agentId},
            )
            return

        registration = AgentRegistration(
            machineIdentifier=self.collector.machine_identifier,
            hostname=socket.gethostname(),
            osType=platform.system(),
            osVersion=platform.version(),
            agentVersion=self.agent_version,
        )
        response = self.client.register_agent(registration)
        self.client.set_agent_token(response.agentToken)
        self.state_store.save(
            AgentState(
                machineIdentifier=response.machineIdentifier,
                agentId=response.agentId,
                agentToken=response.agentToken,
                registeredAt=response.registeredAt,
            )
        )
        logger.info(
            "agent registration completed",
            extra={"machineIdentifier": response.machineIdentifier, "agentId": response.agentId},
        )

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
        self.ensure_registered()
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
