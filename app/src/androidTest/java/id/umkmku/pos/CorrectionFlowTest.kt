package id.umkmku.pos

import android.graphics.Bitmap
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import id.umkmku.pos.data.CashSaleRepository
import id.umkmku.pos.data.CatalogDatabase
import id.umkmku.pos.data.RoomCatalogRepository
import id.umkmku.pos.domain.EditCatalog
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CorrectionFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun recordsCashRefundThenShowsOriginalReceiptAndReport() {
        val capture = InstrumentationRegistry.getArguments().getString("captureCorrections") == "true"
        val name = if (capture) "Produk uji · refund" else "Produk uji refund ${System.nanoTime().toString().takeLast(9)}"
        seed(name)
        click("Riwayat")
        click(name)
        await("Pembayaran tunai tercatat")
        click("Catat refund penuh")
        await("Sumber dana · Kas tunai")
        field("Catat pengembalian").assertIsNotEnabled()
        field("Alasan").performTextInput("Pesanan uji dikembalikan")
        field("Nominal pengembalian aktual (Rp)").performTextInput("15000")
        compose.activityRule.scenario.onActivity {
            it.getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(it.window.decorView.windowToken, 0)
        }
        field("Saya sudah menyerahkan uang tunai penuh kepada pembeli.").performClick()
        if (capture) screenshot("form-refund-tunai")
        field("Catat pengembalian").performClick()
        await("Pengembalian dana")
        if (capture) screenshot("jurnal-refund-tunai")
        compose.onNodeWithContentDescription("Kembali").performClick()
        await("Sudah dikembalikan penuh")
        compose.onNodeWithText(name).assertIsDisplayed()
        if (capture) screenshot("struk-asli")
        compose.onNodeWithContentDescription("Kembali").performClick()
        click("Keuangan")
        click("Laporan")
        await("Penjualan bruto")
        compose.onNodeWithText("Penjualan bersih").assertIsDisplayed()
        if (capture) screenshot("laporan-harian")
        click("Bulanan")
        compose.onNodeWithTag("financial-report").performScrollToNode(hasText("Saldo pada akhir periode"))
        compose.onNodeWithText("Saldo pada akhir periode").performScrollTo().assertIsDisplayed()
        if (capture) screenshot("laporan-saldo")
    }

    private fun seed(name: String) = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.databaseBuilder(context, CatalogDatabase::class.java, "umkm-catalog.db").build()
        try {
            val catalog = RoomCatalogRepository(db)
            val sales = CashSaleRepository(db)
            EditCatalog(catalog).product(null, name, "15000", null)
            sales.changeQuantity(catalog.observe().first().products.single { it.name == name }.id, 1)
            val draft = checkNotNull(sales.observeDraft().first())
            sales.post(draft, draft.total)
        } finally {
            db.close()
        }
    }

    private fun field(label: String) = compose.onNodeWithText(label).performScrollTo()
    private fun click(label: String) { await(label); compose.onNodeWithText(label).performClick() }

    private fun await(label: String) {
        compose.waitUntil(12_000) { compose.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val folder = File(checkNotNull(instrumentation.targetContext.getExternalFilesDir(null)), "screenshots")
        check(folder.exists() || folder.mkdirs())
        File(folder, "$name.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }
}
