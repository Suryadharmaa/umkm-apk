# Phase 3A cash checkout

## Data and posting decisions

- The app supports one open draft per database. Room assigns a UUID when the first item is added. Draft lines store product ID, name, unit Rupiah price, and quantity. The cart and checkout read these persisted lines. Phase 2 task-state carts were transient and do not migrate to sale records.
- The Room 1 → 2 auto-migration adds sale, sale line, payment, journal, and journal line tables. Existing catalog rows, IDs, foreign keys, and archived records survive unchanged. The exported Room schemas describe the shipped database. The Phase 1 DDL describes the broader target. An already migrated database requires a Phase 3A-compatible app. A Phase 2 APK cannot read it.
- Opening checkout checks active products and prices. A difference shows old/new amounts and requires explicit approval. Approval persists a revised draft only if a fresh read matches the proposed lines. Posting revalidates again inside the writer transaction. An archived product blocks posting and can be removed from the cart.
- Payment existence is the durable proof of a paid sale. One writer transaction validates the draft and tender, inserts the payment, inserts a unique sale journal and its two lines, then releases the draft slot. History selects committed payments and reads their original snapshots. Catalog edits and archives cannot rewrite paid lines.
- The persisted sale UUID identifies the operation via the unique key `cash:<sale UUID>`. Replaying the same draft and tender returns the existing receipt. A changed payload for a paid sale is rejected. Concurrent submissions share the same database constraints and transaction checks.
- Money is whole Rupiah. The maximum tender and sale amount is Rp999.999.999.999. Checked integer operations calculate subtotal, total, change, and journal amounts. A sale of Rp16.000 with Rp20.000 tender posts CASH +Rp16.000 and SALES −Rp16.000. Rp4.000 is change, so the journal balances to zero.
- Checkout records cash the operator says was received. When the app is interrupted, reopen the persistent draft or look in Riwayat for the paid receipt before handling cash again. The app does not automatically resubmit tender after relaunch.

## Coverage

The Room integration suite exercises exact payment, insufficient cash, change, concurrent duplicate submission, replay after database reopen, repricing, committed snapshots after reopening, balanced journal lines, invalid tender, injected rollback after the first journal line, archived products, and migration from the Phase 2 schema. The Compose flow enters and reprices a real local catalog item, confirms the update, checks insufficient tender and change, records payment, and reopens the receipt from history.

## Limits

This slice supports cash only. It records sales cash movement rather than an opening balance or cash drawer reconciliation. It has one open draft and an unpaginated local sales list. Date rendering uses the device time zone. QRIS, settlement, refunds, cloud sync, inventory, and export remain outside Phase 3A.

## Emulator evidence

The captures are from a disposable API 28 emulator with a Rp16.000 test sale and Rp20.000 tender.

- [Price review](screenshots/periksa-harga.png)
- [Cash tender and change](screenshots/pembayaran-tunai.png)
- [Paid receipt](screenshots/struk-tunai.png)
- [Sales history](screenshots/riwayat-penjualan.png)


## Run results

The debug build and 4 JUnit tests passed. The API 28 connected suite passed 21 tests with zero failures or skips, including 12 cash database cases, the end-to-end cash flow, and the Phase 2 regression and screenshot tests.

A separate adb force-stop and relaunch changed the app PID. The operator could reopen the committed receipt and see its original product, Rp4.000 change, and net cash movement. The lost-response test uses a database close/reopen and a replay of the same request. The rollback test injects an SQLite failure during the second journal insert.
