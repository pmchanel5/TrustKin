import unittest

from tools.traceability.check_issue_traceability import validate_issue


COMPLETE_BODY = """### Task ID

TK-P0-06

### Milestone or gate

M0 TrustKin Reset / G-RESET

### Acceptance criteria

All checks pass.

### Required automated tests

CI negative fixtures.

### Required human tests

Maintainer repository check.

### Requirements references

requirements §20

### Design references

design §§28, 30, 32
"""


class IssueTraceabilityTest(unittest.TestCase):
    def test_accepts_complete_gate_blocker(self) -> None:
        issue = {"labels": [{"name": "gate-blocker"}], "body": COMPLETE_BODY}
        self.assertEqual([], validate_issue(issue))

    def test_rejects_missing_references(self) -> None:
        body = COMPLETE_BODY.replace("requirements §20", "_No response_")
        issue = {"labels": [{"name": "phase-blocker"}], "body": body}
        self.assertIn("Missing required field: Requirements references", validate_issue(issue))

    def test_ignores_non_blocking_issue(self) -> None:
        issue = {"labels": [{"name": "type:docs"}], "body": ""}
        self.assertEqual([], validate_issue(issue))


if __name__ == "__main__":
    unittest.main()
