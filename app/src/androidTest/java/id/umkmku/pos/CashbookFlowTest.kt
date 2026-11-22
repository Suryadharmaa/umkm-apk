package id.umkmku.pos

import android.graphics.Bitmap
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import id.umkmku.pos.domain.cashbook
import id.umkmku.pos.feature.CashRead
import id.umkmku.pos.ui.CashbookScreen
import id.umkmku.pos.ui.WarungTheme
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CashbookFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun opensAnAccountAndPostsCategorizedExpenseThenReadsHistory() {
        val capture = InstrumentationRegistry.getArguments().getString("captureCashbook") == "true"
        val note = if (capture) "Fixture sewa warung" else "Fixture sewa ${System.nanoTime()}"
        click("Keuangan")
        await("Saldo belum diketahui")
        if (capture) screenshot("saldo-belum-diketahui")
        click("Isi saldo awal kas tunai")
        input("Nominal (Rp)", "0")
        hideKeyboard()
        click("Catat saldo awal", scroll = true)
        await("Rincian mutasi")
        compose.onNodeWithText("Debit · Kas tunai").assertIsDisplayed()
        compose.onNodeWithText("Kredit · Modal pemilik").assertIsDisplayed()
        compose.onNodeWithContentDescription("Kembali").performClick()

        click("Catat mutasi")
        click("Pendapatan lain")
        click("Beban operasional")
        input("Nominal (Rp)", "2500")
        hideKeyboard()
        compose.onNodeWithText("Simpan mutasi").assertIsNotEnabled()
        click("Pilih kategori", scroll = true)
        click("Sewa")
        input("Catatan (opsional)", note)
        hideKeyboard()
        if (capture) {
            compose.onNodeWithText("Simpan mutasi").performScrollTo()
            screenshot("form-beban")
        }
        click("Simpan mutasi", scroll = true)
        await("Rincian mutasi")
        compose.onNodeWithText("Kategori · Sewa").assertIsDisplayed()
        compose.onNodeWithText("Debit · Beban operasional").assertIsDisplayed()
        compose.onNodeWithText("Kredit · Kas tunai").assertIsDisplayed()
        if (capture) screenshot("rincian-jurnal")
        compose.onNodeWithContentDescription("Kembali").performClick()

        await("Saldo tercatat saat ini")
        if (capture) await("Saldo tercatat negatif. Periksa mutasi akun.")
        // Earlier checkout coverage adds rows to the daily card. Scroll to the actual values.
        compose.onNodeWithTag("cashbook").performScrollToNode(hasText("Beban operasional"))
        compose.onNodeWithText("Beban operasional").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("cashbook").performScrollToNode(hasText("Sewa"))
        compose.onNodeWithText("Sewa").performScrollTo().assertIsDisplayed()
        if (capture) screenshot("ringkasan-harian")
        compose.onNodeWithTag("cashbook").performScrollToNode(hasText("Riwayat mutasi"))
        click("Riwayat mutasi")
        click("Kas tunai")
        await("−Rp2.500")
        if (capture) screenshot("riwayat-akun")
        click("Beban operasional")
        compose.activityRule.scenario.recreate()
        await(note)
        compose.onNodeWithText(note).assertIsDisplayed()
    }

    private fun input(label: String, value: String) {
        await(label)
        compose.onNodeWithText(label).performScrollTo().performTextInput(value)
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

    private fun await(text: String) {
        compose.waitUntil(10_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val location = File(checkNotNull(instrumentation.targetContext.getExternalFilesDir(null)), "screenshots")
        check(location.exists() || location.mkdirs())
        File(location, "$name.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }
}

@RunWith(AndroidJUnit4::class)
class CashbookAccessibilityTest {
    @get:Rule val compose = createComposeRule()

    @Test fun dailyDateControlsHaveAccessibleActionsAt320DpAnd200PercentText() {
        val today = LocalDate.of(2026, 11, 21)
        val zone = ZoneId.of("Asia/Jakarta")
        var selected: LocalDate? = null
        compose.setContent {
            WarungTheme {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                    Box(Modifier.width(320.dp)) {
                        CashbookScreen(CashRead.Ready(cashbook(emptyList(), today, zone)), today, zone, {},
                            { selected = it }, {}, {})
                    }
                }
            }
        }
        compose.onNodeWithText("Catat mutasi").assertHasClickAction()
        compose.onNodeWithTag("cashbook").performScrollToNode(hasContentDescription("Hari sebelumnya"))
        compose.onNodeWithContentDescription("Hari berikutnya").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Hari sebelumnya").assertHasClickAction().performClick()
        compose.runOnIdle { assertEquals(today.minusDays(1), selected) }
    }
}
