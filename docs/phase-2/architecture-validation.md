# Phase 2 architecture validation

Reviewed all Phase 1 documents. The workspace and upstream repository initially contained no Android app, and no AGENTS.md was present.

| Finding | Resolution |
|---|---|
| Dashboard requested in Phase 2 but absent from the route map. The first sale gate also exceeded this phase. | Added Beranda and scoped the initial navigation and release gate to real catalog operations and cart review. |
| Cash and bank may move before an opening balance is recorded. A numeric total could imply a known balance. | Display recorded movements separately from an unknown balance until explicit account opening. |
| The payment specification required matching amount but stored the expected amount only. The refund guard described pending QRIS even though reversal starts from PAID. | Persist observed nominal separately and require equality on posting and restore. Clarify that unsettled QRIS refunds require prior confirmation. |
| Replacement cash sale and dependent refund/settlement relationships lacked durable pointers. | Add references to the relational design and validate the relationship in the same writer transaction. |
| Room will initially implement the catalog subset, while the SQLite document describes the full MVP. | Treat exported Room schemas as migration inputs. The reference DDL remains the design for later tables. |
| The newly supplied screenshot is visual inspiration. No first-party frame or baseline previously existed. | Use a blue header, white rounded surfaces, prominent action hierarchy and a compact navigation bar. The first emulator captures become regression baselines, subject to reviewer visual approval. |

Phase 2 uses one Compose activity, Navigation Compose, a Hilt-scoped catalog ViewModel, Room Flow reads and transactional repository writes. Catalog writes trim names, validate whole Rupiah prices, and archive rows. Archiving a category atomically uncategorizes its active products after explicit confirmation. Product IDs and archived rows are retained. The cashier cart is a saved-state draft of item IDs and quantities, rederived from the active catalog. It has no sale, ledger, QRIS or settlement writes. On relaunch, catalog data is durable. Draft continuity after a force stop is outside this slice.

Provider evidence timing tolerances, payout adjustments, QRIS refunds and owner authorization remain open before financial implementation.
