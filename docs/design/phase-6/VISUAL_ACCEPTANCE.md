# Visual acceptance and evidence plan

The four supplied images guide hierarchy, density, type weight and CTA placement. Compare corresponding content regions side by side. Emulator goldens are captured from stable, synthetic UMKM-KU fixtures after implementation and human approval. Prior [app baselines](../../phase-2/testing.md) remain the regression references during 6A. No 6A redesign render or updated golden is part of this PR.

## Device and accessibility matrix

| Run | Required evidence |
| --- | --- |
| API 28 portrait legacy | 720×1440 at 320 dpi (360×720 dp), 100% font. Existing screenshot gates retain their recorded fixture/tolerance. Capture prospective redesign only in its implementation PR. |
| Compact | 320 dp portrait at 100% and 200%, including keyboard-open cash/proof and long labels. Capture and verify all actions with TalkBack or semantic bounds. |
| Standard and large phone | 360 dp and ≥412 dp portrait, plus short landscape checkout with IME. Check gesture and button system bars. |
| Larger layout | ≥600 dp tablet portrait/landscape, 100% and selected 200% forms. Check rail/gutters and independently scrollable main content. |
| Assistive and resilience | TalkBack reading, keyboard/D-pad traversal, disabled/selected state, high text scale, reduced motion, offline, insets, picker denial/cancel and process recreation. |

Common gates: 48 dp effective touch bounds, ≥4.5:1 normal text and ≥3:1 large text/meaningful UI. Verify all signed values and statuses by comparing visible labels to fixture sources. Under IME the focused field and helper remain visible and the associated action remains reachable. At 320 dp/200%, the five destination labels remain readable and actionable without shrinking text or scrolling horizontally. Display busy/disabled and retry states using supported actions only.

## Capture checklist

| Family | Capture pairs or sequences | Gate |
| --- | --- | --- |
| Beranda | ready, genuine no data, loading and failure | First fold at 360 dp includes cashier CTA and source-labeled overview. Recent pending and refunded remain identifiable. No placeholder chart or approximate amounts. |
| Kasir / cart | populated, no match, empty, count 0 and >0, archived/stale and price re-review; long 80-character name | Controls readable and target-safe at 320 dp/200%. Search/category survive supported back path. Bottom total matches visible quantities and final item clears dock. |
| Cash | insufficient, exact/zero change, large amount, busy and receipt; keyboard open/closed | One confirmation, no success before commit; no clipping of grouping or signs. Button does not cover IME. |
| QRIS & profile | absent profile, synthetic preview, pending, mismatched evidence, manual confirmed, pending after restart | QR code renders in unchanged square/quiet zone. Operator confirmation and actual evidence remain distinct from payment display. |
| Katalog | populated lists and saved edit paths, zero results, category editor, archive confirm and error | 3 standard rows visible at 360 dp/100% and dock leaves last row visible on scroll. Archived ID outcome is explicit. |
| Riwayat / receipt | pending/confirmed/refunded and cash rows, original receipt then linked journal | Pending link reopens saved order. Receipt values never rewritten by correction. Clear status at 200%. |
| Keuangan/report | unknown opening, explicit Rp0, clearing, signed balance, daily/monthly zone controls, empty, large values; expense/capital/transfer | Current vs period scope clear. Fees, expense and returns distinct; transfers/clearing stay separate. |
| Settlement/correction | pending or unresolved draft, posted and reversed payouts; cash/refund evidence mismatches; eligible and blocked reversals | Gross/fee/net, funding account and linkage visible. Blocked/ineligible never appear posted. |
| Kelola data | task groups, success, denied/cancel, invalid backup, populated-target block and verified reopen | Use synthetic data. Exclude secure password windows from captures. Picker and verified outcomes distinguish activity from success. |

In implementation PRs record commit, APK/config, exact device/API, viewport/density, locale/zone, font scale, fixture IDs, date and expected status. Attach original app captures only, then compare privately against the matching reference region. Require reviewer acknowledgement of intentional changes before replacing a golden. Run the two [Gradle gates](../../phase-2/testing.md), Compose flows and PR CI. A missing device, TalkBack run, QR scan, or state remains unverified, even with an aesthetically acceptable screenshot.
