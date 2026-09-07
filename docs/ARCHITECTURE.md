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
- `Missing`: no persisted root exists yet; repository initialization immediately creates an encrypted empty root rather than presenting onboarding
- `Ready(vault)`: a schema-v1 root was authenticated; zero profiles is valid, and profile context is chosen only by local list filters, profile-owned create flows, and owner-specific routes
- `Unreadable`: ciphertext exists but cannot be safely decoded or authenticated

User actions flow from Compose to a state holder, through an explicit profile-owned or root-owned repository mutation, into atomic encrypted persistence, and back through Flow. A failed mutation leaves the last valid state active. There is no production sample provider. A fresh install persists a genuinely empty root and opens Home directly; it does not create a placeholder profile.

Schema v1 stores an optional ordered list of `ProfileRecord` values plus root-owned notes, schedules, and contacts. Profiles own their documents, medications, vaccinations, typed measurements, custom measurement types, family history, personal directives, health identifiers, document categories, and category preferences. Contacts contain a name, repeatable phone numbers, email addresses, websites and free-form addresses, plus notes; they have no profile owner or clinical type. Schedules are generic root-level calendar entries; concerned people are copied names rather than profile identifiers. IDs are globally unique, and profile-owned relationships never cross profile ownership.

This schema v1 is a deliberate breaking reset of the development data model. The codec reads and writes only the current schema. It does not decode or migrate snapshots produced by former development schemas, including their earlier numeric version labels.

Creating a root-owned note, schedule, or contact never creates or requests a profile. When a profile-owned action starts without an applicable profile, navigation first asks who the item is for. The user selects an existing person or adds one, the profile is persisted, and the originally requested editor resumes with that owner. Deleting the final profile is valid as long as profile-owned data is handled by the explicit delete operation.

Search, the recent-item index, and dashboard metrics are derived rather than persisted. They aggregate every profile while preserving each profile-owned item’s owner and owner-local relationships, and include root-owned notes, schedules, and contacts without an owner marker. List and search filters are independent unlocked-memory UI state and default to all profiles; root-owned results remain visible when profile-owned groups are narrowed. Schedule and contact fields are searched only in unlocked memory. Search is Unicode-, case-, and diacritic-insensitive. Health identifier values are deliberately excluded from searchable content.

## Local encrypted storage

Vault metadata is serialized with an explicit schema version, encrypted with AES-256-GCM, and stored under `noBackupFilesDir`. Each imported document body is encrypted separately so document updates do not rewrite every blob. Obsolete encrypted profile-selection preference files are removed after a successful vault load; no replacement profile filter is persisted.

Android Keystore owns the non-exportable local AES key. Every encryption operation uses a fresh random nonce. Associated data binds ciphertext to its schema version, purpose, and record or blob identifier.

Writes use atomic replacement. Import commits an authenticated blob before publishing metadata that references it. Interrupted temporary files and unreferenced blobs are cleaned without changing a valid snapshot. An authentication or decoding error in vault data becomes `Unreadable`; it is never treated as missing.

PDF and image contents are authenticated before preview. Preview data remains in bounded process memory and is never written to a plaintext cache file.

## Document import

The system document picker supplies PDF, JPEG, PNG, or WebP content. Import verifies the declared type, file signature, readable length, and the 25 MiB application limit before committing it. The source is copied into encrypted app storage immediately; the application does not retain a dependency on the source document.

No broad storage or media permission is required.

## Navigation and adaptive UI

Navigation 3 owns type-safe destinations and independent saved top-level back stacks. Home is the first destination on a fresh install. Its empty state keeps the normal app shell and places Restore backup prominently at the top instead of diverting the user through a welcome screen. Home, Search, Health records, Notes, Medications, Schedule, Contacts, Settings, and People are the user-facing top-level destinations. Schedule and Contacts are adaptive list/detail experiences with no profile filter or owner marker. Health records is an adaptive hub for Health information, Measurements, and Documents. Each collection uses typed list/detail destinations; category and measurement-type management have dedicated routes. People management, Notes, Schedule, and Contacts remain root-owned.

Home actions are arranged in a `LazyVerticalGrid` with adaptive cells, so as many tiles share a row as the available width permits. Tile colors come from centralized Material theme tokens and use a restrained sticky-note visual treatment without encoding meaning by color alone.

Compact windows expose Home, Search, Health records, and Notes in a fixed short navigation bar, with Medications, Schedule, Contacts, Settings, and People in More. The compact bar overlays full-height screen content and uses an 80%-opaque `surfaceContainer`, while icons and the Material selected-item indicator remain opaque. Scrollable content can move behind the bar; its final-item clearance, floating actions, and root Snackbar use the measured bar inset combined with safe-drawing insets. Expanded navigation rails expose all nine destinations directly and retain the conventional side-by-side layout that reserves horizontal space for the rail. Layout decisions come from official window-size and adaptive APIs rather than device names or hardcoded screen dimensions.

## App lock

When enabled, app lock gates the root content until `BiometricPrompt` succeeds with a strong biometric or device credential. Enabling the feature requires authentication. The default background timeout is immediate; the user may choose another supported timeout. Sensitive content is hidden from recent-app previews.

The manager observes the main activity lifecycle directly so quick app switches
honor the selected timeout without process-lifecycle dispatch delay. Configuration
changes and transitions during device authentication do not interrupt the prompt.
Enabling or cancelling app-lock setup preserves Settings. The locked UI uses
`AndroidKitLockPage`; locking and unlocking preserve the selected destination
and navigation stacks instead of redirecting to Home.

App lock does not wrap or replace the local data key. Local encryption remains active whether app lock is enabled or disabled.

## Portable backup and recovery

The user chooses a destination with the Storage Access Framework. The portable container has a versioned public header and authenticated encrypted contents. A random backup data key encrypts the manifest and document entries. PBKDF2-HMAC-SHA256 derives a wrapping key from a minimum 12-character passphrase, random salt, and 600,000 iterations.

Scheduled backup stores only a Keystore-wrapped copy of the backup data key and the persisted destination permission. The passphrase is never retained. WorkManager coalesces changes and records success or a user-action-required state.

Every backup contains the complete root: all profiles plus every root-owned note, schedule, and contact, with documents flattened in profile/list order. An empty zero-profile root is also a valid backup. The portable container remains format v1 while its manifest identifies domain schema v1. Restore accepts only the current reset schema-v1 contract, validates the complete manifest and every entry, and atomically replaces the active snapshot. Pre-reset development backups are intentionally unsupported. Restore is full-snapshot replacement, not a record merge or synchronization protocol.

## Schedule alerts

Medication notification settings remain profile-owned under each medication. Generic schedules may carry one timed or all-day alert. The pure scheduling rules calculate the next occurrence, and each schedule alert is handed to WorkManager as the next one-time delivery. Reconciliation after delivery computes the following occurrence. Medication notification targets retain a profile ID; schedule targets contain only a schedule ID.

Notification permission is requested in context when the user enables delivery. Notification titles and bodies are always generic and localized; profile names and health details appear only after unlock. A denied permission does not delete the reminder. Exact-alarm access is not requested, so delivery may be delayed by Android power and scheduling policies.

## Build channels

The `env` flavor dimension provides `dev`, `beta`, `stage`, and `prod`. Only `devDebug` is enabled; all channels have release builds. Channel identity is visible to the user, while the storage schema and portable backup format remain shared.
