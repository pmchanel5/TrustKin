#!/usr/bin/env python3
"""Synchronize the repository's canonical label taxonomy through GitHub CLI."""

from __future__ import annotations

import argparse
import json
import subprocess
from pathlib import Path
from typing import Any


def load_labels(path: Path) -> list[dict[str, Any]]:
    with path.open("r", encoding="utf-8") as handle:
        labels = json.load(handle)
    if not isinstance(labels, list):
        raise ValueError("label schema root must be an array")
    return labels


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo", required=True)
    parser.add_argument("--schema", type=Path, default=Path(".github/labels.json"))
    args = parser.parse_args()

    for label in load_labels(args.schema):
        subprocess.run(
            [
                "gh",
                "label",
                "create",
                str(label["name"]),
                "--repo",
                args.repo,
                "--color",
                str(label["color"]),
                "--description",
                str(label["description"]),
                "--force",
            ],
            check=True,
        )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
