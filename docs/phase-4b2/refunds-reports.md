# Phase 4B2 refunds, reversals and reporting

## Recording refunds

Open a paid receipt in **Riwayat → Catat refund penuh**. The original product and price snapshots, totals, tender, and receiving-side QRIS evidence remain unchanged. The receipt and history show the full-refund status and the receipt links to the refund journal. An unpaid or pending QRIS order has no refundable sale journal.

| Source | Required evidence | Debits | Credits |
|---|---|---|---|
| Cash sale | Exact whole amount physically returned to the buyer, explicit operator confirmation, reason | SALES_RETURNS | CASH |
| Confirmed QRIS still clearing | Pinned provider route and NMID, unique provider refund reference, refund time, exact amount and merchant-side evidence of clearing reduction | SALES_RETURNS | QRIS_CLEARING |
| Confirmed QRIS in an active settlement | Same merchant/provider evidence plus exact bank debit and unique bank statement reference, linked to the original settlement | SALES_RETURNS | BANK |

The operator confirms evidence from the actual provider report and bank record. The app validates consistency against local records. QRIS payment, settlement and refund references remain scoped and case-preserving after trimming. A full refund is posted once per sale. Reported amounts use whole Rupiah. Refunds do not reverse settlement fees. A refund after an earlier period is a return in the refund period.

## Reversals and audit

Open a posted manual movement in **Keuangan → Riwayat mutasi**. Record its exact inverse with a reason and explicit approval. Both journals remain readable and link to one another. The reversal uses its own posting date. If needed, record the corrected movement separately. Opening balances and correction entries do not offer reversal.

Open a posted payout in **Pencairan QRIS → Catat pembalik pencairan**. Supply a distinct provider correction reference and attest that clearing and fees were restored to their original amounts. Positive original bank credits require evidence of an equally sized bank debit and a bank statement reference. A zero original bank credit requires a recorded Rp0 and has no bank debit reference. A dependent settled refund blocks payout reversal and calls for manual reconciliation. Reversal releases only active claims. Historical links and payout references remain reserved and readable. A later payout requires a new reference. Already refunded purchases never become eligible for payout.

Room v6 adds the append-only financial correction table and keeps all v5 tables and data. The database requires a v6-compatible APK after upgrade. The form UUID is the stable operation key. Identical retries return the existing journal and conflicting retries fail. References, the inverse/original link and refund/sale link have unique indexes. The write transaction includes both journal and audit rows plus active allocation release where applicable. Reads check balanced signed lines, expected postings and the original/correction relationship.

## Reports

Open **Keuangan → Laporan** for daily or monthly summaries. The merchant can select WIB, WITA, or WIT, defaulting to WIB. The selected business time zone persists locally. The report uses inclusive local midnight through exclusive next local midnight, or first of month through exclusive first of next month. It reads transaction occurrence time and shows cash, bank and QRIS clearing through period end. An account with no recorded opening remains unknown, with its movement shown separately.

Gross sales are split by cash and QRIS. Net sales subtract full returns. Expenses are shown by category, with settlement fees, other income, and owner contributions/withdrawals on separate lines. Reversals apply against the original classification on the reversal date. Transfers and settlement bank credits affect account balances without contributing to sales. Historical totals use exact wide integer arithmetic. Profit requires cost data outside this phase.

## Limits

This release records operator-attested evidence without contacting the provider. It supports one business bank account. Mixed funding, partial refunds, unmatched debits, and payout corrections that differ from an exact inverse need reconciliation outside this workflow. Existing screens still use the device time zone for entering provider timestamps. Report periods independently use the selected business time zone.

## Verification

The branch starts from main at `a8762a3`, after [PR #5](https://github.com/Suryadharmaa/umkm-apk/pull/5) and the passing [main CI run](https://github.com/Suryadharmaa/umkm-apk/actions/runs/45017704036). All 13 existing v5 Room table definitions are identical in v6.

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
```

The Room cases cover cash refunds, pending/unsettled/settled QRIS, same- and different-ID retries, provider and bank references, allocation release, dependent-refund guards, rollback after an injected allocation-release error, preservation of receipts, and a populated v5 database. JVM tests exercise period boundaries in WIB, WITA and WIT and verify that refunds posted later do not overwrite past totals. The Compose flow records and reopens a cash refund, then opens the daily and monthly reports.

### Emulator captures

Taken on an API 28 emulator at 720 × 1440 and 320 dpi with synthetic data. To reproduce, install the debug APKs on a disposable emulator and run `adb shell am instrument -w -e class id.umkmku.pos.CorrectionFlowTest -e captureCorrections true id.umkmku.pos.test/androidx.test.runner.AndroidJUnitRunner`. Images are written to `/sdcard/Android/data/id.umkmku.pos/files/screenshots/`.

- [Cash return form](screenshots/form-refund-tunai.png)
- [Balanced refund journal](screenshots/jurnal-refund-tunai.png)
- [Original receipt with refund status](screenshots/struk-asli.png)
- [Daily gross, refunds, and net sales](screenshots/laporan-harian.png)
- [Monthly balances and unknown openings](screenshots/laporan-saldo.png)
