# Development plan and repository structure

Keep Phase 1 documentation under docs/phase-1. The following is the proposed Phase 2 layout. Use one Gradle application module with packages for the initial release to minimize build and DI overhead.

```text
app/src/main/java/id/umkmku/pos/
  core/designsystem/       Compose theme and components
  core/money/              IDR and checked arithmetic
  domain/model/            Sale, payment, ledger and report models
  domain/repository/       Repository interfaces
  domain/usecase/          Checkout, ledger, reporting and recovery commands
  data/local/              Room entities, DAO, migrations
  data/repository/         Repository implementations and transactions
  data/preferences/        DataStore for UI preferences
  data/export/             Document picker IO, export and backup codecs
  feature/{setup,catalog,checkout,history,finance,settings}/
  navigation/              Typed destinations and restoration
  di/                      Hilt bindings
app/src/test/               JUnit use-case and codec tests
app/src/androidTest/        Room, migration and Compose UI tests
app/schemas/                Exported Room schema history
maestro/                    End-to-end device flows
docs/design/                Approved reference manifest and visual review
```

Use Compose Material 3 with custom tokens, screen ViewModels exposing immutable StateFlow, repository boundaries, use cases for write commands, a version catalog and Hilt DI. Room owns observable durable data. DataStore holds preferences only. Select compatible SDK, Kotlin, Compose, AGP, Hilt and Room versions after review. Confirm package ID, supported OS and hardware before initialization.

| Milestone | Scope | Exit evidence |
|---|---|---|
| 0 | Review Phase 1, resolve financial policy and obtain authorized merchant sample and visual reference, approve original Figma frames | Approved specifications and reference manifest |
| 1 (Phase 2) | Scaffold and DI, tokens, Room, navigation, Beranda, Kasir and catalog. DataStore enters when persistent preferences are needed. | Build, catalog persistence, UI and screenshot tests |
| 2 | Cart, cash, history, QRIS import, pending/resume, verification and reversal | Durable checkout tests, duplicate/race and failure evidence |
| 3 | Cashbook, settlement, reports, CSV and protected backup/restore | Ledger reconciliation, restore and migration tests |
| 4 | Full QA, access and device checks, visual comparison and signing | Passed test matrix, signed APK and install smoke test |

## Portability and recovery

Use the Storage Access Framework for explicit export destinations. CSV contains UTF-8 report rows, UTC timestamps and business dates, with RFC-style escaping and spreadsheet formula protection for user strings. CSV is informational. Use a versioned logical backup from a consistent Room read snapshot containing all business rows, shop config, operation IDs and QR assets. Use reviewed passphrase-based key derivation and authenticated encryption, with a fresh random salt and nonce and explicit format and algorithm metadata. Decide and benchmark cryptography parameters on target devices before implementation. Disable implicit OS/cloud transfer of business data and apply limits before decoding (file, rows and asset dimensions). Never export private app databases as live SQLite files.

Authenticate and parse the backup into staging, run schema, foreign-key and business consistency checks, show restore summary, then import to a verified empty business database in one Room transaction. Recheck emptiness in the writer transaction and lock maintenance operations during restore. Clear temporary artifacts after success or failure. Refuse unsupported newer formats with a specific message. Preserve older-format readers and test forward migration. A failed export may leave a partial destination file, so tell the user and invite a new destination. Success requires a completed write and close. Explain that the passphrase is required for restore and provide no recovery bypass.

## Review decisions and risks

| Item | Proposed decision / unresolved question |
|---|---|
| Reference | GoPay Merchant screenshot supplied as inspiration. Approve original UMKM-KU reference frames before the final visual release gate. |
| Merchant QRIS | Obtain an authorized sample and verify merchant app exposes stable references and transaction times. If evidence is incomplete, leave pending. Agree on timing tolerance and exception handling after inspecting the provider. |
| Provider payouts and refunds | Validate actual bank/fee deductions, provider reference conventions and full-allocation fit. Partial payouts, withheld adjustments and unsupported refunds may need product scope changes. |
| Operator control | Trusted shared-device operator assumed. Confirm whether disbursements, QR replacement, refund and export need a separate owner role or PIN. |
| Local loss and data exposure | Recovery depends on an independently stored backup and passphrase. Confirm retention, device handover, and handling of sensitive transaction references in exports. |
| Device and release | Confirm min SDK, hardware, APK signing ownership, package name and distribution channel. Measure checkout on intended device and scan QR under shop lighting. |
| Accounting scope | Confirm opening-funds/correction policy, business-time-zone lock and backdating rules. Assess reporting and export requirements with the merchant before release. |

