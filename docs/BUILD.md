# Building the Phase 0 Baseline

The Phase 0 repository intentionally builds only a minimal Rust core and the new
TrustKin Android shell.

## Rust

Install `rustup`; `rust-toolchain.toml` selects Rust 1.95.0 with Clippy and rustfmt.

```text
cargo fmt --all -- --check
cargo test --workspace --all-targets --locked
cargo clippy --workspace --all-targets --locked -- -D warnings
```

## Android

Install JDK 17 and Android SDK Platform 35, then set `JAVA_HOME` and `ANDROID_HOME`.

Windows:

```text
./gradlew.bat --no-daemon testDebugUnitTest compileDebugAndroidTestKotlin lintDebug assembleDebug
```

Linux/macOS:

```text
./gradlew --no-daemon testDebugUnitTest compileDebugAndroidTestKotlin lintDebug assembleDebug
```

The APK is written under `apps/android/app/build/outputs/apk/debug/`. It is test-only
and development-signed. Release signing is deliberately deferred to its approved
release-engineering task.
