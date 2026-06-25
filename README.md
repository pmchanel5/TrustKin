# Brotherhood

A private desktop MVP for a small friend circle: post wins or plans, send encouragement notes, and share light activity summaries only with people you allow.

## Run it

Double-click `Start Brotherhood.bat`.

The app opens in your browser as a local desktop app. On first launch, create a profile. Nickname is required; image is optional.

After that, each launch asks whether you want to `Host circle` or `Join circle`. Your profile stays on the computer, but the connection mode resets when the app closes.

## Use it with friends

1. One person chooses `Host circle`.
2. Wait a few seconds for the public Relay URL to appear. It should usually be an `https://...trycloudflare.com` address.
3. Each friend runs the same app on their own computer, chooses `Join circle`, and pastes those two values.
4. For activity sharing, each person must ask and be allowed.

You can switch modes from the Connection panel. Switching from Host to Join stops public relay hosting immediately; the local app stays open so you can enter the circle you want to join.

Use `Close app` in the top bar to stop the local server and the app process.

Distance hosting uses Cloudflare Quick Tunnel through the bundled `cloudflared.exe`. The public URL is temporary and changes whenever the host restarts the app. If the public URL does not appear, check that the host computer has internet access and that antivirus/firewall did not block `cloudflared.exe`.

## Privacy shape

Brotherhood does not take screenshots, read keystrokes, share full URLs, or share search queries.

It shares only:

- active app names sampled roughly every 10 seconds
- app time estimates for the last hour

It does not share websites or browser history. Chrome is shown only as `Chrome`, Discord as `Discord`, Steam as `Steam`, and Codex as `Codex`.

## Data

Local data is stored in `%APPDATA%\BrotherhoodMVP`.

The host computer stores the shared circle data. This MVP is for trusted friends, not for public deployment.

## Known MVP limits

- No end-to-end encryption yet.
- Friends need to run the app for their own activity to update.
- The host must keep Brotherhood open for friends to connect.
- The public tunnel URL is temporary and should only be shared with trusted friends.
- This is a prototype, not a hardened security product.
