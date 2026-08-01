# TrustKin

TrustKin is a public, GPLv3-or-later, privacy-focused messenger under active
development. Its target architecture supports direct LAN/Tor communication and
optional opaque store-and-forward mailboxes without a mandatory central TrustKin
account or message server.

> **Current status:** Phase 0 architecture reset. The active application is a
> test-only Android shell, not a working or audited secure messenger. Do not use it
> for real or high-risk communication.

## Source of truth

Development is governed by three approved documents:

- [Product requirements](docs/architecture/requirements.md)
- [Technical design](docs/architecture/design.md)
- [Development task plan](docs/architecture/task.md)

Changes that affect requirements, architecture, sequencing, or security gates must
update the applicable source-of-truth document through the change-control process it
defines.

## Repository boundary

The active repository starts fresh with:

- package/application ID `org.trustkin.app`;
- Android 10 / API 29 as the production baseline;
- a shared Rust workspace for future security-critical core logic;
- a native Kotlin/Jetpack Compose Android shell;
- no active Brotherhood static crypto, serialized state, or Python/Cloudflare code.

The final Brotherhood experimental alpha remains available at the immutable
[`brotherhood-v0.2.1-alpha03-fix`](https://github.com/pmchanel5/TrustKin/releases/tag/brotherhood-v0.2.1-alpha03-fix)
archive. It is unaudited and unsupported for high-risk use.

## Build the Phase 0 baseline

Prerequisites:

- Rust 1.95.0 (selected automatically by `rust-toolchain.toml`);
- JDK 17;
- Android SDK Platform 35.

```powershell
cargo fmt --all -- --check
cargo test --workspace --all-targets --locked
cargo clippy --workspace --all-targets --locked -- -D warnings
./gradlew.bat --no-daemon testDebugUnitTest compileDebugAndroidTestKotlin lintDebug assembleDebug
```

The generated APK is test-only and uses an Android development signing key.

## Governance and security

- [Contribution policy](CONTRIBUTING.md)
- [Project governance](GOVERNANCE.md)
- [Security reporting](SECURITY.md)
- [Privacy posture](PRIVACY.md)
- [Trademark policy](TRADEMARKS.md)

Do not open public issues containing private keys, PINs, onion addresses, mailbox
capabilities, message content, or other private evidence. Use GitHub private
vulnerability reporting for security reports.

## License

TrustKin source code is licensed under
[GNU GPLv3 or later](LICENSE). Project names and logos are addressed separately in
[TRADEMARKS.md](TRADEMARKS.md).
