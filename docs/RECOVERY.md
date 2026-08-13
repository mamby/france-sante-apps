# Backup and Recovery

## Configure a backup

1. Open Settings and choose **Configure backup**.
2. Select a destination through Android's document-provider interface.
3. Create a passphrase of at least 12 characters and enter it twice.
4. Store the passphrase in a trusted password manager. The application does not keep it and cannot recover it.
5. Wait for the first backup to report success before relying on it.

The backup destination receives one portable encrypted container. Scheduled updates are coalesced after vault changes and may be delayed by Android background-work policy.

## Restore

1. Select the portable backup with the system picker.
2. Enter its passphrase.
3. Review any build-channel warning.
4. Confirm that restore will replace the entire current vault.
5. Keep the application open until validation completes.

Restore authenticates the manifest and every document before replacement. It accepts historical schema-v1 through schema-v6 snapshots. Historical appointments and reminders are deterministically migrated into vault-level schedules, and schema-v3 through schema-v5 care-directory entries become vault-wide contacts; the portable container version remains unchanged. It then encrypts the recovered schema-v6 data with a new local Keystore key. A wrong passphrase, unsupported schema, missing document, modified container, provider failure, or interrupted operation leaves the existing vault unchanged.

## Important limitations

- Restore replaces a full snapshot; it does not merge records.
- Losing the passphrase makes that backup unrecoverable.
- Losing both the device and every valid encrypted backup makes the local vault unrecoverable.
- Deleting the local vault does not delete external backup copies.
- If a provider revokes access or moves the destination, scheduled backup reports that user action is required. Choose a new destination and create a fresh verified backup.

## Before deleting or replacing a vault

- Confirm that the latest backup reports success.
- Confirm that you know its passphrase.
- Keep at least one provider-managed copy that is not on the device being replaced.
- Treat build-channel warnings as identity warnings only; all Android channels use the same versioned portable format.
