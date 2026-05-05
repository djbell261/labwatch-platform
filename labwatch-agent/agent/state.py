from __future__ import annotations

import json
from pathlib import Path

from agent.models import AgentState


class AgentStateStore:
    def __init__(self, path: Path) -> None:
        self.path = path

    def load(self) -> AgentState | None:
        if not self.path.exists():
            return None

        payload = json.loads(self.path.read_text(encoding="utf-8"))
        if not payload.get("agentToken"):
            return None

        return AgentState(
            machineIdentifier=payload.get("machineIdentifier", ""),
            agentId=payload.get("agentId", ""),
            agentToken=payload["agentToken"],
            registeredAt=payload.get("registeredAt", ""),
        )

    def save(self, state: AgentState) -> None:
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self.path.write_text(json.dumps(state.to_dict(), indent=2), encoding="utf-8")
