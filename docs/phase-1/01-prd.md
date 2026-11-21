# Product requirements

**Audience:** An owner or trusted cashier at a single Indonesian outlet using one shared Android device. The owner also records expenses, reconciles settlements, and makes backups. Launch UI uses Bahasa Indonesia, IDR, and the outlet’s configured Indonesian time zone. Assume a trusted operator with device-level access controls and no separate staff identity in v1.

## MVP

| Area | User story and product outcome |
|---|---|
| Catalog | I can create, rename, and archive categories and products, change prices, and find active products during checkout. Archived categories require reassignment or uncategorization of active products. Prior receipts keep their original details. |
| Cart and cash | I can add items, change quantities, review the amount due, enter cash received, see change, and save a receipt. One sale uses one payment method. |
| Static QRIS | I can import an existing merchant-issued QR image and show it with the exact amount due. I check the incoming transaction in the merchant/PJP channel and record the matched payment myself. Pending items remain visible and recoverable. |
| History | I can filter sales and payments by date, method, and status, and inspect the receipt, manual confirmation evidence, and any full reversal. |
| Cashbook | I can record opening funds, other income, operating expenses, and owner contributions or withdrawals. I can see recorded physical cash, unsettled QRIS, and recorded bank funds separately. |
| Settlement and reports | I can match a provider payout to confirmed QRIS payments, enter the actual fee, and inspect sales, refunds, other income, expenses, and account movements for a selected period. |
| Export and backup | I can export CSV reports and save an encrypted local backup through Android’s document picker. I can validate and restore into an empty installation with an explicit confirmation. |

First use: name the shop and confirm its time zone → add a product → add it to cart → enter sufficient cash → see the committed receipt. QRIS setup is optional, so a merchant can start taking cash immediately.

## Financial views

- Sales show gross completed sale value, full reversals in their own occurrence period, and net sales after reversals. Show cash/QRIS breakdown and pending QRIS count separately. Use the outlet time zone and half-open reporting intervals. Persist UTC timestamps and capture outlet business date at posting. Fix the outlet zone after first posting in v1.
- The cashbook shows opening plus signed posted movements by account. Label these as recorded balances, with initial balance unknown until explicitly opened. Show unsettled QRIS as a receivable. Bank represents recorded business movements only. Flag negative balances for reconciliation.
- Show operating expenses and settlement fees separately. Display other income and owner movements in their own lines. Exclude payouts and internal transfers from sales and income totals. Omit profit metrics because the MVP does not capture complete COGS, inventory valuation, or accruals.

## Scope and outcomes

Target one outlet, one device, whole-Rupiah prices, whole-item quantities, and Android 8+ provisionally. Offline checkout, reporting, and backup creation must work after install without sign-in. QRIS acceptance and refunds still depend on the external merchant channel. A provider-issued static QR image does not authenticate a payment. The cashier must match merchant account, amount, transaction time, and unique reference against a successful credit in that channel. [Bank Indonesia QRIS guidance](https://www.bi.go.id/id/fungsi-utama/sistem-pembayaran/ritel/kanal-layanan/qris/default.aspx) · [Bank Indonesia verification guidance](https://www.bi.go.id/id/publikasi/ruang-media/cerita-bi/Pages/cara-memastikan-pembayaran-qris-benar-masuk.aspx)

Later scope includes inventory costing, split payments, partial refunds and settlements, multi-device sync, fiscal integrations, and automatic verification via an authorized provider. Pricing is undecided and outside MVP. The Phase 2 release gate requires the acceptance matrix to pass and an installable APK to be smoke-tested on a target low-end device.

