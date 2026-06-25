# KR1.3 - Join/Profile Rate Limiting And Cooldowns

Status: implemented.

## Summary

Join/profile creation now passes through in-memory throttling before a new profile can consume an invite token.

## Code Map

- `app/brotherhood.py`
  - `JOIN_RATE_LIMIT_PER_SOURCE`
  - `JOIN_RATE_LIMIT_GLOBAL`
  - `JOIN_FAILURE_LOCK_THRESHOLD`
  - `source_key_for()`
  - `check_join_rate_limit()`
  - `record_failed_join()`
  - `consume_invite_token()`
  - `BrotherhoodHandler.relay_profile()`

## Behavior

- Per-source join failures are limited to 5 attempts per minute.
- Global join attempts are limited to reduce tunnel-wide abuse.
- Repeated failed joins temporarily lock the invite.
- Source key prefers `CF-Connecting-IP`, then `X-Forwarded-For`, then socket client address.
- Because tunnel headers can be spoofed outside trusted proxy assumptions, global throttling is also used.

## Verification

Command:

```powershell
py -3 -m unittest tests.test_security_mvp_alpha
```

Coverage:

- `test_invite_join_attempts_are_rate_limited`

## Future-Agent Notes

- This is MVP in-memory protection. It resets when the app restarts.
- If persistence or multi-host behavior is added, move throttling/audit state into a durable store.
- Add post/comment/note spam throttles before broad public testing.
