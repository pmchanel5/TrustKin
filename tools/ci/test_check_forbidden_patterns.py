import unittest

from tools.ci.check_forbidden_patterns import scan_text


class ForbiddenPatternTest(unittest.TestCase):
    def test_detects_private_key_header(self) -> None:
        text = "-----BEGIN " + "PRIVATE KEY-----"
        self.assertIn("private-key block", scan_text(text))

    def test_detects_github_token_shape(self) -> None:
        text = "gh" + "p_" + ("A" * 24)
        self.assertIn("GitHub token", scan_text(text))

    def test_accepts_normal_documentation(self) -> None:
        self.assertEqual([], scan_text("TrustKin contains no embedded credentials."))


if __name__ == "__main__":
    unittest.main()
