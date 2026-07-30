# Personal Health Vault Repository Instructions

## Current product

- The only production application is the native Android project in `src/android`.
- Do not introduce a `clients` directory. Future native applications belong in peer roots such as `src/ios`, `src/windows`, and `src/macos`.
- The Android application ID is `net.mamby.health` for production.
- This is an offline, self-custody application. Do not add a backend, network client, accounts, telemetry, ads, tracking, or artificial-intelligence features.
- Do not make medical, diagnostic, emergency, regulatory, or clinical-certification claims.

## Android stack

- Kotlin, Jetpack Compose, Material 3, AndroidX, coroutines, and Flow.
- Hilt for dependency injection and Kotlin serialization for versioned vault data.
- Navigation 3 with adaptive navigation and list/detail layouts.
- DataStore for non-sensitive preferences and WorkManager for deferrable work.
- Android Keystore and authenticated encryption for persisted health information.
- Storage Access Framework for user-selected document and backup locations.
- Use official, non-deprecated Android APIs. Do not use hidden APIs, reflection, device-specific layout fixes, or legacy view layouts.

## Architecture

- Keep production code in the single `:app` module.
- `core/model` contains pure health models and scheduling rules.
- `crypto` contains authenticated-encryption and key contracts.
- `data` contains encrypted vault and document persistence.
- `backup` contains portable encrypted backup and restore behavior.
- `notifications` contains reminder scheduling and notification delivery.
- `security` contains the app-lock boundary and device authentication.
- `feature/*` contains state-driven Compose screens; `ui` contains shared adaptive components and theme tokens.
- UI code depends on repositories and narrow service contracts, never directly on files or cryptographic primitives.

## Privacy and security

- Never persist sensitive health information in plaintext.
- Store vault metadata and imported document bodies only as authenticated ciphertext under app-private, non-backed-up storage.
- Keep the local vault key non-exportable in Android Keystore.
- Treat app lock as an access gate separate from vault encryption.
- A missing vault, sample workspace, locked vault, and unreadable vault are distinct states. Never replace unreadable data with sample data.
- Backup providers may receive only the portable encrypted backup container. Never retain the user's backup passphrase.
- Do not add `INTERNET`, broad storage, media-library, or exact-alarm permissions.
- Keep raw UI colors in the central Material theme only.

## Product variants

- The `env` dimension contains `dev`, `beta`, `stage`, and `prod`.
- Only `devDebug` is enabled for debug builds. Release builds exist for all four channels.
- Environment flavors change installation identity and visible channel only; they share the same domain schema and portable backup format.
- Keep signing material and machine-specific configuration outside version control.

## Localization and accessibility

- Every user-facing string must exist in English, French, and Arabic with identical resource IDs and format placeholders.
- Preserve right-to-left behavior and use locale-aware dates, times, numbers, and lists.
- Support font scaling, screen readers, keyboard input, orientation changes, tablets, and foldables.
- Do not hardcode screen sizes, system-bar dimensions, or device-specific padding.

## Testing

- Put JVM tests under `src/android/app/src/test` and device tests under `src/android/app/src/androidTest`.
- Test domain behavior, encrypted persistence, backup recovery, reminder scheduling, app locking, permissions, navigation, localization, RTL, and adaptive layouts.
- Use JUnit, AndroidX Test, Compose testing APIs, and WorkManager test utilities.
- Do not add trivial tests or brittle timing-based tests.
- Before handoff, run `:app:assembleDevDebug`, `:app:testDevDebugUnitTest`, `:app:lintDevDebug`, and the applicable device tests.
