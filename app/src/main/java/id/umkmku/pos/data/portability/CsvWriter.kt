package id.umkmku.pos.data.portability

import java.io.Writer
import java.math.BigInteger
import java.text.Normalizer

internal class CsvWriter(private val output: Writer) {
    fun row(vararg fields: Any?) {
        fields.forEachIndexed { index, value ->
            if (index != 0) output.write(",")
            when (value) {
                null -> Unit
                is Long, is Int, is BigInteger -> output.write(value.toString())
                is String -> output.write(csvText(value))
                else -> error("Unsupported CSV field")
            }
        }
        output.write("\r\n")
    }
}

internal fun csvText(raw: String): String {
    val normalized = Normalizer.normalize(raw, Normalizer.Form.NFKC)
    val visible = normalized.trimStart { it.isWhitespace() || it.isISOControl() ||
        Character.getType(it) == Character.FORMAT.toInt() }
    val needsPrefix = normalized.firstOrNull() in listOf('\t', '\r', '\n') ||
        visible.firstOrNull() in listOf('=', '+', '-', '@')
    val content = if (needsPrefix) "'$raw" else raw
    return "\"${content.replace("\"", "\"\"")}\""
}
