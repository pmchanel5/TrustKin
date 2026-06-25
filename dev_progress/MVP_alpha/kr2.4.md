# KR2.4 - Join Screen Uses Invite Link/Token Flow

Status: implemented.

## Summary

The UI moved from separate relay URL plus short circle code to a long invite URL/token flow.

## Code Map

- `app/brotherhood.py`
  - `invite_url_for()`
  - `split_invite_fields()`
  - `/api/bootstrap` returns `host_invite_url`
  - `/api/connect` accepts `invite_url`, `relay_url`, and `invite_token`
- `app/web/app.js`
  - `parseInviteValue()`
  - setup form invite fields
  - connection form invite fields

## Behavior

Host UI:

- shows one copyable invite URL when hosting
- format is `<relay-url>/join#token=<long-token>`

Join UI:

- accepts a pasted invite URL
- also has a token field for fallback/manual entry
- parses `/join#token=...` into relay base URL and token before calling `/api/connect`

## Verification

Commands:

```powershell
py -3 -m py_compile app\brotherhood.py
node --check app\web\app.js
```

Functional checks confirmed invite URL generation and parsing.

## Future-Agent Notes

- Keep the invite copy format single-field for non-technical testers.
- If QR codes are added later, encode only the invite URL, not local secrets.
