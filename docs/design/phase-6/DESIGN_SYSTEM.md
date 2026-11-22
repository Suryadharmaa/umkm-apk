# Compose design contract

## Color and semantics

[Token data](TOKENS.json) is a review artifact. Map color roles to Material 3 `lightColorScheme` and state pairs to component semantic roles. The default stays the app's existing light theme. Dynamic and dark palettes are outside this visual approval; recheck pairs independently if added. No gradients, themed QR artwork or per-screen color drift.

| Role | Foreground on fill | Measured contrast |
| --- | --- | ---: |
| Primary CTA / white content on solid header | `#FFFFFF` on `#1454BD` / `#143A79` | 6.92 / 10.99 |
| Selected chip or settled payout | `#102A56` on `#DDE9FF` | 11.54 |
| Body / muted canvas label | `#17243A` on `#FFFFFF` / `#43536B` on `#F6F8FC` | 15.56 / 7.35 |
| Checkout amount | `#FFFFFF` on `#17243A` | 15.56 |
| Pending or unresolved | `#694500` on `#FFF1CC` | 7.63 |
| Manual confirmation / completed cash | `#12513A` on `#E2F4EA` | 8.11 |
| Refunded | `#8B2134` on `#FBE8EE` | 7.56 |
| Reversed | `#4D3C72` on `#EFE9F8` | 8.02 |
| Error | `#A51D2B` on `#FDEBED` | 6.51 |
| Outline boundary on white only | `#697992` on `#FFFFFF` | 4.42, above 3:1 non-text |

Ratios use sRGB relative luminance on opaque pairs, rounded to two decimals. Phase 6B verifies composited disabled, focus, scrim and pressed states. Use blue outline/fill indicator for focus and ensure ≥3:1 against the adjacent color. Standard text target ≥4.5:1, large text ≥3:1. Outline ink does not serve as ordinary text. A colored badge always carries text. Use **Menunggu pembayaran QRIS**, **QRIS dikonfirmasi manual**, **Pencairan tercatat**, **Sudah dikembalikan penuh**, or **Sudah dibalik** in the relevant contexts. Debit/Kredit use distinct words and original signed values; cash inflow/outflow and debit/credit are not interchangeable. A settled payout is a payout state, separate from the sale badge.

## Type, space and form

Native default sans (Roboto on standard Android) in Material `Typography`. Values are size/line in sp. Use 32/40 semibold for the primary amount, 24/32 semibold for screen titles, 20/28 semibold for section titles, 16/24 semibold for row titles, 14/20 regular body, 14/20 semibold actions and 12/16 metadata. Map to displaySmall, headlineSmall, titleLarge/titleMedium, bodyMedium and label roles. Apply `tnum` to all Rupiah/quantity columns, including totals, where the font supports it. Keep complete currency/grouping and signs, reflow between label and value before shrinking. Break a long amount between complete label/value rows or wrap at currency/group boundaries with the full TalkBack value. Critical data never ellipsizes. Body wraps naturally. Product rows allow two lines at normal scale, then disclose full name in detail or a wrapped row; at 200%, use variable height. No fixed typography scaling override.

Use 4 dp internal rhythm, 8 dp inter-item gap and ordinary horizontal target separation, 12 dp dense row padding, 16 dp compact page/card inset, 24 dp section gap and 32 dp expanded gutter. Row starts at 64 dp when it has stepper/actions. Inputs use 56 dp nominal height and grow with errors/fonts. Buttons and effective controls ≥48×48 dp, even with 24 dp vector symbols. 8 dp radius for small slots, 12 dp fields, 16 dp cards/actions and 24 dp sheets with 1 dp outline where a boundary conveys state. Flat 0 dp cards by default, 1 dp separating dock if required, 4 dp overlay elevation maximum. Use one Material symbol family and consistent 24 dp geometry. Decorative icons next to text have no duplicate semantic label.

## Components

| Component | Contract |
| --- | --- |
| KPI or account tile | Scope/period label, exact amount or explicit unknown, supporting label only when it clarifies the source. Readable without color or a chart. |
| Transaction item and status chip | Description, method, timestamp, amount and state. Chip labels reflow. Preserve link destination and provide shared signed value semantics. |
| Product or catalog row | Stable existing IDs in keyed lazy lists. Category icon/initial tile is decorative where text conveys identity. Stepper buttons announce product and add/remove actions; current quantity is separately readable. |
| Search and filter | Visible label, clear action, selected semantics and a keyboard-accessible single control when chips need wrapping. Filter local ready data only. |
| Amount input and method action | Persistent label, numeric keyboard for exact integer Rupiah, adjacent helper/error, original validation. Cash is primary, supported QRIS secondary in cart. |
| Bottom bar and receipt | Bar reserves measured height and navigation/IME insets. Reflow total and action when needed. Receipt keeps original snapshots and linked corrections. |
| Empty, error, loading | One scoped message and relevant existing action. Reserve space for final hierarchy, avoid fake values or flashed content. Announce update once via polite live region. |
| Dialog and sheet | Explicit action and cancel where allowed, focus on error summary or first invalid field, restore focus on dismiss. Keep secure password window. Use native UI for picker. |
| Destination control | Selected icon, label and visual indicator. At standard compact width one labeled row. At extreme text use a measured 3+2 two-row control preserving order and targets. A labeled navigation rail is acceptable at measured ≥600 dp or short landscape when it fits. Never display two primary navigation patterns at once. |

Use Android press feedback immediately. Optional short state crossfade 100–150 ms; native dialog transitions may use Material defaults. Follow reduced motion/system animation scaling by revealing final state without discretionary transitions. Transaction correctness and focus never wait on animations.
