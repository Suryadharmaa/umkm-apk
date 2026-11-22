# Phase 6B1.1 visual review

[PR #9](https://github.com/Suryadharmaa/umkm-apk/pull/9) stays on the original Phase 6B1 branch. The starting captures are the reviewed and rejected implementation at `1128251`. The revised images below are full Android emulator captures. Both approved source JPEGs were opened and their SHA-256 values and the ZIP hash matched the [manifest](../../phase-6/REFERENCE_MANIFEST.md).

| Screen | Before | Revised |
| --- | --- | --- |
| Beranda with the same catalog and cash sale fixture | [Initial emulator capture](../screenshots/beranda-360dp.png) | [Revised emulator capture](screenshots/beranda-360dp.png) |
| Katalog with the same four products | [Initial emulator capture](../screenshots/katalog-populated-360dp.png) | [Revised emulator capture](screenshots/katalog-populated-360dp.png) |

The local three-column boards pair crops from the original JPEGs with the actual before and after renders. They are available in `.local/phase-6b11-review/beranda-reference-before-after.png` and `.local/phase-6b11-review/katalog-reference-before-after.png`. Original reference pixels and derivatives stay local. The [reference analysis](../../phase-6/REFERENCE_ANALYSIS.md) documents source framing and content exclusions.

## Beranda comparison

| Largest mismatch in the initial capture | Correction across two emulator passes | Remaining difference from `01-Beranda.jpg` |
| --- | --- | --- |
| A flat navy background extended under the whole first block. | The solid UMKM primary blue now covers about 91 dp at the screen edge, down from about 291 dp. | The reference uses angled gradients and brighter tonal transitions. This revision uses the approved solid palette. |
| The overview sat entirely inside the saturated region, and its counts were two heavyweight boxes. | The white overview now straddles the blue to canvas boundary. A single pale surface groups two scoped counts with metadata above values. | The original has shop identity and a wider KPI strip with a different set of measures. Beranda reports the two catalog counts available in the current read model. |
| The large white cashier button competed with the title and overview. | Compact pills sit below the metrics, with solid blue for the cashier and pale blue for the catalog. Their touch areas remain at least 48 dp tall. | The visual shortcut is inside the overview. Its operation is opening the cashier. |
| The transaction row started with a prominent timestamp and oversized symbol. | Product and exact Rupiah lead, with method, time and the payment state below. A compact tile anchors the row. | The source shows an order-oriented layout, while UMKM-KU distinguishes saved cash, QRIS and refund states in text. |
| The single rendered transaction left the rest of the page unused. | The second pass added a compact blue-tinted **Produk per kategori** panel from loaded active categories and product counts. Multiple real recent sales can still appear above it. | The reference uses an advertisement or chart in that space. A one-sale fixture still leaves open canvas below the real content. |

## Katalog comparison

| Largest mismatch in the initial capture | Correction across two emulator passes | Remaining difference from `04-Katalog.jpg` |
| --- | --- | --- |
| A tall header and plain text action underplayed the catalog hierarchy. | The standard-height title is now in a 56 dp app bar and the existing QRIS profile link has a light capsule. Large text retains the stacked layout. | The reference’s top-right action is an import feature. Katalog presents the existing **Profil QRIS** operation. |
| Two broad segmented controls sat before search. | Search and filter lead the screen. **Produk** and **Kategori** use compact selectable targets, with a soft fill only on the selected tab. | Two real modes sit left-aligned. The screenshot reference spreads three modes across the width. |
| Search used a strong outline even when idle and the filter reused the rows’ list icon. | The white search capsule has a visible field label and focused outline. The separate filter has a labeled 48 dp target and a sliders vector with open handles. | The accessible native text field retains its nominal height and floating focused label. The reference uses a shorter placeholder presentation. |
| Product cards used repeated generic symbols and bold arrows. | Denser white rows now show name-derived initials, exact Rupiah, a quiet category tag and a narrow edit chevron. Long names wrap and the tile drops away at large font sizes. | The reference uses photos and merchandise controls. Product titles in the long-name fixture occupy more lines than the reference. |
| A full-width create button visually formed a second bar. | The centered pill is roughly half the old visual width. The reserved dock keeps the list and CTA from intersecting, even at 200% text. | The reference overlays the pill on a product card. The separate dock intentionally leaves clearance above the existing five-tab bar. |

## Capture and regression notes

- API 28 AOSP emulator, 720 × 1440 px. Normal run used 320 dpi and 100% text. The [compact run](screenshots/katalog-320dp-200pct.png) used 360 dpi (320 dp wide) and 200% font scale. The full long name, Rp999.999.999, create pill and all five destinations remain available. The native API 28 clock still partially clips at this font size.
- Additional native captures include [empty Beranda](screenshots/beranda-empty.png), [empty Katalog](screenshots/katalog-empty.png), [no results](screenshots/katalog-no-results.png), and [product editing](screenshots/katalog-edit.png).
- The debug build uses the same isolated fixture as the initial captures. Beranda renders four active products in two categories and a Rp12.000 cash sale entered via the existing repository. The recorded time reflects the emulator run. The old screenshots and automated goldens remain available for review. Only the revised Android captures are tracked here.
- The UI diff is confined to Beranda, Katalog, shared presentation controls and the Katalog app bar. Compose assertions measure the filter and creation targets and verify list clearance in compact mode. Routing, database and all posting, payment, corrections and portability behavior are preserved. TalkBack, physical-device and tablet-rail visual checks remain open.

## Applied UI/UX Pro Max queries

Ran the original `.agents/skills/ui-ux-pro-max/scripts/search.py` with Python after reading its local `SKILL.md`.

| Query and option | Applied recommendation |
| --- | --- |
| `Compose compact touch target clickable modifier lazy list content padding --stack jetpack-compose` | Retain keyed lazy lists and use modifier padding against measured touch areas. |
| `mobile visual hierarchy spacing information density dashboard --domain ux` | Preserve 8 dp target separation and allow text and row height to reflow. |
| `compact financial dashboard merchant layered white panels --domain style` | Group scoped reads and keep currency formatting clear. The approved screens and existing reads govern the actual content. |
| `mobile typography readable numbers metadata hierarchy --domain typography` | Separate metadata from headings and primary values. Keep the approved native type roles and tabular Rupiah. |
| `mobile search filter visible labels touch target sticky actions --domain ux` | Keep a labeled search field, reserve 48 dp click areas independently of the visible capsules and avoid occluding the list. |
