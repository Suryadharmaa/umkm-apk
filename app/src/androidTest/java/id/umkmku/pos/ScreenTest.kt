package id.umkmku.pos

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.onNodeWithContentDescription
import id.umkmku.pos.domain.CartLine
import id.umkmku.pos.ui.CartScreen
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import id.umkmku.pos.domain.Catalog
import id.umkmku.pos.domain.Category
import id.umkmku.pos.domain.Product
import id.umkmku.pos.feature.CatalogState
import id.umkmku.pos.ui.Cashier
import id.umkmku.pos.ui.Dashboard
import id.umkmku.pos.ui.WarungTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScreenTest {
    @get:Rule val compose = createComposeRule()
    private val fixture = Catalog(
        products = listOf(Product("tea", "Es teh manis", 6000, "drinks"), Product("rice", "Nasi uduk", 15000, null)),
        categories = listOf(Category("drinks", "Minuman")),
    )

    @Test fun dashboardTransitionsAndRetries() {
        val state = mutableStateOf<CatalogState>(CatalogState.Loading)
        compose.setContent { WarungTheme { Dashboard(state.value, {}, {}, { state.value = CatalogState.Ready(fixture) }) } }
        compose.onNodeWithText("Memuat katalog…").assertIsDisplayed()
        compose.runOnIdle { state.value = CatalogState.Error }
        compose.onNodeWithText("Coba lagi").performClick()
        compose.onNodeWithText("Produk aktif").assertIsDisplayed()
        compose.runOnIdle { state.value = CatalogState.Ready(Catalog()) }
        compose.onNodeWithText("Belum ada produk").assertIsDisplayed()
    }

    @Test fun cashierShowsEmptyAndNoSearchResults() {
        val state = mutableStateOf<CatalogState>(CatalogState.Ready(Catalog()))
        var openedCatalog = false
        compose.setContent {
            WarungTheme { Cashier(state.value, emptyMap(), { _, _ -> }, {}, { openedCatalog = true }, {}) }
        }
        compose.onNodeWithText("Belum ada produk").assertIsDisplayed()
        compose.onNodeWithText("Kelola katalog").performClick()
        compose.runOnIdle {
            assertTrue(openedCatalog)
            state.value = CatalogState.Ready(fixture)
        }
        compose.onNodeWithText("Cari produk").performTextInput("Tidak ada")
        compose.onNodeWithText("Produk tidak ditemukan").assertIsDisplayed()
    }

    @Test fun dashboardShowsActionsAndCatalogOverview() {
        compose.setContent {
            WarungTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Dashboard(CatalogState.Ready(fixture), {}, {}, {})
                }
            }
        }
        compose.onNodeWithText("Buka kasir").assertIsDisplayed()
        compose.onNodeWithText("Ringkasan katalog").assertIsDisplayed()
        compose.onNodeWithText("Transaksi terbaru").assertIsDisplayed()
    }

    @Test fun cashierScreenshot() {
        compose.setContent {
            WarungTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Cashier(CatalogState.Ready(fixture), mapOf("tea" to 1), { _, _ -> }, {}, {}, {})
                }
            }
        }
        compose.onNodeWithText("1 item").assertIsDisplayed()
        compareScreenshot("cashier")
    }

    @Test fun longNameAndStepperFit320DpAt200Percent() {
        val product = Product("long", "Paket nasi uduk ayam goreng sambal dan lalapan untuk keluarga", 15000, null)
        val count = mutableStateOf(1)
        compose.setContent {
            WarungTheme {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                    Box(Modifier.width(320.dp)) {
                        CartScreen(listOf(CartLine(product, count.value))) { _, delta -> count.value += delta }
                    }
                }
            }
        }
        compose.onNodeWithText(product.name).assertIsDisplayed()
        compose.onNodeWithContentDescription("Tambah ${product.name}").performClick()
        compose.onNodeWithContentDescription("2 item ${product.name}").assertIsDisplayed()
        compose.onNodeWithContentDescription("Kurangi ${product.name}").performClick()
        compose.onNodeWithContentDescription("1 item ${product.name}").assertIsDisplayed()
    }

    @Test fun cartScreenshot() {
        compose.setContent {
            WarungTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    CartScreen(listOf(CartLine(fixture.products.first(), 1))) { _, _ -> }
                }
            }
        }
        compareScreenshot("cart")
    }

    private fun compareScreenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = compose.onNodeWithTag(name).captureToImage().asAndroidBitmap()
        if (InstrumentationRegistry.getArguments().getString("recordScreenshots") == "true") {
            val directory = File(checkNotNull(instrumentation.targetContext.getExternalFilesDir(null)), "screenshots")
            check(directory.exists() || directory.mkdirs())
            File(directory, "$name.png").outputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            return
        }
        val expected = instrumentation.context.assets.open("screenshots/$name.png").use(BitmapFactory::decodeStream)
        assertEquals(expected.width, bitmap.width)
        assertEquals(expected.height, bitmap.height)
        val pixels = bitmap.width * bitmap.height
        val actualColors = IntArray(pixels)
        val expectedColors = IntArray(pixels)
        bitmap.getPixels(actualColors, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        expected.getPixels(expectedColors, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val changed = actualColors.indices.count { actualColors[it] != expectedColors[it] }
        assertTrue("$name changed at $changed of $pixels pixels", changed <= pixels / 1000)
    }
}
