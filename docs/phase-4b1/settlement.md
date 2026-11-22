# Phase 4B1 QRIS settlement

## Workflow

Open **Keuangan → Pencairan QRIS** to start or reopen a settlement. Select eligible confirmed QRIS payments using their merchant transaction references. Transcribe the provider route code, NMID, payout reference, reported gross, actual fee, the credit shown in the business bank account, and the payout time. Attest that the provider report and bank movement match before posting. The form can be saved and reopened while checking evidence.

The provider route and NMID must match every selected payment's original QRIS profile. The app requires a single identifiable route on each pinned QRIS payload. A profile with ambiguous routing, a conflicting account, reused evidence, or mismatched amounts stays unresolved. Pending means the record has insufficient proof or has been saved for later, and unresolved means a detected conflict needs review. Draft selections are provisional. They do not claim the selected payments.

## Ledger and migration

- Only a manually confirmed QRIS payment with merchant evidence and a corresponding sale clearing debit is eligible. The provider gross must equal the exact sum of the selected payments, and actual bank credit must equal gross less fee.
- The writer uses one Room transaction for the payout record, balanced lines, and payment allocations. Debit BANK by actual net, debit SETTLEMENT_FEE by actual fee, and credit QRIS_CLEARING by gross. Omit zero lines. Cashbook and daily views report credits and fees separately from revenue and operating expenses.
- The journal ID is derived from the saved settlement UUID. Replaying a posted submission with the same normalized evidence and selections returns its original result. Changes to already posted records are rejected. A unique posted reference scoped to provider and NMID and a unique posted payment allocation guard competing operations. Reference normalization trims surrounding whitespace and preserves case.
- Room v5 adds settlement records and linked selections. Existing payments, evidence, journals, archived catalog IDs and manual movements remain intact. The v5-compatible APK is required once the database has migrated.
- The payout uses its actual reported minute in the device time zone. It must be at or after the selected customer payment evidence and cannot be in the future. A payout with a timestamp before the existing bank opening remains unresolved to avoid counting it in both the opening and a new credit. Cashbook separates the recorded time from the payout time.

## Limits

Settlements rely on the operator's real provider and receiving-bank records. The app uses the existing single business bank account and cannot read the bank or provider directly. Each submission supports up to 200 whole payments with gross up to Rp999.999.999.999. Partial payouts, adjustments, settlement reversals and refunds require a separate accounting workflow. Existing device-zone behavior applies to the daily summary.

## Verification

The feature branch started from main at `258383c`, after [PR #4](https://github.com/Suryadharmaa/umkm-apk/pull/4) and its [passing main run](https://github.com/Suryadharmaa/umkm-apk/actions/runs/45015811546).

Run the debug build, JVM suite and connected suite from the repository root on a disposable API 28 emulator.

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:connectedDebugAndroidTest
```

Settlement coverage includes competing allocations, concurrent payouts with a duplicate reference, idempotent retries, rollback after a failure injected after journal insertion, pending and unresolved recovery, cash and unconfirmed payment exclusion, provider and NMID mismatch, journal integrity, and migration of populated v4 cash, QRIS, cashbook, and archived catalog data. The Compose flow submits incomplete evidence, reopens the settlement, corrects a gross-to-net mismatch, and reopens the immutable posted details.

### Emulator evidence

The screenshots below use isolated synthetic fixtures. To recreate them on a clean disposable emulator, install the debug and instrumentation APKs and run `adb shell am instrument -w -e class id.umkmku.pos.SettlementFlowTest -e captureSettlement true id.umkmku.pos.test/androidx.test.runner.AndroidJUnitRunner`. Screenshots are written to `/sdcard/Android/data/id.umkmku.pos/files/screenshots/`.

- [Pending evidence](screenshots/menunggu-bukti.png)
- [Unresolved amounts](screenshots/belum-cocok.png)
- [Posted payout details](screenshots/pencairan-tercatat.png)
- [Settlement history](screenshots/riwayat-pencairan.png)
