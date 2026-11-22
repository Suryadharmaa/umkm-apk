package id.umkmku.pos

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.umkmku.pos.domain.Catalog
import id.umkmku.pos.domain.Category
import id.umkmku.pos.domain.Product
import id.umkmku.pos.domain.SaleSnapshot
import id.umkmku.pos.feature.CashRead
import id.umkmku.pos.feature.CatalogState
import id.umkmku.pos.ui.CatalogScreen
import id.umkmku.pos.ui.Dashboard
import id.umkmku.pos.ui.MerchantNavigation
import id.umkmku.pos.ui.Tab
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MerchantRedesignTest {
    @get:Rule val compose = createComposeRule()

    @Test fun fiveDestinationsFit320DpAndRemainSelectableAt200Percent() {
        val selected = mutableStateOf(Tab.HOME)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                Box(Modifier.width(320.dp)) {
                    MerchantNavigation(selected.value, { selected.value = it })
                }
            }
        }
        val tops = Tab.entries.map { tab ->
            val node = compose.onNodeWithTag("destination-${tab.route}")
            node.assertIsDisplayed().assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
            node.performClick()
            node.assertIsSelected()
            node.fetchSemanticsNode().boundsInRoot.top
        }
        assertEquals(listOf(3, 2), tops.groupingBy { it }.eachCount().values.toList())
    }

    @Test fun catalogSearchCombinesWithCategoryAndCanClearBoth() {
        val tea = Product("tea", "Teh tawar", 6000, "drink")
        val bread = Product("bread", "Roti teh hijau", 21000, null)
        val fixture = Catalog(listOf(tea, bread), listOf(Category("drink", "Minuman")))
        var edited: Pair<Boolean, String?>? = null
        compose.setContent { CatalogScreen(CatalogState.Ready(fixture), {}) { product, id -> edited = product to id } }
        compose.onNodeWithText("Cari produk").performTextInput("TEH")
        compose.onNodeWithText(tea.name).assertIsDisplayed()
        compose.onNodeWithText(bread.name).assertIsDisplayed()
        compose.onNodeWithContentDescription("Filter kategori").performClick()
        compose.onNodeWithTag("catalog-category-drink").performClick()
        compose.onNodeWithText(bread.name).assertDoesNotExist()
        compose.onNodeWithText("Cari produk").performTextInput(" x")
        compose.onNodeWithText("Produk tidak ditemukan").assertIsDisplayed()
        compose.onNodeWithText("Bersihkan filter").performClick()
        compose.onNodeWithText(bread.name).performClick()
        compose.runOnIdle { assertEquals(true to bread.id, edited) }
    }

    @Test fun catalogLoadingErrorAndEmptyShowDistinctRecovery() {
        val state = mutableStateOf<CatalogState>(CatalogState.Loading)
        compose.setContent { CatalogScreen(state.value, { state.value = CatalogState.Ready(Catalog()) }) { _, _ -> } }
        compose.onNodeWithText("Memuat katalog…").assertIsDisplayed()
        compose.runOnIdle { state.value = CatalogState.Error }
        compose.onNodeWithText("Coba lagi").performClick()
        compose.onNodeWithText("Belum ada produk").assertIsDisplayed()
        compose.onNodeWithText("Tambah produk").assertIsDisplayed()
        compose.onNodeWithText("Kategori").performClick()
        compose.onNodeWithText("Belum ada kategori").assertIsDisplayed()
    }

    @Test fun longNameAndMaximumPriceRemainReadableAt200Percent() {
        val name = "Paket nasi uduk ayam goreng sambal dan lalapan untuk keluarga"
        val fixture = Catalog(listOf(Product("long", name, 999_999_999, null)))
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                Box(Modifier.width(320.dp)) {
                    CatalogScreen(CatalogState.Ready(fixture), {}) { _, _ -> }
                }
            }
        }
        compose.onNodeWithTag("catalog-list").performScrollToIndex(2)
        compose.onNodeWithText(name).assertIsDisplayed()
        compose.onNodeWithText("Rp999.999.999").assertIsDisplayed()
        compose.onNodeWithText("Tambah produk").assertIsDisplayed()
    }

    @Test fun dashboardActionsAndHistoryErrorRemainIndependent() {
        val history = mutableStateOf<CashRead<List<SaleSnapshot>>>(CashRead.Failed)
        var destination = ""
        compose.setContent {
            Dashboard(
                CatalogState.Ready(Catalog()), { destination = "cashier" }, { destination = "catalog" }, {},
                history.value, { destination = "history" }, onRetryHistory = { history.value = CashRead.Ready(emptyList()) },
            )
        }
        compose.onNodeWithText("Buka kasir").performClick()
        compose.runOnIdle { assertEquals("cashier", destination) }
        compose.onNodeWithText("Kelola katalog").performClick()
        compose.runOnIdle { assertEquals("catalog", destination) }
        compose.onNodeWithText("Lihat riwayat").performClick()
        compose.runOnIdle { assertEquals("history", destination) }
        compose.onNodeWithText("Riwayat gagal dimuat").assertIsDisplayed()
        compose.onNodeWithText("Muat ulang transaksi").performClick()
        compose.onNodeWithText("Belum ada transaksi").assertIsDisplayed()
    }
}
