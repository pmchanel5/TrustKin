# KR2.2 - UI Shows Tunnel States

Status: implemented.

## Summary

Tunnel status is exposed through `/api/bootstrap` and rendered in the connection panel.

## Code Map

- `app/brotherhood.py`
  - `TunnelManager.status`
  - `TunnelManager.info()`
  - `/api/bootstrap` includes `settings.tunnel`
- `app/web/app.js`
  - `renderSidebar()`
  - `tunnelText`

## Behavior

Known states:

- `off`
- `starting`
- `checking`
- `online`
- `failed`
- `missing`

The UI shows whether the internet invite is ready, starting/checking, unavailable, or failed with an error message.

## Verification

Commands:

```powershell
py -3 -m py_compile app\brotherhood.py
node --check app\web\app.js
```

## Future-Agent Notes

- Add clearer recovery text for common failures: firewall, no internet, Cloudflare unavailable, expired invite.
- Avoid showing Cloudflare output lines that may confuse non-technical testers.
