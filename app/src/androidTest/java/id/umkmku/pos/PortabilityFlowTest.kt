package id.umkmku.pos

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import id.umkmku.pos.data.CatalogDatabase
import id.umkmku.pos.data.portability.businessIsEmpty
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PortabilityFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun writesBackupAndCsvThroughDocumentsUiAndChecksRestoreGuard() {
        val showEvidence = InstrumentationRegistry.getArguments().getString("capturePortability") == "true"
        val empty = businessIsEmpty()
        click("Keuangan")
        click("Data")
        await("Cadangan terenkripsi")
        if (showEvidence) capture("kelola-data")
        click("Buat cadangan")
        val passphrase = "fixture offline 0123456"
        compose.onNodeWithText("Kata sandi").performTextInput(passphrase)
        compose.onNodeWithText("Ulangi kata sandi").performTextInput(passphrase)
        click("Pilih lokasi")
        tapNative { it.equals("Save", ignoreCase = true) }
        await("Cadangan terenkripsi tersimpan.")
        if (showEvidence) capture("cadangan-tersimpan")

        compose.onNodeWithTag("data-tools").performScrollToNode(hasText("Simpan CSV"))
        click("Simpan CSV")
        tapNative { it.equals("Save", ignoreCase = true) }
        await("CSV tersimpan.")
        if (showEvidence) capture("csv-tersimpan")

        click("Pilih berkas cadangan")
        tapNative { it.startsWith("UMKM-KU-") && it.contains(".umkmbackup") }
        await("Konfirmasi pemulihan")
        compose.onNodeWithText("Pulihkan data").assertIsNotEnabled()
        compose.onNodeWithText("Kata sandi").performTextInput(passphrase)
        compose.onNodeWithText("Pulihkan data").assertIsNotEnabled()
        click("Pulihkan semua data ke aplikasi kosong.")
        click("Pulihkan data")
        if (empty) await("Pemulihan selesai. Basis data berhasil dibuka ulang dan diverifikasi.")
        else await("Pemulihan memerlukan aplikasi kosong. Data usaha saat ini tetap tersimpan.")
        if (showEvidence) capture("hasil-pemulihan")
    }

    private fun businessIsEmpty(): Boolean = runBlocking(Dispatchers.IO) {
        val db = Room.databaseBuilder(instrumentation.targetContext, CatalogDatabase::class.java, "umkm-catalog.db").build()
        try { db.openHelper.writableDatabase.businessIsEmpty() } finally { db.close() }
    }

    private fun await(text: String) {
        compose.waitUntil(30_000) {
            try {
                compose.onAllNodesWithText(text, substring = false).fetchSemanticsNodes().isNotEmpty()
            } catch (transition: IllegalStateException) {
                if (transition.message?.startsWith("No compose hierarchies found") == true) false else throw transition
            }
        }
    }

    private fun click(text: String) {
        await(text)
        compose.onNodeWithText(text).performClick()
    }

    private fun tapNative(match: (String) -> Boolean) {
        val label = waitForNative(match)
        var node: AccessibilityNodeInfo? = label
        while (node != null) {
            if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return
            node = node.parent
        }
        val bounds = Rect().also(label::getBoundsInScreen)
        check(!bounds.isEmpty)
        val tap = instrumentation.uiAutomation.executeShellCommand("input tap ${bounds.centerX()} ${bounds.centerY()}")
        ParcelFileDescriptor.AutoCloseInputStream(tap).use { it.readBytes() }
    }

    private fun waitForNative(match: (String) -> Boolean): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + 20_000
        while (SystemClock.uptimeMillis() < deadline) {
            val root = instrumentation.uiAutomation.rootInActiveWindow
            if (root != null) {
                val nodes = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
                while (nodes.isNotEmpty()) {
                    val node = nodes.removeFirst()
                    if (node.isEnabled && node.isVisibleToUser && (match(node.text?.toString().orEmpty()) ||
                        match(node.contentDescription?.toString().orEmpty()))) return node
                    repeat(node.childCount) { index -> node.getChild(index)?.let(nodes::addLast) }
                }
            }
            SystemClock.sleep(150)
        }
        error("Document picker item not found")
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val deadline = SystemClock.uptimeMillis() + 6_000
        var screenshot = instrumentation.uiAutomation.takeScreenshot()
        while (screenshot == null && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(150)
            screenshot = instrumentation.uiAutomation.takeScreenshot()
        }
        val bitmap = checkNotNull(screenshot)
        val dir = File(checkNotNull(instrumentation.targetContext.getExternalFilesDir(null)), "screenshots")
        check(dir.exists() || dir.mkdirs())
        File(dir, "$name.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }
}
