package id.umkmku.pos.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class CatalogTest {
    @Test fun cashTenderIsPositiveWholeRupiah() {
        assertEquals(20_000L, parseCashTender(" 20000 "))
        assertEquals(MAX_SALE_IDR, parseCashTender("999999999999"))
        listOf("0", "20.000", "1000,00", "-1", "+1", "1000000000000", "").forEach {
            assertNull(parseCashTender(it))
        }
    }

    @Test fun priceUsesBoundedIntegerRupiah() {
        assertEquals(15000L, parsePrice("15000"))
        assertEquals(999999999L, parsePrice("999999999"))
        listOf("", "0", "-5", "15.000", "15,50", "1000000000", "9223372036854775808").forEach {
            assertNull(parsePrice(it))
        }
        assertEquals("Rp15.000", rupiah(15000))
    }

    @Test fun normalizedCategoryNamesDetectDuplicates() {
        assertEquals("minuman", categoryKey(" ＭＩＮＵＭＡＮ "))
        assertEquals("Nasi uduk", validName(" Nasi uduk "))
        assertThrows(CatalogException::class.java) { validName("  ") }
        assertThrows(CatalogException::class.java) { validName("a".repeat(81)) }
    }

    @Test fun cartUsesCurrentCatalogAndExcludesArchivedItems() {
        val current = Product("rice", "Nasi uduk", 12000, null)
        val lines = cartLines(Catalog(listOf(current)), mapOf("rice" to 3, "archived" to 2))
        assertEquals(listOf(CartLine(current, 3)), lines)
        assertEquals(36000L, lines.single().subtotal)
    }
}
