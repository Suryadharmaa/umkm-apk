package id.umkmku.pos.data.portability

import com.google.crypto.tink.StreamingAead
import com.google.crypto.tink.subtle.AesGcmHkdfStreaming
import java.io.DataInputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

internal class BackupFormatException : IllegalArgumentException()

internal object BackupCipher {
    private val magic = byteArrayOf(0x55, 0x4d, 0x4b, 0x4d, 0x4b, 0x55, 0x42, 0x01)
    private val random = SecureRandom()
    private const val saltSize = 32
    private const val iterations = 600_000

    fun encrypt(password: CharArray, destination: OutputStream): OutputStream {
        val salt = ByteArray(saltSize).also(random::nextBytes)
        val header = magic + salt
        val aead = primitive(password, salt)
        destination.write(header)
        return aead.newEncryptingStream(destination, header)
    }

    fun decrypt(password: CharArray, source: InputStream): InputStream {
        val header = ByteArray(magic.size + saltSize)
        DataInputStream(source).readFully(header)
        if (!header.copyOfRange(0, magic.size).contentEquals(magic)) throw BackupFormatException()
        val salt = header.copyOfRange(magic.size, header.size)
        return primitive(password, salt).newDecryptingStream(source, header)
    }

    private fun primitive(password: CharArray, salt: ByteArray): StreamingAead {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        try {
            val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return try {
                AesGcmHkdfStreaming(key, "HmacSha256", 32, 64 * 1024, 0)
            } finally {
                key.fill(0)
            }
        } finally {
            spec.clearPassword()
        }
    }
}
