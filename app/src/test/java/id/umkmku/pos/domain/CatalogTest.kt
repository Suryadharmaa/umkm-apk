package id.umkmku.pos.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class CatalogTest {
    @Test fun restorationIgnoresMalformedDuplicateAndOutOfRangeCartRows() {
        assertEquals(mapOf("tea" to 2, "rice" to 9999), restoreCart(
            listOf("tea:2", "tea:4", "rice:9999", "bad", "x:10000", ":1", "z:-1", 123)
        ))
        assertEquals(MAX_CART_PRODUCTS, restoreCart(List(300) { "product$it:1" }).size)
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
