# KR2.3 - Tunnel Readiness Uses `/relay/ping`

Status: implemented.

## Summary

Tunnel readiness no longer depends on Cloudflare DNS lookup success. The app now performs an HTTPS request to the public candidate URL's `/relay/ping` endpoint and expects a Brotherhood JSON response.

Update on 2026-06-25: readiness still requires `/relay/ping`, but the candidate Cloudflare URL can be surfaced to the UI while readiness is pending. This is intentional because the host machine's DNS can lag behind the generated quick-tunnel hostname even when another tester can already open the URL.

## Code Map

- `app/brotherhood.py`
  - `TunnelManager._url_works()`
  - `TunnelManager._friendly_url_error()`
  - `TunnelManager._is_dns_error()`
  - `handle_relay_get()` route for `/relay/ping`

## Behavior

Readiness requires:

- HTTP status `200`
- JSON `ok == true`
- JSON `app == "Brotherhood"`

TLS validation uses the default Python HTTPS behavior. The previous unverified SSL context was removed.

Readiness does not require the UI to hide the candidate URL. The app can expose `pending_public_url` while `status == "checking"` and only promotes the same URL to `public_url` after `/relay/ping` succeeds.

DNS errors during local checking are treated as a pending/recovery state, not as raw user-facing exception text.

## Verification

Commands:

```powershell
py -3 -m py_compile app\brotherhood.py
py -3 -m unittest tests.test_security_mvp_alpha
```

The smoke test confirms relay `/relay/ping` is reachable as a relay route and returns `503` before hosting is enabled.

Live check:

- Cloudflare emitted a generated quick-tunnel hostname.
- Local DNS returned `getaddrinfo failed`.
- `/api/bootstrap` exposed a pending invite URL and friendly status rather than a raw Python exception.

## Future-Agent Notes

- Do not revert to DNS-only readiness. DNS success does not prove the app is reachable.
- Do not make `/relay/ping` success a prerequisite for displaying or copying the generated Cloudflare invite URL.
- Keep `/relay/ping` free of private data.
