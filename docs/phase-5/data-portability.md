# Phase 5 encrypted backup, restore and CSV

Open **Keuangan → Data**. Choose **Buat cadangan**, set a unique passphrase, and save the `.umkmbackup` through Android's system picker. **Pilih berkas cadangan** opens the restore picker. Review the confirmation and enter the passphrase. Restore requires a business database with every business table empty. Export a CSV by choosing the dataset and a save location. The report export uses the date, daily/monthly selection, and business zone configured under **Laporan**.

## Backup format and security

- Format 1 starts with a fixed magic and version and a fresh 32-byte random salt. The complete header is authenticated as associated data. PBKDF2-HMAC-SHA-256 derives a 256-bit key with 600,000 iterations. Tink AES256-GCM-HKDF Streaming AEAD authenticates 64 KiB segments and the final segment marker. The backup never includes the password. It is kept only in memory during the action, with mutable password and key buffers cleared after use. Password dialogs use Android's secure window flag. [Tink Streaming AEAD](https://developers.google.com/tink/streaming-aead) · [OWASP PBKDF2 guidance](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html)
- The encrypted stream is a canonical typed logical dump of all 14 Room v6 business tables, including archived items, drafts, merchant payloads and original images, receipts, pending/confirmed QRIS, evidence, payouts, allocations, and corrections. Primary keys and references are unchanged. A single Room transaction reads the live snapshot, including committed WAL frames. Backup prepares the complete ciphertext in app-private cache before opening the destination stream.
- Restore supports format 1 with the Room v6 layout and the compatible v5 layout without the correction table. The app remains on Room v6; earlier backups had no public writer. Unknown formats and schemas are rejected. Restore does not merge databases. Preferences outside Room, including the selected report zone, are device-local.
- SAF grants access to the selected document. The app has no internet permission or cloud account requirement. An operator can choose local storage. A provider may leave an incomplete new document after a write interruption. Any truncated backup is rejected on restore. No plaintext backup is staged on disk. [Android Storage Access Framework](https://developer.android.com/training/data-storage/shared/documents-files)

## Restore verification

The selected ciphertext is copied to private staging and authenticated through EOF before database modification. The confirmation and empty destination are checked, with emptiness rechecked under the writer lock. A second authenticated read inserts rows into one Room transaction. Foreign keys and uniqueness constraints reject orphaned or duplicate rows. The restore audit checks SQLite integrity, sale and receipt totals, pending states, payment evidence, prescribed journal lines, journal balances, payout allocations, and correction dependencies. It compares a SHA-256 digest of the canonical imported snapshot against the authenticated source. A failed check or write rolls back all inserts.

After commit, a separate Room connection opens the physical database and verifies the digest again. The UI reports verified completion only after this succeeds. If the commit succeeded but the separate reopen could not be verified, the UI asks the operator to reopen the app and check history. It does not report an untouched database or invite a second import. An empty installation must be prepared outside this feature when relocating data from a populated device.

The backup limit is 512 MiB of ciphertext and two million rows per table. Processing streams rows and cipher segments. A single merchant blob is bounded at 2 MiB, and reading images from SQLite uses 64 KiB slices to avoid CursorWindow overflow. An export from a very large database can hold a Room transaction while it writes. External providers, low storage, and OS process eviction can affect completion time.

## CSV coverage

- **Penjualan** has one row per draft, pending, paid, or refunded sale. **Rincian produk terjual** preserves item snapshots and joins to sales by ID.
- **Pengembalian dana** links refunds to the original journal, funding account and settlement when present. **Pencairan QRIS** includes pending, unresolved, posted and reversed payouts. Draft evidence columns are distinct from recorded Rupiah totals.
- **Mutasi dan jurnal** includes signed journal lines, correction references and reversal statuses. **Laporan keuangan** includes sales, returns, net sales, income, fees, expenses by category, owner movements, and ending balances. Unknown openings remain blank with an explicit status.
- CSV uses UTF-8, CRLF, escaped text, and exact integer Rupiah. Transaction dates carry ISO offsets and the explicit IANA business-zone column. Text that could trigger a spreadsheet formula is protected before CSV quoting, including prefixed whitespace and full-width characters. CSV is plaintext. Spreadsheet re-saving can remove protective characters or round large values; preserve the original export for audit. [OWASP CSV injection](https://community.owasp.org/attacks/CSV_Injection)

## Verification procedure

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
```

The branch is based on main at `480acb6`, after [PR #6](https://github.com/Suryadharmaa/umkm-apk/pull/6) and the [passing post-merge CI run](https://github.com/Suryadharmaa/umkm-apk/actions/runs/45019496068). Use a disposable emulator and synthetic data. `PortabilityDatabaseTest` covers populated v6 round trips, repeats, wrong passwords, truncation, tampering, authenticated invalid rows, rollback, v5 compatibility, CSV states, and large snapshots. `PortabilityFlowTest` uses Android DocumentsUI for backup, restore and CSV and checks explicit confirmation. The `capturePortability=true` argument captures the real app before and after using the native picker on a clean disposable installation.

## Physical-device coverage still open

The tests use AOSP emulators and the local Downloads provider. OEM file pickers, removable storage, provider disconnects during physical writes, near-full flash, and process eviction on low-RAM phones still need physical-device checks. Failure injection covers interrupted streams, a late transactional insert, and an invalid authenticated source. It does not model every document provider's behavior after a power loss.

## Emulator screenshots

These captures show the disposable API 28 installation and its synthetic backup workflow.

- [Backup, restore and export entry points](screenshots/kelola-data.png)
- [Encrypted backup saved through the picker](screenshots/cadangan-tersimpan.png)
- [Database restored and reopened successfully](screenshots/hasil-pemulihan.png)
