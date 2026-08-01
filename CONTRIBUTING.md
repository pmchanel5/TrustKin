# Contributing to TrustKin

TrustKin is GPLv3-or-later software with a controlled maintainer model. GPL rights to
inspect, modify, fork, and redistribute the software are not restricted by this
repository's contribution process.

## Contribution model

- Repository write and merge access is limited to approved maintainers.
- External implementation work requires a maintainer invitation or prior approval on
  a public issue.
- Unsolicited pull requests may be closed when no scope was approved; this does not
  restrict anyone's right to maintain a fork.
- Security-sensitive work requires review from a CODEOWNER who did not make the last
  change under review.

## Before implementation

1. Read `docs/architecture/requirements.md`, `design.md`, and `task.md`.
2. Identify the task ID, dependencies, acceptance criteria, and release gate.
3. Obtain approval before changing requirements, architecture, cryptography, protocol
   formats, trust boundaries, or privacy guarantees.
4. Open or use a task issue containing requirements and design references.

## Pull requests

Pull requests must:

- remain small enough for meaningful review;
- reference a task ID and applicable requirements/design sections;
- include automated and human-test evidence required by the task card;
- update traceability and documentation;
- avoid secrets, private user evidence, plaintext content, or sensitive diagnostics;
- pass formatting, tests, lint, lockfile verification, and repository-policy checks.

Cryptography, protocol, storage, routing, FFI, release/update, and platform key-store
changes are security-sensitive. TrustKin does not accept home-grown cryptographic
primitives or silent weakening of an approved security gate.

## Security reports

Do not disclose exploitable details in an issue or pull request. Follow
[SECURITY.md](SECURITY.md) and use GitHub private vulnerability reporting.
