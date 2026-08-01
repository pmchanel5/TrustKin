#!/usr/bin/env python3
"""Fail CI when tracked files contain likely credentials or forbidden key artifacts."""

from __future__ import annotations

import re
import subprocess
import sys
from pathlib import Path

FORBIDDEN_SUFFIXES = (".jks", ".keystore", ".p12", ".pfx")
PATTERNS = {
    "private-key block": re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"),
    "GitHub token": re.compile(r"gh[pousr]_[A-Za-z0-9]{20,}"),
    "GitHub fine-grained token": re.compile(r"github_pat_[A-Za-z0-9_]{20,}"),
    "AWS access key": re.compile(r"AKIA[0-9A-Z]{16}"),
}


def tracked_files() -> list[Path]:
    result = subprocess.run(
        ["git", "ls-files", "-z", "--cached", "--others", "--exclude-standard"],
        check=True,
        capture_output=True,
    )
    return [Path(raw.decode("utf-8")) for raw in result.stdout.split(b"\0") if raw]


def scan_text(text: str) -> list[str]:
    return [name for name, pattern in PATTERNS.items() if pattern.search(text)]


def main() -> int:
    failures: list[str] = []
    for path in tracked_files():
        if not path.is_file():
            continue
        if path.suffix.lower() in FORBIDDEN_SUFFIXES:
            failures.append(f"tracked key-store artifact: {path}")
            continue
        try:
            data = path.read_bytes()
        except OSError as error:
            failures.append(f"unable to read {path}: {error}")
            continue
        if b"\0" in data or len(data) > 2_000_000:
            continue
        text = data.decode("utf-8", errors="replace")
        for finding in scan_text(text):
            failures.append(f"{finding}: {path}")

    if failures:
        for failure in failures:
            print(f"::error::{failure}")
        return 1

    print("No forbidden tracked secret patterns or key-store artifacts found.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
