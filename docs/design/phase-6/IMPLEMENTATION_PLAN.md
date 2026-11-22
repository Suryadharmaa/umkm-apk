# Change isolation and approval

Base is verified main `19f649b82171c246e2b9713f9a07d5ecb4823e4f` with Phase 5 integrated. This branch is a visual review proposal. [Main Android checks](https://github.com/Suryadharmaa/umkm-apk/actions/runs/45022513976)

| Checkpoint | Reviewable scope |
| --- | --- |
| 6A | Documentation, original skill execution evidence, proposed tokens and reference/screenshot acceptance. Approve the blue/white/charcoal palette, chart gate, responsive navigation, and UI-only catalog filtering before implementation. |
| 6B1 | Rebase or branch from green integrated main containing approved contract. Compose tokens/components, app navigation shell, Beranda and Katalog. Preserve existing destination IDs and back behavior. |
| 6B2 | Keyed Kasir product/cart UI, cash and QRIS screen presentation and original receipts. Keep view state and command wiring; retain pending recovery and price review. |
| 6B3 | Keuangan and Riwayat presentation, ledger/settlement/returns/reports and linked navigation. Preserve period, asset and transaction semantics. |
| 6B4 | Phase 5 secure Data Tools presentation and remaining consistency. Preserve Storage Access Framework contracts, secure prompt flags, and empty restore guarantees. |
| 6C | Use the [visual gates](VISUAL_ACCEPTANCE.md) throughout 6B and consolidate actual emulator comparison, TalkBack review and unresolved issues. Approval is explicit. |

Production UI changes belong to later PRs. For each 6B/6C PR inspect `git diff --name-only` before review. Keep domain, repositories, financial ViewModel actions, Room schemas/migrations, payment/settlement/refund rules, and portability mechanisms outside the diff. Read-only display adaptations may consume existing state. If a desired visual needs a new accounting calculation, persistence, or route behavior, return for scope approval. Preserve current test tags including `dashboard`, `cashier`, `cart`, `cashbook`, `settlement-form`, `correction-form`, `financial-report`, and `data-tools`. Report intentional screenshot and semantic assertions individually. Keep PRs separate with manual review and merge.
