# Privacy Model

Personal Health Vault is an offline personal record organizer. It does not require an account and does not send health information to an application server.

## Data kept by the application

The vault may contain multiple profiles, each with profile details, allergies, conditions, surgeries, emergency contacts, vaccinations, medication schedules, appointments, reminders, health measurements, care-directory entries, family history, personal directives, health identifiers, category preferences, document metadata, and imported PDF or image contents. Notes are vault-wide and are not assigned to profiles. Invoices, receipts, and reimbursement records are encrypted documents rather than a financial ledger.

User health information is persisted only as authenticated ciphertext in app-private storage that is excluded from Android platform backup. Profile filters and search state exist only in unlocked UI memory and are not persisted. Theme, language, app-lock preference, timeout, and backup-destination state are non-health preferences stored separately.

Fresh install contains no sample health data. Starting new asks who the first profile is for and creates an empty encrypted profile.

Global search examines all profiles and vault-wide notes by default and may narrow profile-owned groups with its screen-local profile filter. Its query, filters, and results remain in unlocked process memory; the app does not create a plaintext search index or persist search terms. Profile-owned results retain their owning profile, while note results are explicitly vault-scoped and never display a profile marker. Identifier values are never indexed or displayed in result lists. They remain masked until the user explicitly reveals a detail value, and that reveal state is temporary.

## Network and third parties

- The application does not declare network access.
- There is no backend, account, advertising, telemetry, or tracking service.
- There is no artificial-intelligence processing.
- No cloud service receives plaintext health information from the application.

The user may choose a system document provider as an encrypted backup destination. That provider receives the encrypted container and may observe external metadata such as its filename, size, and modification time. It does not receive the backup passphrase or decrypted record contents from the application.

## Documents

Import uses Android's system picker. Supported source content is copied into encrypted app-private storage and no broad storage or media permission is requested. The application decrypts a document into bounded memory only while an authenticated user previews it; it does not create a plaintext preview file.

## App lock and notifications

App lock uses the device's biometric or credential prompt when the user enables it. It controls access to the user interface and is separate from encryption at rest.

Reminder calculation and notification creation happen on the device for every profile. Notification text is always generic and contains no profile name, medication, dose, appointment, location, or other health detail. Tapping a reminder unlocks the app before opening the target under its owning profile.

## Backup and restore

Portable backups contain every profile and are encrypted before they are written to the chosen provider. The passphrase is not stored. Scheduled backup retains only a locally wrapped backup key.

Restore validates the entire selected backup before replacing local data. It does not combine records with the current vault. A failed restore leaves current data unchanged.

Deleting the local vault removes local ciphertext, local keys, notification work, and saved provider permissions. It does not delete backup files already stored outside the application; the user must remove those through the selected provider.

## Product limits

Personal Health Vault is not a medical device, diagnostic service, emergency service, prescription source, or official health-record system. It does not provide medical advice. Users are responsible for verifying their records and maintaining a recoverable encrypted backup.
