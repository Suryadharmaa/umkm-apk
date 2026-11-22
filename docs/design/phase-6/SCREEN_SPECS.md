# Screen specifications

## Shared structure

Keep the five top-level destinations in the current order **Beranda · Kasir · Katalog · Riwayat · Keuangan**. First-level screens use a small title/app bar and the shared destination control. Secondary routes use a back affordance, descriptive title and a route-specific action. Preserve state and destination stack on Back. Existing routes continue to receive the existing IDs. Use Indonesian labels, established Rupiah and time formatting and explicit scope for the financial numbers.

## Compact layout at a glance

The bracketed regions are component groups. A lower group can scroll when space or text scaling requires it.

| Tab | Portrait order |
| --- | --- |
| Beranda | [blue title + cashier] ? [white overview] ? [recent sales] ? [five destinations] |
| Kasir | [search] ? [category] ? [product rows] ? [exact cart total + open cart] ? [five destinations] |
| Katalog | [title + QRIS profile] ? [Produk/Kategori + quiet search/filter] ? [edit rows] ? [create] ? [five destinations] |
| Riwayat | [title] ? [sales rows with exact amount and status] ? [five destinations] |
| Keuangan | [title] ? [three account rows] ? [record/links] ? [date and daily summary] ? [recent movements] ? [five destinations] |

## Beranda

1. Solid blue header with UMKM-KU and a short Beranda title. Put **Buka kasir** first and **Kelola katalog** adjacent or directly beneath at large text.
2. Inset white overview. When a ready existing read describes today, show **Penjualan tercatat hari ini** using approved catalog/cashbook/ledger read values only. Never repurpose the finance screen's selected date silently. If a suitable today read cannot be reused without new financial calculation, use active catalog counts in that slot. Unknown/loading/failed reads get their own state and never turn into Rp0. For ready no-activity, use the truthful empty state.
3. Up to three recent loaded sales in current history order. Show method, amount, short description and state. Link **Lihat riwayat** to the existing tab. Pending QRIS may be listed here but never feeds the KPI. Empty history shows a brief cashier shortcut.
4. Use existing exact summary rows instead of a chart in the initial implementation. A chart needs an approved existing ordered read, stable period/zone and an accessible list alternative. It may not require changes to financial logic.

## Kasir and checkout

| Step | Order and actions | State decisions |
| --- | --- | --- |
| Browse | Search field, active category filter with full accessible labels, then stable keyed white rows; name, Rupiah, add/stepper. Nonempty cart docks exact total and **Lihat keranjang**. | Empty catalog links to Katalog. Empty search result offers clear. Loading/failed draft preserves catalog context and disables writes. At large text place controls below name/price. |
| Cart | Frozen item snapshots and quantity controls, labeled total and grouped **Bayar tunai** / **Bayar QRIS** actions on one step. Cash retains existing primary emphasis, QRIS secondary. | No tender options on an empty cart. Preserve review/price gate, changed price before/after and explicit accept or cancel. Stale/archived items keep existing block. |
| Cash | Charcoal **Total tagihan** block, amount field with Rupiah label, received/change beneath, then **Catat pembayaran tunai** using existing action. | Invalid/insufficient entry is announced and prevents commit; exact is valid with zero change. Busy prevents repeat submit. Receipt only after committed response. |
| QRIS | Bill total, verified context/profile, then actual static QR when existing start succeeds. Use stable quiet zone, aspect ratio, unmodified stored rendering and explicit trusted-source text. **Periksa bukti merchant** opens amount, reference, observed time and approval. | Missing/inactive profile links to existing settings; distinguish absent/mismatched evidence, saving, persisted pending, and manual confirmation. A scan/display is never completion. Pending survives process restart through Riwayat. QR credentials in screenshots are synthetic. |
| Receipt | Method/status and exact original total first. Item snapshots, cash received/change or merchant evidence below. Retain the existing history and refund/correction actions. | Refunded receipt retains original amounts with a separate refund status and link. Unpaid QRIS uses the pending route. No new method or receipt sharing. |

At 320 dp or 200% text, move quantity groups and amount pairs to full-width rows where needed. One-hand confirmation rests above system bars. With IME and limited height, make context scrollable and use inline CTA fallback if a dock would hide the focused field. Native dismiss/Back behavior remains available according to the existing busy guard.

## Katalog and QRIS merchant settings

Header and secondary **Profil QRIS** action, then compact Produk/Kategori control. **Cari produk** narrows the active loaded items by name; category choice filters loaded IDs, with **Semua** and clear controls. Kategori retains its list and counts. A name filter for categories may use the same component with the explicit label **Cari kategori**. Searches and filters are local UI state. White rows use a quiet category tile, name, price, category and edit chevron. Creation action follows active tab and sits above navigation. When empty, the existing creation action becomes the only prominent CTA.

Editors group name, exact price and optional category. Retain explicit archive confirmation and the existing category archive consequence. Show error near the owning input when known plus a summary for cross-field errors. QRIS Profile groups current status, image/payload alternatives, preview with merchant/NMID and explicit activation. Cancel/denial, invalid data and errors keep the existing result and recovery path.

## Keuangan and Reports

Top to bottom: **Saldo tercatat saat ini** with three independent account tiles, **Catat mutasi** and quiet links to Riwayat mutasi, Pencairan QRIS, Laporan and Kelola data, then a date-labeled daily summary and existing recent monetary movements. In compact width use one account stack. Known balance uses exact amount, unknown says **Saldo belum diketahui** with separate recorded movement and an opening action, and clearing says **QRIS belum cair**. A negative balance retains the explicit warning. Period controls do not change current balances. Daily cashbook uses its current device-zone source. Report uses the persisted explicit business zone and visible daily/monthly scope. Reports group gross, returns and net, then expense categories, fees, owner movements and ending accounts. No combined worth, credit products, trend placeholders or profit.

Movement editor groups kind, affected account/direction, whole Rupiah, category where required and note. Detail preserves source/effective and recorded timestamps, debits and credits, evidence and original/correction links. Settlement pages make selected gross, actual fee, bank net, evidence and reconciliation status readable separately. Keep pending/unresolved drafts editable where allowed; show posted/reversed read-only with original link. Correction surfaces the funding account, full refund amount, reason and applicable evidence before explicit confirmation. Show blocked or already-completed paths with existing journal navigation. Keep reversals distinct from refunds.

## Riwayat and Kelola data

Riwayat is the existing mixed sales list, with leading method icon, name/summary, timestamp, trailing amount and explicit status. Keep separate journal/payout lists in Keuangan. If a long product title needs compact truncation, announce full content and expose it in the receipt. In finance history keep existing account filters; use current links, no invented search or status filter.

Kelola data has three groups **Cadangan terenkripsi**, **Pemulihan data**, **Ekspor CSV** with one action per task. Keep masked secure password dialogs, explicit empty-install confirmation, native picker, report period/zone and plaintext disclosure. A user who cancels is returned to the task without success. A committed restore with unverified reopen remains distinct from confirmed verified restore. Never depict protected password windows in screenshot assets.
