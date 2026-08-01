import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from tools.github import bootstrap_phase_issues as bootstrap


SYNTHETIC_TASK_PLAN = """# Phase P0

### TK-P0-06 — Establish the baseline

- **Objective and rationale:** Keep the baseline controlled.
- **References:** Requirements §20; design §§28, 30, 32.

# Phase P1 — Architecture validation

This phase introduction must not become part of TK-P0-06.

### TK-P1-08 — Decide compatibility

- **Objective and rationale:** Resolve platform support.
- **References:** Platform/distribution requirements.

# Phase P2 — Shared core

This phase introduction must not become part of TK-P1-08.
"""

TEST_PLANS = {
    "TK-P0-06": bootstrap.IssuePlan("Baseline", "M0", ()),
    "TK-P1-08": bootstrap.IssuePlan("Compatibility", "M1", ()),
}


class BootstrapPhaseIssuesTest(unittest.TestCase):
    def test_end_of_phase_tasks_stop_at_next_markdown_heading(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            task_plan = Path(temp_dir) / "task.md"
            task_plan.write_text(SYNTHETIC_TASK_PLAN, encoding="utf-8")
            with patch.object(bootstrap, "PLANS", TEST_PLANS):
                sections = bootstrap.parse_task_sections(task_plan)
                p0_body = bootstrap.rendered_body("TK-P0-06", sections["TK-P0-06"])
                p1_body = bootstrap.rendered_body("TK-P1-08", sections["TK-P1-08"])

        self.assertEqual(
            "Requirements §20; design §§28, 30, 32.",
            sections["TK-P0-06"]["References"],
        )
        self.assertEqual(
            "Platform/distribution requirements.",
            sections["TK-P1-08"]["References"],
        )
        self.assertNotIn("Architecture validation", p0_body)
        self.assertNotIn("Shared core", p1_body)

    def test_sot_final_planned_tasks_have_exact_references(self) -> None:
        task_plan = Path("docs/architecture/task.md")
        sections = bootstrap.parse_task_sections(task_plan)

        self.assertEqual(
            "Requirements §20; design §§28, 30, 32.",
            sections["TK-P0-06"]["References"],
        )
        self.assertEqual(
            "PLAT-003; design §24.4.",
            sections["TK-P1-08"]["References"],
        )


if __name__ == "__main__":
    unittest.main()
