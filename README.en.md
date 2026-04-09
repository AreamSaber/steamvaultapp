# Steam Vault App

[简体中文](README.md)

[![Android CI](https://github.com/AreamSaber/steamvaultapp/actions/workflows/android-ci.yml/badge.svg)](https://github.com/AreamSaber/steamvaultapp/actions/workflows/android-ci.yml)
[![License](https://img.shields.io/github/license/AreamSaber/steamvaultapp)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-Android-3DDC84)](https://developer.android.com/)

Steam Vault App is a native Android Steam Guard manager focused on secure local storage, mobile authenticator workflows, confirmations, QR approval, and encrypted backup.

This project is not affiliated with Valve. It is an independent client-side tool built for personal account management and research-oriented implementation of Steam Guard related flows.

## Highlights

- Encrypted local vault protected by a master password
- Argon2id-based key derivation with wrapped vault key storage
- Import existing Steam tokens from maFile, JSON, otpauth URI, or manual `shared_secret`
- Generate Steam Guard codes offline
- Store, repair, and refresh Steam mobile session data locally
- Protocol login flow with Steam Guard challenge handling
- Add and finalize Steam mobile authenticator binding
- Fetch, approve, and reject Steam confirmations
- Approve or reject Steam QR login requests with account selection
- Export encrypted local backups
- Upload and restore encrypted WebDAV cloud backups

## Project Status

The repository is already past the initial prototype stage. The current codebase includes:

- Native Android Compose application structure
- Local vault, unlock, and security settings flows
- Steam token import and token detail flows
- Steam session persistence and repair flows
- Confirmation and QR approval flows
- Local backup and WebDAV cloud backup flows

Some lifecycle edges are still being hardened, especially backup behavior after lock/exit and a few Steam session recovery paths.

## Security Model

- Token material is stored in an encrypted vault rather than plaintext project files
- Sensitive data such as `shared_secret`, `identity_secret`, access tokens, refresh tokens, maFiles, and backup exports must never be committed to source control
- Local and cloud backup payloads are encrypted; the repository only contains application code and documentation

## Tech Stack

- Kotlin
- Jetpack Compose
- Android Keystore
- WorkManager
- OkHttp
- WebDAV

## Repository Layout

- `app/`: Android application source
- `docs/`: product, architecture, and implementation notes
- `gradle/`: Gradle wrapper and version catalog

Local research/reference copies of upstream projects such as SDA and SteamKit are intentionally excluded from Git tracking in this workspace.

## Build

Open the project in Android Studio and ensure `local.properties` points to a valid Android SDK.

Common commands:

```powershell
./gradlew.bat :app:compileDebugKotlin
./gradlew.bat :app:testDebugUnitTest
./gradlew.bat :app:assembleDebug
```

## CI

GitHub Actions runs a safe baseline CI workflow on pushes and pull requests:

- Debug Kotlin compile
- Android test source compile
- JVM unit tests

Instrumentation tests and emulator-heavy flows remain better suited for local or dedicated device validation.

## Key Documents

- [Product Spec](docs/PRODUCT_SPEC.md)
- [Android Development Tasks](docs/ANDROID_DEVELOPMENT_TASKS.md)
- [Android Execution Checklist](docs/ANDROID_EXECUTION_CHECKLIST_P2_P4.md)
- [SDA Flow Analysis](docs/SDA_FLOW_ANALYSIS.md)
- [SDA Complete Analysis](docs/SDA_Complete_Analysis.md)

Additional Chinese planning and implementation notes are also available under [`docs/`](docs/).

## Acknowledgements

This project has benefited from the architecture ideas, workflow references, and protocol research from the following upstream projects:

- [SteamDesktopAuthenticator (SDA)](https://github.com/Jessecar96/SteamDesktopAuthenticator)
- [SteamKit](https://github.com/SteamRE/SteamKit)

## Safety Notes

- Do not commit `local.properties`
- Do not commit exported backup files such as `steam-vault-backup-*.json`
- Do not commit real maFiles or any payloads containing Steam secrets
- If any Steam secret has been exposed, treat it as compromised and rotate it from the Steam side
