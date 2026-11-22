# Phase 6B1 implementation and visual evidence

The implementation is based on [approved Phase 6A](../phase-6/README.md) at main commit `2c480326612b8c6ce0dc743a72c2a84f30335eb3` after [PR #8](https://github.com/Suryadharmaa/umkm-apk/pull/8). [Main Android checks](https://github.com/Suryadharmaa/umkm-apk/actions/runs/45024340250) succeeded, and Phase 5 was in the base.

- The design primitives map approved semantic roles and type to Material 3. Phase 6B1 adopts them in the app shell, Beranda and Katalog. Existing route IDs, stack operations, tab restoration, secondary routes and existing test tags are retained.
- Beranda uses active catalog counts in the overview. There is no dedicated verified current-day read in this UI. It displays up to three entries from the existing sales history, in its existing order, with the original amount, payment method and status. It reuses the same pending and receipt navigation as Riwayat.
- Katalog searches and filters the loaded list locally. Product and category queries, selected category and list position are presentation state. The existing create, edit, validation and archive operations remain the source for mutations. Catalog creation stays above navigation and moves inline when the viewport is too short for the dock.
- The five destinations retain their order, 48 dp targets and selected state. Labels are measured before choosing one row or a 3+2 wrap. The expanded layout uses a labeled rail.
- Room, domain, posting, payment, settlement, corrections and portability code are outside the implementation diff. No dependency was added. [Skill queries and applied guidance](SKILL_EVIDENCE.md)

## Actual Android screenshots

Debug APK on the disposable API 28 AOSP emulator `emulator-5554` on 22 November 2026. The normal capture profile used 720 × 1440 px, 320 dpi, 360 × 720 dp and 100% font. The compact profile used the same native pixels at 360 dpi, 320 × 640 dp and font scale 2.0. System time zone was Asia/Bangkok (UTC+7). App copy and Rupiah formatting are Indonesian. Every image is an original full-window `UiAutomation.takeScreenshot()` capture.

| Capture | Review |
| --- | --- |
| [Beranda with catalog and recent sale](screenshots/beranda-360dp.png) | Cashier action, two active catalog counts, one recorded cash sale and five destinations. |
| [Beranda empty](screenshots/beranda-empty.png) | Honest no-data states and a cashier shortcut. |
| [Katalog populated](screenshots/katalog-populated-360dp.png) | Produk/Kategori, search/filter, four rows, exact Rupiah, edit affordances and docked creation. |
| [Katalog empty](screenshots/katalog-empty.png) | One prominent creation action. |
| [Katalog no search results](screenshots/katalog-no-results.png) | Visible query and explicit reset. |
| [Product editor](screenshots/katalog-edit.png) | Existing edit and archive workflow. The editable name scrolls in its single-line field. |
| [Katalog at 320 dp and 200%](screenshots/katalog-320dp-200pct.png) | Scrolled product list with the full long name, Rp999.999.999, visible creation action and five readable labels in 3+2. The native API 28 status-bar clock is partially clipped at this OS font setting. |

The populated fixture uses four synthetic products and two categories created through the existing use case. The Beranda sale was created through the existing checkout flow in Room, using two Teh melati dingin at Rp6.000 and Rp15.000 cash tendered. The draft and receipt IDs were assigned by the existing repository. The screenshot contains no merchant credentials or real transactions. Empty captures were made after clearing the disposable install. See `MerchantAppFlowTest` and its opt-in `captureMerchant` modes `empty`, `normal` and `compact`.

## Side-by-side visual review

The ZIP and individual JPEG SHA-256 digests matched every value in the [approved reference manifest](../phase-6/REFERENCE_MANIFEST.md). All four original JPEGs were visually opened. The paired views for Beranda and Katalog were also reviewed locally at `.local/phase-6b1-references/comparison-beranda.png` and `.local/phase-6b1-references/comparison-katalog.png`. Only the Android side of each pair is tracked.

| Approved target and inspected region | Android result | Reproduced | Intentional deviations and remaining visual differences |
| --- | --- | --- | --- |
| `01-Beranda.jpg`, blue merchant header and inset overview on both phones, activity on the second phone | [Beranda](screenshots/beranda-360dp.png) | Blue top region, rounded inset white overview, large readable counts and grouped activity below. | The solid approved navy replaces the source gradients. Cashier and catalog actions take priority. Activity reads existing sales as vertically grouped rows. One fixture sale leaves open space below. |
| `04-Katalog.jpg`, title and search, repeated white product rows and floating creation action | [Katalog](screenshots/katalog-populated-360dp.png) | A clear title with one contextual shortcut, quiet search and filter, rounded readable rows, exact price and strong creation action. | The approved product/category switch sits above search. Quiet icons and category names replace imagery and stock controls. The blue create action reserves its own width and height above navigation. Large text trades the category tile and normal density for name and price legibility. |
| `02-Kasir.jpg`, browse/cart/payment sequence | The existing route uses the shared shell | Checked the reference for its later redesign scope. | Cashier and checkout content are deferred to 6B2. |
| `03-Keuangan.jpg`, balance and activity regions | The existing route uses the shared shell | Checked the reference for its later redesign scope. | Finance content is deferred to 6B3. |

## Regression strategy

The full-window evidence is a review candidate. All checked-in golden PNG bytes were preserved. The pre-redesign Dashboard pixel assertion was replaced by a contract test for the cashier CTA, catalog overview and recent activity. Cashier and Cart retain their existing pixel assertions. Human visual review is required before establishing a new Dashboard golden.

The targeted Compose coverage exercises catalog states, combined product query and category selection, no-results recovery, navigational touch bounds and selected state at 320 dp/200%, long name and exact large price, Beranda routes and retry, and editing with activity recreation. Activity recreation exercises Android saved state and persistence. TalkBack and physical device checks require separate validation. The compact screenshot shows Android’s native system-clock truncation, outside the app content.
