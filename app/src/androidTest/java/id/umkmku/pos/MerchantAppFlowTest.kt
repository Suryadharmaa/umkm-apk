package id.umkmku.pos

import android.graphics.Bitmap
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import id.umkmku.pos.data.CashSaleRepository
import id.umkmku.pos.data.RoomCatalogRepository
import id.umkmku.pos.domain.EditCatalog
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MerchantAppFlowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val capture = InstrumentationRegistry.getArguments().getString("captureMerchant")
    private val compose = createAndroidComposeRule<MainActivity>()
    private val longName = "Paket nasi uduk ayam goreng sambal dan lalapan untuk keluarga"
    private val name = if (capture != null) longName else "Produk uji ${System.nanoTime()}"

    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            if (capture != "empty") seedCatalog()
        }

        override fun after() {
            if (capture == null) archiveTestProduct()
        }
    }).around(compose)

    @Test fun homeCatalogEditingAndRecreation() {
        if (capture == "empty") {
            awaitText("Belum ada produk")
            screenshot("beranda-empty")
            tab("catalog")
            awaitText("Produk aktif tampil di Kasir.")
            screenshot("katalog-empty")
            return
        }
        if (capture == "compact") {
            tab("catalog")
            awaitText("Cari produk")
            compose.onNodeWithTag("catalog-list").performScrollToIndex(2)
            awaitText(name)
            compose.onNodeWithText("Rp999.999.999").assertIsDisplayed()
            screenshot("katalog-320dp-200pct")
            return
        }
        awaitText("Produk aktif")
        if (capture == "normal") {
            awaitText("Tunai tercatat")
            screenshot("beranda-360dp")
        }
        click("Buka kasir")
        compose.onNodeWithTag("destination-cashier").assertIsSelected()
        tab("home")
        click("Kelola katalog")
        awaitText(name)
        if (capture == "normal") screenshot("katalog-populated-360dp")
        compose.onNodeWithText("Cari produk").performTextInput("TidakAdaHasil")
        dismissKeyboard()
        awaitText("Produk tidak ditemukan")
        if (capture == "normal") screenshot("katalog-no-results")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("destination-catalog").assertIsSelected()
        awaitText("Produk tidak ditemukan")
        click("Bersihkan filter")
        click(name)
        awaitText("Edit produk")
        if (capture == "normal") screenshot("katalog-edit")
        val updated = if (capture == "normal") "Paket nasi uduk keluarga" else "$name edit"
        compose.onNodeWithText("Nama produk").performTextClearance()
        compose.onNodeWithText("Nama produk").performTextInput(updated)
        click("Simpan")
        awaitText(updated)
        compose.activityRule.scenario.recreate()
        awaitText(updated)
        tab("history")
        tab("finance")
        tab("home")
    }

    private fun seedCatalog() = runBlocking {
        val db = CatalogModule.database(instrumentation.targetContext)
        try {
            val repository = RoomCatalogRepository(db)
            val edit = EditCatalog(repository)
            if (capture == "normal" || capture == "compact") {
                edit.category(null, "Makanan")
                edit.category(null, "Minuman")
                val categories = repository.observe().first().categories.associate { category -> category.name to category.id }
                edit.product(null, name, "999999999", categories["Makanan"])
                edit.product(null, "Roti bakar cokelat", "18000", categories["Makanan"])
                edit.product(null, "Susu jahe hangat", "12000", categories["Minuman"])
                edit.product(null, "Teh melati dingin", "6000", categories["Minuman"])
                if (capture == "normal") {
                    val tea = repository.observe().first().products.first { product -> product.name == "Teh melati dingin" }
                    val cash = CashSaleRepository(db)
                    repeat(2) { cash.changeQuantity(tea.id, 1) }
                    val draft = checkNotNull(cash.observeDraft().first())
                    cash.post(draft, 15000)
                }
            } else {
                edit.product(null, name, "999999999", null)
            }
        } finally {
            db.close()
        }
    }

    private fun archiveTestProduct() = runBlocking {
        val db = CatalogModule.database(instrumentation.targetContext)
        try {
            val repository = RoomCatalogRepository(db)
            val product = repository.observe().first().products.firstOrNull { product ->
                product.name == name || product.name == "$name edit"
            }
            if (product != null) EditCatalog(repository).archiveProduct(product.id)
        } finally {
            db.close()
        }
    }

    private fun tab(route: String) {
        compose.onNodeWithTag("destination-$route").performClick().assertIsSelected()
    }

    private fun click(label: String) {
        awaitText(label)
        compose.onNodeWithText(label).performClick()
    }

    private fun awaitText(label: String) {
        compose.waitUntil(5000) { compose.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(label).assertIsDisplayed()
    }

    private fun dismissKeyboard() {
        compose.activityRule.scenario.onActivity { activity ->
            val ime = activity.getSystemService(InputMethodManager::class.java)
            ime.hideSoftInputFromWindow(activity.window.decorView.windowToken, 0)
        }
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val directory = File(checkNotNull(instrumentation.targetContext.getExternalFilesDir(null)), "phase-6b1")
        check(directory.exists() || directory.mkdirs())
        File(directory, "$name.png").outputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }
}
