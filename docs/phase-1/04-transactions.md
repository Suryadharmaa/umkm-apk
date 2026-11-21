# Checkout transitions and financial posting

All writes pass through use cases. Room runs each command in one writer transaction covering status, evidence, journal, allocations, operation result and audit event. A stable request UUID is created before submission and survives retries. Look up that UUID first. Return the prior durable result for identical kind and canonical payload hash, or reject mismatched reuse. Fresh operation IDs still face status guards and unique keys. Update state with a compare-and-set and require exactly one affected row. Re-read the result after an ambiguous commit before reporting failure or success.

| From → to | Trigger, guards and effects |
|---|---|
| New → DRAFT | Persist cart ID and draft lines. Edits can replace draft lines only. |
| DRAFT → PAID | Revalidate active catalog and prices, require consent if changed, freeze snapshots, require sufficient tender and write payment, journal and event. Change = tender − total. |
| DRAFT → PENDING_QRIS | Revalidate and freeze snapshots, copy total into payment, bind the active immutable QR profile, persist before displaying QR. No journal. |
| DRAFT → CANCELLED | Explicit discard. Preserve current record and event. |
| PENDING_QRIS → PENDING_QRIS | Back, timeout, no evidence, mismatch or duplicate. Preserve pending state and route. Never infer payment or retry as cash. |
| PENDING_QRIS → PAID | Operator confirms successful merchant credit with matched merchant account, exact amount, time and provider reference. Record evidence and journal atomically. Deduplicate across profile replacements using stable provider and merchant namespaces. |
| PENDING_QRIS → CANCELLED | Operator records reason and confirms merchant-side check found no matching payment. Uncertainty stays pending. A cash replacement uses a separate sale ID. |
| CANCELLED → PAID | Explicit late recovery for cancelled QRIS only. Require matching merchant evidence and reason. Show possible duplicate fulfillment or tender, and require a decision before posting. If a replacement sale already represents the same purchase, reconcile the extra receipt outside sales via provider refund and retain the original cancellation. No automatic recovery. |
| PAID → REVERSED | Full correction only, with reason and actual cash return or merchant-side QRIS refund evidence. Unique refund journal per sale. A settled QRIS refund requires an explicitly evidenced bank debit. Pending/unsettled QRIS refund requires evidence that clearing was reduced. Other provider refund/settlement patterns remain unresolved for manual reconciliation. |

Payment history is derived from payment, sale state and events. Change is derived and the cash journal records the net retained amount. Archive catalog records and deactivate QR profiles; historical snapshots and original profile bytes remain available. Do not expose general delete/edit endpoints for posted records. Normalize category keys with Unicode normalization, trim and locale-independent case folding. Provider transaction and payout keys use provider-defined canonicalization (default: trim only, preserve case).

## Journal rules

Signed amounts use positive debits and negative credits. Opposite signs below refer to account entries, not the UI display sign.

| Posting | Debits | Credits |
|---|---|---|
| Cash sale | CASH total | SALES total |
| QRIS sale | QRIS_CLEARING total | SALES total |
| Full refund | SALES_RETURNS total | CASH, QRIS_CLEARING or BANK, as evidenced |
| Opening cash/bank | CASH or BANK opening amount (explicit zero allowed) | OWNER_EQUITY |
| Other income | CASH or BANK | OTHER_INCOME |
| Operating expense | OPERATING_EXPENSE | CASH or BANK |
| Owner contribution / withdrawal | CASH or BANK / OWNER_EQUITY | OWNER_EQUITY / CASH or BANK |
| Cash/bank transfer | Destination | Source |
| QRIS settlement | BANK gross minus fee, SETTLEMENT_FEE actual fee | QRIS_CLEARING gross |

Record settlement only for confirmed, unreversed payments from the matching provider account, each unallocated to any active settlement. Verify selected payments sum to gross and reconcile actual bank credit with gross less actual fee. A settlement reversal negates the original journal, keeps allocations for audit, and releases them for future settlement. The original payout reference remains reserved after reversal. Reposting requires a distinct provider correction reference. A payout with the same reference stays unresolved for review. Block settlement reversal after dependent bank refund and surface for reconciliation. For other manual corrections post one exact inverse linked to the original with a reason. Never reopen opening balances by reversal; use a linked accounting correction workflow deferred from v1, with recorded discrepancy called out for review. Do not reverse reversals or use a generic ledger entry to bypass sale/refund rules.

SQLite constraints cover local uniqueness and bounds. Cross-table checks are use-case invariants under the same write lock: line sum and payment match total, all journal lines balance and match their prescribed accounts, allocations and refund funding are valid, evidence ties to the correct merchant, and immutable rows stay immutable. Restore revalidates these invariants across the dataset, including active allocations, ordered sale events and posting counts. Use checked Long math for bounded transactions and wide exact accumulation for historical aggregates, with explicit out-of-range errors where converting back to Long. Store UTC epoch milliseconds and ISO local business dates. Use the commit time for normal sales, the provider time as separate evidence, and use actual occurrence time for settlements, refunds and manual movements with separate recorded-at audit time.

