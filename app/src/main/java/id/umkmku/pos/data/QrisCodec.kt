package id.umkmku.pos.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.EncodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.multi.qrcode.QRCodeMultiReader
import com.google.zxing.qrcode.QRCodeWriter
import dagger.hilt.android.qualifiers.ApplicationContext
import id.umkmku.pos.domain.CashCheckoutException
import id.umkmku.pos.domain.QrisAsset
import id.umkmku.pos.domain.parseStaticQris
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MAX_IMAGE_BYTES = 768 * 1024
private const val MAX_IMAGE_PIXELS = 4_000_000L

class QrisCodec @Inject constructor(@param:ApplicationContext private val context: Context) {
    suspend fun importImage(uri: Uri): QrisAsset = withContext(Dispatchers.IO) {
        val raw = context.contentResolver.openInputStream(uri)?.use { stream ->
            val data = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var size = stream.read(buffer)
            while (size != -1) {
                if (data.size() + size > MAX_IMAGE_BYTES) invalidImage()
                data.write(buffer, 0, size)
                size = stream.read(buffer)
            }
            data.toByteArray()
        } ?: invalidImage()
        decodeQrisImage(raw)
    }
}

internal fun decodeQrisImage(raw: ByteArray): QrisAsset {
    if (raw.isEmpty() || raw.size > MAX_IMAGE_BYTES) invalidImage()
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
    if (bounds.outMimeType !in setOf("image/png", "image/jpeg") ||
        bounds.outWidth <= 0 || bounds.outHeight <= 0 ||
        bounds.outWidth.toLong() * bounds.outHeight > MAX_IMAGE_PIXELS
    ) invalidImage()

    val bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.size) ?: invalidImage()
    val decoded = try {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
        QRCodeMultiReader().decodeMultiple(BinaryBitmap(HybridBinarizer(source))).singleOrNull()?.text
            ?: invalidImage()
    } catch (_: ReaderException) {
        invalidImage()
    } finally {
        bitmap.recycle()
    }
    return parseStaticQris(decoded).copy(image = raw)
}

internal fun qrisBitmap(asset: QrisAsset): Bitmap {
    // Render only the validated payload. Other artwork or codes in an imported image are never displayed.
    val qr = QRCodeWriter().encode(
        asset.payload, BarcodeFormat.QR_CODE, 512, 512, mapOf(EncodeHintType.CHARACTER_SET to "UTF-8"),
    )
    val pixels = IntArray(qr.width * qr.height) { index ->
        if (qr[index % qr.width, index / qr.width]) Color.BLACK else Color.WHITE
    }
    return Bitmap.createBitmap(pixels, qr.width, qr.height, Bitmap.Config.ARGB_8888)
}

private fun invalidImage(): Nothing = throw CashCheckoutException(
    "Gambar tidak terbaca. Gunakan PNG atau JPG QRIS yang jelas, hingga 768 KB dan 4 megapiksel."
)
