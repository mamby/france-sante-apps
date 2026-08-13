# Personal Health Vault Repository Instructions

## Current product

- The only production application is the native Android project in `src/android`.
- Do not introduce a `clients` directory. Future native applications belong in peer roots such as `src/ios`, `src/windows`, and `src/macos`.
- The Android application ID is `net.mamby.health` for production.
- This is an offline, self-custody application. Do not add a backend, network client, accounts, telemetry, ads, tracking, or artificial-intelligence features.
- Do not make medical, diagnostic, emergency, regulatory, or clinical-certification claims.
- The user-facing product name is **Personal Health Vault** in every locale and channel. Do not rename or translate the product name. Outside the product name, prefer plain UI terms such as health data, records, backup, and people. Internal types and repository documentation may retain `HealthVault` or “vault” where they describe the encrypted storage boundary.

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
- Schema v1 is the only supported domain schema. It deliberately replaces the former development schemas; do not add legacy decoding or migrations.
- A valid ready schema-v1 root may contain zero profiles. On first launch, initialize and persist that empty encrypted root and open Home directly; `Missing` is not an onboarding screen.
- Locked, ready, and unreadable vaults are distinct states. Production sample data is prohibited, and unreadable data is never treated as missing or replaced by a fresh root.
- Notes, schedules, and contacts belong to the root and never require a profile. A profile is created or selected only when the user creates profile-owned health information.
- Profile filters are transient, screen-local UI state, default to `All profiles`, and must not be persisted, change the vault revision, or trigger backup.
- Backups contain every profile. Filtered multi-profile search stays in unlocked memory and must never create plaintext indexes or persisted queries.
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
- Screenshot-test reference images and generated captures are local artifacts. Never commit files under `src/android/app/src/screenshotTest*/reference`.
- Before handoff, run `:app:assembleDevDebug`, `:app:testDevDebugUnitTest`, `:app:lintDevDebug`, and the applicable device tests.
