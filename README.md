# Personal Health Vault

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
![Android](https://img.shields.io/badge/Android-green.svg)
![Kotlin](https://img.shields.io/badge/Kotlin-purple.svg)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-UI-blue.svg)
![iOS](https://img.shields.io/badge/iOS-black.svg)
![macOS](https://img.shields.io/badge/macOS-black.svg)
![Swift](https://img.shields.io/badge/Swift-orange.svg)
![SwiftUI](https://img.shields.io/badge/SwiftUI-UI-blue.svg)
![Windows](https://img.shields.io/badge/Windows-blue.svg)
![WinUI](https://img.shields.io/badge/WinUI-UI-blue.svg)

Personal health records that stay local, encrypted, and fully user-controlled. No backend, no tracking, no cloud required.

Personal Health Vault is a privacy-first health app for iOS, Android, Windows, and macOS. It helps people organize personal medical records, documents, treatments, a flexible personal schedule, health measurements, notes, contacts, directives, identifiers, and health summaries while keeping their health data on their own devices.

## Project Status

This project is in early development.

The current Android application provides a local-first foundation with optional person-owned records, shared notes, schedules and contacts, document import, medication notifications, and user-controlled encrypted backup and restore. A fresh installation opens Home immediately with no required profile and no sample data.

Personal Health Vault is not ready for production use with real medical data yet.

## Core Principles

- Local-first by default
- Encrypted storage on the device
- No backend required
- No tracking
- No ads
- No cloud account required
- No cloud AI required
- User-controlled backup only when explicitly enabled
- Health data should never leave the device in plaintext

## What It Does

Personal Health Vault is designed to help users keep a private, structured copy of their health information, including:

- Medical documents
- Treatments and medications
- A generic schedule for timed, all-day, and recurring entries with optional people and alerts
- Vaccinations
- Emergency contacts
- Independent health notes
- Health measurements with explicit units
- Shared contacts with phone numbers, email addresses, websites, addresses, and notes
- Family history, personal directives, and health identifiers
- Categorized invoices, receipts, and reimbursement documents
- Personal health summary
- Important notes for future consultations
- Encrypted full-data backup and restore

## What It Is Not

Personal Health Vault is not a medical device, diagnostic app, emergency app, or replacement for a healthcare professional.

It does not provide:

- Medical advice
- Diagnosis
- Emergency recommendations
- Triage
- Prescriptions
- Clinical certification
- Regulatory compliance claims

Do not use this app as a replacement for a doctor, pharmacist, emergency service, hospital system, or official medical record platform.

## Privacy Model

Personal Health Vault follows a simple privacy model:

- Health data is stored locally by default.
- Saved health data is encrypted.
- The app does not require a backend to work.
- The app does not send health data to a server.
- The app does not include ads or tracking.
- Backup and sync, when added, must be user-controlled.
- Backup data must be encrypted before leaving the device.
- Backup providers must never receive plaintext health data.

## Security Model

The app uses a local encrypted storage model. Internal source types may retain the historical `HealthVault` name, but the product interface uses plain language such as health data, records, backup, and people.

Plaintext health data should only exist in memory while the app is actively using it. Saved records should be encrypted before being written to disk.

Current security boundaries:

- Health data is encrypted locally.
- Device biometric or credential unlock can be used as an app access gate.
- App lock is separate from data encryption.
- Biometric unlock must not be described as hardware-bound encryption unless that is guaranteed on every supported platform.
- Real health data must never be committed to the repository.
