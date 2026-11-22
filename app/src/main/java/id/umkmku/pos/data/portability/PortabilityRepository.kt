package id.umkmku.pos.data.portability

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import id.umkmku.pos.data.CatalogDatabase
import id.umkmku.pos.domain.ReportFilter
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.DigestInputStream
import java.security.DigestOutputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class RestoreNeedsEmptyDatabase : IllegalStateException()
internal class RestoreRejected : IllegalArgumentException()
internal enum class RestoreResult { REOPEN_VERIFIED, IMPORTED_REOPEN_UNVERIFIED }

@Singleton
internal class PortabilityRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val db: CatalogDatabase,
) {
    private val gate = Mutex()

    suspend fun createBackup(uri: Uri, password: CharArray) =
        createBackup(password) { openDestination(uri) }

    suspend fun restoreBackup(uri: Uri, password: CharArray, confirmed: Boolean): RestoreResult =
        restoreBackup(password, confirmed) {
            require(uri.scheme == "content")
            checkNotNull(context.contentResolver.openInputStream(uri))
        }

    suspend fun export(uri: Uri, kind: CsvKind, filter: ReportFilter) = withContext(Dispatchers.IO) {
        gate.withLock {
            openDestination(uri).bufferedWriter(Charsets.UTF_8).use { output ->
                db.withTransaction { db.exportCsv(output, kind, filter) }
            }
        }
    }

    internal suspend fun createBackup(password: CharArray, destination: () -> OutputStream) =
        withContext(Dispatchers.IO) {
            require(password.size in 12..128)
            gate.withLock {
                withStagingFile { file ->
                    file.outputStream().buffered().use { raw ->
                        DataOutputStream(BackupCipher.encrypt(password, BoundedOutput(raw)).buffered()).use { stream ->
                            db.withTransaction { writeSnapshot(db.openHelper.writableDatabase, stream) }
                        }
                    }
                    destination().buffered().use { target ->
                        file.inputStream().use { source -> copyBounded(source, target) }
                    }
                }
            }
        }

    internal suspend fun restoreBackup(
        password: CharArray, confirmed: Boolean, source: () -> InputStream,
    ): RestoreResult = withContext(Dispatchers.IO) {
        require(confirmed && password.size in 1..128)
        gate.withLock {
            if (!db.withTransaction { db.openHelper.writableDatabase.businessIsEmpty() }) {
                throw RestoreNeedsEmptyDatabase()
            }
            withStagingFile { file ->
                val verified = try {
                    source().use { input -> file.outputStream().buffered().use { copyBounded(input, it) } }
                    file.readVerified(password)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    throw RestoreRejected()
                }
                try {
                    db.withTransaction {
                        val database = db.openHelper.writableDatabase
                        if (!database.businessIsEmpty()) throw RestoreNeedsEmptyDatabase()
                        val imported = file.readVerified(password, database)
                        check(imported.schema == verified.schema && imported.digest.contentEquals(verified.digest))
                        db.auditSnapshot()
                        check(db.snapshotDigest(verified.schema).contentEquals(verified.digest))
                    }
                } catch (refused: RestoreNeedsEmptyDatabase) {
                    throw refused
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    throw RestoreRejected()
                }
                withContext(NonCancellable) {
                    try {
                        val name = checkNotNull(db.openHelper.databaseName)
                        val reopened = Room.databaseBuilder(context, CatalogDatabase::class.java, name).build()
                        try {
                            reopened.withTransaction {
                                check(reopened.snapshotDigest(verified.schema).contentEquals(verified.digest))
                            }
                        } finally {
                            reopened.close()
                        }
                        RestoreResult.REOPEN_VERIFIED
                    } catch (_: Exception) {
                        RestoreResult.IMPORTED_REOPEN_UNVERIFIED
                    }
                }
            }
        }
    }

    private fun openDestination(uri: Uri): OutputStream {
        require(uri.scheme == "content")
        return checkNotNull(context.contentResolver.openOutputStream(uri, "wt"))
    }

    private suspend fun <T> withStagingFile(action: suspend (File) -> T): T {
        val file = File.createTempFile("umkm-", ".encrypted", context.cacheDir)
        return try {
            action(file)
        } finally {
            file.delete()
        }
    }
}

private data class VerifiedSnapshot(val schema: Int, val digest: ByteArray)

private suspend fun File.readVerified(
    password: CharArray, into: androidx.sqlite.db.SupportSQLiteDatabase? = null,
): VerifiedSnapshot {
    val digest = MessageDigest.getInstance("SHA-256")
    val schema = inputStream().buffered().use { raw ->
        DataInputStream(DigestInputStream(BackupCipher.decrypt(password, raw).buffered(), digest)).use {
            readSnapshot(it, into)
        }
    }
    return VerifiedSnapshot(schema, digest.digest())
}

private suspend fun CatalogDatabase.snapshotDigest(schema: Int): ByteArray {
    val digest = MessageDigest.getInstance("SHA-256")
    DataOutputStream(DigestOutputStream(DiscardOutput, digest)).use {
        writeSnapshot(openHelper.writableDatabase, it, schema)
    }
    return digest.digest()
}

private object DiscardOutput : OutputStream() {
    override fun write(byte: Int) = Unit
    override fun write(bytes: ByteArray, offset: Int, length: Int) = Unit
}

private class BoundedOutput(destination: OutputStream) : FilterOutputStream(destination) {
    private var count = 0L

    override fun write(byte: Int) {
        count++
        if (count > maxBackupBytes) throw BackupFormatException()
        out.write(byte)
    }

    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        count += length
        if (count > maxBackupBytes) throw BackupFormatException()
        out.write(bytes, offset, length)
    }
}

private suspend fun copyBounded(input: InputStream, output: OutputStream) {
    val coroutine = currentCoroutineContext()
    val buffer = ByteArray(64 * 1024)
    var total = 0L
    while (true) {
        coroutine.ensureActive()
        val read = input.read(buffer)
        if (read == -1) break
        if (read == 0) throw BackupFormatException()
        total += read
        if (total > maxBackupBytes) throw BackupFormatException()
        output.write(buffer, 0, read)
    }
}
