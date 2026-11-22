package id.umkmku.pos

import android.graphics.Bitmap
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QrisFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun persistsPendingAndRequiresExactMerchantEvidence() {
        val capture = InstrumentationRegistry.getArguments().getString("captureQris") == "true"
        val product = if (capture) "Produk fixture QRIS" else "Uji QRIS ${System.nanoTime().toString().takeLast(7)}"
        val reference = if (capture) "FIXTURE_ONLY_UI" else "FIXTURE_ONLY_${System.nanoTime()}"
        click("Katalog")
        click("Profil QRIS")
        compose.onNodeWithText("Payload QRIS").performTextInput(fixturePayload())
        click("Periksa payload", scroll = true)
        await("Aktifkan profil")
        compose.onNodeWithText("Aktifkan profil").assertIsNotEnabled()
        click("Saya sudah mencocokkan profil ini dengan QRIS resmi dari penyedia pembayaran merchant.", scroll = true)
        click("Aktifkan profil", scroll = true)
        await("Nonaktifkan profil")
        compose.onNodeWithContentDescription("Kembali").performClick()

        click("Tambah produk")
        compose.onNodeWithText("Nama produk").performTextInput(product)
        compose.onNodeWithText("Harga (Rp)").performTextInput("15000")
        click("Simpan")
        await(product)
        addPending(product)
        compose.onNodeWithText("Rp15.000").assertIsDisplayed()
        compose.onNodeWithContentDescription("Kode QRIS merchant").assertIsDisplayed()
        if (capture) screenshot("qris-menunggu")

        click("Kembali ke kasir", scroll = true)
        click("Riwayat")
        click(product)
        await("Menunggu pembayaran QRIS")
        compose.activityRule.scenario.recreate()
        await("Menunggu pembayaran QRIS")
        click("Periksa bukti merchant", scroll = true)
        await("Catat konfirmasi manual")
        compose.onNodeWithText("Catat konfirmasi manual").assertIsNotEnabled()
        if (capture) screenshot("bukti-merchant")

        input("Nominal diterima (Rp)", "14000")
        input("Nomor referensi merchant", reference)
        input("Waktu transaksi merchant", fixtureMerchantTime())
        hideKeyboard()
        click("Saya melihat pembayaran berhasil pada aplikasi penerima dan telah mencocokkan identitas merchant.", scroll = true)
        click("Catat konfirmasi manual", scroll = true)
        await("Nominal bukti harus sama dengan total tagihan.")

        val amount = compose.onNodeWithText("Nominal diterima (Rp)").performScrollTo()
        amount.performTextClearance()
        amount.performTextInput("15000")
        hideKeyboard()
        click("Catat konfirmasi manual", scroll = true)
        await("QRIS dikonfirmasi manual")
        compose.onNodeWithText("QRIS belum cair").assertIsDisplayed()
        if (capture) screenshot("qris-konfirmasi-manual")
        click("Lihat riwayat")

        if (capture) {
            addPending(product)
            click("Kembali ke kasir", scroll = true)
            click("Riwayat")
            await("QRIS menunggu pembayaran", substring = true)
            await("QRIS dikonfirmasi manual", substring = true)
            screenshot("riwayat-qris")
        } else {
            click(product)
            compose.onNodeWithText("QRIS dikonfirmasi manual").assertIsDisplayed()
        }
    }

    private fun addPending(product: String) {
        click("Kasir")
        compose.onNodeWithContentDescription("Tambah $product").performClick()
        click("1 item")
        click("Bayar QRIS")
        // The checkout does a background price review before enabling this action.
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Tampilkan QRIS").fetchSemanticsNodes().any {
                !it.config.contains(SemanticsProperties.Disabled)
            }
        }
        click("Tampilkan QRIS", scroll = true)
        await("Menunggu pembayaran QRIS")
        compose.waitUntil(10_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasContentDescription("Kode QRIS merchant"))
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun input(label: String, text: String) {
        compose.onNodeWithText(label).performScrollTo().performTextInput(text)
    }

    private fun hideKeyboard() {
        compose.activityRule.scenario.onActivity { activity ->
            activity.getSystemService(InputMethodManager::class.java)
                .hideSoftInputFromWindow(activity.window.decorView.windowToken, 0)
        }
        compose.waitForIdle()
    }

    private fun click(text: String, scroll: Boolean = false) {
        await(text)
        val node = compose.onNodeWithText(text)
        if (scroll) node.performScrollTo()
        node.performClick()
    }

    private fun await(text: String, substring: Boolean = false) {
        compose.waitUntil(10_000) { compose.onAllNodesWithText(text, substring).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val root = File(checkNotNull(instrumentation.targetContext.getExternalFilesDir(null)), "screenshots")
        check(root.exists() || root.mkdirs())
        File(root, "$name.png").outputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }
}
