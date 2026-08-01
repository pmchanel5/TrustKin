#!/usr/bin/env python3
"""Create the approved P0/P1 task issues directly from the task-plan SOT."""

from __future__ import annotations

import argparse
import json
import re
import subprocess
from dataclasses import dataclass
from pathlib import Path

TASK_HEADER = re.compile(r"^### (TK-P[01]-\d+) .*?$", re.MULTILINE)
MARKDOWN_HEADER = re.compile(r"^#{1,6}[ \t]+", re.MULTILINE)
FIELD = re.compile(
    r"^- \*\*(?P<name>[^*]+):\*\* (?P<value>.*?)(?=^- \*\*|\Z)",
    re.MULTILINE | re.DOTALL,
)


@dataclass(frozen=True)
class IssuePlan:
    title: str
    milestone: str
    labels: tuple[str, ...]


PLANS = {
    "TK-P0-01": IssuePlan("Tag the final Brotherhood experimental alpha", "M0 TrustKin Reset", ("type:release", "area:release", "gate-blocker", "human-test-required", "priority:critical")),
    "TK-P0-02": IssuePlan("Rename repository and project identity to TrustKin", "M0 TrustKin Reset", ("type:refactor", "area:android", "gate-blocker", "priority:critical")),
    "TK-P0-03": IssuePlan("Remove legacy production paths from main", "M0 TrustKin Reset", ("type:refactor", "security-sensitive", "phase-blocker", "priority:critical")),
    "TK-P0-04": IssuePlan("Establish repository governance and security policy", "M0 TrustKin Reset", ("type:security", "area:release", "security-sensitive", "gate-blocker", "priority:critical")),
    "TK-P0-05": IssuePlan("Create planning traceability and issue taxonomy", "M0 TrustKin Reset", ("type:docs", "area:release", "phase-blocker", "priority:high")),
    "TK-P0-06": IssuePlan("Establish initial CI and protected baseline", "M0 TrustKin Reset", ("type:test", "area:release", "security-sensitive", "gate-blocker", "priority:critical")),
    "TK-P1-01": IssuePlan("Select and approve the one-to-one protocol provider", "M1 Architecture Validation", ("type:spike", "area:crypto", "security-sensitive", "gate-blocker", "external-review", "priority:critical")),
    "TK-P1-02": IssuePlan("Validate OpenMLS on all planned targets", "M1 Architecture Validation", ("type:spike", "area:mls", "security-sensitive", "gate-blocker", "priority:high")),
    "TK-P1-03": IssuePlan("Validate SQLCipher and encrypted relational storage", "M1 Architecture Validation", ("type:spike", "area:storage", "security-sensitive", "gate-blocker", "priority:high")),
    "TK-P1-04": IssuePlan("Define the cross-platform Tor runtime strategy", "M1 Architecture Validation", ("type:spike", "area:tor", "privacy-sensitive", "gate-blocker", "priority:high")),
    "TK-P1-05": IssuePlan("Validate the Windows shell choice", "M1 Architecture Validation", ("type:spike", "area:windows", "security-sensitive", "phase-blocker", "priority:high")),
    "TK-P1-06": IssuePlan("Validate self-hosted and personal-mailbox deployment", "M1 Architecture Validation", ("type:spike", "area:relay", "privacy-sensitive", "gate-blocker", "priority:high")),
    "TK-P1-07": IssuePlan("Complete the pre-beta legal and store compliance plan", "M1 Architecture Validation", ("type:compliance", "area:release", "external-review", "gate-blocker", "priority:high")),
    "TK-P1-08": IssuePlan("Decide Android 9 conditional compatibility", "M1 Architecture Validation", ("type:spike", "area:android", "conditional", "gate-blocker", "human-test-required", "priority:high")),
}


def parse_task_sections(path: Path) -> dict[str, dict[str, str]]:
    text = path.read_text(encoding="utf-8")
    headers = list(TASK_HEADER.finditer(text))
    sections: dict[str, dict[str, str]] = {}
    for header in headers:
        task_id = header.group(1)
        if task_id not in PLANS:
            continue
        next_header = MARKDOWN_HEADER.search(text, header.end())
        end = next_header.start() if next_header else len(text)
        body = text[header.end() : end]
        fields = {
            match.group("name").strip(): match.group("value").strip()
            for match in FIELD.finditer(body)
        }
        sections[task_id] = fields
    missing = sorted(set(PLANS) - set(sections))
    if missing:
        raise ValueError(f"task-plan parser missed: {', '.join(missing)}")
    return sections


def rendered_body(task_id: str, fields: dict[str, str]) -> str:
    plan = PLANS[task_id]
    references = fields.get("References", "See task-plan SOT.")
    return f"""### Task ID

{task_id}

### Milestone or gate

{plan.milestone}

### Objective and rationale

{fields.get('Objective and rationale', '')}

### Dependencies and affected components

Dependencies: {fields.get('Dependencies', '')}

Components: {fields.get('Components', '')}

### Implementation details

{fields.get('Implementation', '')}

### Security and privacy considerations

{fields.get('Security', '')}

### Acceptance criteria

{fields.get('Acceptance', '')}

### Required automated tests

{fields.get('Tests', '')}

### Required human tests

Complete and record every HUM-P validation or workflow check named by the task card and acceptance criteria.

### Deliverables and blocking classification

{fields.get('Deliverables', '')}

Effort: {fields.get('Effort', '')}

Blocking: {fields.get('Blocking', '')}

### Requirements references

{references}

### Design references

{references}
"""


def existing_issue(repo: str, task_id: str) -> str | None:
    result = subprocess.run(
        ["gh", "issue", "list", "--repo", repo, "--state", "all", "--search", f"{task_id} in:title", "--limit", "100", "--json", "title,url"],
        check=True,
        capture_output=True,
        text=True,
        encoding="utf-8",
    )
    for issue in json.loads(result.stdout):
        if str(issue["title"]).startswith(task_id):
            return str(issue["url"])
    return None


def create_issue(repo: str, task_id: str, fields: dict[str, str]) -> str:
    plan = PLANS[task_id]
    command = [
        "gh",
        "issue",
        "create",
        "--repo",
        repo,
        "--title",
        f"{task_id} — {plan.title}",
        "--body",
        rendered_body(task_id, fields),
        "--milestone",
        plan.milestone,
    ]
    for label in plan.labels:
        command.extend(("--label", label))
    result = subprocess.run(command, check=True, capture_output=True, text=True, encoding="utf-8")
    return result.stdout.strip()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo", required=True)
    parser.add_argument("--task-plan", type=Path, default=Path("docs/architecture/task.md"))
    args = parser.parse_args()

    sections = parse_task_sections(args.task_plan)
    for task_id in PLANS:
        url = existing_issue(args.repo, task_id)
        if url is None:
            url = create_issue(args.repo, task_id, sections[task_id])
        print(f"{task_id}\t{url}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
