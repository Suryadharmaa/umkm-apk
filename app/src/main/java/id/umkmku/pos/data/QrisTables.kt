package id.umkmku.pos.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import id.umkmku.pos.domain.QrisAsset
import id.umkmku.pos.domain.QrisProfile
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "qris_profile", indices = [Index(value = ["activeSlot"], unique = true)])
data class QrisProfileRow(
    @PrimaryKey val id: String,
    val merchantId: String,
    val merchantName: String,
    val payload: String,
    val image: ByteArray?,
    val activeSlot: Int?,
) {
    internal fun profile() = QrisProfile(id, QrisAsset(payload, merchantName, merchantId, image))
}

@Entity(
    tableName = "qris_order",
    indices = [Index("profileId")],
    foreignKeys = [
        ForeignKey(entity = SaleRow::class, parentColumns = ["id"], childColumns = ["saleId"]),
        ForeignKey(entity = QrisProfileRow::class, parentColumns = ["id"], childColumns = ["profileId"]),
    ],
)
data class QrisOrderRow(
    @PrimaryKey val saleId: String,
    val profileId: String,
    val totalIdr: Long,
    val startedAt: Long,
    val status: String,
)

@Entity(
    tableName = "qris_evidence",
    indices = [Index(value = ["merchantId", "reference"], unique = true)],
    foreignKeys = [ForeignKey(entity = QrisOrderRow::class, parentColumns = ["saleId"], childColumns = ["saleId"])],
)
data class QrisEvidenceRow(
    @PrimaryKey val saleId: String,
    val merchantId: String,
    val reference: String,
    val observedIdr: Long,
    val observedAt: Long,
)

@Dao
interface QrisDao {
    @Query("SELECT * FROM qris_profile WHERE activeSlot = 1")
    fun observeActive(): Flow<QrisProfileRow?>

    @Query("SELECT * FROM qris_profile WHERE id = :id")
    fun observeProfile(id: String): Flow<QrisProfileRow?>

    @Query("SELECT * FROM qris_profile WHERE activeSlot = 1")
    suspend fun active(): QrisProfileRow?

    @Query("SELECT * FROM qris_profile WHERE id = :id")
    suspend fun profile(id: String): QrisProfileRow?

    @Query("UPDATE qris_profile SET activeSlot = NULL WHERE activeSlot = 1")
    suspend fun deactivateActive()

    @Query("SELECT EXISTS(SELECT 1 FROM qris_evidence WHERE merchantId = :merchantId AND reference = :reference)")
    suspend fun referenceUsed(merchantId: String, reference: String): Boolean

    @Query("UPDATE qris_order SET status = 'MANUALLY_CONFIRMED' WHERE saleId = :id AND status = 'PENDING_QRIS'")
    suspend fun markConfirmed(id: String): Int

    @Insert suspend fun insertProfile(row: QrisProfileRow)
    @Insert suspend fun insertOrder(row: QrisOrderRow)
    @Insert suspend fun insertEvidence(row: QrisEvidenceRow)
}
