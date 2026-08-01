# Phase 0 implementation record

## Purpose and authority

This record documents the implementation of Phase P0, tasks `TK-P0-01` through
`TK-P0-06`. The authoritative product and architecture sources remain:

- `docs/architecture/requirements.md`
- `docs/architecture/design.md`
- `docs/architecture/task.md`

This file records implementation evidence and does not override those sources.

## Result

The active repository has been reset from the experimental Brotherhood Android
messenger to the TrustKin Phase 0 baseline. Brotherhood remains recoverable from an
immutable tag and GitHub release. The active tree now contains a minimal shared Rust
core, a test-only Android shell, repository governance, traceability tooling, and
baseline CI. No Brotherhood messaging, static encryption, Tor, Cloudflare, or serialized
state implementation is part of the active TrustKin build graph.

`G-RESET` must not be declared complete until both remaining human/external checks are
recorded: a human installs and launches the archived Brotherhood APK, and the Phase 0
pull request passes its required GitHub checks and is approved and merged under branch
protection.

## Task status

| Task | Implementation status | Acceptance status |
| --- | --- | --- |
| `TK-P0-01` | Final Brotherhood source, test APK, release artifact, checksum, limitations, tag, and release are preserved. | Source/build/checksum complete; human APK install and launch remains required. |
| `TK-P0-02` | Repository, active documentation, Gradle project, Android label, and package identity use TrustKin and `org.trustkin.app`. | Local configuration/build/package checks pass. |
| `TK-P0-03` | Legacy Python, Cloudflare, Brotherhood Android protocol, static cryptography, old state, and checked-in APK paths are removed from the active tree. | Active-path search and build-graph checks pass; history remains available through the archive tag. |
| `TK-P0-04` | Governance, CODEOWNERS, contribution, trademark, privacy, and security policies exist; the repository is public; private vulnerability reporting and branch protection are enabled. | Maintainer and protection settings verified through GitHub; the policy was approved by the project owner. |
| `TK-P0-05` | Issue forms, labels, milestones, P0/P1 issues, and traceability validation are established. | YAML parsing and a live P0 issue traceability check pass. |
| `TK-P0-06` | Pinned baseline CI for Rust, Android, policy, dependency locks, tests, lint, and secret patterns is implemented. | Local equivalents and the first GitHub PR run pass; all three status contexts are required on protected `main`. |

## TK-P0-01: immutable Brotherhood archive

- Tag: `brotherhood-v0.2.1-alpha03-fix`
- Release: <https://github.com/pmchanel5/TrustKin/releases/tag/brotherhood-v0.2.1-alpha03-fix>
- Archived debug-test APK SHA-256:
  `a9e5c01512d5007e182df47ace2d40ef75575c4fdec0ef072922529a03908d32`
- Archived unsigned release APK SHA-256:
  `bd33f93047f3ea3d084111e5dadd7936ed1a2b6583d35392c1934f04045ff47c`
- The release identifies the software as experimental, unsupported, and unsuitable for
  high-risk use.
- Before the reset, the archived Android source completed unit tests, Android-test
  compilation, lint, debug assembly, and release assembly: 112 Gradle tasks succeeded.

Historical code must be inspected from the immutable tag rather than restored into an
active TrustKin production path.

## TK-P0-02 and TK-P0-03: identity reset and active tree

The GitHub repository is public at <https://github.com/pmchanel5/TrustKin>. The Gradle
root project is `TrustKin`; the Android namespace and application ID are
`org.trustkin.app`; the current test shell is `0.1.0-phase0`, version code `1`, and
requires Android API 29 or newer.

The active implementation is intentionally small:

- `crates/trustkin-core` is the initial Rust workspace member. It forbids unsafe Rust
  and exposes only a build-identity placeholder and unit test.
- `apps/android/app` is a Jetpack Compose shell that proves the TrustKin package,
  platform build, local unit-test, Android-test compilation, lint, and APK boundaries.
- `legacy/README.md` points maintainers to the immutable archive and states that old
  components must not be copied into the active architecture.

Removed active paths include the Python/Cloudflare prototype, the
`org.brotherhood.app` Android implementation, its static ECIES-style messaging code,
serialized-state storage, LAN/Tor transport implementation, obsolete tests and design
documents, and the checked-in legacy APK release folder.

No data or identity migration from Brotherhood is provided. This follows the SOT reset
decision and prevents obsolete protocol or state assumptions from becoming TrustKin
compatibility constraints.

## TK-P0-04: governance and security

The public governance baseline consists of:

- `.github/CODEOWNERS`, with `@pmchanel5` and `@xMrBobo` covering the repository and
  security-sensitive paths;
- `GOVERNANCE.md`, `CONTRIBUTING.md`, `SECURITY.md`, `TRADEMARKS.md`, `PRIVACY.md`,
  `NOTICE.md`, and the pull-request template;
- invitation-based maintainer governance while retaining the GPL right to fork;
- a private vulnerability intake at
  <https://github.com/pmchanel5/TrustKin/security/advisories/new>.

The security policy was resolved as the repository-wide policy after approval. It
documents current non-claims, trust boundaries, security invariants, reportable
findings, exclusions that are not blanket waivers, and required review evidence.

GitHub `main` protection requires one approving review, CODEOWNER review, dismissal of
stale approvals, approval after the latest push, administrator enforcement, linear
history, and resolved conversations. Force pushes and branch deletion are disabled.

GitHub personal repositories expose `@xMrBobo` as a collaborator with write permission;
there is no organization-style `maintain` role on this repository. That permission can
review CODEOWNED changes but cannot bypass the protected `main` rules.

## TK-P0-05: traceability

The repository includes structured task and bug issue forms. They warn reporters not to
post secrets or private evidence publicly and capture task ID, blocking status,
requirements references, design references, security sensitivity, acceptance criteria,
and validation evidence.

The canonical label schema is stored in `.github/labels.json` and can be synchronized
with `tools/github/sync_labels.py`. Phase issues can be bootstrapped with
`tools/github/bootstrap_phase_issues.py`.

Created milestones:

- `M0 TrustKin Reset`
- `M1 Architecture Validation`

Created tracking issues:

- `TK-P0-01` through `TK-P0-06`: GitHub issues 13 through 18
- `TK-P1-01` through `TK-P1-08`: GitHub issues 19 through 26

`tools/traceability/check_issue_traceability.py` validates gate-blocking issue text.
Its unit tests pass, and live issue 13 passes the same validation.

Review hardening changed `tools/github/bootstrap_phase_issues.py` to end each parsed
task card at the next Markdown heading. This prevents the last planned task in a phase
from absorbing the following phase introduction or, for the last planned P1 task, the
remainder of the SOT. Synthetic end-of-phase and current-SOT regression tests verify
the exact `TK-P0-06` and `TK-P1-08` reference fields used by generated issue bodies.
The previously generated bodies of issues 18 and 26 were regenerated with the fixed
parser and verified against `rendered_body()`; their SOT references are now limited to
`Requirements §20; design §§28, 30, 32.` and `PLAT-003; design §24.4.`, respectively.

## TK-P0-06: protected build baseline

The repository pins Rust with `rust-toolchain.toml`, commits `Cargo.lock`, enables
Gradle dependency locking, and commits the Android lockfile. The baseline workflows use
least-privilege permissions and pin third-party actions to immutable commit SHAs.

`baseline-ci.yml` defines these job names for branch protection:

- `Repository policy`
- `Rust baseline`
- `Android baseline`

The policy job checks traceability-tool tests, forbidden secret and legacy patterns,
and lockfiles. Secret scanning includes unencrypted and encrypted private-key PEM
headers plus prohibited key-store files. The legacy checks reject the retired Android
namespace, Cloudflare tunnel identifiers, static ECIES suite, serialized state store,
and `brotherhood.py` production path in active product/build locations. Architecture,
migration, and phase records remain able to name the retired components. Unit tests
cover each rejection boundary and the documentation exception.

Review hardening removed two content-scan escape hatches. Forbidden ASCII signatures
are now scanned across the complete tracked file after NUL removal, so UTF-16/UTF-32,
NUL-injected, binary-looking, and files larger than 2 MB cannot bypass credential or
active-legacy checks. The private-key matcher accepts any standard PEM label ending in
`PRIVATE KEY` or `PRIVATE KEY BLOCK`, and AWS temporary access-key IDs are covered
alongside long-lived IDs. Regression fixtures cover each boundary.

Rust CI checks formatting, tests, and Clippy with warnings denied. Android CI checks
the dependency lock, unit tests, Android-test compilation, lint, and a test-only debug
APK. No release keys are available to the workflow.

Pull request 27 is the protected review path for this reset:
<https://github.com/pmchanel5/TrustKin/pull/27>. Its first Baseline CI run completed
successfully at <https://github.com/pmchanel5/TrustKin/actions/runs/30678680847>.
`Repository policy`, `Rust baseline`, and `Android baseline` are now strict required
status checks on `main`.

`issue-traceability.yml` validates opened and edited task issues separately from code
CI.

## Validation evidence

The following local checks passed on the Phase 0 tree:

- `cargo fmt --all -- --check`
- `cargo test --workspace --all-targets --locked` (one Rust unit test)
- `cargo clippy --workspace --all-targets --locked -- -D warnings`
- `python -m unittest discover tools -p test_*.py` (21 Python tests)
- forbidden-pattern and secret-pattern scanning
- parsing all three `.github/ISSUE_TEMPLATE/*.yml` files as YAML
- Gradle unit tests, Android-test compilation, lint, and debug APK assembly (61 tasks)
- Android package inspection: `org.trustkin.app`, version `0.1.0-phase0`, minimum SDK
  29, target SDK 35, label `TrustKin`
- active-path search for Brotherhood package names, Cloudflare, static crypto, and old
  state-store identifiers
- `git diff --check`

The locally assembled TrustKin Phase 0 debug APK has SHA-256:
`3c2daf067b044111b773f2c72d85b4f07401076d2b0925a2dc5392843f3a91a1`.
It is a test-only shell, not a messenger release.

## Follow-up and operational notes

1. A human must install and launch the APK from the Brotherhood archive release and
   record the device/API result on `TK-P0-01`.
2. The Phase 0 pull request must remain green after any review changes; its three
   baseline contexts are already required by `main` protection.
3. An approved maintainer must review and merge the pull request; direct pushes to
   `main` are not part of the workflow.
4. Phase P1 architecture and dependency validation begins only after `G-RESET` is
   formally closed.
