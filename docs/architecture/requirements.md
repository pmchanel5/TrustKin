# TrustKin Product Requirements Specification

**Document version:** 0.1  
**Status:** Requirements baseline approved for design specification  
**Date:** 2026-07-31  
**Primary platform:** Android  
**Planned platforms:** Windows, then iOS

## 1. Purpose

This document defines the functional, security, privacy, operational, distribution, and release requirements for TrustKin. It is the agreed baseline for the subsequent `design.md` and `task.md` documents.

TrustKin is a public, open-source, privacy-focused messenger intended to provide an alternative to centralized corporate communication platforms and to reduce exposure to corporate and state surveillance.

The product shall support two primary operating modes:

1. **Pure P2P:** direct delivery between user devices through LAN or Tor, without mailbox storage.
2. **Hybrid:** direct delivery when possible, with an optional opaque store-and-forward mailbox for asynchronous delivery.

Additional modes and routing strategies may be introduced later without weakening the security requirements in this document.

## 2. Normative language

The terms **MUST**, **MUST NOT**, **SHOULD**, **SHOULD NOT**, and **MAY** are normative.

- **MUST / MUST NOT:** mandatory for the specified release gate.
- **SHOULD / SHOULD NOT:** expected unless a documented technical or legal reason justifies a deviation.
- **MAY:** optional or implementation-dependent.

## 3. Product vision and principles

TrustKin MUST be designed around the following principles:

- No mandatory telephone number, email address, contact-book upload, central username, or central TrustKin account.
- No mandatory central TrustKin message server.
- End-to-end encryption for private communication.
- User-controlled identities derived from cryptographic keys.
- Direct communication preferred where practical.
- Optional relays treated as untrusted transport and temporary storage providers.
- Local encrypted storage as the authoritative source of user state.
- No advertising identifiers, commercial behavioral analytics, metadata sales, or surveillance-oriented monetization.
- No intentional cryptographic backdoors, key escrow, covert remote-access mechanisms, server-side plaintext scanning, or mandatory client-side content scanning.
- Public documentation of what each component can observe.
- Interoperable, versioned protocols that third parties may implement.
- Community operation and decentralization wherever technically practical.

### 3.1 Public commitment

TrustKin's public commitment SHOULD use strong but accurate language similar to the following:

> TrustKin is designed to do its utmost to reduce surveillance, protect private communication, and support anonymity for eligible users regardless of nationality or other identity characteristics. It uses end-to-end encryption, peer-to-peer communication, Tor, and optional opaque relays so that no mandatory central service can read private conversations. TrustKin does not claim perfect anonymity or protection after a device or operating system has been fully compromised. The project will accurately document what every component can observe and will not intentionally build centralized plaintext access, key escrow, covert surveillance capabilities, or mandatory content scanning.

## 4. Audience and scope

### 4.1 Target audience

- TrustKin is a general-purpose private messenger optimized initially for close relationships and small trusted groups.
- The public application is intended for users aged **18 and older**.
- TrustKin SHOULD avoid collecting dates of birth, identity documents, or age-verification records unless a binding legal or store requirement makes this unavoidable.
- Store-level audience classification and a visible 18+ notice MAY be used without creating an internal age-profile database.

### 4.2 Group scale

- Before and at stable 1.0, private groups MUST support up to 20 identities.
- Large communities, public channels, and substantially larger groups are post-1.0 capabilities.

### 4.3 Product scope for stable 1.0

Stable 1.0 MUST include:

1. One-to-one text messaging.
2. Image messaging.
3. Voice messages.
4. Private groups.
5. One-time message-request links.
6. Reusable "message me" links.
7. Optional anonymous dropbox links.
8. Disappearing messages.
9. Replies and reactions.
10. End-to-end encrypted voice calls.
11. End-to-end encrypted video calls.
12. File attachments.

The original accountability-network features, such as goals, check-ins, wins/plans feeds, and encouragement notes, are not required for stable 1.0 and MAY be reconsidered post-1.0.

## 5. Definitions

- **Identity:** A locally controlled cryptographic identity, independent of phone numbers, email addresses, or central accounts.
- **Trusted contact:** A user identity accepted through a verified invitation or an accepted message request.
- **Message request:** A first-contact message from an identity that is not yet a trusted contact.
- **Pure P2P mode:** Delivery through direct LAN or Tor connections without mailbox storage.
- **Hybrid mode:** Direct delivery plus an optional mailbox fallback or user-selected relay routing strategy.
- **Mailbox relay:** An untrusted, capability-based, store-and-forward service that temporarily stores opaque ciphertext.
- **Media relay:** A real-time packet-forwarding service for calls; it is distinct from a mailbox and MUST NOT persist call media.
- **Recipient receipt:** A cryptographically authenticated acknowledgement generated by the recipient device.
- **Relay storage receipt:** A relay acknowledgement that ciphertext was accepted for temporary storage; it is not proof of recipient delivery.

## 6. Functional requirements

### 6.1 Identity and onboarding

**FR-ID-001** TrustKin MUST permit identity creation without a phone number, email address, central account, or uploaded address book.

**FR-ID-002** Each identity MUST have cryptographically distinct signing and encryption/session material as required by the selected protocol.

**FR-ID-003** TrustKin MUST support signed invitations through text, links, and QR codes.

**FR-ID-004** The user MUST be able to inspect and compare a contact fingerprint or equivalent safety identifier.

**FR-ID-005** TrustKin MUST NOT silently trust a changed contact identity key. It MUST:

- display a prominent warning;
- pause sensitive delivery until the user accepts the change;
- support re-verification by QR code or safety identifier;
- preserve a local history of accepted key changes.

**FR-ID-006** Identity recovery MUST support:

- encrypted export protected by a strong passphrase; and
- direct device-to-device transfer.

A central recovery account MUST NOT be required.

**FR-ID-007** Multi-device operation MUST be included in the protocol and data model before public beta and implemented before stable 1.0. Each device MUST have independently revocable device credentials and sessions.

**FR-ID-008** Multiple isolated identities on one device are post-1.0, but the architecture SHOULD NOT make them infeasible.

### 6.2 Trusted contacts and invitations

**FR-CON-001** Adding a trusted contact MUST require an explicit invitation, accepted message request, or another cryptographically authenticated exchange.

**FR-CON-002** Invitations MUST be signed, versioned, bounded in size, and capable of expiring.

**FR-CON-003** Users MUST be able to revoke or rotate reusable invitations.

**FR-CON-004** TrustKin MUST support local contact renaming, verification state, blocking, removal, and endpoint/session revocation.

### 6.3 One-way invitations and message requests

**FR-REQ-001** Public beta MUST support:

- one-time first-message links; and
- reusable "message me" links.

**FR-REQ-002** Stable 1.0 MUST additionally support an optional anonymous dropbox mode.

**FR-REQ-003** Unknown senders MUST enter a separate Requests inbox and MUST NOT become trusted contacts automatically.

**FR-REQ-004** Accepting a request MUST:

- validate the sender identity or temporary identity material;
- create or upgrade a trusted contact;
- establish dedicated bidirectional routing/session state; and
- preserve the accepted first message in the resulting conversation.

**FR-REQ-005** A recipient MUST be able to send a limited reply through a temporary reply route without first accepting the sender as a trusted contact.

**FR-REQ-006** The recipient MAY disable temporary replies globally or per request.

**FR-REQ-007** Message-request links MUST support revocation, rotation, expiry, rate limits, pending-request limits, local blocking, and optional proof-of-work or equivalent abuse friction.

**FR-REQ-008** Unknown-sender media MUST NOT download automatically. The client MUST first display a small encrypted manifest containing, where safely available:

- claimed sender information;
- media type;
- approximate size; and
- a security warning.

The user MUST explicitly choose to download, delete, block, or report the material.

**FR-REQ-009** Unknown-sender attachment policy MUST be user-configurable. Text-only MUST remain available as the safest policy.

**FR-REQ-010** Executable files MUST NOT be accepted as first-contact attachments.

**FR-REQ-011** Media parsing and safety checks MUST occur locally. Unknown media MUST NOT be uploaded to a third party for mandatory scanning.

### 6.4 Messaging

**FR-MSG-001** TrustKin MUST support one-to-one text, images, voice messages, replies, reactions, disappearing messages, and file attachments according to the release scope.

**FR-MSG-002** Public beta MUST include:

- text;
- images;
- voice messages;
- private groups;
- one-time message requests;
- reusable message links;
- disappearing messages;
- replies and reactions; and
- limited file attachments.

Voice calls, video calls, and anonymous dropboxes MAY follow during beta but MUST be complete before stable 1.0.

**FR-MSG-003** Images SHOULD be normalized and re-encoded locally to remove EXIF and other unnecessary metadata before sending.

**FR-MSG-004** Voice and large attachment transfers MUST support interruption recovery and integrity verification.

**FR-MSG-005** The application MUST distinguish at least these delivery states:

- Preparing
- Queued locally
- Trying direct delivery
- Stored on mailbox
- Received by recipient device
- Read, only when enabled
- Expired
- Permanently failed

**FR-MSG-006** Read receipts MUST be disabled by default and user-selectable in settings. The design MAY also support per-conversation overrides.

**FR-MSG-007** Only a valid recipient-generated cryptographic receipt MAY produce the "Received by recipient device" state.

**FR-MSG-008** A mailbox storage acknowledgement MUST NOT be displayed as final delivery.

**FR-MSG-009** When delivery expires, TrustKin MUST retain the sender's local encrypted conversation copy, stop automatic delivery attempts, mark the item expired, and permit manual resend.

**FR-MSG-010** Duplicate network delivery MUST NOT produce duplicate visible messages.

### 6.5 Groups

**FR-GRP-001** Public beta MUST use a modern group encryption protocol that provides appropriate membership authentication, forward secrecy, and post-compromise recovery. Simple long-term static pairwise encryption is insufficient for the public beta security gate.

**FR-GRP-002** Groups MUST support up to 20 identities before and at stable 1.0.

**FR-GRP-003** Membership changes MUST prevent removed members from receiving future group messages.

**FR-GRP-004** TrustKin MUST clearly disclose that removing a member cannot erase messages or media already received by that member.

**FR-GRP-005** Group state updates MUST be authenticated, versioned, and protected against rollback.

### 6.6 Voice and video calls

**FR-CALL-001** Stable 1.0 MUST provide end-to-end encrypted voice and video calls.

**FR-CALL-002** In Pure P2P mode, call establishment MUST attempt direct encrypted media and MAY offer Tor-routed media with a latency and reliability warning. If no permitted path exists, the call MAY fail.

**FR-CALL-003** In Hybrid mode, calls MAY use a privacy-preserving TURN or equivalent media relay after direct connection fails.

**FR-CALL-004** A media relay MUST forward encrypted real-time packets and MUST NOT persist call media.

**FR-CALL-005** The application MUST disclose when a direct call path may reveal participant IP addresses to the other participant and when a relay or Tor path is being used.

## 7. Operating modes and routing

### 7.1 Pure P2P mode

**FR-NET-001** Pure P2P mode MUST use only direct LAN and/or direct Tor message delivery and MUST NOT deposit message ciphertext in a mailbox.

**FR-NET-002** Pure P2P mode MUST maintain undelivered items in the sender's local encrypted queue until delivery, expiry, or user deletion.

**FR-NET-003** Pure P2P mode MUST NOT promise immediate or time-bounded delivery. It provides eventual delivery when sender and recipient become mutually reachable.

**FR-NET-004** Users MUST be able to choose whether Pure P2P permits content-free third-party wake notifications.

**FR-NET-005** A Strict P2P preset MUST disable proprietary third-party push infrastructure entirely.

### 7.2 Hybrid mode

**FR-NET-006** Hybrid mode MUST support direct-first delivery as its default policy:

1. LAN when suitable.
2. Direct Tor.
3. Recipient-selected mailbox fallback.

**FR-NET-007** Advanced users MUST be able to select additional policies:

- mailbox-first;
- Tor-only;
- relay-only; and
- automatic routing based on battery, network, availability, or user policy.

**FR-NET-008** Routing policy MUST support a global default and per-contact override.

**FR-NET-009** A sender MUST respect the routes advertised and permitted by the recipient. If the recipient permits only Pure P2P, the sender MUST keep the message locally until a direct route becomes available or the message expires.

**FR-NET-010** Future delivery-speed improvements MAY optimize route probing, connection reuse, background wake behavior, and retry scheduling, but MUST NOT silently weaken the selected privacy mode.

## 8. Optional mailbox requirements

### 8.1 Architectural model

**FR-MBX-001** TrustKin MUST support an optional, small, capability-based mailbox relay inspired by SMP architectural principles.

**FR-MBX-002** A mailbox relay MUST NOT require user accounts, phone numbers, email addresses, social profiles, or global user identifiers.

**FR-MBX-003** The relay MUST treat message payloads as opaque ciphertext and MUST NOT possess the keys needed to decrypt them.

**FR-MBX-004** The mailbox MUST be temporary store-and-forward infrastructure, not an authoritative conversation database.

**FR-MBX-005** The mailbox protocol SHOULD support separate send, read, and delete capabilities, using high-entropy random values or cryptographically equivalent capability credentials.

**FR-MBX-006** Queue identifiers and capabilities MUST NOT encode user identity.

**FR-MBX-007** The protocol SHOULD use separate queues per relationship and direction to reduce social-graph correlation.

### 8.2 Supported deployments

**FR-MBX-008** Initial mailbox support MUST include:

- independent community relays;
- user-selected custom relays;
- self-hosted relays, including a documented Docker deployment; and
- a personal mailbox running on a spare device or user-controlled home device.

**FR-MBX-009** The TrustKin project MUST NOT operate a public mailbox relay during the initial product scope. Initial public operation is limited to independent community, custom, self-hosted, and personal mailbox deployments.

**FR-MBX-010** Multiple redundant mailbox relays MAY be added post-1.0.

**FR-MBX-011** Relay protocols and conformance tests MUST be public so independent operators can deploy compatible infrastructure.

### 8.3 Retention and limits

**FR-MBX-012** Default retention SHOULD be:

- ordinary messages: 7 days;
- message requests: 72 hours;
- images and voice attachments: 3 days; and
- incomplete transfers: between 3 and 7 days.

**FR-MBX-013** Users MUST be allowed to select shorter retention periods where the relay supports them.

**FR-MBX-014** Relay items MUST be deleted after authenticated recipient acknowledgement or expiry, subject to crash-safe deletion and documented storage behavior.

**FR-MBX-015** Initial receiving-queue limits SHOULD be:

- no more than 100 pending items; and
- approximately 10 to 25 MiB total temporary storage.

Exact values MAY vary by relay and MUST be disclosed to users.

**FR-MBX-016** Offline text, image, and voice delivery MUST work in the first Hybrid release.

**FR-MBX-017** Large files SHOULD use a separate encrypted attachment-storage mechanism rather than treating the normal mailbox as unlimited object storage.

### 8.4 Mailbox access and metadata

**FR-MBX-018** TrustKin MUST offer these privacy-oriented access presets:

- Maximum Privacy: Tor only.
- Balanced: Tor preferred with HTTPS fallback.
- Maximum Reliability: HTTPS permitted.

**FR-MBX-019** Users MUST be able to choose or override the access preset.

**FR-MBX-020** TrustKin relay software MUST NOT write source IP addresses to persistent application logs.

**FR-MBX-021** HTTP access logging MUST be disabled by default in the reference relay.

**FR-MBX-022** Operational metrics MUST exclude source IP addresses, message contents, stable user identifiers, contact relationships, and queue-to-user mappings.

**FR-MBX-023** TrustKin MUST disclose that a hosting provider, upstream network, firewall, or non-TrustKin infrastructure may still observe connection metadata when direct HTTPS is used.

**FR-MBX-024** Public relay descriptors MUST expose, in a user-readable way:

- operator identity;
- jurisdiction;
- privacy policy;
- Tor availability;
- retention and quota policy; and
- any infrastructure logging that the operator cannot disable.

## 9. Reliability and synchronization

**NFR-REL-001** TrustKin MUST persist accepted outbound messages and queue state before reporting them as queued.

**NFR-REL-002** Delivery MUST use retry, backoff, expiry, deduplication, and authenticated recipient receipts.

**NFR-REL-003** Pure P2P delivery occurs when sender and recipient are reachable at overlapping times; it MUST NOT be marketed as guaranteed immediate delivery.

**NFR-REL-004** Hybrid delivery allows the sender to store opaque ciphertext while the recipient is offline; final delivery occurs when the recipient retrieves and accepts it.

**NFR-REL-005** Neither mode promises instantaneous delivery.

**NFR-REL-006** A relay storage receipt MUST allow the sender to know that temporary storage succeeded, while the UI continues to distinguish this from recipient delivery.

**NFR-REL-007** State writes, migrations, queue updates, and receipt processing MUST be crash-safe and resistant to partial writes.

**NFR-REL-008** Interrupted media transfers MUST resume without retransmitting already authenticated chunks where the selected transport supports resumption.

## 10. Notifications and background operation

**FR-NOT-001** During onboarding, users MUST choose an availability profile with a clear explanation of battery, privacy, and delivery consequences. The profile MUST remain editable in settings.

**FR-NOT-002** Google Play builds MAY use Firebase Cloud Messaging only as a user-selectable, content-free wake signal.

**FR-NOT-003** FCM payloads MUST NOT contain message content, contact identity, conversation identity, or plaintext metadata.

**FR-NOT-004** F-Droid-compatible builds MUST support a combination of:

- UnifiedPush where available;
- periodic polling;
- a visible foreground service where permitted; and
- manual refresh.

**FR-NOT-005** Notification implementations MUST be modular so proprietary push services are not required by the protocol or core client.

**FR-NOT-006** Notification previews MUST be user-selectable and default to a generic message such as "New TrustKin message."

**FR-NOT-007** Background limitations imposed by Android, iOS, device manufacturers, battery-saving policies, or Force Stop MUST be documented honestly.

## 11. Local data protection and privacy UX

**SEC-LOC-001** Local identity keys, session state, contacts, messages, queue state, mailbox capabilities, downloaded media, and settings MUST be encrypted at rest.

**SEC-LOC-002** Platform-provided secure key storage MUST be used where available, including hardware-backed protection where supported.

**SEC-LOC-003** TrustKin MUST support:

- PIN or passphrase unlock;
- biometric unlock;
- platform system credential integration where safe;
- automatic lock timers; and
- immediate manual lock.

**SEC-LOC-004** Screenshot and screen-recording protection MUST be user-selectable. Security-sensitive screens MAY recommend protection by default.

### 11.1 Duress PIN

**SEC-DUR-001** TrustKin MUST support an optional duress PIN distinct from the normal access PIN.

**SEC-DUR-002** The user MUST choose between:

- local silent cryptographic erase; and
- local erase plus best-effort network revocation.

Local silent erase MUST be the default.

**SEC-DUR-003** Local silent erase MUST remove or render unrecoverable, to the extent supported by the platform:

- local identity decryption keys;
- session and ratchet keys;
- encrypted local database access;
- downloaded local media;
- mailbox read/delete capabilities;
- linked-device credentials; and
- cached sensitive notification data.

**SEC-DUR-004** Network revocation MAY attempt to revoke the current device, rotate mailbox capabilities, notify linked devices, and revoke active invitations.

**SEC-DUR-005** The duress action MUST NOT require a confirmation dialog and SHOULD NOT visibly disclose that a duress workflow occurred.

**SEC-DUR-006** Setup MUST display an irreversible-loss warning, and the normal and duress PINs MUST NOT match.

**SEC-DUR-007** TrustKin MUST disclose that physical erasure from flash storage cannot be guaranteed.

### 11.2 Deletion semantics

**FR-DEL-001** The UI and protocol MUST distinguish:

- local message deletion;
- cancellation of queued unsent delivery;
- remote deletion request;
- disappearing-message expiry;
- complete local identity/data deletion;
- linked-device revocation;
- invitation revocation; and
- mailbox queue revocation.

**FR-DEL-002** TrustKin MUST NOT claim that a remote deletion request can erase plaintext already copied, exported, screenshotted, or retained by a recipient.

## 12. Encryption and protocol security

**SEC-CRY-001** All private messages, group messages, media, files, call media, and protocol-sensitive metadata MUST be end-to-end encrypted according to their threat model.

**SEC-CRY-002** Before public beta, the messaging protocol MUST provide:

- forward secrecy;
- post-compromise recovery;
- unique or safely ratcheted message keys;
- authenticated asynchronous session establishment;
- out-of-order message handling; and
- replay protection.

**SEC-CRY-003** Static long-term identity-key encryption alone is insufficient for public beta.

**SEC-CRY-004** Stable 1.0 MUST include a documented post-quantum protection strategy implemented in the production protocol, subject to external review.

**SEC-CRY-005** The protocol MUST be cryptographically agile so suites, primitives, and versions can be upgraded without replacing mailbox or transport APIs.

**SEC-CRY-006** Public beta group messaging MUST use a modern group protocol rather than unaudited static pairwise group encryption.

**SEC-CRY-007** All network envelopes, invitations, endpoint updates, receipts, device-link operations, and group-membership changes MUST be authenticated.

**SEC-CRY-008** Protocol downgrade and endpoint rollback MUST be detected and rejected.

**SEC-CRY-009** Sensitive plaintext and key material SHOULD remain in memory for the shortest practical period and SHOULD be cleared when practical, without making unverifiable guarantees about managed runtimes or operating-system memory.

## 13. Threat model

### 13.1 High-priority adversaries and risks

TrustKin MUST prioritize mitigations against:

- a large-scale observer monitoring significant portions of internet traffic;
- cloud infrastructure providers;
- mass state surveillance;
- targeted government investigation and technical surveillance;
- malicious contacts; and
- malware on an unlocked phone, to the extent possible without claiming security after full endpoint compromise.

### 13.2 Medium-priority adversaries and risks

TrustKin MUST also address:

- mailbox relay operators;
- public Wi-Fi operators; and
- compromised operating-system components, while recognizing that full OS compromise is outside the guaranteed security boundary.

### 13.3 Lower-priority adversary

A stolen but locked phone is lower priority than the adversaries above, but local data encryption and platform secure storage remain mandatory.

### 13.4 Required protections

TrustKin MUST provide or pursue:

- end-to-end encryption;
- forward secrecy;
- post-compromise recovery;
- post-quantum migration/protection by stable 1.0;
- metadata minimization;
- Tor routing options;
- no centralized plaintext access;
- resistance to relay compromise;
- isolation and blocking of malicious contacts;
- replay, spam, resource-exhaustion, and denial-of-service controls;
- hardware-backed local key protection where supported; and
- best-effort resistance to large-scale traffic analysis.

### 13.5 Explicit non-guarantees

TrustKin MUST clearly disclose that it cannot guarantee:

- perfect anonymity;
- protection after the operating system or application process is fully compromised;
- resistance to a global traffic-correlation adversary under all conditions;
- immediate delivery in Pure P2P mode;
- physical erasure from flash storage; or
- prevention of recipients copying plaintext.

## 14. Security prohibitions

TrustKin MUST NOT intentionally implement:

- cryptographic backdoors;
- key escrow;
- undisclosed remote-access functionality;
- centralized plaintext access;
- server-side plaintext scanning;
- mandatory client-side content scanning;
- advertising identifiers;
- commercial behavioral analytics;
- sale or monetization of message or relationship metadata; or
- hidden telemetry containing identities, contacts, message data, or keys.

## 15. Diagnostics and telemetry

**NFR-DIA-001** Before stable 1.0, diagnostics MUST remain local and manually exportable by the user.

**NFR-DIA-002** Diagnostic exports MUST exclude private keys, PINs, plaintext messages, complete onion addresses, mailbox capabilities, and other secrets unless the user explicitly selects specific evidence for a report.

**NFR-DIA-003** Optional, opt-in, privacy-preserving crash reporting MAY be considered post-1.0 after a separate design and privacy review.

**NFR-DIA-004** No diagnostic mechanism may become a prerequisite for using TrustKin.

## 16. Blocking, reporting, and safety

**SAFE-001** TrustKin MUST support immediate local blocking of a reported or unwanted identity.

**SAFE-002** Reports MUST be initiated voluntarily by the recipient. TrustKin MUST NOT automatically upload conversation content.

**SAFE-003** The reporting interface MUST allow the user to select exactly which message, attachment metadata, contact information, and diagnostic evidence are disclosed.

**SAFE-004** A report MUST be sent only to:

- the operator of the relay currently involved, where applicable; and
- the TrustKin project safety contact.

**SAFE-005** The reported identity MUST be blocked locally as part of the report workflow.

**SAFE-006** A project safety contact MAY contact an external authority after reviewing a submitted report, subject to applicable law and the project's published safety policy. TrustKin MUST NOT silently report ordinary private conversations.

**SAFE-007** Relay operators MAY apply relay-specific capability bans, queue revocation, rate limiting, or denial of service to abusive capabilities. There is no required global account ban because there is no central TrustKin account.

**SAFE-008** Public message links MUST be revocable.

**SAFE-009** TrustKin MUST publish clear user conduct, safety, and child-protection standards needed for store distribution, without introducing mandatory content scanning or routine access to plaintext.

## 17. Legal and store compliance

**COMP-001** TrustKin targets global distribution with an initial operational and policy focus on Europe.

**COMP-002** Before Google Play public beta, the project MUST complete a focused legal and store-compliance review covering at least:

- target audience and 18+ declaration;
- privacy disclosures;
- GPL obligations;
- user-generated-content requirements;
- child-safety standards;
- relay operation and operator responsibilities;
- sanctions and export considerations; and
- applicable communications-service law.

**COMP-003** Compliance work MUST NOT be used to justify weakening end-to-end encryption, adding key escrow, or introducing mandatory content scanning.

**COMP-004** TrustKin MUST accurately describe the limits of anonymity, Tor, relay metadata protection, background delivery, remote deletion, and endpoint compromise.

## 18. Platform and distribution requirements

### 18.1 Platform sequence

1. Android.
2. Windows.
3. iOS.

The protocol and shared security model MUST be designed for all three even while Android is the first implementation.

### 18.2 Platform parity

**PLAT-001** Messaging and security properties MUST remain compatible across supported platforms.

**PLAT-002** Secondary UI and operating-system integration MAY differ where platform constraints require it.

**PLAT-003** Android is the first public beta and stable client. The exact minimum Android version for public beta will be finalized in `design.md`; the current implementation baseline is Android 9 or later.

### 18.3 Distribution

The Android client MUST be prepared for distribution through:

- Google Play;
- F-Droid;
- GitHub Releases; and
- direct APK download from an official project website.

Additional Android stores MAY be considered post-1.0.

### 18.4 Internationalization and accessibility

**PLAT-004** English is the initial application language.

**PLAT-005** The UI architecture MUST support localization from the start.

**PLAT-006** Stable 1.0 MUST provide full internationalization readiness, including right-to-left layout support.

**PLAT-007** Stable 1.0 MUST meet applicable mobile accessibility expectations for keyboard/switch access, screen readers, scalable text, contrast, and non-color-only status communication.

## 19. Open-source governance and funding

**GOV-001** TrustKin will remain licensed under GPLv3-or-later unless a documented legal review before public release recommends a compatible change.

**GOV-002** Recipients retain GPL rights to inspect, modify, fork, and redistribute the software and modified versions under the license.

**GOV-003** The official TrustKin repository MAY restrict write, merge, and maintainer access to the current main developer and approved participants.

**GOV-004** External contribution MAY require invitation, prior approval, or a reviewed contribution process.

**GOV-005** TrustKin name, logo, and official distribution identity MAY be protected through a separate trademark and branding policy consistent with GPL rights.

**GOV-006** Third parties MUST be permitted to create compatible clients and relays using the public protocol specification and conformance tests.

**GOV-007** Initial funding is community based, with donations as the intended income path.

**GOV-008** The project will not initially operate a public mailbox relay.

**GOV-009** An optional paid hosted-relay service MAY be considered post-1.0 if the project is successful, subject to a separate governance, privacy, legal, and infrastructure review.

## 20. Release and security gates

### 20.1 Public beta gate

Before public beta, the project MUST complete at least:

- forward-secret, post-compromise-recovering one-to-one protocol;
- modern group protocol;
- two-device Android tests;
- direct LAN tests;
- Tor end-to-end tests;
- mailbox integration tests;
- text, image, voice, group, request-link, disappearing-message, reply/reaction, and limited-file flows;
- protocol test vectors;
- parser and network fuzzing;
- dependency locking;
- signed test/release builds;
- software bill of materials;
- current threat model;
- public security policy;
- local-storage and migration tests;
- notification/privacy verification;
- legal and store-compliance review;
- no known unmitigated critical vulnerabilities; and
- clear experimental-security warnings.

An external audit is encouraged before beta but is not a mandatory beta blocker under this baseline.

### 20.2 Stable 1.0 gate

Stable 1.0 MUST NOT ship until all of the following are complete:

- all twelve stable-1.0 product capabilities;
- post-quantum protection integrated into the production protocol;
- multi-device support with independent device revocation;
- an independent cryptographic and implementation audit;
- remediation of all critical and high audit findings;
- penetration testing;
- reproducible or independently verifiable builds;
- tested signing-key protection and update process;
- documented incident-response procedure;
- responsible vulnerability-disclosure process;
- hardened reference relay and deployment guidance;
- physical-device testing across major Android manufacturers;
- full internationalization readiness and right-to-left support; and
- no known unmitigated critical or high-severity vulnerabilities.

The independent audit is a mandatory blocker for stable 1.0.

### 20.3 Vulnerability reporting

Before funding permits a bounty, TrustKin MUST maintain a responsible-disclosure process. A paid public bug bounty MAY be introduced during beta or before stable 1.0.

### 20.4 Update support

TrustKin's initial support commitment is intentionally minimal but MUST:

- preserve supported data migrations where reasonably possible;
- publish critical security fixes as resources permit;
- refuse network connections from protocol versions with known critical flaws when continued interoperability would endanger users; and
- clearly communicate support and compatibility limits.

Pure P2P interoperability with old clients MAY also be refused when an old protocol has a known critical vulnerability.

## 21. Quality attributes

### 21.1 Security

Security-sensitive behavior MUST fail closed where practical. Unsupported, downgraded, malformed, unauthenticated, expired, replayed, or policy-forbidden traffic MUST be rejected.

### 21.2 Privacy

The default configuration SHOULD minimize metadata exposure while remaining understandable to ordinary users. More restrictive presets MUST be available without requiring a separate application build.

### 21.3 Reliability

Local state, outbound queues, receipts, and migrations MUST survive normal process termination, device restart, and recoverable write interruption.

### 21.4 Maintainability

Cryptography, transport, mailbox, notification, storage, UI, and platform integrations SHOULD have replaceable interfaces and independent test coverage.

### 21.5 Interoperability

Network and storage formats MUST be versioned. Public test vectors and conformance tests MUST permit independent compatible implementations.

### 21.6 Transparency

Security claims, known limitations, relay metadata exposure, external dependencies, and audit findings MUST be documented publicly.

## 22. Explicit pre-1.0 non-goals

The following are not required before stable 1.0 unless later promoted through an approved requirements change:

- public channels or communities larger than 20 identities;
- centralized discovery or searchable user directories;
- advertising or commercial analytics;
- project-operated public mailbox infrastructure;
- multiple isolated identities on one device;
- large-scale social-network feeds;
- the original accountability/check-in feature set; and
- guaranteed protection after full device or operating-system compromise.

## 23. Design decisions reserved for `design.md`

The following are intentionally deferred to the design-specification phase:

- exact one-to-one session protocol and post-quantum construction;
- exact modern group protocol;
- mailbox wire protocol, capability format, and relay implementation language;
- mailbox database and encrypted attachment-store design;
- exact Android minimum version for beta;
- FCM, UnifiedPush, polling, and foreground-service adapter architecture;
- multi-device linking and revocation protocol;
- encrypted export format and device-transfer UX;
- call stack, NAT traversal, TURN policy, and Tor-call behavior;
- duress erase implementation and observable failure behavior;
- route-selection policy engine;
- traffic padding and metadata-hardening strategy;
- release-signing, reproducibility, and update distribution architecture; and
- cross-platform shared-core strategy.

## 24. Requirements change control

Changes that weaken a mandatory security, privacy, or release-gate requirement MUST be documented, justified, reviewed against the threat model, and approved before implementation. Convenience or schedule pressure alone is not sufficient justification for silently weakening a security guarantee.
