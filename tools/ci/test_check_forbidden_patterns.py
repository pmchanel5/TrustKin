import unittest
from pathlib import Path

from tools.ci.check_forbidden_patterns import (
    is_active_implementation_path,
    scan_file_content,
    scan_legacy_path,
    scan_legacy_text,
    scan_text,
)


class ForbiddenPatternTest(unittest.TestCase):
    def test_detects_private_key_header(self) -> None:
        text = "-----BEGIN " + "PRIVATE KEY-----"
        self.assertIn("private-key block", scan_text(text))

    def test_detects_encrypted_private_key_header(self) -> None:
        text = "-----BEGIN " + "ENCRYPTED PRIVATE KEY-----"
        self.assertIn("private-key block", scan_text(text))

    def test_detects_other_standard_private_key_headers(self) -> None:
        text = "-----BEGIN " + "DSA PRIVATE KEY-----"
        self.assertIn("private-key block", scan_text(text))

    def test_detects_utf16_private_key_content(self) -> None:
        text = "-----BEGIN " + "ENCRYPTED PRIVATE KEY-----"
        findings = scan_file_content(Path("identity.pem"), text.encode("utf-16"))
        self.assertIn("private-key block", findings)

    def test_detects_private_key_content_with_an_injected_nul(self) -> None:
        text = "-----BEGIN " + "PRIVATE KEY-----"
        data = text.encode("ascii").replace(b"PRIVATE", b"PRI\0VATE")
        self.assertIn(
            "private-key block",
            scan_file_content(Path("identity.key"), data),
        )

    def test_detects_forbidden_content_after_two_megabytes(self) -> None:
        text = "-----BEGIN " + "PRIVATE KEY-----"
        data = (b"x" * 2_000_001) + text.encode("ascii")
        self.assertIn(
            "private-key block",
            scan_file_content(Path("oversized.txt"), data),
        )

    def test_detects_github_token_shape(self) -> None:
        text = "gh" + "p_" + ("A" * 24)
        self.assertIn("GitHub token", scan_text(text))

    def test_detects_temporary_aws_access_key_shape(self) -> None:
        text = "AS" + "IA" + ("A" * 16)
        self.assertIn("AWS access key", scan_text(text))

    def test_accepts_normal_documentation(self) -> None:
        self.assertEqual([], scan_text("TrustKin contains no embedded credentials."))

    def test_active_paths_include_product_code_and_build_manifests(self) -> None:
        self.assertTrue(
            is_active_implementation_path(Path("apps/android/app/src/main/App.kt"))
        )
        self.assertTrue(is_active_implementation_path(Path("Cargo.toml")))
        self.assertTrue(
            is_active_implementation_path(Path("services/mailbox-relay/src/main.rs"))
        )
        self.assertFalse(
            is_active_implementation_path(Path("docs/architecture/design.md"))
        )

    def test_detects_retired_source_and_artifact_paths(self) -> None:
        self.assertIn(
            "legacy Brotherhood Python path",
            scan_legacy_path(Path("app/brotherhood.py")),
        )
        self.assertIn(
            "legacy Android namespace path",
            scan_legacy_path(
                Path("apps/android/app/src/main/java/org/brotherhood/app/Main.kt")
            ),
        )
        self.assertIn(
            "legacy Brotherhood APK",
            scan_legacy_path(Path("releases/Brotherhood-alpha-debug.apk")),
        )

    def test_detects_legacy_namespace_in_active_code(self) -> None:
        text = "package org." + "brotherhood.app"
        self.assertIn(
            "legacy Android namespace",
            scan_legacy_text(Path("apps/android/app/src/main/App.kt"), text),
        )

    def test_detects_cloudflare_tunnel_in_active_code(self) -> None:
        text = "https://example." + "trycloudflare.com"
        self.assertIn(
            "legacy Cloudflare tunnel",
            scan_legacy_text(Path("crates/trustkin-core/src/lib.rs"), text),
        )

    def test_detects_retired_static_crypto_suite_in_active_code(self) -> None:
        text = "ECIES_P256_HKDF_" + "HMAC_SHA256_AES128_GCM"
        self.assertIn(
            "legacy static ECIES suite",
            scan_legacy_text(Path("apps/android/app/src/main/Crypto.kt"), text),
        )

    def test_detects_retired_serialized_state_store_in_active_code(self) -> None:
        text = "class Secure" + "StateStore"
        self.assertIn(
            "legacy serialized state store",
            scan_legacy_text(Path("apps/android/app/src/main/Storage.kt"), text),
        )

    def test_allows_legacy_terms_in_source_of_truth_documentation(self) -> None:
        text = "org." + "brotherhood.app used " + "cloudflared"
        self.assertEqual(
            [],
            scan_legacy_text(Path("docs/architecture/design.md"), text),
        )


if __name__ == "__main__":
    unittest.main()
