import base64
import io
import json
import os
import subprocess
import sys
import tempfile
import time
import unittest
from pathlib import Path
from urllib.error import HTTPError
from urllib.request import Request, urlopen


TEST_DATA = tempfile.TemporaryDirectory()
os.environ["BROTHERHOOD_DATA_DIR"] = TEST_DATA.name

import app.brotherhood as brotherhood  # noqa: E402


ROOT = Path(__file__).resolve().parents[1]


def request_json(url: str, method: str = "GET", data: dict | None = None) -> tuple[int, dict, dict]:
    body = None
    headers = {"Accept": "application/json"}
    if data is not None:
        body = json.dumps(data).encode("utf-8")
        headers["Content-Type"] = "application/json"
    req = Request(url, data=body, headers=headers, method=method)
    try:
        with urlopen(req, timeout=5) as resp:
            payload = json.loads(resp.read().decode("utf-8") or "{}")
            return resp.status, payload, dict(resp.headers)
    except HTTPError as exc:
        try:
            raw = exc.read().decode("utf-8") or "{}"
            return exc.code, json.loads(raw), dict(exc.headers)
        finally:
            exc.close()


class SecurityMvpAlphaTests(unittest.TestCase):
    def tearDown(self) -> None:
        brotherhood.JOIN_ATTEMPTS_BY_SOURCE.clear()
        brotherhood.JOIN_ATTEMPTS_GLOBAL.clear()

    def test_public_relay_server_blocks_local_api_and_static_ui(self) -> None:
        with tempfile.TemporaryDirectory() as data_dir:
            env = os.environ.copy()
            env["BROTHERHOOD_DATA_DIR"] = data_dir
            env["PYTHONUNBUFFERED"] = "1"
            process = subprocess.Popen(
                [sys.executable, str(ROOT / "app" / "brotherhood.py"), "--no-open"],
                cwd=ROOT,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
                env=env,
            )
            try:
                stdout = []
                deadline = time.time() + 10
                while time.time() < deadline:
                    line = process.stdout.readline() if process.stdout else ""
                    if line:
                        stdout.append(line)
                    if any("Brotherhood relay is running at" in item for item in stdout):
                        break
                    if process.poll() is not None:
                        break

                output = "".join(stdout)
                self.assertIn("Brotherhood is running at", output)
                self.assertIn("Brotherhood relay is running at", output)
                ui_url = output.split("Brotherhood is running at ", 1)[1].splitlines()[0].strip()
                relay_url = output.split("Brotherhood relay is running at ", 1)[1].splitlines()[0].strip()

                status, _, headers = request_json(f"{ui_url}/api/bootstrap")
                self.assertEqual(status, 200)
                self.assertIn("default-src 'self'", headers.get("Content-Security-Policy", ""))

                status, payload, _ = request_json(f"{relay_url}/api/bootstrap")
                self.assertEqual(status, 404)
                self.assertFalse(payload["ok"])

                status, payload, _ = request_json(f"{relay_url}/")
                self.assertEqual(status, 404)
                self.assertFalse(payload["ok"])
            finally:
                process.terminate()
                try:
                    process.wait(timeout=4)
                except subprocess.TimeoutExpired:
                    process.kill()
                if process.stdout:
                    process.stdout.close()
                if process.stderr:
                    process.stderr.close()

    def test_invite_join_attempts_are_rate_limited(self) -> None:
        brotherhood.rotate_invite_token()
        bad_token = "A" * 43
        for _ in range(brotherhood.JOIN_RATE_LIMIT_PER_SOURCE):
            with self.assertRaisesRegex(ValueError, "invalid or expired"):
                brotherhood.consume_invite_token(brotherhood.load_db(), bad_token, "198.51.100.8")
        with self.assertRaisesRegex(ValueError, "Too many join attempts"):
            brotherhood.consume_invite_token(brotherhood.load_db(), bad_token, "198.51.100.8")

    @unittest.skipUnless(brotherhood.PIL_AVAILABLE, "Pillow is required for image validation")
    def test_image_uploads_reject_svg_and_reencode_to_jpeg(self) -> None:
        from PIL import Image

        image = Image.new("RGBA", (2, 2), (255, 0, 0, 255))
        buf = io.BytesIO()
        image.save(buf, format="PNG")
        png_data_url = "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode("ascii")

        sanitized = brotherhood.safe_avatar(png_data_url)
        self.assertTrue(sanitized.startswith("data:image/jpeg;base64,"))

        svg_data_url = "data:image/svg+xml;base64," + base64.b64encode(b"<svg></svg>").decode("ascii")
        with self.assertRaisesRegex(ValueError, "PNG, JPEG, or WebP"):
            brotherhood.safe_avatar(svg_data_url)

    def test_relay_state_filters_activity_without_permission(self) -> None:
        db = brotherhood.default_db()
        owner_id = "a" * 32
        viewer_id = "b" * 32
        db["profiles"] = {
            owner_id: {"id": owner_id, "nickname": "Owner", "avatar": ""},
            viewer_id: {"id": viewer_id, "nickname": "Viewer", "avatar": ""},
        }
        db["user_secrets"] = {
            owner_id: brotherhood.hash_secret("owner-secret"),
            viewer_id: brotherhood.hash_secret("viewer-secret"),
        }
        db["activities"] = {owner_id: {"current_app": "Private app"}}
        db["permissions"] = {}
        brotherhood.save_db(db)

        status, payload = brotherhood.relay_state_for(viewer_id, "viewer-secret")
        self.assertEqual(status, 200)
        self.assertNotIn(owner_id, payload["activities"])

        db["permissions"] = {owner_id: {viewer_id: True}}
        brotherhood.save_db(db)
        status, payload = brotherhood.relay_state_for(viewer_id, "viewer-secret")
        self.assertEqual(status, 200)
        self.assertIn(owner_id, payload["activities"])

    def test_oversized_json_payload_is_rejected_before_read(self) -> None:
        class FakeHandler:
            headers = {"Content-Length": str(brotherhood.MAX_BODY_BYTES + 1)}
            rfile = io.BytesIO(b"")

        with self.assertRaisesRegex(ValueError, "too large"):
            brotherhood.read_json_body(FakeHandler())

    def test_actor_secrets_are_not_sent_in_relay_state_query_strings(self) -> None:
        source = Path(brotherhood.__file__).read_text(encoding="utf-8")
        self.assertNotIn('relay_get("/relay/state', source)
        self.assertNotIn("actor_secret=", source)

    def test_host_bootstrap_does_not_expose_local_invite_url_while_tunnel_starts(self) -> None:
        class FakeTunnelManager:
            def __init__(self, status: str, url: str = "") -> None:
                self.status = status
                self.url = url

            def info(self) -> dict:
                return {
                    "available": True,
                    "url": self.url,
                    "status": self.status,
                    "error": "",
                    "recent": [],
                }

        old_manager = brotherhood.TUNNEL_MANAGER
        settings = brotherhood.default_settings()
        settings.update({
            "user_id": "c" * 32,
            "user_secret": "host-secret",
            "nickname": "Host",
            "connection_mode": "host",
            "hosting_enabled": True,
            "relay_url": brotherhood.local_relay_base_url(),
        })
        brotherhood.save_settings(settings)
        try:
            brotherhood.TUNNEL_MANAGER = FakeTunnelManager("starting")
            payload = brotherhood.bootstrap_payload()
            host_settings = payload["settings"]
            self.assertEqual(host_settings["relay_url"], "")
            self.assertEqual(host_settings["public_url"], "")
            self.assertEqual(host_settings["host_invite_url"], "")
            self.assertTrue(host_settings["host_invite_token"])

            brotherhood.TUNNEL_MANAGER = FakeTunnelManager("checking", "https://example.trycloudflare.com")
            payload = brotherhood.bootstrap_payload()
            host_settings = payload["settings"]
            self.assertEqual(host_settings["public_url"], "")
            self.assertEqual(host_settings["host_invite_url"], "")

            brotherhood.TUNNEL_MANAGER = FakeTunnelManager("online", "https://example.trycloudflare.com")
            payload = brotherhood.bootstrap_payload()
            host_settings = payload["settings"]
            self.assertEqual(host_settings["public_url"], "https://example.trycloudflare.com")
            self.assertTrue(host_settings["host_invite_url"].startswith("https://example.trycloudflare.com/join#token="))
        finally:
            brotherhood.TUNNEL_MANAGER = old_manager
            brotherhood.reset_connection_choice()


def tearDownModule() -> None:
    TEST_DATA.cleanup()
