# KR1.1 - Public Tunnel Exposes Only `/relay/*`

Status: implemented.

## Summary

The app now runs two HTTP servers with separate responsibilities:

- local UI/API server on `127.0.0.1:8765`
- relay-only server on `127.0.0.1:8766`

Cloudflare Quick Tunnel is pointed at the relay-only server. Public tunnel traffic can reach `/relay/*` only; `/api/*` and static UI routes return `404` from the relay-only server.

## Code Map

- `app/brotherhood.py`
  - `DEFAULT_RELAY_PORT = 8766`
  - `local_relay_base_url()`
  - `TunnelManager._launch()` uses `local_relay_base_url()`
  - `RelayOnlyHandler`
  - `main()` starts both servers on `127.0.0.1`

## Behavior

- Local browser UI uses the UI/API server.
- Host-mode internal relay calls use the relay server.
- Cloudflare tunnels only the relay server.
- The relay-only handler rejects any path that does not start with `/relay/`.
- The app no longer binds the main UI/API server to `0.0.0.0`.

## Verification

Command:

```powershell
py -3 -m unittest tests.test_security_mvp_alpha
```

Coverage:

- `test_public_relay_server_blocks_local_api_and_static_ui`
- UI `/api/bootstrap` returns `200`.
- Relay `/api/bootstrap` returns `404`.
- Relay `/` returns `404`.

## Future-Agent Notes

- Do not point `cloudflared --url` at the UI/API server.
- Do not reintroduce public LAN binding for local controls unless a separate explicit LAN-sharing mode is designed and reviewed.
- Keep relay routes narrow and auditable.
