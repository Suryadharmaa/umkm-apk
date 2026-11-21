package id.umkmku.pos

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CashCheckoutFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun acceptsRevisedPriceAndShowsDurableReceiptInHistory() {
        val capture = InstrumentationRegistry.getArguments().getString("captureCash") == "true"
        val product = if (capture) "Paket nasi uduk" else "Uji nasi uduk ${System.nanoTime().toString().takeLast(7)}"
        click("Katalog")
        click("Tambah produk")
        compose.onNodeWithText("Nama produk").performTextInput(product)
        compose.onNodeWithText("Harga (Rp)").performTextInput("15000")
        click("Simpan")
        await(product)
        click("Kasir")
        compose.onNodeWithContentDescription("Tambah $product").performClick()
        await("1 item")
        click("Katalog")
        click(product)
        val price = compose.onNodeWithText("Harga (Rp)")
        price.performTextClearance()
        price.performTextInput("16000")
        click("Simpan")
        await("Tambah produk")
        click("Kasir")
        click("1 item")
        click("Bayar tunai")
        await("Periksa perubahan produk")
        if (capture) capture("periksa-harga")
        click("Setujui perubahan")
        await("Rp16.000")
        val tender = compose.onNodeWithText("Uang diterima (Rp)")
        tender.performTextInput("15000")
        compose.onNodeWithText("Kurang Rp1.000").assertIsDisplayed()
        compose.onNodeWithText("Catat pembayaran tunai").assertIsNotEnabled()
        tender.performTextClearance()
        tender.performTextInput("20000")
        compose.onNodeWithText("Kembalian Rp4.000").assertIsDisplayed()
        if (capture) {
            compose.activityRule.scenario.onActivity { activity ->
                val ime = activity.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                ime.hideSoftInputFromWindow(activity.window.decorView.windowToken, 0)
            }
            capture("pembayaran-tunai")
        }
        click("Catat pembayaran tunai")
        await("Pembayaran tunai tercatat")
        compose.onNodeWithText("Kas masuk").assertIsDisplayed()
        compose.onNodeWithText("Rp4.000").assertIsDisplayed()
        if (capture) capture("struk-tunai")
        click("Lihat riwayat")
        await(product)
        if (capture) capture("riwayat-penjualan")
        click(product)
        compose.onNodeWithText("Pembayaran tunai tercatat").assertIsDisplayed()
        compose.onNodeWithText(product).assertIsDisplayed()
    }

    private fun click(text: String) {
        await(text)
        compose.onNodeWithText(text).performClick()
    }

    private fun await(text: String) {
        compose.waitUntil(10_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val directory = File(checkNotNull(instrumentation.targetContext.getExternalFilesDir(null)), "screenshots")
        check(directory.exists() || directory.mkdirs())
        File(directory, "$name.png").outputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }
}
