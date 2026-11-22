package id.umkmku.pos.data.portability

import java.io.ByteArrayOutputStream
import java.io.StringWriter
import java.math.BigInteger
import org.junit.Assert.*
import org.junit.Test

class PortabilityTest {
    private val password = "fixture-only-passphrase".toCharArray()

    @Test fun authenticatesEverySegmentAndRejectsTruncationAndTrailingBytes() {
        val plaintext = ByteArray(150_000) { (it % 251).toByte() }
        fun encrypted() = ByteArrayOutputStream().apply {
            BackupCipher.encrypt(password, this).use { it.write(plaintext) }
        }.toByteArray()
        val bytes = encrypted()
        assertFalse(bytes.contentEquals(encrypted()))
        assertArrayEquals(plaintext, BackupCipher.decrypt(password, bytes.inputStream()).use { it.readBytes() })
        for (invalid in listOf(bytes.copyOf(20), bytes.copyOf(bytes.size - 3), bytes + 1.toByte(),
            bytes.clone().apply { this[30] = (this[30].toInt() xor 1).toByte() },
            bytes.clone().apply { this[70_000] = (this[70_000].toInt() xor 1).toByte() })) {
            assertTrue(runCatching { BackupCipher.decrypt(password, invalid.inputStream()).use { it.readBytes() } }.isFailure)
        }
        assertTrue(runCatching {
            BackupCipher.decrypt("incorrect".toCharArray(), bytes.inputStream()).use { it.readBytes() }
        }.isFailure)
    }

    @Test fun csvPreservesExactNumbersAndMakesFormulaCellsInert() {
        val output = StringWriter()
        CsvWriter(output).row("normal, \"kutip\"\r\nbaris", "  =SUM(1,2)", "＠fixture", "\tfixture", "\u200B-12",
            -15L, BigInteger("123456789012345678901"), null)
        assertEquals(
            "\"normal, \"\"kutip\"\"\r\nbaris\",\"'  =SUM(1,2)\",\"'＠fixture\",\"'\tfixture\",\"'\u200B-12\",-15,123456789012345678901,\r\n",
            output.toString(),
        )
    }
}
