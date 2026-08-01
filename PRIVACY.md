# TrustKin Privacy Posture

TrustKin is currently a Phase 0 architecture shell. It does not yet provide messaging,
identity, Tor, mailbox, analytics, telemetry, or remote service functionality.

The target product requirements prohibit advertising identifiers, commercial
behavioral analytics, metadata sales, hidden telemetry, centralized plaintext access,
key escrow, covert remote access, and mandatory content scanning.

Future components must document what they can observe. In particular:

- local clients will necessarily process plaintext and relationship state while an
  authorized user is using an uncompromised device;
- LAN peers and networks may observe addressing, timing, and sizes;
- Tor reduces direct address exposure but cannot guarantee perfect anonymity or defeat
  every traffic-correlation adversary;
- optional mailbox relays may observe queue activity, timing, sizes, and direct IP
  metadata when HTTPS is used without Tor, but must not possess message-decryption keys;
- optional push providers may receive only content-free wake signals.

Before stable 1.0, diagnostics must remain local and manually exportable. Reports and
evidence are disclosed only through explicit user action.

The normative privacy requirements are in
[`docs/architecture/requirements.md`](docs/architecture/requirements.md).
