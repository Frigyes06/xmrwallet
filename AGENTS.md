# AGENTS.md

## Scope and project overview

These instructions apply to the entire repository. There are currently no more-specific `AGENTS.md` files in subdirectories.

This repository is Monerujo, an Android Monero wallet. It is a single Gradle module (`:app`) containing:

- Java Android UI, wallet, networking, exchange, Tor/Orbot, and Ledger integration code under `app/src/main/java/`.
- A small JNI/C++ bridge under `app/src/main/cpp/monerujo.cpp`.
- Android resources and network/build-type source sets under `app/src/main/` and `app/src/{alpha,debug,stagenet}/`.
- Prebuilt Monero and third-party native libraries under `external-libs/`; these are generated locally and ignored by Git.
- Local unit tests under `app/src/test/`.

The wallet handles private keys, seeds, passwords, daemon credentials, and real funds. Treat changes to wallet storage, signing, address/URI parsing, synchronization, node connections, Ledger code, exchange integrations, and network selection as security-sensitive.

## Repository map

| Path | Purpose |
| --- | --- |
| `app/src/main/java/com/m2049r/xmrwallet/` | Main wallet/application code |
| `app/src/main/java/com/m2049r/xmrwallet/model/` | Wallet and Monero model/manager wrappers |
| `app/src/main/java/com/m2049r/xmrwallet/service/` | Background services, exchanges, and integrations |
| `app/src/main/java/com/m2049r/xmrwallet/util/` | Parsing, formatting, crypto-adjacent, storage, and utility code |
| `app/src/main/cpp/` | JNI bridge to the Monero wallet API |
| `app/src/test/` | JVM unit tests; network-facing code is generally tested with mocks |
| `app/src/main/res/` | Shared Android resources and translations |
| `app/src/{alpha,debug,stagenet}/` | Variant-specific resources |
| `app/CMakeLists.txt` | Links generated native libraries for each Android ABI |
| `external-libs/` | Docker/Podman builds for Monero and third-party native libraries |
| `doc/BUILDING-external-libs.md` | Existing native-library build guide |
| `.circleci/config.yml` | CI dependency download and unit-test workflow |

## Toolchain and prerequisites

Use the Gradle wrapper from the repository root; do not install or invoke a system Gradle version.

The versions currently declared by the project are:

- Gradle wrapper: `8.14.5`.
- Android Gradle Plugin: `8.13.2`.
- Compile/target SDK: Android 35; build tools `35.0.0`.
- Minimum Android SDK: 21.
- Java source/target compatibility: Java 17.
- Gradle daemon toolchain: Java 21, as configured in `gradle/gradle-daemon-jvm.properties`.
- Android NDK: `17.2.4988734`.
- Docker or Podman and GNU `make` for rebuilding native libraries.

Android Studio is useful for emulator/device work, but command-line checks should use `./gradlew` so they match CI.

## Local setup

1. Create a local `monerujo.id` file at the repository root. It is intentionally ignored by Git and is read by `app/build.gradle` to inject `BuildConfig.ID_F`, which is used by the Exolix integration. Obtain the value through the project’s private maintainer channel; do not invent, commit, log, or paste the credential into source or issues.

   Minimal shape:

   ```properties
   ID_F=<locally-provided-value>
   ```

2. Prepare the native dependency checkout. Follow `doc/BUILDING-external-libs.md` and use the Monero revision compatible with the current native Dockerfiles and wallet API. The expected checkout is linked as `external-libs/monero`:

   ```sh
   git clone https://github.com/monero-project/monero.git ../monero
   cd ../monero
   git checkout <compatible-release-or-project-branch>
   git submodule update --init --force
   cd -
   ln -s ../monero external-libs/monero
   ```

   The exact Monero revision is a compatibility boundary: changing it can alter the wallet API and native ABI. Do not silently upgrade it while making an unrelated app change.

3. Build the native libraries from `external-libs/` when they are absent or stale:

   ```sh
   cd external-libs
   make debug                 # arm64-v8a and armeabi-v7a, suitable for normal debug work
   make                      # arm64-v8a, armeabi-v7a, and x86_64
   cd ..
   ```

   Override the detected container runtime or parallelism when needed, for example `make CONTAINER_RUNTIME=podman NPROC=4`. The build produces ignored directories such as `arm64-v8a/`, `armeabi-v7a/`, `x86_64/`, `include/`, and `VERSION`.

## Build and test commands

Run commands from the repository root. The project has two flavor dimensions: type (`prod`, `alpha`) and network (`mainnet`, `stagenet`, `devnet`), plus `debug` and `release` build types. Gradle task names combine those dimensions, for example `assembleProdMainnetDebug`.

Recommended checks:

```sh
# Discover the exact tasks available in the checked-out plugin/toolchain.
./gradlew tasks --all

# Same unit-test entry point used by CircleCI.
./gradlew test

# Run one flavor's JVM tests when iterating quickly.
./gradlew testProdMainnetDebugUnitTest

# Compile/package a normal development APK.
./gradlew assembleProdMainnetDebug

# Run Android lint for the application module.
./gradlew lint
```

The native libraries must exist before an APK task that configures CMake or links JNI code. If a build fails with missing `.a` files or `wallet2_api.h`, fix the `external-libs` setup rather than copying binaries into Git.

CI currently runs `./gradlew androidDependencies` followed by `./gradlew test`, and stores reports from `app/build/reports` and `app/build/test-results`. Preserve this test contract when changing build configuration.

## Testing guidance

- Add or update JVM unit tests in the package matching the production code under test.
- Keep exchange/network tests deterministic: use the existing `MockWebServer` pattern and mocked clients rather than live endpoints.
- Run the narrowest relevant test task first, then `./gradlew test` before handoff.
- Changes to wallet state, restore height, address parsing, transaction amounts, node parsing, URI/QR handling, or cryptographic/native boundaries require regression tests for valid, invalid, boundary, and failure cases.
- For UI, permissions, background services, Bluetooth/Ledger, Tor/Orbot, and actual wallet synchronization, unit tests are not sufficient. Test the affected flavor on an emulator or physical device and record the Android version, ABI, network (mainnet/stagenet/devnet), and whether a real or disposable wallet was used.
- Never use a real seed, private key, funded wallet, or production credential in tests, screenshots, logs, fixtures, or commits.

## Code and resource conventions

- Preserve the existing Java style and Apache 2.0 copyright/license headers when adding files.
- Keep package placement aligned with the existing `com.m2049r.xmrwallet` structure; avoid creating a parallel architecture unless the change clearly requires it.
- Keep wallet and Monero work off the Android main thread. Existing code uses asynchronous services/executors; do not introduce blocking RPC, synchronization, exchange, or Ledger calls on UI callbacks.
- Preserve exact monetary units and precision. Monero amounts are represented in atomic units in model/native boundaries; use the existing helpers and avoid `float`/`double` for balances, fees, or transaction amounts.
- Treat network-specific behavior explicitly. Verify changes in the intended mainnet, stagenet, and devnet variants; do not assume a mainnet default is safe for test networks.
- Add user-visible text to `app/src/main/res/values/strings.xml` and keep translation/resource conventions intact. Do not hand-edit crowd-sourced translations unless the change specifically targets that locale.
- Use existing resource IDs, styles, logging conventions, and helpers where possible. Avoid broad formatting or dependency churn in feature patches.
- Keep native JNI method names/signatures synchronized with the Java declarations and update the native-library compatibility assumptions when the Monero API changes.

## Security and privacy rules

- Do not commit `monerujo.id`, API keys, daemon credentials, wallet files, seeds, view/spend keys, test vectors containing secrets, or generated native binaries.
- Do not weaken TLS, certificate, proxy/Tor, password, seed, hardware-wallet, or permission checks to make a test pass.
- Treat QR-code and payment-URI parsing as untrusted input. Validate before displaying or signing, and preserve clear confirmation of destination and amount.
- Do not add analytics, telemetry, remote logging, or new network calls without an explicit privacy/security review.
- Do not change default nodes, exchange providers, fees, or network selection without documenting the operational and privacy impact and adding tests.
- If a change may expose or lose funds, stop and request maintainer review rather than guessing.

## Change workflow

1. Inspect the existing implementation, source-set behavior, tests, and relevant history before editing.
2. Keep the patch focused. Do not commit generated build output or native artifacts.
3. Add regression coverage for behavior changes, especially security-sensitive and monetary logic.
4. Run the narrowest relevant checks, then the full `./gradlew test`; run a flavor build/lint check when build or resources changed.
5. Review `git diff` and `git status --short` for credentials, wallet material, generated files, accidental dependency changes, or unrelated formatting.
6. In the handoff, state what was tested and call out any checks blocked by missing credentials, Android SDK/NDK components, native libraries, device hardware, or unavailable network access.

