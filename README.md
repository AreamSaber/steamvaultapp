# Steam Vault App

Steam Vault App is a native Android Steam Guard manager focused on secure local storage, Steam mobile authenticator workflows, confirmations, and encrypted backup.

This project is not affiliated with Valve. It is an independent client-side tool built for personal account management and research-oriented implementation of Steam Guard related flows.

## Current Capabilities

- Encrypted local vault protected by master password
- Argon2id-based key derivation with wrapped vault key storage
- Import existing Steam tokens from maFile, JSON, otpauth URI, or manual `shared_secret`
- Generate Steam Guard codes offline
- Store and repair Steam mobile session data locally
- Protocol login flow with Steam Guard challenge handling
- Add and finalize Steam mobile authenticator binding
- Fetch, approve, and reject Steam confirmations
- Approve or reject Steam QR login requests with account selection
- Export encrypted local backups
- Upload and restore encrypted WebDAV cloud backups

## Security Model

- Token material is stored in an encrypted vault rather than plaintext project files
- Sensitive data such as `shared_secret`, `identity_secret`, access tokens, refresh tokens, and backup exports must never be committed to source control
- Local and cloud backup payloads are encrypted; the repository only contains application code and documentation

## Project Status

The repository is beyond the initial prototype stage. The current codebase already contains:

- Native Android Compose application structure
- Local vault, unlock, and security settings flows
- Steam token import and token detail flows
- Steam session persistence and repair flows
- Confirmation and QR approval flows
- Local backup and WebDAV cloud backup flows

Some areas are still being hardened, especially lifecycle edge cases such as background cloud backup after lock/exit and broader recovery coverage around Steam session state.

## Tech Stack

- Kotlin
- Jetpack Compose
- Android Keystore
- WorkManager
- OkHttp
- WebDAV

## Project Structure

- `app/`: Android application source
- `docs/`: product, architecture, and implementation notes
- `gradle/`: Gradle wrapper and version catalog

Local research/reference copies of upstream projects such as SDA and SteamKit are intentionally excluded from Git tracking in this workspace.

## Key Documents

- [Product Spec](docs/PRODUCT_SPEC.md)
- [Android Development Tasks](docs/ANDROID_DEVELOPMENT_TASKS.md)
- [Android Execution Checklist](docs/ANDROID_EXECUTION_CHECKLIST_P2_P4.md)
- [SDA Android Phase Progress](docs/SDA_Android_阶段进度与后续计划.md)

## Build

Open the project in Android Studio and ensure `local.properties` points to a valid Android SDK.

Typical commands:

```powershell
./gradlew.bat :app:compileDebugKotlin
./gradlew.bat :app:assembleDebug
```

## Safety Notes

- Do not commit `local.properties`
- Do not commit exported backup files such as `steam-vault-backup-*.json`
- Do not commit real maFiles or any payloads containing Steam secrets
- If any Steam secret has been exposed, treat it as compromised and rotate it from the Steam side
