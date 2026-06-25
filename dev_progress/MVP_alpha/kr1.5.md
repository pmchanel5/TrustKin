# KR1.5 - Security Regression Test Suite

Status: implemented for current MVP Alpha baseline.

## Summary

A stdlib `unittest` security regression file was added to make the alpha security baseline repeatable.

## Code Map

- `tests/test_security_mvp_alpha.py`

## Coverage

The suite currently covers:

- relay-only public exposure
- `/api/*` blocked on relay server
- static UI blocked on relay server
- CSP header present on local API response
- invite join brute-force throttling
- SVG image rejection and JPEG re-encoding
- activity permission filtering
- oversized JSON body rejection before read
- actor-secret query-string regression

## Verification

Command:

```powershell
py -3 -m unittest tests.test_security_mvp_alpha
```

Current result:

```text
Ran 6 tests
OK
```

## Future-Agent Notes

- Keep this suite dependency-light unless the project adopts pytest.
- If adding pytest later, preserve these test cases or port them one-for-one.
- Add tests for post/comment/note spam throttles when those throttles are implemented.
- Add end-to-end malicious member tests once member removal and invite revocation exist.
