# KR1.6 - Actor Secrets Are Not Sent In URL Query Strings

Status: implemented.

## Summary

Relay state sync no longer puts `actor_secret` in the URL. Local `/api/state` now sends actor credentials in a POST body to `/relay/state`.

## Code Map

- `app/brotherhood.py`
  - `/api/state` uses `relay_post("/relay/state", actor_payload())`
  - `handle_relay_get()` rejects GET `/relay/state` with `405`
  - `handle_relay_post()` accepts POST `/relay/state`

## Behavior

- Actor credentials are sent in JSON request bodies.
- GET `/relay/state` no longer accepts actor credentials in query parameters.
- This reduces leakage through browser/server logs, proxies, and copied URLs.

## Verification

Command:

```powershell
py -3 -m unittest tests.test_security_mvp_alpha
```

Coverage:

- `test_actor_secrets_are_not_sent_in_relay_state_query_strings`

## Future-Agent Notes

- Do not add actor secrets to URLs for convenience.
- A stronger future shape would move actor auth to an `Authorization` header or signed per-device session token.
