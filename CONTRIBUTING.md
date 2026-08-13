# Contributing to Personal Health Vault

## Set up

1. Install Android Studio, JDK 21, and Android SDK 37.
2. Clone the repository.
3. Open `src/android` in Android Studio, or run the committed Gradle wrapper from that directory.

## Development workflow

- Keep production code in the single `src/android/app` module.
- Create state-driven Compose UI and follow official Android architecture guidance.
- Use the existing domain, repository, encryption, backup, security, and notification boundaries.
- Add every user-facing string to English, French, and Arabic resources with matching positional placeholders.
- Keep user-facing language non-technical: use records, health data, backup, and people, never “vault.” Internal storage types may retain the existing name.
- Use the central Material theme; do not place raw colors in feature code.
- Keep machine paths, signing material, credentials, personal documents, and real health information out of version control.

Before opening a change, run from `src/android`:

```powershell
.\gradlew.bat :app:assembleDevDebug
.\gradlew.bat :app:testDevDebugUnitTest
.\gradlew.bat :app:lintDevDebug
```

Run `:app:connectedDevDebugAndroidTest` when the change affects Android Keystore, document providers, notifications, lifecycle behavior, or Compose interaction.

## Product boundaries

- Keep the application fully useful offline.
- Do not add a backend, account system, cloud processing, artificial intelligence, ads, telemetry, or tracking.
- Do not add broad storage, media-library, network, or exact-alarm permissions.
- Do not persist health data, imported documents, keys, or backup passphrases in plaintext.
- Do not weaken authenticated encryption, atomic replacement, corruption handling, or the app-lock gate.
- Do not make medical, diagnostic, emergency, prescription, regulatory, or clinical claims.
- Treat schema v1 as the deliberate current baseline. Do not add decoders, migrations, or compatibility claims for pre-reset development schemas.

## Tests

Write tests that protect meaningful behavior:

- Recurrence, time-zone, and date-boundary calculations
- Immutable vault mutations and search/filter behavior
- Encryption round trips, associated data, tamper detection, and interrupted writes
- Backup passphrase handling, full validation before replacement, and failure recovery
- App-lock lifecycle and device-authentication outcomes
- Permission-denied and provider-unavailable states
- Compact, expanded, right-to-left, accessibility, and navigation behavior

Avoid snapshot-only coverage, arbitrary delays, and assertions that merely restate implementation details.
