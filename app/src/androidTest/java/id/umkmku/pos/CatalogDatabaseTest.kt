package id.umkmku.pos

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import id.umkmku.pos.data.CatalogDatabase
import id.umkmku.pos.data.RoomCatalogRepository
import id.umkmku.pos.domain.CatalogException
import id.umkmku.pos.domain.EditCatalog
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CatalogDatabaseTest {
    @Test fun catalogCrudSurvivesClosingAndReopeningFile() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = "test-${UUID.randomUUID()}.db"
        fun open() = Room.databaseBuilder(context, CatalogDatabase::class.java, file).build()
        try {
            val productId = open().useDatabase { db ->
                val repository = RoomCatalogRepository(db)
                val edits = EditCatalog(repository)
                edits.category(null, "Minuman")
                val category = repository.observe().first().categories.single()
                try {
                    edits.category(null, " ＭＩＮＵＭＡＮ ")
                    fail("Duplicate category should be rejected")
                } catch (_: CatalogException) { }
                edits.category(category.id, "Minuman dingin")
                edits.product(null, "Es teh", "5000", category.id)
                val product = repository.observe().first().products.single()
                edits.product(product.id, "Es teh manis", "6000", category.id)
                product.id
            }
            open().useDatabase { db ->
                val repository = RoomCatalogRepository(db)
                val edits = EditCatalog(repository)
                val reopened = repository.observe().first()
                val category = reopened.categories.single()
                assertEquals("Minuman dingin", category.name)
                assertEquals("Es teh manis", reopened.products.single().name)
                assertEquals(6000L, reopened.products.single().price)
                assertEquals(category.id, reopened.products.single().categoryId)
                edits.archiveCategory(category.id)
                val uncategorized = repository.observe().first()
                assertTrue(uncategorized.categories.isEmpty())
                assertNull(uncategorized.products.single().categoryId)
                edits.archiveProduct(productId)
            }
            open().useDatabase { db ->
                val repository = RoomCatalogRepository(db)
                assertTrue(repository.observe().first().products.isEmpty())
                db.openHelper.readableDatabase.query("SELECT archived_at FROM product WHERE id = ?", arrayOf(productId)).use {
                    assertTrue(it.moveToFirst())
                    assertNotNull(it.getLong(0).takeIf { value -> value > 0 })
                }
            }
        } finally {
            context.deleteDatabase(file)
        }
        Unit
    }
}

private inline fun <T> CatalogDatabase.useDatabase(block: (CatalogDatabase) -> T): T =
    try {
        block(this)
    } finally {
        close()
    }
