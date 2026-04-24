from __future__ import annotations

import argparse
import logging
import sys

from agent.client import LabWatchApiClient
from agent.collector import TelemetryCollector
from agent.config import AgentConfig
from agent.logging_config import configure_logging
from agent.runner import AgentRunner


def main() -> int:
    parser = argparse.ArgumentParser(description="LabWatch host monitoring agent")
    parser.add_argument(
        "--once",
        action="store_true",
        help="Collect and send one telemetry snapshot, then exit",
    )
    args = parser.parse_args()

    configure_logging()

    try:
        config = AgentConfig.from_env()
    except ValueError as exc:
        logging.getLogger(__name__).error("invalid agent configuration", extra={"errorMessage": str(exc)})
        return 1

    collector = TelemetryCollector(
        machine_identifier=config.machine_identifier,
        top_process_count=config.top_process_count,
    )
    client = LabWatchApiClient(
        config.api_url,
        request_timeout_seconds=config.request_timeout_seconds,
        max_retries=config.max_retries,
        max_backoff_seconds=config.max_backoff_seconds,
    )
    runner = AgentRunner(
        collector,
        client,
        interval_seconds=config.collection_interval_seconds,
    )

    if args.once:
        runner.run_once()
        return 0

    runner.run_forever()
    return 0


if __name__ == "__main__":
    sys.exit(main())
