#!/usr/bin/env python3
"""Fail CI on likely credentials, key artifacts, or active legacy implementation."""

from __future__ import annotations

import re
import subprocess
import sys
from pathlib import Path

FORBIDDEN_SUFFIXES = (".jks", ".keystore", ".p12", ".pfx")
PATTERNS = {
    "private-key block": re.compile(
        r"-----BEGIN [A-Z0-9 -]*PRIVATE KEY(?: BLOCK)?-----"
    ),
    "GitHub token": re.compile(r"gh[pousr]_[A-Za-z0-9]{20,}"),
    "GitHub fine-grained token": re.compile(r"github_pat_[A-Za-z0-9_]{20,}"),
    "AWS access key": re.compile(r"(?:AKIA|ASIA)[0-9A-Z]{16}"),
}

ACTIVE_IMPLEMENTATION_ROOTS = frozenset({"apps", "crates", "protocol", "services"})
ACTIVE_BUILD_FILES = frozenset(
    {
        "Cargo.toml",
        "build.gradle.kts",
        "gradle.properties",
        "settings.gradle.kts",
    }
)
LEGACY_PATH_PATTERNS = {
    "legacy Brotherhood Python path": re.compile(r"(?:^|/)brotherhood[.]py$", re.I),
    "legacy Android namespace path": re.compile(
        r"(?:^|/)org/brotherhood/app(?:/|$)", re.I
    ),
    "legacy Brotherhood APK": re.compile(r"(?:^|/)Brotherhood[^/]*[.]apk$", re.I),
}
LEGACY_PATTERNS = {
    "legacy Android namespace": re.compile(r"org[.]brotherhood[.]app"),
    "legacy Cloudflare tunnel": re.compile(r"(?:cloudflared|trycloudflare[.]com)", re.I),
    "legacy static ECIES suite": re.compile(
        r"ECIES_P256_HKDF_HMAC_SHA256_AES128_GCM"
    ),
    "legacy serialized state store": re.compile(r"\bSecureStateStore\b"),
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


def scan_file_content(path: Path, data: bytes) -> list[str]:
    """Scan ASCII signatures across text, binary, and common Unicode encodings.

    Every forbidden content signature is ASCII. Removing NUL bytes before decoding
    preserves those signatures in UTF-16/UTF-32 files and prevents one injected NUL
    from turning credential material into an apparent binary file. Scanning the full
    tracked file also prevents an oversized text file from bypassing policy checks.
    """
    signature_text = data.replace(b"\0", b"").decode("ascii", errors="ignore")
    return scan_text(signature_text) + scan_legacy_text(path, signature_text)


def is_active_implementation_path(path: Path) -> bool:
    """Return whether a path participates directly in an active product build."""
    normalized = path.as_posix()
    root = normalized.partition("/")[0]
    return root in ACTIVE_IMPLEMENTATION_ROOTS or normalized in ACTIVE_BUILD_FILES


def scan_legacy_text(path: Path, text: str) -> list[str]:
    """Detect retired Brotherhood implementation only in active product paths."""
    if not is_active_implementation_path(path):
        return []
    return [name for name, pattern in LEGACY_PATTERNS.items() if pattern.search(text)]


def scan_legacy_path(path: Path) -> list[str]:
    """Detect retired source and artifact paths anywhere in the active tree."""
    normalized = path.as_posix()
    return [
        name for name, pattern in LEGACY_PATH_PATTERNS.items() if pattern.search(normalized)
    ]


def main() -> int:
    failures: list[str] = []
    for path in tracked_files():
        if not path.is_file():
            continue
        legacy_path_findings = scan_legacy_path(path)
        if legacy_path_findings:
            for finding in legacy_path_findings:
                failures.append(f"{finding}: {path}")
            continue
        if path.suffix.lower() in FORBIDDEN_SUFFIXES:
            failures.append(f"tracked key-store artifact: {path}")
            continue
        try:
            data = path.read_bytes()
        except OSError as error:
            failures.append(f"unable to read {path}: {error}")
            continue
        for finding in scan_file_content(path, data):
            failures.append(f"{finding}: {path}")

    if failures:
        for failure in failures:
            print(f"::error::{failure}")
        return 1

    print("No forbidden secrets, key artifacts, or active legacy patterns found.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
