from __future__ import annotations

import logging

import httpx

from agent.backoff import sleep_with_backoff
from agent.models import AgentRegistration, AgentRegistrationResponse, TelemetrySnapshot

logger = logging.getLogger(__name__)


class LabWatchApiClient:
    def __init__(
        self,
        api_url: str,
        *,
        request_timeout_seconds: float = 10.0,
        max_retries: int = 3,
        max_backoff_seconds: float = 30.0,
    ) -> None:
        self.api_url = api_url.rstrip("/")
        self.request_timeout_seconds = request_timeout_seconds
        self.max_retries = max_retries
        self.max_backoff_seconds = max_backoff_seconds
        self.agent_token: str | None = None

    def set_agent_token(self, agent_token: str | None) -> None:
        self.agent_token = agent_token.strip() if agent_token else None

    def register_agent(self, registration: AgentRegistration) -> AgentRegistrationResponse:
        endpoint = f"{self.api_url}/api/v1/agents/register"
        response = self._post_with_retries(endpoint, registration.to_dict(), registration.machineIdentifier)
        payload = response.json()
        return AgentRegistrationResponse(
            agentId=payload["agentId"],
            agentToken=payload["agentToken"],
            machineIdentifier=payload["machineIdentifier"],
            registeredAt=payload["registeredAt"],
        )

    def send_snapshot(self, snapshot: TelemetrySnapshot) -> None:
        endpoint = f"{self.api_url}/api/v1/telemetry/snapshots"
        logger.info("payload", extra={"data": snapshot.to_dict()})
        self._post_with_retries(endpoint, snapshot.to_dict(), snapshot.machineIdentifier)

    def _post_with_retries(self, endpoint: str, payload: dict, machine_identifier: str) -> httpx.Response:
        for attempt in range(1, self.max_retries + 2):
            logger.info(
                "telemetry request sent",
                extra={
                    "attempt": attempt,
                    "endpoint": endpoint,
                    "machineIdentifier": machine_identifier,
                },
            )

            try:
                with httpx.Client(timeout=self.request_timeout_seconds) as client:
                    headers = {"X-Agent-Token": self.agent_token} if self.agent_token else {}
                    response = client.post(endpoint, json=payload, headers=headers)
                    response.raise_for_status()

                logger.info(
                    "telemetry request succeeded",
                    extra={
                        "statusCode": response.status_code,
                        "machineIdentifier": machine_identifier,
                    },
                )
                return response
            except httpx.HTTPError as exc:
                is_last_attempt = attempt > self.max_retries
                logger.warning(
                    "telemetry request failed",
                    extra={
                        "attempt": attempt,
                        "machineIdentifier": machine_identifier,
                        "errorType": exc.__class__.__name__,
                        "errorMessage": str(exc),
                        "willRetry": not is_last_attempt,
                    },
                )

                if is_last_attempt:
                    raise

                delay = sleep_with_backoff(attempt, max_seconds=self.max_backoff_seconds)
                logger.info(
                    "retry backoff applied",
                    extra={
                        "attempt": attempt,
                        "delaySeconds": round(delay, 2),
                        "machineIdentifier": machine_identifier,
                    },
                )
