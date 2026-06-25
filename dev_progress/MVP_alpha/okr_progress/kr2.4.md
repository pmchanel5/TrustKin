# KR2.4 - Join Screen Uses Invite Link/Token Flow

Status: implemented.

## Summary

The UI moved from separate relay URL plus short circle code to a long invite URL/token flow.

Update on 2026-06-25: the host-side invite field now uses a pending generated Cloudflare invite URL when available, even before local `/relay/ping` readiness has succeeded. This keeps the one-link flow usable for testers when the host machine's own DNS check lags behind the generated quick-tunnel hostname.

## Code Map

- `app/brotherhood.py`
  - `invite_url_for()`
  - `split_invite_fields()`
  - `/api/bootstrap` returns `host_invite_url`
  - `/api/bootstrap` returns `pending_host_invite_url`
  - `/api/connect` accepts `invite_url`, `relay_url`, and `invite_token`
- `app/web/app.js`
  - `parseInviteValue()`
  - setup form invite fields
  - connection form invite fields

## Behavior

Host UI:

- shows one copyable invite URL when hosting
- format is `<relay-url>/join#token=<long-token>`
- uses `host_invite_url` when the tunnel is verified online
- falls back to `pending_host_invite_url` while Cloudflare has created a URL and the app is still checking it
- keeps the copy button enabled for the generated pending invite URL

Join UI:

- accepts a pasted invite URL
- also has a token field for fallback/manual entry
- parses `/join#token=...` into relay base URL and token before calling `/api/connect`

## Verification

Commands:

```powershell
py -3 -m py_compile app\brotherhood.py
node --check app\web\app.js
py -3 -m unittest tests.test_security_mvp_alpha
```

Functional checks confirmed invite URL generation and parsing.

Live check:

- In-app browser showed a generated `https://...trycloudflare.com/join#token=...` URL.
- `Copy invite URL` was enabled while status was `Cloudflare invite URL created`.

## Future-Agent Notes

- Keep the invite copy format single-field for non-technical testers.
- Preserve pending invite copy behavior unless a stronger Cloudflare readiness signal replaces the current host-local DNS/HTTPS check.
- If QR codes are added later, encode only the invite URL, not local secrets.
