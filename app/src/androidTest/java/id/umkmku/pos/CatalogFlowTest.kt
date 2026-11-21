package id.umkmku.pos

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CatalogFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun createsCatalogAndAddsItemToDraft() {
        val capture = InstrumentationRegistry.getArguments().getString("captureApp") == "true"
        val suffix = System.nanoTime().toString().takeLast(7)
        val category = if (capture) "Minuman" else "Uji Minuman $suffix"
        val product = if (capture) "Es teh manis" else "Uji Es Teh $suffix"
        if (capture) {
            awaitText("Belum ada produk")
            screenshot("beranda-kosong")
        }
        click("Katalog")
        click("Kategori")
        click("Tambah kategori")
        click("Simpan")
        compose.onNodeWithText("Nama harus berisi 1–80 karakter.").assertIsDisplayed()
        compose.onNodeWithText("Nama kategori").performTextInput(category)
        click("Simpan")
        awaitText(category)
        click("Produk")
        click("Tambah produk")
        compose.onNodeWithText("Nama produk").performTextInput(product)
        compose.onNodeWithText("Harga (Rp)").performTextInput("6000")
        click("Tanpa kategori")
        click(category)
        click("Simpan")
        awaitText(product)
        if (capture) {
            click("Beranda")
            awaitText("Produk aktif")
            screenshot("beranda-katalog")
        }
        click("Kasir")
        compose.onNodeWithText("Cari produk").performTextInput(product)
        click("Tambah")
        if (capture) screenshot("kasir-keranjang")
        click("1 item")
        compose.onNodeWithText("Total keranjang").assertIsDisplayed()
        compose.onAllNodesWithText("Rp6.000").assertCountEquals(2)
        if (capture) {
            screenshot("keranjang")
            return
        }
        // Remove the exercise item and category from active catalog.
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        click("Katalog")
        click(product)
        click("Arsipkan produk")
        click("Arsipkan")
        awaitText("Tambah produk")
        click("Kategori")
        click(category)
        click("Arsipkan kategori")
        click("Arsipkan")
        awaitText("Tambah kategori")
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val directory = File(checkNotNull(instrumentation.targetContext.getExternalFilesDir(null)), "screenshots")
        check(directory.exists() || directory.mkdirs())
        File(directory, "$name.png").outputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }

    private fun awaitText(label: String) {
        compose.waitUntil(5000) { compose.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun click(label: String) {
        awaitText(label)
        compose.onNodeWithText(label).performClick()
    }
}
