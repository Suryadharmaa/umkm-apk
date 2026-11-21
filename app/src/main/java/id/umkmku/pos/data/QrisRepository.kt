package id.umkmku.pos.data

import androidx.room.withTransaction
import id.umkmku.pos.domain.CashCheckoutException
import id.umkmku.pos.domain.CashDraft
import id.umkmku.pos.domain.MAX_QRIS_IDR
import id.umkmku.pos.domain.PENDING_QRIS
import id.umkmku.pos.domain.QRIS
import id.umkmku.pos.domain.QrisAsset
import id.umkmku.pos.domain.QrisEvidenceInput
import id.umkmku.pos.domain.QrisReceipt
import id.umkmku.pos.domain.parseStaticQris
import id.umkmku.pos.domain.validate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.map

class QrisRepository @Inject constructor(
    private val db: CatalogDatabase,
    private val sales: CashSaleRepository,
) {
    private val dao = db.qrisDao()
    private val saleDao = db.cashSaleDao()

    fun activeProfile() = dao.observeActive().map { it?.profile() }
    fun profile(id: String) = dao.observeProfile(id).map { it?.profile() }

    suspend fun activate(asset: QrisAsset, issuerAttested: Boolean) {
        if (!issuerAttested) throw CashCheckoutException("Pastikan QRIS berasal dari penyedia pembayaran merchant.")
        val parsed = parseStaticQris(asset.payload)
        db.withTransaction {
            dao.deactivateActive()
            dao.insertProfile(QrisProfileRow(
                UUID.randomUUID().toString(), parsed.merchantId, parsed.merchantName,
                parsed.payload, asset.image, 1,
            ))
        }
    }

    suspend fun deactivate() = db.withTransaction { dao.deactivateActive() }

    suspend fun begin(expected: CashDraft, expectedProfileId: String) {
        db.withTransaction {
            val row = saleDao.sale(expected.id) ?: throw CashCheckoutException("Keranjang tidak ditemukan.")
            if (row.qrisOrder != null && row.quote() == expected && row.qrisOrder.profileId == expectedProfileId) {
                return@withTransaction
            }
            val active = dao.active()
            if (active?.id != expectedProfileId) throw CashCheckoutException("Profil QRIS berubah. Periksa kembali profil aktif.")
            val total = sales.validatedDraftTotal(row, expected)
            if (total > MAX_QRIS_IDR) throw CashCheckoutException("Batas pembayaran QRIS Rp10.000.000 per transaksi.")
            dao.insertOrder(QrisOrderRow(expected.id, active.id, total, System.currentTimeMillis(), PENDING_QRIS))
            check(saleDao.closeDraft(expected.id) == 1)
        }
    }

    suspend fun confirm(id: String, input: QrisEvidenceInput, zone: ZoneId = ZoneId.systemDefault()): QrisReceipt {
        val entered = input.validate(zone)
        return db.withTransaction {
            val row = saleDao.sale(id) ?: throw CashCheckoutException("Penjualan QRIS tidak ditemukan.")
            val order = row.qrisOrder ?: throw CashCheckoutException("Penjualan belum menunggu QRIS.")
            val profile = checkNotNull(dao.profile(order.profileId))
            val previous = row.receipt()
            if (previous != null) {
                if (previous is QrisReceipt && previous.reference == entered.reference &&
                    previous.observedAt == entered.observedAt && previous.draft.total == entered.amount
                ) return@withTransaction previous
                throw CashCheckoutException("Penjualan sudah tercatat dengan bukti pembayaran berbeda.")
            }
            check(order.status == PENDING_QRIS && row.sale.draftSlot == null && row.evidence == null)
            val draft = row.quote()
            check(draft.total == order.totalIdr)
            if (entered.amount != order.totalIdr) throw CashCheckoutException("Nominal bukti harus sama dengan total tagihan.")
            val now = System.currentTimeMillis()
            // The evidence form captures whole minutes in the device time zone.
            if (entered.observedAt / 60_000 !in order.startedAt / 60_000..now / 60_000) {
                throw CashCheckoutException("Waktu bukti tidak sesuai. Periksa waktu penerimaan di aplikasi merchant.")
            }
            if (dao.referenceUsed(profile.merchantId, entered.reference)) {
                throw CashCheckoutException("Nomor referensi sudah dipakai pada penjualan QRIS lain.")
            }
            dao.insertEvidence(QrisEvidenceRow(id, profile.merchantId, entered.reference, entered.amount, entered.observedAt))
            saleDao.postPayment(
                PaymentRow(id, "qris:$id", order.totalIdr, null, now, QRIS),
                "QRIS_CLEARING",
            )
            check(dao.markConfirmed(id) == 1)
            QrisReceipt(draft, now, entered.reference, entered.observedAt)
        }
    }
}
