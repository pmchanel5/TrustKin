from __future__ import annotations

import ctypes
import ctypes.wintypes
import hashlib
import json
import mimetypes
import os
import re
import secrets
import socket
import ssl
import subprocess
import sys
import threading
import time
import uuid
import webbrowser
from collections import Counter, deque
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, quote, urlparse
from urllib.request import Request, urlopen


APP_NAME = "Brotherhood"
DEFAULT_PORT = 8765
MAX_BODY_BYTES = 900_000
SAMPLE_SECONDS = 10
PUBLISH_SECONDS = 20

if getattr(sys, "frozen", False) and hasattr(sys, "_MEIPASS"):
    BASE_DIR = Path(sys._MEIPASS)
else:
    BASE_DIR = Path(__file__).resolve().parent

WEB_DIR = BASE_DIR / "web"
BIN_DIR = BASE_DIR / "bin"
DATA_DIR = Path(os.environ.get("APPDATA", str(BASE_DIR))) / "BrotherhoodMVP"
DB_PATH = DATA_DIR / "circle.json"
SETTINGS_PATH = DATA_DIR / "local_settings.json"

SERVER_PORT = DEFAULT_PORT
LOCAL_IPS_CACHE: set[str] = set()
DB_LOCK = threading.RLock()
SETTINGS_LOCK = threading.RLock()


SENSITIVE_APP_WORDS = {
    "1password",
    "bitwarden",
    "keepass",
    "lastpass",
    "signal",
    "whatsapp",
    "telegram",
    "outlook",
    "thunderbird",
    "mail",
    "bank",
    "wallet",
}

FRIENDLY_APPS = {
    "chrome": "Chrome",
    "msedge": "Edge",
    "firefox": "Firefox",
    "brave": "Brave",
    "code": "VS Code",
    "explorer": "Files",
    "cmd": "Command Prompt",
    "powershell": "PowerShell",
    "windowsterminal": "Terminal",
    "notepad": "Notepad",
    "winword": "Word",
    "excel": "Excel",
    "powerpnt": "PowerPoint",
    "discord": "Discord",
    "steam": "Steam",
    "codex": "Codex",
}


def now_ts() -> float:
    return time.time()


def iso_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def ensure_data_dir() -> None:
    DATA_DIR.mkdir(parents=True, exist_ok=True)


def read_json(path: Path, default):
    ensure_data_dir()
    if not path.exists():
        return default
    try:
        with path.open("r", encoding="utf-8") as f:
            return json.load(f)
    except (OSError, json.JSONDecodeError):
        return default


def write_json(path: Path, data) -> None:
    ensure_data_dir()
    tmp = path.with_suffix(path.suffix + ".tmp")
    with tmp.open("w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=True)
    tmp.replace(path)


def default_db() -> dict:
    return {
        "circle_code": secrets.token_hex(3).upper(),
        "created_at": iso_now(),
        "profiles": {},
        "user_secrets": {},
        "posts": [],
        "comments": [],
        "notes": [],
        "requests": [],
        "permissions": {},
        "activities": {},
    }


def load_db() -> dict:
    with DB_LOCK:
        existed = DB_PATH.exists()
        db = read_json(DB_PATH, default_db())
        changed = not existed
        for key, value in default_db().items():
            if key not in db:
                db[key] = value
                changed = True
        if changed:
            write_json(DB_PATH, db)
        return db


def save_db(db: dict) -> None:
    with DB_LOCK:
        write_json(DB_PATH, db)


def default_settings() -> dict:
    return {
        "user_id": "",
        "user_secret": "",
        "relay_url": "",
        "circle_code": "",
        "connection_mode": "",
        "hosting_enabled": False,
        "share_activity": True,
        "local_port": DEFAULT_PORT,
        "nickname": "",
        "avatar": "",
    }


def load_settings() -> dict:
    with SETTINGS_LOCK:
        existed = SETTINGS_PATH.exists()
        settings = read_json(SETTINGS_PATH, default_settings())
        changed = not existed
        for key, value in default_settings().items():
            if key not in settings:
                settings[key] = value
                changed = True
        if not settings.get("user_secret"):
            settings["user_secret"] = secrets.token_urlsafe(32)
            changed = True
        if changed:
            write_json(SETTINGS_PATH, settings)
        return settings


def save_settings(settings: dict) -> None:
    with SETTINGS_LOCK:
        write_json(SETTINGS_PATH, settings)


def hash_secret(secret: str) -> str:
    return hashlib.sha256(secret.encode("utf-8")).hexdigest()


def clean_text(value: str, limit: int) -> str:
    return " ".join(str(value or "").strip().split())[:limit]


def clean_long_text(value: str, limit: int) -> str:
    return str(value or "").replace("\r\n", "\n").replace("\r", "\n").strip()[:limit]


def json_response(handler: BaseHTTPRequestHandler, status: int, payload: dict) -> None:
    body = json.dumps(payload, ensure_ascii=True).encode("utf-8")
    handler.send_response(status)
    handler.send_header("Content-Type", "application/json; charset=utf-8")
    handler.send_header("Content-Length", str(len(body)))
    handler.send_header("Cache-Control", "no-store")
    handler.end_headers()
    handler.wfile.write(body)


def read_json_body(handler: BaseHTTPRequestHandler) -> dict:
    length = int(handler.headers.get("Content-Length", "0") or "0")
    if length > MAX_BODY_BYTES:
        raise ValueError("Request is too large.")
    raw = handler.rfile.read(length)
    if not raw:
        return {}
    return json.loads(raw.decode("utf-8"))


def normalize_relay_url(value: str) -> str:
    value = (value or "").strip().rstrip("/")
    if not value:
        return ""
    if not value.startswith(("http://", "https://")):
        value = "http://" + value
    return value.rstrip("/")


def local_base_url() -> str:
    return f"http://127.0.0.1:{SERVER_PORT}"


class TunnelManager:
    def __init__(self) -> None:
        self.process: subprocess.Popen | None = None
        self.url = ""
        self.status = "off"
        self.error = ""
        self.lines: deque[str] = deque(maxlen=20)
        self._lock = threading.RLock()

    def executable(self) -> Path:
        return BIN_DIR / "cloudflared.exe"

    def available(self) -> bool:
        return self.executable().exists()

    def start(self) -> str:
        with self._lock:
            if self.process and self.process.poll() is None and self.url and self.status == "online":
                return self.url

        last_error = ""
        for _ in range(3):
            self._launch()
            candidate = self._wait_for_candidate(14)
            if candidate and self._url_works(candidate, 24):
                with self._lock:
                    self.url = candidate
                    self.status = "online"
                    self.error = ""
                return candidate
            with self._lock:
                last_error = self.error or "Public invite URL was not reachable yet."
            self.stop()
            time.sleep(1)

        with self._lock:
            self.status = "failed"
            self.error = last_error
        return ""

    def _launch(self) -> None:
        with self._lock:
            self.stop()
            self.url = ""
            self.error = ""
            self.status = "starting"
            exe = self.executable()
            if not exe.exists():
                self.status = "missing"
                self.error = "Cloudflare tunnel helper is missing."
                return

            creationflags = 0
            startupinfo = None
            if os.name == "nt":
                creationflags = getattr(subprocess, "CREATE_NO_WINDOW", 0)
                startupinfo = subprocess.STARTUPINFO()
                startupinfo.dwFlags |= subprocess.STARTF_USESHOWWINDOW

            try:
                self.process = subprocess.Popen(
                    [
                        str(exe),
                        "tunnel",
                        "--url",
                        local_base_url(),
                        "--no-autoupdate",
                    ],
                    stdout=subprocess.PIPE,
                    stderr=subprocess.STDOUT,
                    stdin=subprocess.DEVNULL,
                    text=True,
                    encoding="utf-8",
                    errors="replace",
                    creationflags=creationflags,
                    startupinfo=startupinfo,
                )
            except Exception as exc:
                self.status = "failed"
                self.error = str(exc)[:180]
                self.process = None
                return

            threading.Thread(target=self._read_output, name="BrotherhoodTunnel", daemon=True).start()

    def _wait_for_candidate(self, timeout: float) -> str:
        deadline = now_ts() + timeout
        while now_ts() < deadline:
            with self._lock:
                if self.url:
                    return self.url
                if self.status in {"failed", "missing"}:
                    return ""
            time.sleep(0.2)
        with self._lock:
            self.error = "Timed out waiting for a public tunnel URL."
        return ""

    def _url_works(self, url: str, timeout: float) -> bool:
        host = urlparse(url).hostname or ""
        if not host:
            return False
        time.sleep(8)
        deadline = now_ts() + timeout
        while now_ts() < deadline:
            try:
                dns_url = f"https://cloudflare-dns.com/dns-query?name={quote(host)}&type=A"
                req = Request(dns_url, headers={"Accept": "application/dns-json"})
                with urlopen(req, timeout=5, context=ssl._create_unverified_context()) as resp:
                    payload = json.loads(resp.read().decode("utf-8"))
                if payload.get("Status") == 0 and payload.get("Answer"):
                    return True
            except Exception as exc:
                with self._lock:
                    self.error = str(exc)[:180]
            time.sleep(1)
        return False

    def _read_output(self) -> None:
        assert self.process is not None
        pattern = re.compile(r"https://[A-Za-z0-9-]+\.trycloudflare\.com")
        try:
            assert self.process.stdout is not None
            for line in self.process.stdout:
                clean = line.strip()
                if not clean:
                    continue
                with self._lock:
                    self.lines.append(clean)
                    match = pattern.search(clean)
                    if match:
                        self.url = match.group(0).rstrip("/")
                        self.status = "checking"
                        self.error = ""
        except Exception as exc:
            with self._lock:
                self.error = str(exc)[:180]
        finally:
            code = self.process.poll() if self.process else None
            with self._lock:
                if not self.url and self.status != "off":
                    self.status = "failed"
                    if not self.error:
                        self.error = f"Tunnel stopped before it was ready. Exit code: {code}"

    def stop(self) -> None:
        process = self.process
        self.process = None
        if process and process.poll() is None:
            try:
                process.terminate()
                process.wait(timeout=4)
            except Exception:
                try:
                    process.kill()
                except Exception:
                    pass
        self.url = ""
        self.status = "off"
        self.error = ""

    def info(self) -> dict:
        with self._lock:
            return {
                "available": self.available(),
                "url": self.url,
                "status": self.status,
                "error": self.error,
                "recent": list(self.lines)[-5:],
            }


def stop_public_tunnel() -> None:
    manager = globals().get("TUNNEL_MANAGER")
    if manager:
        manager.stop()


def is_local_relay(url: str) -> bool:
    parsed = urlparse(url or "")
    host = (parsed.hostname or "").lower()
    port = parsed.port or (443 if parsed.scheme == "https" else 80)
    if port != SERVER_PORT:
        return False
    return host in {"127.0.0.1", "localhost", "::1"} or host in LOCAL_IPS_CACHE


def get_local_ips() -> set[str]:
    ips = {"127.0.0.1"}
    try:
        hostname = socket.gethostname()
        for item in socket.gethostbyname_ex(hostname)[2]:
            if item and not item.startswith("127."):
                ips.add(item)
    except OSError:
        pass
    try:
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        sock.settimeout(0.2)
        sock.connect(("8.8.8.8", 80))
        ip = sock.getsockname()[0]
        if ip and not ip.startswith("127."):
            ips.add(ip)
        sock.close()
    except OSError:
        pass
    return ips


def is_local_client(address: str) -> bool:
    if address in {"127.0.0.1", "::1"}:
        return True
    return address in LOCAL_IPS_CACHE


def make_request(url: str, method: str = "GET", data: dict | None = None, timeout: float = 4.0) -> dict:
    headers = {"Accept": "application/json"}
    body = None
    if data is not None:
        body = json.dumps(data, ensure_ascii=True).encode("utf-8")
        headers["Content-Type"] = "application/json"
    req = Request(url, data=body, headers=headers, method=method)
    with urlopen(req, timeout=timeout) as resp:
        raw = resp.read()
    if not raw:
        return {}
    return json.loads(raw.decode("utf-8"))


def actor_payload(extra: dict | None = None) -> dict:
    settings = load_settings()
    if not settings.get("user_id"):
        raise ValueError("Create a profile first.")
    payload = {
        "actor_id": settings["user_id"],
        "actor_secret": settings["user_secret"],
    }
    if extra:
        payload.update(extra)
    return payload


def relay_url_for(path: str) -> str:
    settings = load_settings()
    if settings.get("connection_mode") == "host":
        return local_base_url() + path
    relay = normalize_relay_url(settings.get("relay_url"))
    if not relay:
        raise ValueError("Choose Host or Join first.")
    return relay + path


def relay_post(path: str, data: dict) -> dict:
    return make_request(relay_url_for(path), "POST", data)


def relay_get(path: str) -> dict:
    return make_request(relay_url_for(path), "GET")


def verify_actor(db: dict, actor_id: str, actor_secret: str) -> bool:
    stored = db.get("user_secrets", {}).get(actor_id)
    return bool(stored and stored == hash_secret(actor_secret or ""))


def public_profile(profile: dict) -> dict:
    return {
        "id": profile.get("id", ""),
        "nickname": profile.get("nickname", ""),
        "avatar": profile.get("avatar", ""),
        "created_at": profile.get("created_at", ""),
        "updated_at": profile.get("updated_at", ""),
    }


def is_hosting_enabled(settings: dict | None = None) -> bool:
    settings = settings or load_settings()
    return (
        settings.get("connection_mode") == "host"
        and bool(settings.get("hosting_enabled"))
    )


def reset_connection_choice() -> None:
    stop_public_tunnel()
    settings = load_settings()
    settings["connection_mode"] = ""
    settings["hosting_enabled"] = False
    settings["relay_url"] = ""
    settings["circle_code"] = ""
    save_settings(settings)


def ensure_local_profile(settings: dict) -> None:
    user_id = settings.get("user_id", "")
    user_secret = settings.get("user_secret", "")
    nickname = clean_text(settings.get("nickname", ""), 32)
    if not user_id or not user_secret or not nickname:
        return
    db = load_db()
    created_at = db.get("profiles", {}).get(user_id, {}).get("created_at", iso_now())
    db.setdefault("profiles", {})[user_id] = {
        "id": user_id,
        "nickname": nickname,
        "avatar": settings.get("avatar", ""),
        "created_at": created_at,
        "updated_at": iso_now(),
    }
    db.setdefault("user_secrets", {})[user_id] = hash_secret(user_secret)
    save_db(db)


def relay_state_for(actor_id: str, actor_secret: str) -> tuple[int, dict]:
    db = load_db()
    if not verify_actor(db, actor_id, actor_secret):
        return 403, {"ok": False, "error": "Profile is not verified on this circle."}

    profiles = [public_profile(p) for p in db["profiles"].values()]
    profiles.sort(key=lambda item: item.get("nickname", "").lower())

    visible_activities = {}
    permissions = db.get("permissions", {})
    for owner_id, activity in db.get("activities", {}).items():
        if owner_id == actor_id or permissions.get(owner_id, {}).get(actor_id):
            visible_activities[owner_id] = activity

    related_permissions = {}
    for owner_id, viewers in permissions.items():
        if owner_id == actor_id:
            related_permissions[owner_id] = viewers
            continue
        if actor_id in viewers:
            related_permissions[owner_id] = {actor_id: bool(viewers[actor_id])}

    related_requests = [
        item for item in db.get("requests", [])
        if item.get("requester_id") == actor_id or item.get("owner_id") == actor_id
    ]

    related_notes = [
        item for item in db.get("notes", [])
        if item.get("from_id") == actor_id or item.get("to_id") == actor_id
    ][-100:]

    posts = db.get("posts", [])[-100:]
    post_ids = {post.get("id") for post in posts}
    comments = [
        item for item in db.get("comments", [])
        if item.get("post_id") in post_ids
    ][-300:]

    return 200, {
        "ok": True,
        "profiles": profiles,
        "posts": posts,
        "comments": comments,
        "notes": related_notes,
        "requests": related_requests,
        "permissions": related_permissions,
        "activities": visible_activities,
        "server_time": iso_now(),
    }


def safe_avatar(value: str) -> str:
    value = str(value or "")
    if not value:
        return ""
    if len(value) > 550_000:
        raise ValueError("Profile image is too large.")
    if not value.startswith("data:image/"):
        raise ValueError("Profile image must be an image.")
    return value


def safe_post_image(value: str) -> str:
    value = str(value or "")
    if not value:
        return ""
    if len(value) > 700_000:
        raise ValueError("Post image is too large.")
    if not value.startswith("data:image/"):
        raise ValueError("Post image must be an image.")
    return value


def mask_app_name(name: str) -> str:
    raw = clean_text(name or "Unknown", 80)
    key = raw.lower().replace(".exe", "")
    friendly = FRIENDLY_APPS.get(key, raw)
    lowered = friendly.lower()
    if any(word in lowered for word in SENSITIVE_APP_WORDS):
        return "Private app"
    return friendly[:40] or "Unknown"


class ActivityTracker:
    def __init__(self) -> None:
        self.samples: deque[tuple[float, str]] = deque(maxlen=1000)
        self.current_app = "Unknown"
        self.last_error = ""
        self._stop = threading.Event()
        self._last_publish = 0.0
        self._lock = threading.RLock()

    def start(self) -> None:
        thread = threading.Thread(target=self._loop, name="BrotherhoodActivity", daemon=True)
        thread.start()

    def _loop(self) -> None:
        while not self._stop.is_set():
            try:
                app_name = get_foreground_app_name()
                app_name = mask_app_name(app_name)
                with self._lock:
                    self.current_app = app_name
                    self.samples.append((now_ts(), app_name))
                    self._trim()

                if now_ts() - self._last_publish > PUBLISH_SECONDS:
                    self._last_publish = now_ts()
                    self.publish()
            except Exception as exc:
                self.last_error = str(exc)[:180]
            self._stop.wait(SAMPLE_SECONDS)

    def _trim(self) -> None:
        cutoff = now_ts() - 3600
        while self.samples and self.samples[0][0] < cutoff:
            self.samples.popleft()

    def summary(self) -> dict:
        with self._lock:
            cutoff = now_ts() - 3600
            samples = [(ts, app) for ts, app in self.samples if ts >= cutoff]
            counts = Counter(app for _, app in samples)
            apps = []
            total_seconds = max(1, len(samples) * SAMPLE_SECONDS)
            for app, count in counts.most_common(8):
                seconds = count * SAMPLE_SECONDS
                apps.append({
                    "name": app,
                    "minutes": round(seconds / 60, 1),
                    "share": round(seconds / total_seconds, 3),
                })
            return {
                "updated_at": iso_now(),
                "window_minutes": 60,
                "current_app": self.current_app,
                "apps": apps,
                "privacy": "apps_only",
                "error": self.last_error,
            }

    def publish(self) -> None:
        settings = load_settings()
        if not settings.get("share_activity") or not settings.get("user_id") or not settings.get("connection_mode"):
            return
        payload = actor_payload({"summary": self.summary()})
        try:
            relay_post("/relay/activity", payload)
            self.last_error = ""
        except Exception as exc:
            self.last_error = f"Could not reach circle: {exc}"[:180]


def get_foreground_app_name() -> str:
    if os.name != "nt":
        return "Desktop"
    user32 = ctypes.windll.user32
    kernel32 = ctypes.windll.kernel32
    hwnd = user32.GetForegroundWindow()
    if not hwnd:
        return "Desktop"

    pid = ctypes.wintypes.DWORD()
    user32.GetWindowThreadProcessId(hwnd, ctypes.byref(pid))
    if not pid.value:
        return "Desktop"

    PROCESS_QUERY_LIMITED_INFORMATION = 0x1000
    handle = kernel32.OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, False, pid.value)
    if not handle:
        return "Desktop"
    try:
        buffer_len = ctypes.wintypes.DWORD(1024)
        buffer = ctypes.create_unicode_buffer(buffer_len.value)
        ok = kernel32.QueryFullProcessImageNameW(handle, 0, buffer, ctypes.byref(buffer_len))
        if ok:
            return Path(buffer.value).stem
    finally:
        kernel32.CloseHandle(handle)
    return "Desktop"


TRACKER = ActivityTracker()
TUNNEL_MANAGER = TunnelManager()


class BrotherhoodHandler(BaseHTTPRequestHandler):
    server_version = "BrotherhoodMVP/0.1"

    def log_message(self, format: str, *args) -> None:
        return

    def do_GET(self) -> None:
        parsed = urlparse(self.path)
        if parsed.path.startswith("/relay/"):
            self.handle_relay_get(parsed)
            return
        if not is_local_client(self.client_address[0]):
            json_response(self, 403, {"ok": False, "error": "Open the Brotherhood app on this computer."})
            return
        if parsed.path.startswith("/api/"):
            self.handle_api_get(parsed)
            return
        self.serve_static(parsed.path)

    def do_POST(self) -> None:
        parsed = urlparse(self.path)
        if parsed.path.startswith("/relay/"):
            self.handle_relay_post(parsed)
            return
        if not is_local_client(self.client_address[0]):
            json_response(self, 403, {"ok": False, "error": "Open the Brotherhood app on this computer."})
            return
        if parsed.path.startswith("/api/"):
            self.handle_api_post(parsed)
            return
        json_response(self, 404, {"ok": False, "error": "Not found."})

    def serve_static(self, route: str) -> None:
        if route in {"", "/"}:
            route = "/index.html"
        target = (WEB_DIR / route.lstrip("/")).resolve()
        try:
            target.relative_to(WEB_DIR.resolve())
        except ValueError:
            self.send_error(404)
            return
        if not target.exists() or not target.is_file():
            self.send_error(404)
            return
        content_type = mimetypes.guess_type(str(target))[0] or "application/octet-stream"
        data = target.read_bytes()
        self.send_response(200)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(data)

    def handle_api_get(self, parsed) -> None:
        if parsed.path == "/api/bootstrap":
            settings = load_settings()
            relay = normalize_relay_url(settings.get("relay_url"))
            invite_urls = [f"http://{ip}:{SERVER_PORT}" for ip in sorted(LOCAL_IPS_CACHE) if not ip.startswith("127.")]
            host_circle_code = load_db().get("circle_code", "")
            host_active = is_hosting_enabled(settings)
            tunnel_info = TUNNEL_MANAGER.info()
            public_url = tunnel_info.get("url") if host_active else ""
            if public_url:
                relay = public_url
            payload = {
                "ok": True,
                "settings": {
                    "user_id": settings.get("user_id", ""),
                    "nickname": settings.get("nickname", ""),
                    "avatar": settings.get("avatar", ""),
                    "has_profile": bool(settings.get("user_id") and settings.get("nickname")),
                    "connection_mode": settings.get("connection_mode", ""),
                    "needs_connection": not bool(settings.get("connection_mode")),
                    "relay_url": relay,
                    "circle_code": settings.get("circle_code", ""),
                    "share_activity": bool(settings.get("share_activity", True)),
                    "local_url": local_base_url(),
                    "invite_urls": invite_urls,
                    "public_url": public_url,
                    "host_circle_code": host_circle_code,
                    "hosting_enabled": host_active,
                    "is_host": host_active,
                    "tunnel": tunnel_info,
                },
                "local_activity": TRACKER.summary(),
            }
            json_response(self, 200, payload)
            return
        if parsed.path == "/api/state":
            try:
                settings = load_settings()
                if not settings.get("connection_mode"):
                    raise ValueError("Choose Host or Join first.")
                query = (
                    f"?actor_id={settings.get('user_id', '')}"
                    f"&actor_secret={settings.get('user_secret', '')}"
                )
                state = relay_get("/relay/state" + query)
                state["local_activity"] = TRACKER.summary()
                json_response(self, 200, state)
            except Exception as exc:
                json_response(self, 502, {"ok": False, "error": str(exc)})
            return
        json_response(self, 404, {"ok": False, "error": "Not found."})

    def handle_api_post(self, parsed) -> None:
        try:
            data = read_json_body(self)
            if parsed.path == "/api/connect":
                settings = load_settings()
                mode = data.get("mode", "host")
                if mode == "host":
                    settings["connection_mode"] = "host"
                    settings["hosting_enabled"] = True
                    settings["relay_url"] = local_base_url()
                    settings["circle_code"] = load_db().get("circle_code", "")
                    ensure_local_profile(settings)
                    save_settings(settings)
                    tunnel_url = TUNNEL_MANAGER.start()
                    if tunnel_url:
                        settings["relay_url"] = tunnel_url
                else:
                    stop_public_tunnel()
                    relay_url = normalize_relay_url(data.get("relay_url", ""))
                    if not relay_url:
                        raise ValueError("Relay URL is required.")
                    settings["connection_mode"] = "join"
                    settings["hosting_enabled"] = False
                    settings["relay_url"] = relay_url
                    settings["circle_code"] = clean_text(data.get("circle_code", ""), 20).upper()
                if "share_activity" in data:
                    settings["share_activity"] = bool(data.get("share_activity"))
                save_settings(settings)
                json_response(self, 200, {"ok": True, "settings": settings})
                return

            if parsed.path == "/api/session/reset":
                reset_connection_choice()
                json_response(self, 200, {"ok": True})
                return

            if parsed.path == "/api/close":
                reset_connection_choice()
                json_response(self, 200, {"ok": True, "closing": True})
                threading.Thread(target=self.shutdown_soon, daemon=True).start()
                return

            if parsed.path == "/api/profile":
                settings = load_settings()
                if not settings.get("user_id"):
                    settings["user_id"] = uuid.uuid4().hex
                if not settings.get("user_secret"):
                    settings["user_secret"] = secrets.token_urlsafe(32)
                settings["nickname"] = clean_text(data.get("nickname", ""), 32)
                settings["avatar"] = safe_avatar(data.get("avatar", ""))
                save_settings(settings)
                payload = {
                    "user_id": settings["user_id"],
                    "user_secret": settings["user_secret"],
                    "circle_code": settings.get("circle_code", ""),
                    "nickname": settings["nickname"],
                    "avatar": settings["avatar"],
                }
                if not payload["nickname"]:
                    raise ValueError("Nickname is required.")
                result = relay_post("/relay/profile", payload)
                json_response(self, 200, result)
                return

            if parsed.path == "/api/activity-sharing":
                settings = load_settings()
                settings["share_activity"] = bool(data.get("share_activity"))
                save_settings(settings)
                if not settings["share_activity"] and settings.get("user_id"):
                    try:
                        relay_post("/relay/activity", actor_payload({"summary": {"paused": True, "updated_at": iso_now()}}))
                    except Exception:
                        pass
                json_response(self, 200, {"ok": True, "share_activity": settings["share_activity"]})
                return

            if parsed.path == "/api/post":
                payload = actor_payload({
                    "kind": clean_text(data.get("kind", "win"), 16),
                    "text": clean_long_text(data.get("text", ""), 600),
                    "image": safe_post_image(data.get("image", "")),
                })
                result = relay_post("/relay/post", payload)
                json_response(self, 200, result)
                return

            if parsed.path == "/api/comment":
                payload = actor_payload({
                    "post_id": clean_text(data.get("post_id", ""), 64),
                    "text": clean_long_text(data.get("text", ""), 300),
                })
                result = relay_post("/relay/comment", payload)
                json_response(self, 200, result)
                return

            if parsed.path == "/api/note":
                payload = actor_payload({
                    "to_id": clean_text(data.get("to_id", ""), 64),
                    "text": clean_long_text(data.get("text", ""), 500),
                })
                result = relay_post("/relay/note", payload)
                json_response(self, 200, result)
                return

            if parsed.path == "/api/note/read":
                payload = actor_payload({"note_id": clean_text(data.get("note_id", ""), 64)})
                result = relay_post("/relay/note/read", payload)
                json_response(self, 200, result)
                return

            if parsed.path == "/api/request-access":
                payload = actor_payload({"owner_id": clean_text(data.get("owner_id", ""), 64)})
                result = relay_post("/relay/request-access", payload)
                json_response(self, 200, result)
                return

            if parsed.path == "/api/permission":
                payload = actor_payload({
                    "viewer_id": clean_text(data.get("viewer_id", ""), 64),
                    "allow": bool(data.get("allow")),
                })
                result = relay_post("/relay/permission", payload)
                json_response(self, 200, result)
                return

            json_response(self, 404, {"ok": False, "error": "Not found."})
        except Exception as exc:
            json_response(self, 400, {"ok": False, "error": str(exc)})

    def shutdown_soon(self) -> None:
        time.sleep(0.25)
        self.server.shutdown()

    def handle_relay_get(self, parsed) -> None:
        if not is_hosting_enabled():
            json_response(self, 503, {"ok": False, "error": "This app is not hosting a circle."})
            return
        if parsed.path == "/relay/ping":
            db = load_db()
            json_response(self, 200, {
                "ok": True,
                "app": APP_NAME,
                "server_time": iso_now(),
                "profiles": len(db.get("profiles", {})),
            })
            return
        if parsed.path == "/relay/state":
            params = parse_qs(parsed.query)
            actor_id = (params.get("actor_id") or [""])[0]
            actor_secret = (params.get("actor_secret") or [""])[0]
            status, payload = relay_state_for(actor_id, actor_secret)
            json_response(self, status, payload)
            return
        json_response(self, 404, {"ok": False, "error": "Not found."})

    def handle_relay_post(self, parsed) -> None:
        if not is_hosting_enabled():
            json_response(self, 503, {"ok": False, "error": "This app is not hosting a circle."})
            return
        try:
            data = read_json_body(self)
            if parsed.path == "/relay/profile":
                self.relay_profile(data)
                return
            if parsed.path == "/relay/post":
                self.relay_post_item(data)
                return
            if parsed.path == "/relay/comment":
                self.relay_comment(data)
                return
            if parsed.path == "/relay/note":
                self.relay_note(data)
                return
            if parsed.path == "/relay/note/read":
                self.relay_note_read(data)
                return
            if parsed.path == "/relay/request-access":
                self.relay_request_access(data)
                return
            if parsed.path == "/relay/permission":
                self.relay_permission(data)
                return
            if parsed.path == "/relay/activity":
                self.relay_activity(data)
                return
            json_response(self, 404, {"ok": False, "error": "Not found."})
        except Exception as exc:
            json_response(self, 400, {"ok": False, "error": str(exc)})

    def relay_profile(self, data: dict) -> None:
        db = load_db()
        user_id = clean_text(data.get("user_id", ""), 64)
        user_secret = str(data.get("user_secret", ""))
        nickname = clean_text(data.get("nickname", ""), 32)
        avatar = safe_avatar(data.get("avatar", ""))
        if not user_id or not user_secret or not nickname:
            raise ValueError("Nickname is required.")

        exists = user_id in db["profiles"]
        if not exists and clean_text(data.get("circle_code", ""), 20).upper() != db.get("circle_code", ""):
            raise ValueError("Circle code is wrong.")
        if exists and not verify_actor(db, user_id, user_secret):
            json_response(self, 403, {"ok": False, "error": "Profile secret does not match."})
            return

        created_at = db["profiles"].get(user_id, {}).get("created_at", iso_now())
        db["profiles"][user_id] = {
            "id": user_id,
            "nickname": nickname,
            "avatar": avatar,
            "created_at": created_at,
            "updated_at": iso_now(),
        }
        db["user_secrets"][user_id] = hash_secret(user_secret)
        save_db(db)
        status, payload = relay_state_for(user_id, user_secret)
        json_response(self, status, payload)

    def require_actor(self, db: dict, data: dict) -> tuple[str, str]:
        actor_id = clean_text(data.get("actor_id", ""), 64)
        actor_secret = str(data.get("actor_secret", ""))
        if not verify_actor(db, actor_id, actor_secret):
            raise ValueError("Profile is not verified on this circle.")
        return actor_id, actor_secret

    def relay_post_item(self, data: dict) -> None:
        db = load_db()
        actor_id, actor_secret = self.require_actor(db, data)
        text = clean_long_text(data.get("text", ""), 600)
        image = safe_post_image(data.get("image", ""))
        kind = clean_text(data.get("kind", "win"), 16) or "win"
        if not text and not image:
            raise ValueError("Post needs text or image.")
        db["posts"].append({
            "id": uuid.uuid4().hex,
            "author_id": actor_id,
            "kind": kind,
            "text": text,
            "image": image,
            "created_at": iso_now(),
        })
        db["posts"] = db["posts"][-150:]
        save_db(db)
        status, payload = relay_state_for(actor_id, actor_secret)
        json_response(self, status, payload)

    def relay_comment(self, data: dict) -> None:
        db = load_db()
        actor_id, actor_secret = self.require_actor(db, data)
        post_id = clean_text(data.get("post_id", ""), 64)
        text = clean_long_text(data.get("text", ""), 300)
        if not post_id or not any(post.get("id") == post_id for post in db.get("posts", [])):
            raise ValueError("Choose a post.")
        if not text:
            raise ValueError("Write a comment first.")
        db.setdefault("comments", []).append({
            "id": uuid.uuid4().hex,
            "post_id": post_id,
            "author_id": actor_id,
            "text": text,
            "created_at": iso_now(),
        })
        db["comments"] = db["comments"][-400:]
        save_db(db)
        status, payload = relay_state_for(actor_id, actor_secret)
        json_response(self, status, payload)

    def relay_note(self, data: dict) -> None:
        db = load_db()
        actor_id, actor_secret = self.require_actor(db, data)
        to_id = clean_text(data.get("to_id", ""), 64)
        text = clean_long_text(data.get("text", ""), 500)
        if to_id not in db["profiles"]:
            raise ValueError("Choose a brother.")
        if not text:
            raise ValueError("Write a note first.")
        db["notes"].append({
            "id": uuid.uuid4().hex,
            "from_id": actor_id,
            "to_id": to_id,
            "text": text,
            "created_at": iso_now(),
            "read_at": "",
            "no_reply_needed": True,
        })
        db["notes"] = db["notes"][-250:]
        save_db(db)
        status, payload = relay_state_for(actor_id, actor_secret)
        json_response(self, status, payload)

    def relay_note_read(self, data: dict) -> None:
        db = load_db()
        actor_id, actor_secret = self.require_actor(db, data)
        note_id = clean_text(data.get("note_id", ""), 64)
        for note in db["notes"]:
            if note.get("id") == note_id and note.get("to_id") == actor_id and not note.get("read_at"):
                note["read_at"] = iso_now()
                break
        save_db(db)
        status, payload = relay_state_for(actor_id, actor_secret)
        json_response(self, status, payload)

    def relay_request_access(self, data: dict) -> None:
        db = load_db()
        actor_id, actor_secret = self.require_actor(db, data)
        owner_id = clean_text(data.get("owner_id", ""), 64)
        if owner_id not in db["profiles"]:
            raise ValueError("Choose a brother.")
        if owner_id == actor_id:
            raise ValueError("You already see your own activity.")
        if db.get("permissions", {}).get(owner_id, {}).get(actor_id):
            status, payload = relay_state_for(actor_id, actor_secret)
            json_response(self, status, payload)
            return
        for item in db["requests"]:
            if item.get("requester_id") == actor_id and item.get("owner_id") == owner_id and item.get("status") == "pending":
                status, payload = relay_state_for(actor_id, actor_secret)
                json_response(self, status, payload)
                return
        db["requests"].append({
            "id": uuid.uuid4().hex,
            "requester_id": actor_id,
            "owner_id": owner_id,
            "status": "pending",
            "created_at": iso_now(),
        })
        save_db(db)
        status, payload = relay_state_for(actor_id, actor_secret)
        json_response(self, status, payload)

    def relay_permission(self, data: dict) -> None:
        db = load_db()
        actor_id, actor_secret = self.require_actor(db, data)
        viewer_id = clean_text(data.get("viewer_id", ""), 64)
        allow = bool(data.get("allow"))
        if viewer_id not in db["profiles"]:
            raise ValueError("Choose a brother.")
        db.setdefault("permissions", {}).setdefault(actor_id, {})
        if allow:
            db["permissions"][actor_id][viewer_id] = True
            for item in db["requests"]:
                if item.get("requester_id") == viewer_id and item.get("owner_id") == actor_id:
                    item["status"] = "accepted"
                    item["answered_at"] = iso_now()
        else:
            db["permissions"][actor_id].pop(viewer_id, None)
        save_db(db)
        status, payload = relay_state_for(actor_id, actor_secret)
        json_response(self, status, payload)

    def relay_activity(self, data: dict) -> None:
        db = load_db()
        actor_id, actor_secret = self.require_actor(db, data)
        summary = data.get("summary", {})
        if not isinstance(summary, dict):
            raise ValueError("Activity summary is invalid.")
        summary["updated_at"] = summary.get("updated_at") or iso_now()
        db.setdefault("activities", {})[actor_id] = summary
        save_db(db)
        json_response(self, 200, {"ok": True})


def choose_port() -> int:
    settings = load_settings()
    start = int(settings.get("local_port", DEFAULT_PORT) or DEFAULT_PORT)
    for port in list(range(start, start + 20)) + list(range(DEFAULT_PORT, DEFAULT_PORT + 20)):
        try:
            server = ThreadingHTTPServer(("0.0.0.0", port), BrotherhoodHandler)
            server.server_close()
            settings["local_port"] = port
            save_settings(settings)
            return port
        except OSError:
            continue
    raise RuntimeError("Could not find a free local port.")


def main() -> None:
    global SERVER_PORT, LOCAL_IPS_CACHE
    ensure_data_dir()
    load_db()
    load_settings()
    reset_connection_choice()
    SERVER_PORT = choose_port()
    LOCAL_IPS_CACHE = get_local_ips()
    settings = load_settings()
    if is_hosting_enabled(settings):
        settings["relay_url"] = local_base_url()
        if not settings.get("circle_code"):
            settings["circle_code"] = load_db().get("circle_code", "")
        save_settings(settings)

    TRACKER.start()
    server = ThreadingHTTPServer(("0.0.0.0", SERVER_PORT), BrotherhoodHandler)
    url = local_base_url()
    print(f"Brotherhood is running at {url}")
    print(f"Local data: {DATA_DIR}")
    if "--no-open" not in sys.argv:
        threading.Timer(0.7, lambda: webbrowser.open(url)).start()
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        reset_connection_choice()
        server.server_close()


if __name__ == "__main__":
    main()
