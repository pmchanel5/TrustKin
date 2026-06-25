# KR1.2 - Circle Join Uses Long Invite Tokens

Status: implemented.

## Summary

Short 24-bit circle codes were replaced with long invite tokens. The host generates a token with `secrets.token_urlsafe(32)`. The host database stores only the SHA-256 hash of that token.

## Code Map

- `app/brotherhood.py`
  - `default_invite_record()`
  - `rotate_invite_token()`
  - `current_invite_token()`
  - `invite_url_for()`
  - `split_invite_fields()`
  - `consume_invite_token()`

## Behavior

- Invite URL format: `/join#token=<long-token>`
- Host-side persistent DB stores `invite.hash`, not the raw token.
- Invite expires after 24 hours.
- Invite can be used at most 3 times.
- Host raw token is kept in process memory for display/copy during the host session.
- Joiner settings store the token locally so profile creation can submit it to the relay.

## Verification

Command:

```powershell
py -3 -m unittest tests.test_security_mvp_alpha
```

Coverage:

- `test_invite_join_attempts_are_rate_limited`
- Functional checks confirmed token parsing from invite URLs and hash-only host storage.

## Future-Agent Notes

- Do not restore `circle_code` as an authorization mechanism.
- `circle_code` remains only as migration/compatibility baggage in a few payload/settings fields.
- If invite revocation UI is added, update `invite.hash`, `expires_at`, `uses`, and in-memory `CURRENT_INVITE_TOKEN` together.
