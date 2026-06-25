# KR2.1 - Host Flow Returns Immediately

Status: implemented.

## Summary

Starting host mode no longer blocks the `/api/connect` response while Cloudflare tunnel setup runs.

## Code Map

- `app/brotherhood.py`
  - `TunnelManager.start_async()`
  - `/api/connect` host branch calls `TUNNEL_MANAGER.start_async()`

## Behavior

- `/api/connect` sets host mode and returns immediately.
- Tunnel startup runs on a background thread.
- The frontend refresh loop can observe tunnel state through `/api/bootstrap`.

## Verification

Commands:

```powershell
py -3 -m py_compile app\brotherhood.py
node --check app\web\app.js
```

Manual smoke:

- `/api/bootstrap` remains responsive while tunnel state changes.

## Future-Agent Notes

- Keep slow network checks outside request handlers.
- If tunnel startup needs cancellation/retry controls, add explicit API state rather than blocking `/api/connect`.
