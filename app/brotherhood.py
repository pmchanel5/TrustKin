from __future__ import annotations

import base64
import binascii
import ctypes
import ctypes.wintypes
import hashlib
import hmac
import io
import json
import mimetypes
import os
import re
import secrets
import socket
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
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

try:
    from PIL import Image, ImageOps, UnidentifiedImageError
    PIL_AVAILABLE = True
except ImportError:
    Image = None
    ImageOps = None
    UnidentifiedImageError = OSError
    PIL_AVAILABLE = False


APP_NAME = "Brotherhood"
DEFAULT_PORT = 8765
DEFAULT_RELAY_PORT = 8766
MAX_BODY_BYTES = 900_000
SAMPLE_SECONDS = 10
PUBLISH_SECONDS = 20
INVITE_TOKEN_TTL_SECONDS = 24 * 60 * 60
INVITE_MAX_USES = 3
JOIN_RATE_WINDOW_SECONDS = 60
JOIN_RATE_LIMIT_PER_SOURCE = 5
JOIN_RATE_LIMIT_GLOBAL = 30
JOIN_FAILURE_WINDOW_SECONDS = 5 * 60
JOIN_FAILURE_LOCK_SECONDS = 5 * 60
JOIN_FAILURE_LOCK_THRESHOLD = 10
TUNNEL_START_ATTEMPTS = 3
TUNNEL_CANDIDATE_TIMEOUT_SECONDS = 20
TUNNEL_READY_TIMEOUT_SECONDS = 90
MAX_IMAGE_PIXELS = 4_000_000
IMAGE_DATA_URL_RE = re.compile(r"^data:image/(jpeg|jpg|png|webp);base64,([A-Za-z0-9+/]+={0,2})$")
INVITE_TOKEN_RE = re.compile(r"^[A-Za-z0-9_-]{32,256}$")
PROFILE_ID_RE = re.compile(r"^[a-f0-9]{32}$")
CSP_POLICY = (
    "default-src 'self'; "
    "script-src 'self'; "
    "object-src 'none'; "
    "base-uri 'none'; "
    "img-src 'self' data: blob:; "
    "connect-src 'self'"
)

if getattr(sys, "frozen", False) and hasattr(sys, "_MEIPASS"):
    BASE_DIR = Path(sys._MEIPASS)
else:
    BASE_DIR = Path(__file__).resolve().parent

WEB_DIR = BASE_DIR / "web"
BIN_DIR = BASE_DIR / "bin"
DATA_DIR = Path(
    os.environ.get(
        "BROTHERHOOD_DATA_DIR",
        str(Path(os.environ.get("APPDATA", str(BASE_DIR))) / "BrotherhoodMVP"),
    )
)
DB_PATH = DATA_DIR / "circle.json"
SETTINGS_PATH = DATA_DIR / "local_settings.json"

SERVER_PORT = DEFAULT_PORT
RELAY_PORT = DEFAULT_RELAY_PORT
LOCAL_IPS_CACHE: set[str] = set()
DB_LOCK = threading.RLock()
SETTINGS_LOCK = threading.RLock()
INVITE_LOCK = threading.RLock()
JOIN_RATE_LOCK = threading.RLock()
CURRENT_INVITE_TOKEN = ""
JOIN_ATTEMPTS_BY_SOURCE: dict[str, deque[float]] = {}
JOIN_ATTEMPTS_GLOBAL: deque[float] = deque()

if PIL_AVAILABLE:
    Image.MAX_IMAGE_PIXELS = MAX_IMAGE_PIXELS
IMAGE_VERIFY_ERRORS = (
    (binascii.Error, OSError, UnidentifiedImageError, Image.DecompressionBombError)
    if PIL_AVAILABLE
    else (binascii.Error, OSError)
)


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


def default_invite_record() -> dict:
    return {
        "hash": "",
        "created_at": "",
        "expires_at": 0.0,
        "uses": 0,
        "max_uses": INVITE_MAX_USES,
        "disabled_until": 0.0,
        "failures": [],
    }


def default_db() -> dict:
    return {
        "circle_code": "",
        "invite": default_invite_record(),
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
        if not isinstance(db.get("invite"), dict):
            db["invite"] = default_invite_record()
            changed = True
        else:
            for key, value in default_invite_record().items():
                if key not in db["invite"]:
                    db["invite"][key] = value
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
        "invite_token": "",
        "connection_mode": "",
        "hosting_enabled": False,
        "share_activity": True,
        "local_port": DEFAULT_PORT,
        "relay_port": DEFAULT_RELAY_PORT,
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


def send_security_headers(handler: BaseHTTPRequestHandler) -> None:
    handler.send_header("Content-Security-Policy", CSP_POLICY)
    handler.send_header("X-Content-Type-Options", "nosniff")
    handler.send_header("Referrer-Policy", "no-referrer")


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
    send_security_headers(handler)
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


def local_relay_base_url() -> str:
    return f"http://127.0.0.1:{RELAY_PORT}"


def clean_invite_token(value: str) -> str:
    token = str(value or "").strip()
    if not token:
        return ""
    if not INVITE_TOKEN_RE.fullmatch(token):
        raise ValueError("Invite token is invalid.")
    return token


def split_invite_fields(relay_or_invite_url: str, token_value: str = "") -> tuple[str, str]:
    raw = str(relay_or_invite_url or "").strip()
    token = str(token_value or "").strip()
    if raw:
        parsed_raw = raw if raw.startswith(("http://", "https://")) else "http://" + raw
        parsed = urlparse(parsed_raw)
        fragment_token = (parse_qs(parsed.fragment).get("token") or [""])[0]
        query_token = (parse_qs(parsed.query).get("token") or [""])[0]
        token = token or fragment_token or query_token
        path = parsed.path.rstrip("/")
        if path == "/join":
            path = ""
        relay_url = f"{parsed.scheme}://{parsed.netloc}{path}".rstrip("/")
    else:
        relay_url = ""
    return normalize_relay_url(relay_url), clean_invite_token(token)


def invite_url_for(relay_base_url: str, token: str) -> str:
    if not relay_base_url or not token:
        return ""
    return f"{normalize_relay_url(relay_base_url)}/join#token={quote(token)}"


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
        final_status = "failed"
        for _ in range(TUNNEL_START_ATTEMPTS):
            self._launch()
            candidate = self._wait_for_candidate(TUNNEL_CANDIDATE_TIMEOUT_SECONDS)
            if candidate and self._url_works(candidate, TUNNEL_READY_TIMEOUT_SECONDS):
                with self._lock:
                    self.url = candidate
                    self.status = "online"
                    self.error = ""
                return candidate
            with self._lock:
                last_error = self.error or "Public invite URL was not reachable yet."
                if self.status == "missing":
                    final_status = "missing"
            self.stop()
            if final_status == "missing":
                break
            time.sleep(1)

        with self._lock:
            self.status = final_status
            self.error = last_error
        return ""

    def start_async(self) -> None:
        with self._lock:
            if self.status in {"starting", "checking"}:
                return
            if self.process and self.process.poll() is None and self.status == "online":
                return
            self.status = "starting"
            self.error = ""
        threading.Thread(target=self.start, name="BrotherhoodTunnelStart", daemon=True).start()

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
                        local_relay_base_url(),
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
        if not (urlparse(url).hostname or ""):
            return False
        deadline = now_ts() + timeout
        health_url = normalize_relay_url(url) + "/relay/ping"
        last_error = ""
        saw_dns_error = False
        while now_ts() < deadline:
            with self._lock:
                process = self.process
                exit_code = process.poll() if process else None
                if not process or exit_code is not None:
                    self.error = f"Cloudflare tunnel stopped before the invite was ready. Exit code: {exit_code}"
                    return False
            try:
                req = Request(health_url, headers={"Accept": "application/json"})
                with urlopen(req, timeout=5) as resp:
                    payload = json.loads(resp.read().decode("utf-8"))
                if resp.status == 200 and payload.get("ok") is True and payload.get("app") == APP_NAME:
                    return True
            except Exception as exc:
                last_error = self._friendly_url_error(exc)
                saw_dns_error = saw_dns_error or self._is_dns_error(exc)
                with self._lock:
                    self.error = last_error
            time.sleep(1)
        with self._lock:
            if saw_dns_error:
                self.error = "Cloudflare created an invite address, but this computer could not resolve it yet. Check internet or DNS settings, then try hosting again."
            else:
                self.error = last_error or "Cloudflare created an invite address, but Brotherhood did not answer through it yet."
        return False

    def _is_dns_error(self, exc: Exception) -> bool:
        if isinstance(exc, URLError):
            reason = getattr(exc, "reason", "")
            return isinstance(reason, socket.gaierror) or "getaddrinfo" in str(reason).lower()
        return "getaddrinfo" in str(exc).lower()

    def _friendly_url_error(self, exc: Exception) -> str:
        if isinstance(exc, HTTPError):
            return f"Cloudflare is reachable, but the invite is not ready yet. HTTP {exc.code}."
        if self._is_dns_error(exc):
            return "Cloudflare created an invite address. Waiting for DNS to recognize it..."
        text = str(exc).lower()
        if "timed out" in text or "timeout" in text:
            return "Cloudflare invite address did not answer yet. Still checking..."
        return "Cloudflare invite address is not ready yet. Still checking..."

    def _display_error(self, error: str) -> str:
        if not error:
            return ""
        text = str(error)
        lowered = text.lower()
        if "getaddrinfo" in lowered or "urlopen error" in lowered:
            if self.status == "checking":
                return "Cloudflare created an invite address. Waiting for DNS to recognize it..."
            return "Cloudflare created an invite address, but this computer could not resolve it yet. Check internet or DNS settings, then try hosting again."
        return text[:180]

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
                "error": self._display_error(self.error),
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
    if port != RELAY_PORT:
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


def invite_is_usable(invite: dict) -> bool:
    return (
        bool(invite.get("hash"))
        and float(invite.get("expires_at", 0) or 0) > now_ts()
        and int(invite.get("uses", 0) or 0) < int(invite.get("max_uses", INVITE_MAX_USES) or INVITE_MAX_USES)
        and float(invite.get("disabled_until", 0) or 0) <= now_ts()
    )


def rotate_invite_token() -> str:
    global CURRENT_INVITE_TOKEN
    token = secrets.token_urlsafe(32)
    db = load_db()
    db["invite"] = {
        **default_invite_record(),
        "hash": hash_secret(token),
        "created_at": iso_now(),
        "expires_at": now_ts() + INVITE_TOKEN_TTL_SECONDS,
        "max_uses": INVITE_MAX_USES,
    }
    save_db(db)
    with INVITE_LOCK:
        CURRENT_INVITE_TOKEN = token
    return token


def current_invite_token() -> str:
    with INVITE_LOCK:
        token = CURRENT_INVITE_TOKEN
    db = load_db()
    invite = db.get("invite", {})
    if float(invite.get("disabled_until", 0) or 0) > now_ts():
        return ""
    if token and invite_is_usable(invite) and hmac.compare_digest(invite.get("hash", ""), hash_secret(token)):
        return token
    return rotate_invite_token()


def source_key_for(handler: BaseHTTPRequestHandler) -> str:
    for header in ("CF-Connecting-IP", "X-Forwarded-For"):
        value = (handler.headers.get(header, "") or "").split(",")[0].strip()
        if value:
            return value[:80]
    return str(handler.client_address[0])[:80]


def prune_deque(values: deque[float], window_seconds: int) -> None:
    cutoff = now_ts() - window_seconds
    while values and values[0] < cutoff:
        values.popleft()


def check_join_rate_limit(source: str, db: dict) -> None:
    disabled_until = float(db.get("invite", {}).get("disabled_until", 0) or 0)
    if disabled_until > now_ts():
        raise ValueError("Invite is temporarily locked. Try again in a few minutes.")

    with JOIN_RATE_LOCK:
        prune_deque(JOIN_ATTEMPTS_GLOBAL, JOIN_RATE_WINDOW_SECONDS)
        source_attempts = JOIN_ATTEMPTS_BY_SOURCE.setdefault(source, deque())
        prune_deque(source_attempts, JOIN_RATE_WINDOW_SECONDS)
        if len(source_attempts) >= JOIN_RATE_LIMIT_PER_SOURCE or len(JOIN_ATTEMPTS_GLOBAL) >= JOIN_RATE_LIMIT_GLOBAL:
            raise ValueError("Too many join attempts. Try again in a minute.")
        source_attempts.append(now_ts())
        JOIN_ATTEMPTS_GLOBAL.append(now_ts())


def record_failed_join(db: dict, source: str) -> None:
    invite = db.setdefault("invite", default_invite_record())
    failures = [
        item for item in invite.get("failures", [])
        if float(item.get("at", 0) or 0) >= now_ts() - JOIN_FAILURE_WINDOW_SECONDS
    ]
    failures.append({"at": now_ts(), "source": source[:80]})
    invite["failures"] = failures[-50:]
    if len(failures) >= JOIN_FAILURE_LOCK_THRESHOLD:
        invite["disabled_until"] = now_ts() + JOIN_FAILURE_LOCK_SECONDS


def consume_invite_token(db: dict, token: str, source: str) -> None:
    check_join_rate_limit(source, db)
    invite = db.setdefault("invite", default_invite_record())
    expected_hash = str(invite.get("hash", ""))
    token_hash = hash_secret(token or "")
    if not invite_is_usable(invite) or not hmac.compare_digest(expected_hash, token_hash):
        record_failed_join(db, source)
        save_db(db)
        raise ValueError("Invite token is invalid or expired.")
    invite["uses"] = int(invite.get("uses", 0) or 0) + 1
    invite["failures"] = []


def clean_profile_id(value: str) -> str:
    user_id = clean_text(value, 64).lower()
    if not PROFILE_ID_RE.fullmatch(user_id):
        raise ValueError("Profile id is invalid.")
    return user_id


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
        return local_relay_base_url() + path
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
    global CURRENT_INVITE_TOKEN
    with INVITE_LOCK:
        CURRENT_INVITE_TOKEN = ""
    settings = load_settings()
    settings["connection_mode"] = ""
    settings["hosting_enabled"] = False
    settings["relay_url"] = ""
    settings["circle_code"] = ""
    settings["invite_token"] = ""
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


def sanitize_image_data_url(value: str, max_input_chars: int, max_dimension: int, quality: int) -> str:
    value = str(value or "")
    if not value:
        return ""
    if not PIL_AVAILABLE or Image is None or ImageOps is None:
        raise ValueError("Image uploads require Pillow to be installed.")
    if len(value) > max_input_chars:
        raise ValueError("Image is too large.")
    match = IMAGE_DATA_URL_RE.fullmatch(value)
    if not match:
        raise ValueError("Image must be a PNG, JPEG, or WebP data URL.")
    try:
        raw = base64.b64decode(match.group(2), validate=True)
        with Image.open(io.BytesIO(raw)) as image:
            if image.format not in {"JPEG", "PNG", "WEBP"}:
                raise ValueError("Image must be a PNG, JPEG, or WebP file.")
            image.load()
            image = ImageOps.exif_transpose(image)
            image.thumbnail((max_dimension, max_dimension), Image.Resampling.LANCZOS)
            if image.mode not in {"RGB", "L"}:
                background = Image.new("RGB", image.size, (255, 255, 255))
                if image.mode in {"RGBA", "LA"}:
                    background.paste(image, mask=image.getchannel("A"))
                else:
                    background.paste(image.convert("RGB"))
                image = background
            else:
                image = image.convert("RGB")
            output = io.BytesIO()
            image.save(output, format="JPEG", quality=quality, optimize=True)
    except IMAGE_VERIFY_ERRORS as exc:
        raise ValueError("Image could not be verified.") from exc
    encoded = base64.b64encode(output.getvalue()).decode("ascii")
    result = f"data:image/jpeg;base64,{encoded}"
    if len(result) > max_input_chars:
        raise ValueError("Image is too large.")
    return result


def safe_avatar(value: str) -> str:
    return sanitize_image_data_url(value, 550_000, 512, 84)


def safe_post_image(value: str) -> str:
    return sanitize_image_data_url(value, 700_000, 1400, 82)


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


def bootstrap_payload() -> dict:
    settings = load_settings()
    relay = normalize_relay_url(settings.get("relay_url"))
    host_active = is_hosting_enabled(settings)
    tunnel_info = TUNNEL_MANAGER.info()
    public_url = tunnel_info.get("url") if host_active and tunnel_info.get("status") == "online" else ""
    pending_public_url = tunnel_info.get("url") if host_active and tunnel_info.get("status") == "checking" else ""
    host_invite_token = current_invite_token() if host_active else ""
    host_invite_url = invite_url_for(public_url, host_invite_token)
    pending_host_invite_url = invite_url_for(pending_public_url, host_invite_token)
    if host_active:
        relay = public_url
    return {
        "ok": True,
        "settings": {
            "user_id": settings.get("user_id", ""),
            "nickname": settings.get("nickname", ""),
            "avatar": settings.get("avatar", ""),
            "has_profile": bool(settings.get("user_id") and settings.get("nickname")),
            "connection_mode": settings.get("connection_mode", ""),
            "needs_connection": not bool(settings.get("connection_mode")),
            "relay_url": relay,
            "circle_code": "",
            "invite_token": "" if host_active else settings.get("invite_token", ""),
            "share_activity": bool(settings.get("share_activity", True)),
            "local_url": local_base_url(),
            "local_relay_url": local_relay_base_url(),
            "invite_urls": [],
            "public_url": public_url,
            "pending_public_url": pending_public_url,
            "host_circle_code": "",
            "host_invite_url": host_invite_url,
            "pending_host_invite_url": pending_host_invite_url,
            "host_invite_token": host_invite_token,
            "hosting_enabled": host_active,
            "is_host": host_active,
            "tunnel": tunnel_info,
        },
        "local_activity": TRACKER.summary(),
    }


class BrotherhoodHandler(BaseHTTPRequestHandler):
    server_version = "BrotherhoodMVP/0.1"
    public_relay_server = False

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
        send_security_headers(self)
        self.end_headers()
        self.wfile.write(data)

    def handle_api_get(self, parsed) -> None:
        if parsed.path == "/api/bootstrap":
            json_response(self, 200, bootstrap_payload())
            return
        if parsed.path == "/api/state":
            try:
                settings = load_settings()
                if not settings.get("connection_mode"):
                    raise ValueError("Choose Host or Join first.")
                state = relay_post("/relay/state", actor_payload())
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
                    current_invite_token()
                    settings["connection_mode"] = "host"
                    settings["hosting_enabled"] = True
                    settings["relay_url"] = ""
                    settings["circle_code"] = ""
                    settings["invite_token"] = ""
                    ensure_local_profile(settings)
                    save_settings(settings)
                    TUNNEL_MANAGER.start_async()
                else:
                    stop_public_tunnel()
                    relay_url, invite_token = split_invite_fields(
                        data.get("invite_url") or data.get("relay_url", ""),
                        data.get("invite_token") or data.get("circle_code", ""),
                    )
                    if not relay_url:
                        raise ValueError("Invite URL is required.")
                    if not invite_token:
                        raise ValueError("Invite token is required.")
                    settings["connection_mode"] = "join"
                    settings["hosting_enabled"] = False
                    settings["relay_url"] = relay_url
                    settings["circle_code"] = ""
                    settings["invite_token"] = invite_token
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
                    "invite_token": settings.get("invite_token", ""),
                    "nickname": settings["nickname"],
                    "avatar": settings["avatar"],
                }
                if not payload["nickname"]:
                    raise ValueError("Nickname is required.")
                if settings.get("connection_mode") == "host" and settings.get("hosting_enabled"):
                    ensure_local_profile(settings)
                    status, result = relay_state_for(settings["user_id"], settings["user_secret"])
                    if status >= 400:
                        json_response(self, status, result)
                        return
                else:
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
            json_response(self, 405, {"ok": False, "error": "Use POST for relay state."})
            return
        json_response(self, 404, {"ok": False, "error": "Not found."})

    def handle_relay_post(self, parsed) -> None:
        if not is_hosting_enabled():
            json_response(self, 503, {"ok": False, "error": "This app is not hosting a circle."})
            return
        try:
            data = read_json_body(self)
            if parsed.path == "/relay/state":
                db = load_db()
                actor_id, actor_secret = self.require_actor(db, data)
                status, payload = relay_state_for(actor_id, actor_secret)
                json_response(self, status, payload)
                return
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
        user_id = clean_profile_id(data.get("user_id", ""))
        user_secret = str(data.get("user_secret", ""))
        nickname = clean_text(data.get("nickname", ""), 32)
        avatar = safe_avatar(data.get("avatar", ""))
        if not user_id or not user_secret or not nickname:
            raise ValueError("Nickname is required.")

        exists = user_id in db["profiles"]
        if not exists:
            invite_token = clean_invite_token(data.get("invite_token", ""))
            consume_invite_token(db, invite_token, source_key_for(self))
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


class RelayOnlyHandler(BrotherhoodHandler):
    public_relay_server = True

    def do_GET(self) -> None:
        parsed = urlparse(self.path)
        if not parsed.path.startswith("/relay/"):
            json_response(self, 404, {"ok": False, "error": "Not found."})
            return
        self.handle_relay_get(parsed)

    def do_POST(self) -> None:
        parsed = urlparse(self.path)
        if not parsed.path.startswith("/relay/"):
            json_response(self, 404, {"ok": False, "error": "Not found."})
            return
        self.handle_relay_post(parsed)


def choose_port(bind_host: str, start_port: int, settings_key: str | None = None, avoid: set[int] | None = None) -> int:
    settings = load_settings()
    start = int(settings.get(settings_key, start_port) or start_port) if settings_key else start_port
    avoid = avoid or set()
    for port in list(range(start, start + 20)) + list(range(start_port, start_port + 20)):
        if port in avoid:
            continue
        try:
            server = ThreadingHTTPServer((bind_host, port), BrotherhoodHandler)
            server.server_close()
            if settings_key:
                settings[settings_key] = port
                save_settings(settings)
            return port
        except OSError:
            continue
    raise RuntimeError("Could not find a free local port.")


def main() -> None:
    global SERVER_PORT, RELAY_PORT, LOCAL_IPS_CACHE
    ensure_data_dir()
    load_db()
    load_settings()
    reset_connection_choice()
    SERVER_PORT = choose_port("127.0.0.1", DEFAULT_PORT, "local_port")
    RELAY_PORT = choose_port("127.0.0.1", DEFAULT_RELAY_PORT, "relay_port", {SERVER_PORT})
    LOCAL_IPS_CACHE = get_local_ips()

    TRACKER.start()
    server = ThreadingHTTPServer(("127.0.0.1", SERVER_PORT), BrotherhoodHandler)
    relay_server = ThreadingHTTPServer(("127.0.0.1", RELAY_PORT), RelayOnlyHandler)
    relay_thread = threading.Thread(target=relay_server.serve_forever, name="BrotherhoodRelay", daemon=True)
    relay_thread.start()
    url = local_base_url()
    print(f"Brotherhood is running at {url}")
    print(f"Brotherhood relay is running at {local_relay_base_url()}")
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
        relay_server.shutdown()
        relay_server.server_close()


if __name__ == "__main__":
    main()
