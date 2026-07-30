# Native Product Roadmap

## Android hardening

- Exercise encrypted import, backup, restore, and app-lock behavior across supported Android versions and device security configurations.
- Expand accessibility testing for screen readers, large text, keyboard use, right-to-left layout, tablets, and foldables.
- Add automated compatibility tests for older portable backup schema versions.
- Improve recovery diagnostics without exposing sensitive record content.
- Complete independent security review and dependency review before a production release.

## Future native applications

- Add an iOS application under `src/ios` using official Apple frameworks.
- Add a Windows application under `src/windows` using the current Windows application platform.
- Add a macOS application under `src/macos` using official Apple frameworks.
- Keep each application native while sharing the documented portable backup schema, privacy guarantees, and user-confirmed restore behavior.

## Cross-platform format hardening

- Publish test vectors for authenticated portable backups that contain synthetic data only.
- Define explicit schema-support windows and upgrade tests for every native application.
- Verify that no provider receives plaintext during export or restore on any supported platform.
