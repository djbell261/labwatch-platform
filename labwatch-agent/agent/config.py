from __future__ import annotations

import os
from dataclasses import dataclass

from dotenv import load_dotenv

load_dotenv()


@dataclass(frozen=True)
class AgentConfig:
    api_url: str
    machine_identifier: str
    collection_interval_seconds: int
    request_timeout_seconds: float = 10.0
    max_retries: int = 3
    max_backoff_seconds: float = 30.0
    top_process_count: int = 5

    @classmethod
    def from_env(cls) -> "AgentConfig":
        api_url = os.getenv("LABWATCH_API_URL", "").strip()
        machine_identifier = os.getenv("LABWATCH_MACHINE_IDENTIFIER", "").strip()

        if not api_url:
            raise ValueError("LABWATCH_API_URL is required")
        if not machine_identifier:
            raise ValueError("LABWATCH_MACHINE_IDENTIFIER is required")

        interval = int(os.getenv("COLLECTION_INTERVAL_SECONDS", "15"))
        if interval <= 0:
            raise ValueError("COLLECTION_INTERVAL_SECONDS must be greater than 0")

        return cls(
            api_url=api_url.rstrip("/"),
            machine_identifier=machine_identifier,
            collection_interval_seconds=interval,
            request_timeout_seconds=float(os.getenv("REQUEST_TIMEOUT_SECONDS", "10")),
            max_retries=int(os.getenv("MAX_RETRIES", "3")),
            max_backoff_seconds=float(os.getenv("MAX_BACKOFF_SECONDS", "30")),
            top_process_count=int(os.getenv("TOP_PROCESS_COUNT", "5")),
        )
