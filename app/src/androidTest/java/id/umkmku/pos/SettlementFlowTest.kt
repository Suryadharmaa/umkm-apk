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
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import id.umkmku.pos.data.CashSaleRepository
import id.umkmku.pos.data.CatalogDatabase
import id.umkmku.pos.data.QrisRepository
import id.umkmku.pos.data.RoomCatalogRepository
import id.umkmku.pos.domain.EditCatalog
import id.umkmku.pos.domain.QrisEvidenceInput
import id.umkmku.pos.domain.parseStaticQris
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettlementFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun savesDraftThenReconcilesAnUnresolvedPayout() {
        val capture = InstrumentationRegistry.getArguments().getString("captureSettlement") == "true"
        val suffix = if (capture) "01" else System.nanoTime().toString().takeLast(10)
        val paymentReference = "FIXTURE_PAYMENT_$suffix"
        val reference = "FIXTURE_PAYOUT_$suffix"
        seed(paymentReference)

        click("Keuangan")
        click("Pencairan QRIS")
        click("Tambah pencairan")
        field("$paymentReference · Rp15.000").performClick()
        input("Referensi pencairan", reference)
        hideKeyboard()
        field("Catat pencairan").assertIsNotEnabled()
        field("Simpan untuk ditinjau").performClick()
        await("Menunggu bukti")
        compose.onNodeWithText("Menunggu bukti").assertIsDisplayed()
        if (capture) screenshot("menunggu-bukti")
        compose.activityRule.scenario.recreate()
        field("Bruto pilihan")
        await("Menunggu bukti")

        input("Kode penyedia", "INVALID.TEST")
        input("NMID pada laporan", "FIXTURE_ONLY")
        input("Bruto laporan (Rp)", "15000")
        input("Biaya aktual (Rp)", "1000")
        input("Kredit aktual bank (Rp)", "13000")
        input("Waktu pencairan", fixtureMerchantTime())
        hideKeyboard()
        field("Saya sudah mencocokkan laporan penyedia dengan mutasi rekening bank usaha.").performClick()
        field("Catat pencairan").performClick()
        await("Belum cocok")
        compose.onNodeWithText("Belum cocok").assertIsDisplayed()
        if (capture) screenshot("belum-cocok")
        val net = field("Kredit aktual bank (Rp)")
        net.performTextClearance()
        net.performTextInput("14000")
        hideKeyboard()
        field("Catat pencairan").assertIsNotEnabled()
        field("Saya sudah mencocokkan laporan penyedia dengan mutasi rekening bank usaha.").performClick()
        field("Catat pencairan").performClick()
        await("Pencairan tercatat")
        compose.onNodeWithText("Bruto · Rp15.000").assertIsDisplayed()
        compose.onNodeWithText("Biaya aktual · Rp1.000").assertIsDisplayed()
        compose.onNodeWithText("Kredit bank · Rp14.000").assertIsDisplayed()
        if (capture) screenshot("pencairan-tercatat")
        compose.onNodeWithContentDescription("Kembali").performClick()
        click(reference)
        await("Pencairan tercatat")
        compose.onNodeWithContentDescription("Kembali").performClick()
        if (capture) {
            click("Tambah pencairan")
            field("Simpan untuk ditinjau").performClick()
            await("Menunggu bukti")
            compose.onNodeWithContentDescription("Kembali").performClick()
            await(reference)
            screenshot("riwayat-pencairan")
        }
    }

    private fun seed(reference: String) = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.databaseBuilder(context, CatalogDatabase::class.java, "umkm-catalog.db").build()
        try {
            val catalog = RoomCatalogRepository(db)
            val sales = CashSaleRepository(db)
            val qris = QrisRepository(db, sales)
            val name = "Barang $reference"
            EditCatalog(catalog).product(null, name, "15000", null)
            sales.changeQuantity(catalog.observe().first().products.single { it.name == name }.id, 1)
            val draft = checkNotNull(sales.observeDraft().first())
            qris.activate(parseStaticQris(fixturePayload()), true)
            qris.begin(draft, checkNotNull(qris.activeProfile().first()).id)
            qris.confirm(draft.id, QrisEvidenceInput("15000", reference, fixtureMerchantTime(), true))
        } finally {
            db.close()
        }
    }

    private fun field(label: String): androidx.compose.ui.test.SemanticsNodeInteraction {
        compose.onNodeWithTag("settlement-form").performScrollToNode(hasText(label))
        return compose.onNodeWithText(label).performScrollTo()
    }

    private fun input(label: String, value: String) = field(label).performTextInput(value)
    private fun click(label: String) { await(label); compose.onNodeWithText(label).performClick() }

    private fun await(label: String) {
        compose.waitUntil(10_000) { compose.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun hideKeyboard() {
        compose.activityRule.scenario.onActivity {
            it.getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(it.window.decorView.windowToken, 0)
        }
        compose.waitForIdle()
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
