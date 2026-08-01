#!/usr/bin/env python3
"""Validate traceability fields on gate- and phase-blocking GitHub issues."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any

BLOCKING_LABELS = frozenset({"gate-blocker", "phase-blocker"})
REQUIRED_FIELDS = (
    "Task ID",
    "Milestone or gate",
    "Acceptance criteria",
    "Required automated tests",
    "Required human tests",
    "Requirements references",
    "Design references",
)
EMPTY_VALUES = frozenset({"", "_No response_", "No response", "N/A", "None"})
TASK_ID_PATTERN = re.compile(r"^TK-P\d+-\d+$")


def extract_form_field(body: str, heading: str) -> str:
    """Extract a rendered GitHub issue-form field by its Markdown heading."""
    pattern = re.compile(
        rf"^### {re.escape(heading)}\s*$\n+(.*?)(?=^### |\Z)",
        flags=re.MULTILINE | re.DOTALL,
    )
    match = pattern.search(body)
    return match.group(1).strip() if match else ""


def validate_issue(issue: dict[str, Any]) -> list[str]:
    """Return traceability errors for a GitHub issue event payload."""
    labels = {
        str(label.get("name", ""))
        for label in issue.get("labels", [])
        if isinstance(label, dict)
    }
    if not labels.intersection(BLOCKING_LABELS):
        return []

    body = str(issue.get("body") or "")
    errors: list[str] = []
    values: dict[str, str] = {}
    for heading in REQUIRED_FIELDS:
        value = extract_form_field(body, heading)
        values[heading] = value
        if value in EMPTY_VALUES:
            errors.append(f"Missing required field: {heading}")

    task_id = values.get("Task ID", "")
    if task_id not in EMPTY_VALUES and not TASK_ID_PATTERN.fullmatch(task_id):
        errors.append("Task ID must match TK-P<phase>-<number>")

    return errors


def load_event(path: Path) -> dict[str, Any]:
    """Load a GitHub event JSON document."""
    with path.open("r", encoding="utf-8") as handle:
        event = json.load(handle)
    if not isinstance(event, dict):
        raise ValueError("event root must be an object")
    return event


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--event", required=True, type=Path)
    args = parser.parse_args()

    event = load_event(args.event)
    issue = event.get("issue")
    if not isinstance(issue, dict):
        print("No issue payload; nothing to validate.")
        return 0

    errors = validate_issue(issue)
    if errors:
        for error in errors:
            print(f"::error::{error}")
        return 1

    print("Issue traceability is complete for its blocking classification.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
