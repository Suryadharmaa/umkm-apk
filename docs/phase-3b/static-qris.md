# Phase 3B static QRIS

## Merchant workflow

Open **Katalog → Profil QRIS** to choose a PNG/JPG from storage or paste a merchant-issued payload. Review the decoded merchant name and National Merchant ID (NMID), then attest that the asset came from the merchant's payment provider. Replace or deactivate the active profile in the same screen. Begin a sale in Kasir, select **Bayar QRIS**, and check the total. **Tampilkan QRIS** persists the order before showing a scannable code and the amount to type in the buyer's app. Static QRIS does not embed the purchase amount. The app caps QRIS at Rp10.000.000 per transaction. [Bank Indonesia QRIS guidance](https://www.bi.go.id/id/fungsi-utama/sistem-pembayaran/ritel/kanal-layanan/qris/default.aspx)

In **Riwayat**, open a pending sale to resume it after leaving the screen or terminating the process. Check the successful entry in the merchant's receiving application or merchant transaction history. Transcribe the exact amount, transaction reference, and local transaction time, and attest that the payment and merchant identity match. The receipt and history say **QRIS dikonfirmasi manual**. The cashier records evidence locally from the receiving side. [Bank Indonesia merchant evidence guidance](https://www.bi.go.id/id/publikasi/ruang-media/cerita-bi/Pages/cara-memastikan-pembayaran-qris-benar-benar-masuk.aspx)

## Persistence and accounting

- Room v3 adds `qris_profile`, `qris_order`, and `qris_evidence`. The `payment` table gains an explicit method and allows null cash tender for QRIS. Migration copies existing payment keys, timestamps, and amounts as CASH, preserving sale lines, historical IDs, archived catalog rows, and journal entries. The Room v3 APK is required after migration.
- The profile contains the canonical payment payload and, for an image import, the original bounded image bytes. Validation checks TLV structure, CRC, Indonesian country/currency, QRIS national registry and routing fields, and static initiation without an embedded amount or surcharge. The UI renders just the validated payload with a quiet zone, excluding unrelated artwork and codes. Validation checks the structure. The cashier checks issuer provenance.
- Activating a replacement releases the prior active slot while preserving old profile versions for pending orders. Starting checkout revalidates the draft, pins the active profile and item snapshots, and releases the draft slot in one transaction. Multiple pending orders can coexist with new draft and completed sales.
- Confirmation keeps the original expected total and observed amount independently. The observed local minute must lie between the checkout and confirmation minutes inclusive. Evidence reference uniqueness is scoped to the stable NMID across profile replacements, with surrounding whitespace removed and case preserved. The same scope covers multiple provider channels conservatively where an NMID is unchanged.
- The confirmation writer is atomic. It inserts evidence and the paid payment, writes one balanced journal (QRIS_CLEARING debit and SALES credit), and transitions PENDING_QRIS to MANUALLY_CONFIRMED. Replaying identical evidence returns the existing receipt; conflicting or already used evidence leaves the target unchanged. A pending order has zero payment, evidence, or ledger rows. Posting represents funds awaiting settlement.
- The app stores imported assets and evidence in its app-private database. Evidence fields stay out of saved navigation state. Fixtures and captures use non-routable synthetic data only.

## Coverage and limits

The suite exercises v2 migration with a paid cash sale, archived product ID, and both journal lines; pending recovery; exact matching; two concurrent confirmations and concurrent payment methods; reference reuse across profiles; posting rollback; format validation; and QR image round trips. The Compose flow exercises profile import, pending checkout, activity recreation, mismatch rejection, manual confirmation, and History navigation. GitHub Actions assembles the debug and instrumentation APKs and runs JVM tests; the connected tests use a disposable API 28 emulator.

A cashier needs the provider's receiving-side record and a device clock matching the provider time display. A static code is shared across payments, so orders for the same amount in the same minute still require operator reconciliation of the reference and order. Unsupported QR encodings or provider image formats require a supported export. Production merchant onboarding, real provider interoperability, and physical-device scanning have yet to be validated. Phase 3B leaves unresolved transfers pending for operator review. Settlement, refunds, and other account movements are outside this slice.


## Verification evidence

- The final debug build passed, with 7 JVM tests and 35 connected API 28 emulator tests. Zero tests failed or were skipped.

- The merged main commit `f2b589e` passed GitHub Actions and its 21-test connected suite before implementation. [Main workflow](https://github.com/Suryadharmaa/umkm-apk/actions/runs/44906549468)
- The targeted Phase 3B run passed 14 emulator tests, including a v2 database containing a paid cash sale and a QRIS UI flow.
- A separate adb force-stop and relaunch changed the app PID from 5672 to 5707. History reopened the still-pending Rp15.000 sale with its stored profile and the code visible.
- Reproduce with `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:connectedDebugAndroidTest`. Use the `captureQris=true` instrumentation argument on `QrisFlowTest` in a disposable installation to generate the UI captures.

## Emulator captures

- [Pending checkout and exact purchase amount](screenshots/qris-menunggu.png)
- [Merchant evidence entry](screenshots/bukti-merchant.png)
- [Manually confirmed receipt](screenshots/qris-konfirmasi-manual.png)
- [History with distinct pending and confirmed statuses](screenshots/riwayat-qris.png)
- [Pending recovery after process termination](screenshots/qris-pemulihan.png)
