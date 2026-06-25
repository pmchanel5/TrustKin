# KR2.3 - Tunnel Readiness Uses `/relay/ping`

Status: implemented.

## Summary

Tunnel readiness no longer depends on Cloudflare DNS lookup success. The app now performs an HTTPS request to the public candidate URL's `/relay/ping` endpoint and expects a Brotherhood JSON response.

## Code Map

- `app/brotherhood.py`
  - `TunnelManager._url_works()`
  - `handle_relay_get()` route for `/relay/ping`

## Behavior

Readiness requires:

- HTTP status `200`
- JSON `ok == true`
- JSON `app == "Brotherhood"`

TLS validation uses the default Python HTTPS behavior. The previous unverified SSL context was removed.

## Verification

Commands:

```powershell
py -3 -m py_compile app\brotherhood.py
py -3 -m unittest tests.test_security_mvp_alpha
```

The smoke test confirms relay `/relay/ping` is reachable as a relay route and returns `503` before hosting is enabled.

## Future-Agent Notes

- Do not revert to DNS-only readiness. DNS success does not prove the app is reachable.
- Keep `/relay/ping` free of private data.
