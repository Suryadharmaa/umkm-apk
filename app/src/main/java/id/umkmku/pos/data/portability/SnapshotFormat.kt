package id.umkmku.pos.data.portability

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal const val currentBackupSchema = 6
internal const val maxBackupBytes = 512L * 1024 * 1024

// Table and column order is part of format 1. Append a new definition for a new schema.
private data class Table(val name: String, val key: String, val fields: String) {
    val columns = fields.split(" ").map { it.trimEnd('#', '~') }
    val blobIndex = fields.split(" ").indexOfFirst { it.endsWith('~') }
    val types = fields.split(" ").map { it.last().takeIf { last -> last == '#' || last == '~' } }
}

private val tables = listOf(
    Table("category", "id", "id name name_key archived_at#"),
    Table("product", "id", "id name unit_idr# category_id archived_at#"),
    Table("sale", "id", "id draftSlot# openedAt#"),
    Table("sale_line", "saleId,productId", "saleId productId nameSnapshot unitIdr# quantity#"),
    Table("payment", "saleId", "saleId operationKey totalIdr# tenderIdr# paidAt# method"),
    Table("journal", "id", "id saleId kind"),
    Table("journal_line", "journalId,account", "journalId account signedIdr#"),
    Table("qris_profile", "id", "id merchantId merchantName payload image~ activeSlot#"),
    Table("qris_order", "saleId", "saleId profileId totalIdr# startedAt# status"),
    Table("qris_evidence", "saleId", "saleId merchantId reference observedIdr# observedAt#"),
    Table("financial_movement", "journalId", "journalId account openingAccount amountIdr# category note effectiveAt# recordedAt#"),
    Table("settlement", "id", "id state reason updatedAt# postedReference journalId postedAt# effectiveAt# provider merchantId reference gross fee net payoutTime attested#"),
    Table("settlement_payment", "settlementId,saleId", "settlementId saleId postedSaleId"),
    Table("financial_correction", "journalId", "journalId originalJournalId kind reason funding provider merchant reference settlementId evidencedIdr# evidenceTime evidenceZone effectiveAt# recordedAt# saleId bankReference"),
)

private const val maxRowsPerTable = 2_000_000
private const val maxText = 64 * 1024
private const val maxBlob = 2 * 1024 * 1024
private const val chunkSize = 64 * 1024

internal fun SupportSQLiteDatabase.businessIsEmpty(): Boolean {
    val select = tables.joinToString(" UNION ALL ") { "SELECT 1 FROM ${it.name}" }
    return query("SELECT 1 FROM ($select) LIMIT 1").use { !it.moveToFirst() }
}

private fun tablesFor(schema: Int) = when (schema) {
    5 -> tables.dropLast(1)
    currentBackupSchema -> tables
    else -> throw BackupFormatException()
}

internal suspend fun writeSnapshot(
    database: SupportSQLiteDatabase, output: DataOutputStream, schema: Int = currentBackupSchema,
) {
    check(database.version == currentBackupSchema)
    val coroutine = currentCoroutineContext()
    output.writeInt(schema)
    for (table in tablesFor(schema)) {
        val projection = table.columns.mapIndexed { index, field ->
            if (index == table.blobIndex) "length($field)" else field
        }.joinToString()
        val count = database.query("SELECT COUNT(*) FROM ${table.name}").use {
            check(it.moveToFirst())
            it.getLong(0)
        }
        check(count in 0..maxRowsPerTable.toLong())
        output.writeInt(count.toInt())
        database.query("SELECT $projection FROM ${table.name} ORDER BY ${table.key}").use { rows ->
            while (rows.moveToNext()) {
                coroutine.ensureActive()
                table.types.forEachIndexed { index, type ->
                    if (rows.isNull(index)) output.writeByte(0)
                    else {
                        output.writeByte(1)
                        when (type) {
                            '#' -> {
                                check(rows.getType(index) == Cursor.FIELD_TYPE_INTEGER)
                                output.writeLong(rows.getLong(index))
                            }
                            '~' -> writeImage(database, rows, index, output)
                            else -> {
                                check(rows.getType(index) == Cursor.FIELD_TYPE_STRING)
                                val bytes = rows.getString(index).toByteArray(Charsets.UTF_8)
                                check(bytes.size <= maxText)
                                output.writeInt(bytes.size)
                                output.write(bytes)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun writeImage(database: SupportSQLiteDatabase, rows: Cursor, index: Int, output: DataOutputStream) {
    val bytes = rows.getLong(index)
    check(bytes in 0..maxBlob.toLong())
    output.writeInt(bytes.toInt())
    var written = 0L
    while (written < bytes) {
        val count = minOf(bytes - written, chunkSize.toLong()).toInt()
        database.query("SELECT substr(image, ?, ?) FROM qris_profile WHERE id = ?",
            arrayOf(written + 1, count, rows.getString(0))).use {
            check(it.moveToFirst())
            val chunk = it.getBlob(0)
            check(chunk.size == count)
            output.write(chunk)
        }
        written += count
    }
}

internal suspend fun readSnapshot(input: DataInputStream, database: SupportSQLiteDatabase? = null): Int {
    val coroutine = currentCoroutineContext()
    if (database != null) check(database.version == currentBackupSchema)
    val schema = input.readInt()
    for (table in tablesFor(schema)) {
        val count = input.readInt()
        if (count !in 0..maxRowsPerTable) throw BackupFormatException()
        val parameters = table.columns.joinToString { "?" }
        val statement = database?.compileStatement(
            "INSERT INTO ${table.name} (${table.columns.joinToString()}) VALUES ($parameters)",
        )
        statement.use {
            repeat(count) {
                coroutine.ensureActive()
                table.types.forEachIndexed { column, type ->
                    val value = when (input.readUnsignedByte()) {
                        0 -> null
                        1 -> when (type) {
                            '#' -> input.readLong()
                            '~' -> input.readBytesBounded(maxBlob)
                            else -> Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                                .decode(ByteBuffer.wrap(input.readBytesBounded(maxText))).toString()
                        }
                        else -> throw BackupFormatException()
                    }
                    val position = column + 1
                    when (value) {
                        null -> statement?.bindNull(position)
                        is Long -> statement?.bindLong(position, value)
                        is String -> statement?.bindString(position, value)
                        is ByteArray -> statement?.bindBlob(position, value)
                    }
                }
                if (statement != null) {
                    statement.executeInsert()
                    statement.clearBindings()
                }
            }
        }
    }
    // Reading EOF also makes Tink check the final segment and reject appended bytes.
    if (input.read() != -1) throw BackupFormatException()
    return schema
}

private fun DataInputStream.readBytesBounded(max: Int): ByteArray {
    val size = readInt()
    if (size !in 0..max) throw BackupFormatException()
    return ByteArray(size).also(::readFully)
}
