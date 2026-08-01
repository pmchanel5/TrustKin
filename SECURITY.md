# Security Policy

## Current security status

TrustKin is in the Phase 0 architecture reset. The active Android application is a
test-only shell and does not yet implement messaging, identity, cryptographic sessions,
Tor, or mailbox delivery. It is not an audited secure messenger and must not be used for
real or high-risk communication.

The final Brotherhood experimental alpha is preserved for historical reference at
`brotherhood-v0.2.1-alpha03-fix`. It is unsupported, unaudited, uses obsolete static
message encryption, and is not eligible for production security claims.

## Reporting a vulnerability

Do not disclose vulnerabilities or exploitable details in a public issue, discussion,
pull request, or chat. Use GitHub
[private vulnerability reporting](https://github.com/pmchanel5/TrustKin/security/advisories/new).
Reports are visible only to authorized repository maintainers.

Include, where safely possible:

- affected commit, version, component, and platform;
- realistic impact and attacker prerequisites;
- minimal reproduction using non-sensitive test data;
- relevant security invariant or trust boundary;
- suggested mitigation, if known.

Do not submit private keys, PINs or passphrases, complete onion addresses, mailbox
capabilities, real message content, personal media, unredacted databases, signing keys,
or active production credentials. Maintainers may request narrowly scoped evidence
through the private report.

The project does not currently promise a fixed response SLA. Maintainers will triage
reports according to impact, reachability, and release-gate risk and will coordinate
responsible disclosure when a fix is available.

## System and scope

This policy covers active TrustKin source and release infrastructure, including:

- the shared Rust core and protocol crates;
- Android, Windows, and iOS platform shells and adapters as they are introduced;
- cryptographic, storage, routing, Tor, mailbox, media, notification, backup, duress,
  FFI, and update boundaries;
- reference mailbox and media-relay services;
- build, dependency, signing, release, and update tooling;
- protocol schemas, parsers, test vectors, migrations, and conformance tooling.

The immutable Brotherhood archive is historical and unsupported. A defect in that
archive remains relevant when the same behavior, dependency, credential, or vulnerable
artifact is reachable from active TrustKin code or infrastructure.

## Threat model and trust boundaries

TrustKin prioritizes large-scale network observers, cloud and relay operators, targeted
technical surveillance, malicious contacts, hostile public-network participants, and
malware acting through reachable application or platform interfaces.

The local client core and platform shell are trusted only while the device and operating
system are not fully compromised. LANs, Tor relays, mailbox relays, push providers,
media relays, relay directories, imported files, invitations, deep links, network
packets, and unknown-sender content are untrusted inputs or infrastructure.

TrustKin does not claim guaranteed protection after full operating-system or active
process compromise, perfect anonymity against every global observer, guaranteed
immediate Pure P2P delivery, physical flash erasure, or prevention of an authorized
recipient copying plaintext.

## Security invariants

Security review should treat violation of these properties as reportable when the path
is realistically reachable:

- no mandatory central TrustKin service may obtain private-message plaintext or message
  decryption keys;
- private content and protocol-sensitive metadata must use the approved end-to-end
  encryption and authentication design for their release gate;
- obsolete Brotherhood static encryption must never re-enter an active TrustKin build or
  protocol negotiation path;
- identity, device, endpoint, invitation, receipt, group, mailbox-capability, and update
  state changes must be authenticated, versioned, and rollback/downgrade resistant as
  required by their phase;
- malformed, oversized, replayed, expired, unauthenticated, downgraded, or
  policy-forbidden traffic must fail closed with bounded work and memory;
- local identities, session state, messages, capabilities, media, and settings must be
  encrypted at rest once those features exist;
- logs, diagnostics, notifications, CI artifacts, and reports must not expose plaintext,
  keys, PINs, complete onion addresses, mailbox capabilities, stable private identifiers,
  or source IPs where the SOT prohibits them;
- optional relays, push providers, and transports must not silently override the user's
  selected operating mode, privacy preset, or recipient-advertised constraints;
- cryptographic primitives and secure-messaging protocols must come from reviewed,
  maintained implementations; home-grown cryptographic substitutes are prohibited;
- release artifacts, native binaries, dependencies, and update metadata must be pinned,
  attributable, and verified according to the applicable release gate.

## Reportable findings and severity context

Examples of reportable findings include remote code execution, plaintext or private-key
exposure, authentication or capability bypass, signature or receipt forgery, protocol
downgrade, replay that changes visible state, ratchet or group-state rollback, sandbox or
path traversal, unsafe attachment parsing, cross-contact data exposure, signing/update
compromise, secret-bearing logs, privacy-policy bypass, and remotely triggerable
resource exhaustion beyond documented bounds.

Critical and high severity depend on realistic reachability and impact, not only the
name of a primitive or theoretical weakness. Any known unmitigated critical finding
blocks public beta and stable release. Known unmitigated critical or high findings block
stable 1.0.

## Out of scope and known limitations

The following are not vulnerabilities by themselves unless implementation behavior
contradicts documented guarantees or creates an additional reachable impact:

- the explicit non-guarantees listed in the approved requirements;
- social engineering without a TrustKin control bypass;
- denial of service requiring complete control of the user's device or infrastructure;
- recipients retaining plaintext they were authorized to receive;
- findings that exist only in the immutable Brotherhood archive and have no active
  TrustKin or release-infrastructure path;
- purely theoretical cryptographic concerns without a plausible construction,
  implementation, or protocol impact.

Known limitations are not blanket exclusions. A bypass of a documented mitigation,
privacy preset, warning, authorization boundary, parser limit, or release gate remains
reportable.

## Expectations for security changes

Security-sensitive changes require CODEOWNER review, regression tests, updated threat
and traceability documentation, dependency/lockfile review, and the automated and human
evidence required by the applicable task card. Security, privacy, or release gates may
be strengthened but must not be silently waived.
