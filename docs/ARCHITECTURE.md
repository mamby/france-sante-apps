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
- `Missing`: no local vault exists; onboarding may create an empty first profile or restore a backup
- `Ready(vault)`: a schema-v6 multi-profile vault was authenticated; profile context is chosen only by local list filters, create forms, and owner-specific routes
- `Unreadable`: ciphertext exists but cannot be safely decoded or authenticated

User actions flow from Compose to a state holder, through an explicit profile-owned or vault-wide repository mutation, into atomic encrypted persistence, and back through Flow. A failed mutation leaves the last valid state active. There is no production sample provider: starting new creates one genuinely empty profile.

Schema v6 stores ordered `ProfileRecord` values plus vault-wide notes, schedules, and contacts. Profiles own their documents, medications, vaccinations, typed measurements, custom measurement types, family history, personal directives, health identifiers, document categories, and category preferences. Contacts contain a name, repeatable phone numbers, email addresses, websites and free-form addresses, plus notes; they have no profile owner or clinical type. Schedules are generic vault-level calendar entries; concerned people are copied names rather than profile identifiers. Exact frozen schema-v1 through schema-v5 wire shapes are decoded and deterministically migrated. Legacy appointments and reminders become schedules while preserving identity, timing, recurrence, owner display name, alert state and offset, and update time; their former clinician, location, notes, directory, and document relationships are intentionally discarded. Schema-v3 through schema-v5 care-directory entries are flattened into vault-wide contacts in stable profile/list order while preserving their contact values; structured addresses become multiline text, and former types, specialties, organizations, primary-doctor selections, and record links are intentionally discarded. Only schema v6 is encoded. IDs are globally unique, and profile-owned relationships never cross profile ownership.

Search, the recent-item index, and dashboard metrics are derived rather than persisted. They aggregate every profile while preserving each profile-owned item’s owner and owner-local relationships, and include vault-wide notes, schedules, and contacts without an owner marker. List and search filters are independent unlocked-memory UI state and default to all profiles; vault-wide results remain visible when profile-owned groups are narrowed. Schedule and contact fields are searched only in unlocked memory. Search is Unicode-, case-, and diacritic-insensitive. Health identifier values are deliberately excluded from searchable content.

## Local encrypted storage

Vault metadata is serialized with an explicit schema version, encrypted with AES-256-GCM, and stored under `noBackupFilesDir`. Each imported document body is encrypted separately so document updates do not rewrite every blob. Obsolete encrypted profile-selection preference files are removed after a successful vault load; no replacement profile filter is persisted.

Android Keystore owns the non-exportable local AES key. Every encryption operation uses a fresh random nonce. Associated data binds ciphertext to its schema version, purpose, and record or blob identifier.

Writes use atomic replacement. Import commits an authenticated blob before publishing metadata that references it. Interrupted temporary files and unreferenced blobs are cleaned without changing a valid snapshot. An authentication or decoding error in vault data becomes `Unreadable`; it is never treated as missing.

PDF and image contents are authenticated before preview. Preview data remains in bounded process memory and is never written to a plaintext cache file.

## Document import

The system document picker supplies PDF, JPEG, PNG, or WebP content. Import verifies the declared type, file signature, readable length, and the 25 MiB application limit before committing it. The source is copied into encrypted app storage immediately; the application does not retain a dependency on the source document.

No broad storage or media permission is required.

## Navigation and adaptive UI

Navigation 3 owns type-safe destinations and independent saved top-level back stacks. Home, Search, Health records, Notes, Medications, Schedule, Contacts, Settings, and Profiles are the top-level destinations. Schedule and Contacts are adaptive list/detail experiences with no profile filter or owner marker. Health records is an adaptive hub for Health information, Measurements, and Documents. Each collection uses typed list/detail destinations; category and measurement-type management have dedicated routes. Profile management, Notes, Schedule, and Contacts remain vault-wide.

Compact windows expose Home, Search, Health records, and Notes in a fixed short navigation bar, with Medications, Schedule, Contacts, Settings, and Profiles in More. The compact bar overlays full-height screen content and uses an 80%-opaque `surfaceContainer`, while icons and the Material selected-item indicator remain opaque. Scrollable content can move behind the bar; its final-item clearance, floating actions, and root Snackbar use the measured bar inset combined with safe-drawing insets. Expanded navigation rails expose all nine destinations directly and retain the conventional side-by-side layout that reserves horizontal space for the rail. Layout decisions come from official window-size and adaptive APIs rather than device names or hardcoded screen dimensions.

## App lock

When enabled, app lock gates the root content until `BiometricPrompt` succeeds with a strong biometric or device credential. Enabling the feature requires authentication. The default background timeout is immediate; the user may choose another supported timeout. Sensitive content is hidden from recent-app previews.

App lock does not wrap or replace the local data key. Local encryption remains active whether app lock is enabled or disabled.

## Portable backup and recovery

The user chooses a destination with the Storage Access Framework. The portable container has a versioned public header and authenticated encrypted contents. A random backup data key encrypts the manifest and document entries. PBKDF2-HMAC-SHA256 derives a wrapping key from a minimum 12-character passphrase, random salt, and 600,000 iterations.

Scheduled backup stores only a Keystore-wrapped copy of the backup data key and the persisted destination permission. The passphrase is never retained. WorkManager coalesces changes and records success or a user-action-required state.

Every backup contains every profile, vault-wide note, schedule, and contact in one encrypted snapshot, with documents flattened in profile/list order. The portable container remains format v1 while its manifest identifies the embedded vault schema. Restore accepts schema-v1 through schema-v6 snapshots, migrates historical schemas through the same strict codec, validates the complete manifest and every entry, and atomically replaces the active snapshot. Restore is full-snapshot replacement, not a record merge or synchronization protocol.

## Schedule alerts

Medication notification settings remain profile-owned under each medication. Generic schedules may carry one timed or all-day alert. The pure scheduling rules calculate the next occurrence, and each schedule alert is handed to WorkManager as the next one-time delivery. Reconciliation after delivery computes the following occurrence. Medication notification targets retain a profile ID; schedule targets contain only a schedule ID.

Notification permission is requested in context when the user enables delivery. Notification titles and bodies are always generic and localized; profile names and health details appear only after unlock. A denied permission does not delete the reminder. Exact-alarm access is not requested, so delivery may be delayed by Android power and scheduling policies.

## Build channels

The `env` flavor dimension provides `dev`, `beta`, `stage`, and `prod`. Only `devDebug` is enabled; all channels have release builds. Channel identity is visible to the user, while the storage schema and portable backup format remain shared.
