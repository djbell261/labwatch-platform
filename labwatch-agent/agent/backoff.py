from __future__ import annotations

import random
import time


def calculate_backoff_seconds(
    attempt: int,
    *,
    base_seconds: float = 1.0,
    max_seconds: float = 30.0,
    jitter_ratio: float = 0.2,
) -> float:
    exponential_delay = min(base_seconds * (2 ** max(attempt - 1, 0)), max_seconds)
    jitter = exponential_delay * jitter_ratio * random.random()
    return min(exponential_delay + jitter, max_seconds)


def sleep_with_backoff(attempt: int, *, max_seconds: float = 30.0) -> float:
    delay = calculate_backoff_seconds(attempt, max_seconds=max_seconds)
    time.sleep(delay)
    return delay
