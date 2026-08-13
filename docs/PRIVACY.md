# Privacy Model

Personal Health Vault is an offline personal record organizer. It does not require an account and does not send health information to an application server. Outside the product name, its interface uses plain terms such as records, health data, backup, and people.

## Data kept by the application

The app may contain zero or more people. Each person can have profile details, allergies, conditions, surgeries, emergency contacts, vaccinations, medications, health measurements, family history, personal directives, health identifiers, category preferences, document metadata, and imported PDF or image contents. Notes, generic schedules, and contacts belong to the shared root and are not assigned to a person. Contacts may contain names, phone numbers, email addresses, websites, free-form addresses, and notes. A schedule’s optional concerned people are copied names, not links to profiles. Invoices, receipts, and reimbursement records are encrypted documents rather than a financial ledger.

User health information is persisted only as authenticated ciphertext in app-private storage that is excluded from Android platform backup. Profile filters and search state exist only in unlocked UI memory and are not persisted. Theme, language, app-lock preference, timeout, and backup-destination state are non-health preferences stored separately.

Fresh install contains no sample health data. The app immediately persists a valid encrypted empty root and opens Home. It does not require or invent a person. If the user later creates person-owned health information, the app asks who it is for and lets them select or add that person before continuing. Creating a note, schedule, or contact never triggers that step.

Global search examines all profiles, shared notes, shared schedules, and shared contacts by default and may narrow person-owned groups with its screen-local profile filter. Its query, filters, and results remain in unlocked process memory; the app does not create a plaintext search index or persist search terms. Person-owned results retain their owning profile, while note, schedule, and contact results are explicitly shared and never display a profile marker. Identifier values are never indexed or displayed in result lists. They remain masked until the user explicitly reveals a detail value, and that reveal state is temporary.

## Network and third parties

- The application does not declare network access.
- There is no backend, account, advertising, telemetry, or tracking service.
- There is no artificial-intelligence processing.
- No cloud service receives plaintext health information from the application.

Tapping a saved phone number, email address, website, or street address sends only that selected value to an external dialer, email, browser, or maps application through an Android system intent. Personal Health Vault does not request call, location, contacts, or Internet permission for these actions; the selected external application applies its own privacy policy.

The user may choose a system document provider as an encrypted backup destination. That provider receives the encrypted container and may observe external metadata such as its filename, size, and modification time. It does not receive the backup passphrase or decrypted record contents from the application.

## Documents

Import uses Android's system picker. Supported source content is copied into encrypted app-private storage and no broad storage or media permission is requested. The application decrypts a document into bounded memory only while an authenticated user previews it; it does not create a plaintext preview file.

## App lock and notifications

App lock uses the device's biometric or credential prompt when the user enables it. It controls access to the user interface and is separate from encryption at rest.

Medication and schedule alert calculation happens entirely on the device. Notification text is always generic and contains no profile name, medication, dose, schedule title, person, location, or other health detail. Tapping a notification unlocks the app before opening its type-safe medication or schedule target.

## Backup and restore

Portable backups contain the complete root, including every profile and every shared note, schedule, and contact, and are encrypted before they are written to the chosen provider. A zero-profile root is valid. The passphrase is not stored. Scheduled backup retains only a locally wrapped backup key.

Restore validates the entire selected backup before replacing local data. It does not combine records with the current data set. A failed restore leaves current data unchanged.

Deleting local health data removes local ciphertext, local keys, notification work, and saved provider permissions, then returns the app to a new valid empty Home state. It does not delete backup files already stored outside the application; the user must remove those through the selected provider.

## Product limits

Personal Health Vault is not a medical device, diagnostic service, emergency service, prescription source, or official health-record system. It does not provide medical advice. Users are responsible for verifying their records and maintaining a recoverable encrypted backup.
