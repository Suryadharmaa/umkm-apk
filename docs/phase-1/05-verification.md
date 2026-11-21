# Acceptance criteria and test cases

Each row gives a scenario and required result. JUnit tests cover use cases and calculations. Room instrumentation exercises actual persisted constraints and transactional failure. Compose tests cover screen behavior. Maestro covers complete workflows on a device. Test fixtures are isolated from production builds.

| ID | Scenario and expected result | Suite |
|---|---|---|
| A01 | Start in airplane mode with a clean install, set up shop, create category and product, search, and complete a cash sale. State and history survive relaunch. | Maestro |
| A02 | Rename/reprice a sold product and archive it. Old receipt still shows original name and price. Archived item is absent from checkout. Category archive with active dependents prompts reassignment. | JUnit, Room, Compose |
| A03 | Change price or archive item after cart opened. Checkout revalidates and requests explicit agreement before any posting with changed values. Reject quantity, price and arithmetic overflow. | JUnit, Compose |
| A04 | Total Rp15.000 and tender Rp20.000 show change Rp5.000 and persist cash movement Rp15.000. Exact cash shows zero change. Insufficient tender prevents submission. | JUnit, Room, Compose, Maestro |
| A05 | Absent/unreadable QR asset cannot activate. Imported merchant asset is readable and retained offline. Rotating profile leaves existing pending order bound to its original asset. | JUnit, Room, Compose, device scan |
| A06 | QRIS checkout in airplane mode displays pending. Leave, rotate, force stop and reopen. Status, amount and QR remain, with zero posted sales and clearing funds. | Room, Compose, Maestro |
| A07 | Customer screenshot alone, wrong merchant/account, wrong amount, incompatible time or repeated provider reference leaves order pending. Explicit correct merchant-side evidence posts once, labelled Catatan konfirmasi manual. | JUnit, Room, Compose |
| A08 | Race cash submissions and QRIS confirmations with same and different UUIDs. Commit one payment, journal and event. Fault-inject between writes, assert full rollback, then retry. Reject operation ID with a changed payload. | Room instrumentation |
| A09 | Cancel pending with merchant check and reason, then match late evidence through explicit recovery. If a substitute sale represents the purchase, no second sale is posted. Paid full reversal preserves original record and posts one refund. | JUnit, Room, Compose |
| A10 | Post opening zero then income, expense, owner movement and transfer. Verify classification and account balances independently. Reverse an expense once and retain both event records. Empty initial cash/bank balance is labelled unknown. | JUnit, Room, Maestro |
| A11 | Settle Rp100.000 verified gross, actual fee Rp1.000, bank Rp99.000. Sales stay Rp100.000, clearing drops Rp100.000 and fee is separate. Reject wrong profile, mismatched bank credit, duplicate payout and concurrent allocation. | JUnit, Room |
| A12 | Reverse settlement, retain allocations for history, and release eligibility. Test unsettled and settled full refunds, preventing unsupported funding and reversal of settlement with a dependent bank refund. | JUnit, Room |
| A13 | Verify period edges at local midnight in WIB, WITA and WIT. Refund in a later period appears in that period. Pending and cancelled items excluded from sales. No profit label. Filter history by method and status. | JUnit, Compose |
| A14 | Export a filtered report as UTF-8 CSV through the picker and inspect totals and escaping including formula-like user text. Simulate cancel, storage outage and incomplete write. Confirm no misleading completion state. | JUnit, device |
| A15 | Back up and restore a mixed ledger, pending QRIS and assets on an empty installation. Compare record IDs, ledger totals, business dates and readability. Reject wrong passphrase, tampering, missing references, unsupported version and populated destination. Interrupt restore and show original state after restart. | JUnit, Room, device |
| A16 | Exercise every screen's relevant empty, loading, valid, invalid, pending, commit failure and committed states from UX spec. Check hardware back, IME, TalkBack, landscape and font scaling to 200%. | Compose, Maestro, manual |

## Visual approval and comparison

Before coding, approve reference frames for Kasir with cart, cart review, cash insufficient and ready, QRIS pending and confirmation error, receipt, populated and empty finance/history, reports and restore validation. Store approved frame URLs and export revisions in the repository test reference manifest. Capture the same states from the actual emulator with fixed viewport, font scale, locale and isolated fixtures. Overlay against references and review layout, typography, spacing, status readability and CTA placement. Repeat with small screen and large fonts, and scan the real displayed QR on a second device. Require review sign-off for deliberate visual deviations. No emulator evidence exists during Phase 1.

