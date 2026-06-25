# Brotherhood MVP Alpha - Main OKR

Status: active OKR source of truth.
Primary goal: move Brotherhood from a private MVP into a safe, understandable, reproducible alpha that can be tested by close friends without exposing local controls or creating avoidable privacy risk.

This document follows the checkbox style used in task files: objectives and key results can be marked complete with `[x]` only when implementation and verification evidence exists. Implementation details for completed KRs live under `dev_progress/MVP_alpha/`.

## Progress semantics

- `[x]` means implemented and backed by code, repository state, or test evidence.
- `[ ]` means not implemented, not externally validated, or intentionally deferred.
- Objective 1 is complete for the current MVP Alpha security baseline.
- Objective 2 is partially complete; third-party tester validation is still open.
- Release, privacy-center, threat-model, and feature-expansion controls remain active follow-up work.

## Alpha guardrails

- Define and preserve a clear trust model before adding meeting, audio, or video features.
- Keep public internet exposure relay-only; local controls stay local.
- Use long invite tokens, revocation/expiry, rate limiting, and audit-friendly failure behavior.
- Keep privacy controls understandable: users must know what is shared and who can see it.
- Prefer notifications and consent-based coordination before heavy media features.
- Keep packaged builds reproducible and out of source history.

---

## [x] Objective 1 - Make Brotherhood safe enough for private alpha testing

Goal: users can invite close friends without accidentally exposing local controls or private circle data.

### [x] KR1.1 - Public tunnel exposes only `/relay/*`

Target: public Cloudflare tunnel exposes only `/relay/*`; `/api/*` and static UI are unreachable from the public tunnel.

Implementation status: runtime-complete.
Reference: `dev_progress/MVP_alpha/kr1.1.md`

### [x] KR1.2 - Circle join uses 128-bit+ invite tokens

Target: circle join uses long invite tokens, not 24-bit codes.

Implementation status: runtime-complete.
Reference: `dev_progress/MVP_alpha/kr1.2.md`

### [x] KR1.3 - Join/profile endpoints have rate limiting

Target: join/profile paths have per-source rate limiting, global throttling, and failed-attempt cooldown behavior.

Implementation status: runtime-complete.
Reference: `dev_progress/MVP_alpha/kr1.3.md`

### [x] KR1.4 - Image uploads are decoded, verified, and re-encoded

Target: image uploads are decoded, verified as PNG/JPEG/WebP, re-encoded, and reject SVG/script-capable payloads.

Implementation status: runtime-complete.
Reference: `dev_progress/MVP_alpha/kr1.4.md`

### [x] KR1.5 - Security regression tests cover the alpha baseline

Target: security tests cover API exposure, brute-force joins, stored XSS image vectors, permission bypass, oversized payloads, and actor-secret query regressions.

Implementation status: test-complete for current MVP Alpha baseline.
Reference: `dev_progress/MVP_alpha/kr1.5.md`

### [x] KR1.6 - No actor secrets are sent in URL query strings

Target: actor secrets are sent in request bodies, not URL query strings.

Implementation status: runtime-complete.
Reference: `dev_progress/MVP_alpha/kr1.6.md`

### Initiatives

- [x] Split app into local UI/API server and relay-only server.
- [x] Add invite-token model with expiry and limited uses.
- [x] Add basic in-memory rate limiter for MVP.
- [x] Add Pillow-based image validation and re-encoding.
- [x] Add CSP and security headers.
- [x] Add MVP Alpha security regression tests.
- [x] Move relay state sync from GET query secrets to POST body.

---

## [ ] Objective 2 - Make connection flow reliable and understandable

Goal: non-technical testers can host/join without confusion.

### [x] KR2.1 - Host flow returns immediately

Target: host flow returns immediately and tunnel starts in the background.

Implementation status: runtime-complete.
Reference: `dev_progress/MVP_alpha/kr2.1.md`

### [x] KR2.2 - UI shows tunnel states

Target: UI shows tunnel states: starting, checking, online, failed.

Implementation status: runtime-complete.
Reference: `dev_progress/MVP_alpha/kr2.2.md`

### [x] KR2.3 - Tunnel readiness uses `/relay/ping`

Target: tunnel readiness is based on successful `/relay/ping`, not DNS only.

Implementation status: runtime-complete.
Reference: `dev_progress/MVP_alpha/kr2.3.md`

### [x] KR2.4 - Join screen uses one invite link/token flow

Target: join screen uses one copyable invite link/token flow instead of separate URL plus weak code.

Implementation status: runtime-complete.
Reference: `dev_progress/MVP_alpha/kr2.4.md`

### [ ] KR2.5 - Third-party testers can host/join without developer help

Target: at least 3 third-party testers can host/join without developer help.

Implementation status: not externally validated.

### Initiatives

- [x] Convert tunnel start to background job.
- [x] Poll tunnel state through `/api/bootstrap`.
- [x] Create copyable invite URL format: `/join#token=<long-token>`.
- [ ] Add clearer user-facing recovery steps for firewall, Cloudflare unavailable, wrong invite, and expired invite.
- [ ] Run and record third-party host/join tests.

---

## [ ] Objective 3 - Build trustworthy privacy and permissions

Goal: users understand and control exactly what friends can see.

### [ ] KR3.1 - Every activity-sharing screen shows what is shared and who can see it

Target: every activity-sharing screen shows what is shared and who can see it.

### [ ] KR3.2 - Users can revoke a member's access and see the effect immediately

Target: revocation is clear, immediate, and verified by tests.

### [ ] KR3.3 - Users can pause sharing, delete activity, and remove their local profile

Target: pause, delete activity, and remove local profile flows exist.

### [ ] KR3.4 - README and in-app privacy copy match actual behavior

Target: docs and UI privacy language stay aligned with implementation.

### [ ] KR3.5 - Threat model document exists

Target: threat model covers host, member, outsider, leaked invite, and malicious member.

### Initiatives

- [ ] Add Privacy Center panel.
- [ ] Add delete/export/reset controls.
- [ ] Add member removal and invite revocation.
- [ ] Add `SECURITY.md`.
- [ ] Add `THREAT_MODEL.md`.

---

## [ ] Objective 4 - Establish clean release and testing workflow

Goal: source stays clean; packaged builds are reproducible.

### [x] KR4.1 - `dist/` remains ignored and untracked

Target: `dist/` remains ignored and never manually committed.

Implementation status: repository-state-complete; ongoing policy.
Reference: `dev_progress/MVP_alpha/kr4.1.md`

### [ ] KR4.2 - GitHub Actions builds Windows package

Target: GitHub Actions builds Windows package on push/tag.

### [ ] KR4.3 - Per-commit builds are workflow artifacts

Target: per-commit builds are uploaded as workflow artifacts.

### [ ] KR4.4 - Tester builds are GitHub Release assets

Target: real tester builds are uploaded as GitHub Release assets.

### [ ] KR4.5 - `cloudflared.exe` is not stored in normal source history going forward

Target: CI downloads a pinned Cloudflare binary and verifies SHA-256.

### [x] KR4.6 - Start scripts contain no machine-specific absolute paths

Target: `.bat` and `.ps1` scripts have no local machine paths.

### Initiatives

- [ ] Add CI build workflow.
- [ ] Add release workflow on `v*` tags.
- [ ] Add checksum verification for Cloudflare binary.
- [x] Remove local Codex Python fallback path from `.bat` and `.ps1`.

---

## [ ] Objective 5 - Prepare feature expansion without weakening security

Goal: new features do not create avoidable privacy/security holes.

### [ ] KR5.1 - Each new feature has a mini threat review

Target: every feature touching trust boundaries has a short threat review before implementation.

### [ ] KR5.2 - Notifications ship before heavy media features

Target: local notifications and async circle update notifications come before video/audio.

### [ ] KR5.3 - Meeting requests ship with consent and visibility rules

Target: meeting requests have explicit consent and visibility behavior.

### [ ] KR5.4 - Video/audio are blocked until risk decisions are documented

Target: video upload/audio calls are blocked until auth, storage, moderation, and bandwidth decisions are documented.

### [ ] KR5.5 - Regression tests are added for sensitive features

Target: every feature touching identity, media, or permissions adds regression tests.

### Initiatives

- [ ] Add async circle update notifications.
- [ ] Add local desktop notifications.
- [ ] Add meeting request MVP.
- [ ] Delay video/audio until the security baseline, privacy model, and release workflow are stronger.
