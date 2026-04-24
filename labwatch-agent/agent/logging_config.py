from __future__ import annotations

import logging


class KeyValueFormatter(logging.Formatter):
    def format(self, record: logging.LogRecord) -> str:
        base = [
            f"timestamp={self.formatTime(record, self.datefmt)}",
            f"level={record.levelname}",
            f"logger={record.name}",
            f"message=\"{record.getMessage()}\"",
        ]

        for key, value in sorted(record.__dict__.items()):
            if key in _SKIP_FIELDS or key.startswith("_"):
                continue
            base.append(f"{key}={_sanitize_value(value)}")

        if record.exc_info:
            base.append(f"exception=\"{self.formatException(record.exc_info)}\"")

        return " ".join(base)


def configure_logging(level: int = logging.INFO) -> None:
    handler = logging.StreamHandler()
    handler.setFormatter(KeyValueFormatter(datefmt="%Y-%m-%dT%H:%M:%S%z"))

    root_logger = logging.getLogger()
    root_logger.handlers.clear()
    root_logger.addHandler(handler)
    root_logger.setLevel(level)


def _sanitize_value(value: object) -> str:
    text = str(value).replace('"', "'")
    return f"\"{text}\"" if " " in text else text


_SKIP_FIELDS = {
    "args",
    "asctime",
    "created",
    "exc_info",
    "exc_text",
    "filename",
    "funcName",
    "levelname",
    "levelno",
    "lineno",
    "module",
    "msecs",
    "message",
    "msg",
    "name",
    "pathname",
    "process",
    "processName",
    "relativeCreated",
    "stack_info",
    "thread",
    "threadName",
}
