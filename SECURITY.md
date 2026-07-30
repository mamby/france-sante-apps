# Security Policy

## Reporting a vulnerability

Report vulnerabilities privately to the maintainers. Do not open a public issue when a report involves health information, encryption, key handling, backup recovery, document import, notifications, or app-lock behavior.

Include the affected build channel and version, Android version, device model, reproduction steps, expected and observed behavior, and a concise impact assessment. Never include real health records, passphrases, keys, or unredacted backups.

## Security boundaries

Personal Health Vault protects saved vault metadata and imported document bodies with authenticated encryption. The local encryption key is created and used through Android Keystore and is not exported into application files. App-private vault files are excluded from platform backup.

The encrypted portable backup uses a separate random data key. A key derived from the user's passphrase wraps that data key. The passphrase is never stored, and a backup is not recoverable if the passphrase is lost.

App lock is an access gate for the running application. It uses the device's strong biometric or credential authentication when enabled. It is separate from file encryption and does not turn the application into a hardened environment after the user has unlocked it.

## Threat model

The design is intended to protect against:

- Inspection of copied app files or portable backup files
- Modification or truncation of authenticated ciphertext
- A backup provider reading health-record contents
- Accidental replacement of a valid vault by an incomplete or unauthenticated restore

The design does not claim to protect against:

- A compromised, rooted, or actively instrumented device
- Malicious software with control of an unlocked application process
- Someone viewing information while the application or a notification is visible
- Loss of both the device and every valid encrypted backup
- Loss of the backup passphrase

Android Keystore may use hardware-backed protection when the device provides it, but the application does not claim that every supported device has identical hardware security.

## Data-loss safeguards

- An unreadable vault is never treated as an empty vault or sample workspace.
- Local replacement occurs only after staged ciphertext has been completely validated.
- Restore authenticates the manifest and every document before replacing current data.
- Wrong passphrases, unsupported versions, missing entries, and failed provider writes leave the current vault unchanged.
- Deleting the local vault does not delete copies already written to a user-selected external provider.

See [Recovery](docs/RECOVERY.md) before testing destructive or restore flows.
