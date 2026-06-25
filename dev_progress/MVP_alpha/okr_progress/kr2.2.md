# KR2.2 - UI Shows Tunnel States

Status: implemented.

## Summary

Tunnel status is exposed through `/api/bootstrap` and rendered in the connection panel.

Update on 2026-06-25: the UI now separates "Cloudflare generated a candidate invite URL" from "the local readiness check has confirmed `/relay/ping`." This prevents the connection panel from looking stuck or broken when the host computer cannot resolve the generated quick-tunnel DNS name, but other testers can already open it.

## Code Map

- `app/brotherhood.py`
  - `TunnelManager.status`
  - `TunnelManager.info()`
  - `TunnelManager._display_error()`
  - `/api/bootstrap` includes `settings.tunnel`
  - `/api/bootstrap` includes `settings.pending_public_url`
- `app/web/app.js`
  - `renderSidebar()`
  - `renderSetup()`
  - `tunnelLabel()`

## Behavior

Known states:

- `off`
- `starting`
- `checking`
- `online`
- `failed`
- `missing`

The UI shows whether the internet invite is ready, starting/checking, unavailable, or failed with an error message.

When Cloudflare has emitted a `trycloudflare.com` URL but the local readiness check is still pending, the UI shows:

- the generated invite URL in the field
- `Cloudflare invite URL created`
- no raw `urlopen` or `getaddrinfo` error text

The stale tunnel error is not shown while hosting is inactive.

## Verification

Commands:

```powershell
py -3 -m py_compile app\brotherhood.py
node --check app\web\app.js
py -3 -m unittest tests.test_security_mvp_alpha
```

Live check:

- In-app browser showed the generated Cloudflare invite URL while status was still checking.
- Copy button was enabled while local DNS readiness remained unresolved.

## Future-Agent Notes

- Keep generated URL display separate from verified readiness. Do not hide a Cloudflare URL only because this host cannot resolve its DNS yet.
- Add clearer recovery text for remaining common failures: firewall, no internet, Cloudflare unavailable, expired invite.
- Avoid showing Cloudflare output lines that may confuse non-technical testers.
