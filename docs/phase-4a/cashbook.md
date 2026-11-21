# Phase 4A cashbook

## Cashbook workflow

Open **Keuangan** for recorded cash, bank, and QRIS clearing amounts. **Isi saldo awal** records an account's funds before its first journal movement. An explicit Rp0 is valid. Until an opening is posted, the account card shows **Saldo belum diketahui** and displays recorded movements separately. Negative recorded cash or bank amounts are flagged for reconciliation.

**Catat mutasi** uses one form for other income, expenses, owner capital, and transfers. Expenses require a category. Transfers select cash or bank as the source and use the other as the destination. **Riwayat mutasi** filters all posted journal entries by asset account and opens read-only details, including debit and credit, category, note, and recorded time. Posted sales link to their original receipts.

The daily view classifies cash sales, confirmed QRIS sales, other income, operating expenses by category, capital contributions, owner withdrawals, and each transfer direction. The two funding-account net movements exclude openings. Each journal header drives one classification. The date range is half-open at local midnight using the device time zone shown on screen. Current recorded balances remain independent of the selected summary date.

## Posting design

| Event | Debit | Credit |
|---|---|---|
| Cash or bank opening | Selected funding account | OWNER_EQUITY |
| Other income | Selected funding account | OTHER_INCOME |
| Operating expense | OPERATING_EXPENSE | Selected funding account |
| Owner contribution | Selected funding account | OWNER_EQUITY |
| Owner withdrawal | OWNER_EQUITY | Selected funding account |
| Cash → Bank | BANK | CASH |
| Bank → Cash | CASH | BANK |

Cash and confirmed QRIS sales continue to post through the existing checkout code. Journal reads join the existing payment for that sale, so neither its receipt nor its two lines cause a second revenue entry. Pending QRIS has no journal. QRIS clearing is shown separately and is unavailable as an account on manual input forms.

A movement uses the UUID of its form route as a stable operation key. The journal ID is prefixed with `movement:`. A replay returns the existing immutable entry for the same canonical fields and rejects a different payload. Within one Room writer transaction, the repository guards the opening slot, inserts the header and audit metadata, and inserts both signed lines. The database uniquely indexes the opening account and journal ID. Amounts are checked whole Rupiah and aggregates use exact wide integers.

For an opening entered after existing movements, the effective timestamp is the account's earliest recorded movement. The audit keeps the actual submission time separately. The baseline sorts before activity at a tied timestamp and is excluded from revenue and daily net movements. All other manual movements use the time of posting. Posted entries have read-only UI and insert-only journal/metadata access.

## Migration and rollout

Room v4 adds `financial_movement` and makes the journal sale link nullable for manual entries. The existing journal foreign key to payments remains in place for sale rows. The migration preserves old journal IDs, lines, payments, archived catalog IDs, QRIS evidence, and pending profiles. The tests open a populated v3 fixture and verify both payment methods, pending recovery, and foreign-key integrity before creating a v4 movement. The v4-compatible APK is required once the database has migrated.

## Limits

Balances represent recorded business activity. Operators supply real opening balances and classify manual entries. The device determines the business date, so changing its time zone can regroup old entries. Expense categories are built-in, history is local and unpaginated, and this release has no correction flow for already-posted entries. Phase 4A covers manual cash/bank movements and summary. QRIS settlement and refunds remain outside this phase.


## Emulator evidence

Screens from a disposable API 28 emulator use test data only.

- [Unknown opening balances](screenshots/saldo-belum-diketahui.png)
- [Reusable expense form](screenshots/form-beban.png)
- [Posted two-line journal](screenshots/rincian-jurnal.png)
- [Daily summary and expense categories](screenshots/ringkasan-harian.png)
- [Account-filtered history](screenshots/riwayat-akun.png)

Run `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:connectedDebugAndroidTest`. To capture the screens, run `CashbookFlowTest` with the `captureCashbook=true` instrumentation argument on a clean disposable installation.

## Verification

- Starting main commit `9e44eca` passed hosted CI. [Main workflow](https://github.com/Suryadharmaa/umkm-apk/actions/runs/44908655880)
- The local debug build and 11 JVM tests passed. Nine targeted API 28 emulator tests passed, covering the populated v3 migration, balanced entries, exact arithmetic, duplicate and concurrent operations, rollback, transfer directions, unknown balances, and cashbook Compose flows. The accessibility test exercised named date controls at 320 dp and 200% text.
- A separate adb force-stop and relaunch changed the app process PID. History reopened the posted expense, audit note, and both journal lines.
