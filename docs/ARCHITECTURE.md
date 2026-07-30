# Android Architecture

## Repository structure

`src/android` is the only current application root. It contains one Gradle `:app` module. Future platform applications will be independent peers under `src`, allowing each platform to use its official native UI, security, storage, and lifecycle APIs.

Within the Android module:

- `core/model`: immutable health records, identifiers, versioned data shapes, and recurrence rules
- `crypto`: local and portable authenticated-encryption contracts
- `data`: encrypted snapshot and document-blob persistence plus repositories
- `backup`: Storage Access Framework configuration, encrypted export, and staged restore
- `notifications`: WorkManager scheduling, notification channels, and rescheduling events
- `security`: app-lock state and device-authentication integration
- `feature/*`: screen state, events, and Compose UI
- `ui`: adaptive navigation, reusable components, localization helpers, and Material theme tokens

Hilt supplies implementations at the application boundary. Feature state holders depend on repository and service interfaces rather than Android files or cryptographic primitives.

## State and data flow

Repositories expose immutable state through Flow. The root vault state is one of:

- `Loading`: encrypted state is being inspected
- `Demo`: localized sample data exists only in memory
- `Ready`: a user vault was authenticated and loaded
- `Unreadable`: ciphertext exists but cannot be safely decoded or authenticated

User actions flow from Compose to a state holder, through a repository mutation, into atomic encrypted persistence, and back through Flow. A failed mutation leaves the last valid state active. Starting a real vault creates a new user snapshot; sample records are never copied into it.

Health-profile fields are the source of truth for the displayed summary. Search indexes and dashboard metrics are derived rather than persisted as duplicate writable records.

## Local encrypted storage

Vault metadata is serialized with an explicit schema version, encrypted with AES-256-GCM, and stored under `noBackupFilesDir`. Each imported document body is encrypted separately so document updates do not rewrite every blob.

Android Keystore owns the non-exportable local AES key. Every encryption operation uses a fresh random nonce. Associated data binds ciphertext to its schema version, purpose, and record or blob identifier.

Writes use atomic replacement. Import commits an authenticated blob before publishing metadata that references it. Interrupted temporary files and unreferenced blobs are cleaned without changing a valid snapshot. An authentication or decoding error becomes `Unreadable`; it never falls back to sample data.

PDF and image contents are authenticated before preview. Preview data remains in bounded process memory and is never written to a plaintext cache file.

## Document import

The system document picker supplies PDF, JPEG, PNG, or WebP content. Import verifies the declared type, file signature, readable length, and the 25 MiB application limit before committing it. The source is copied into encrypted app storage immediately; the application does not retain a dependency on the source document.

No broad storage or media permission is required.

## Navigation and adaptive UI

Navigation 3 owns type-safe destinations and saved top-level back stacks. Dashboard, Vault, Summary, Medications, and Appointments are the five top-level destinations. Settings is an app-bar destination.

Compact windows use bottom navigation and full-screen detail destinations. Expanded windows use a navigation rail and list/detail scenes where useful. Layout decisions come from official window-size and adaptive APIs rather than device names or hardcoded screen dimensions.

## App lock

When enabled, app lock gates the root content until `BiometricPrompt` succeeds with a strong biometric or device credential. Enabling the feature requires authentication. The default background timeout is immediate; the user may choose another supported timeout. Sensitive content is hidden from recent-app previews.

App lock does not wrap or replace the local data key. Local encryption remains active whether app lock is enabled or disabled.

## Portable backup and recovery

The user chooses a destination with the Storage Access Framework. The portable container has a versioned public header and authenticated encrypted contents. A random backup data key encrypts the manifest and document entries. PBKDF2-HMAC-SHA256 derives a wrapping key from a minimum 12-character passphrase, random salt, and 600,000 iterations.

Scheduled backup stores only a Keystore-wrapped copy of the backup data key and the persisted destination permission. The passphrase is never retained. WorkManager coalesces changes and records success or a user-action-required state.

Restore decrypts into isolated staging, validates the complete manifest and every entry, re-encrypts with the destination device's local key, and then atomically replaces the active snapshot. Restore is full-snapshot replacement, not a record merge or bidirectional synchronization protocol.

## Reminders

Medication, appointment, and general reminders are persisted in the encrypted snapshot. WorkManager schedules the next future occurrence and recalculates after delivery, edits, reboot, clock changes, and time-zone changes.

Notification permission is requested in context when the user enables delivery. A denied permission does not delete the reminder. Exact-alarm access is not requested, so delivery may be delayed by Android power and scheduling policies.

## Build channels

The `env` flavor dimension provides `dev`, `beta`, `stage`, and `prod`. Only `devDebug` is enabled; all channels have release builds. Channel identity is visible to the user, while the storage schema and portable backup format remain shared.
